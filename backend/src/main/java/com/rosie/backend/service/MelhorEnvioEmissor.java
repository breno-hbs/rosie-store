package com.rosie.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rosie.backend.exception.EtiquetaFalhouException;
import com.rosie.backend.exception.EtiquetaIndisponivelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// Etiqueta pela API do Melhor Envio (#25), com o mesmo cliente HTTP da cotação (MelhorEnvioApi). Docs:
// - carrinho:  POST /api/v2/me/cart                https://docs.melhorenvio.com.br/reference/inserir-fretes-no-carrinho
// - compra:    POST /api/v2/me/shipment/checkout   https://docs.melhorenvio.com.br/reference/compra-de-fretes-1
// - geração:   POST /api/v2/me/shipment/generate   https://docs.melhorenvio.com.br/reference/geracao-de-etiquetas
// - impressão: POST /api/v2/me/shipment/print      https://docs.melhorenvio.com.br/reference/impressao-de-etiquetas
// - rastreio:  POST /api/v2/me/shipment/tracking   https://docs.melhorenvio.com.br/reference/rastreio-de-envios
@Slf4j
public class MelhorEnvioEmissor implements EmissorEtiqueta {

    static final String CARRINHO = "/api/v2/me/cart";
    static final String COMPRA = "/api/v2/me/shipment/checkout";
    static final String GERACAO = "/api/v2/me/shipment/generate";
    static final String IMPRESSAO = "/api/v2/me/shipment/print";
    static final String RASTREIO = "/api/v2/me/shipment/tracking";

    static final String FALHA_GENERICA = "Não foi possível falar com o Melhor Envio agora. Tente de novo em instantes.";

    private final RestClient restClient;
    private final Remetente remetente;

    public MelhorEnvioEmissor(RestClient.Builder builder, String urlBase, String token, String userAgent,
                              Remetente remetente) {
        this.restClient = MelhorEnvioApi.cliente(builder, urlBase, token, userAgent);
        this.remetente = remetente;
    }

    // Dados da loja que saem na etiqueta (variáveis ROSIE_REMETENTE_*). documento: CPF (11 dígitos) ou
    // CNPJ (14); cep só com os dígitos.
    public record Remetente(String nome, String telefone, String email, String documento, String inscricaoEstadual,
                            String cep, String logradouro, String numero, String complemento, String bairro,
                            String cidade, String uf) {
    }

    @Override
    public String adicionarAoCarrinho(NovoEnvio envio) {
        Carrinho carrinho = new Carrinho(
                envio.servicoId(),
                Endereco.doRemetente(remetente),
                Endereco.doDestinatario(envio.destinatario()),
                envio.itens().stream()
                        .map(item -> new Produto(item.nome(), String.valueOf(item.quantidade()),
                                item.valorUnitario().toPlainString()))
                        .toList(),
                List.of(new Volume(envio.volume().alturaCm(), envio.volume().larguraCm(),
                        envio.volume().comprimentoCm(), envio.volume().pesoKg())),
                // Sem nota fiscal: vai com declaração de conteúdo (non_commercial), montada a partir de products
                new Opcoes(envio.valorDeclarado(), false, false, false, true,
                        List.of(new Tag("Pedido " + envio.pedidoId()))));

        EnvioCriado criado = chamar(() -> restClient.post().uri(CARRINHO)
                .contentType(MediaType.APPLICATION_JSON)
                .body(carrinho)
                .retrieve()
                .body(EnvioCriado.class));
        if (criado == null || criado.id() == null || criado.id().isBlank()) {
            log.warn("Melhor Envio não devolveu o id do envio ao pôr no carrinho");
            throw new EtiquetaFalhouException(FALHA_GENERICA);
        }
        return criado.id();
    }

    @Override
    public SituacaoEnvio consultar(String envioId) {
        Map<String, Rastreio> resposta = chamar(() -> restClient.post().uri(RASTREIO)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Ordens(List.of(envioId)))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Rastreio>>() {
                }));
        Rastreio rastreio = resposta == null ? null : resposta.get(envioId);
        if (rastreio == null) {
            log.warn("Melhor Envio não devolveu a situação do envio {}", envioId);
            throw new EtiquetaFalhouException(FALHA_GENERICA);
        }
        Etapa etapa = etapa(rastreio.status());
        if (etapa == null) {
            log.warn("Envio {} com situação inesperada no Melhor Envio: {}", envioId, rastreio.status());
            throw new EtiquetaFalhouException("O envio está com a situação \"" + rastreio.status()
                    + "\" no Melhor Envio. Confira no painel do Melhor Envio.");
        }
        boolean entregue = "delivered".equals(rastreio.status());
        return new SituacaoEnvio(etapa, rastreio.codigo(), entregue,
                entregue ? diaDaEntrega(envioId, rastreio.deliveredAt()) : null);
    }

    @Override
    public void comprar(String envioId) {
        chamar(() -> restClient.post().uri(COMPRA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Ordens(List.of(envioId)))
                .retrieve()
                .toBodilessEntity());
    }

    // A resposta vem por envio: { "<id>": { "status": true, "message": "Envio gerado com sucesso" } }
    @Override
    public void gerar(String envioId) {
        Map<String, Geracao> resposta = chamar(() -> restClient.post().uri(GERACAO)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Ordens(List.of(envioId)))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Geracao>>() {
                }));
        Geracao geracao = resposta == null ? null : resposta.get(envioId);
        if (geracao == null || !Boolean.TRUE.equals(geracao.status())) {
            String motivo = geracao == null || geracao.message() == null ? "" : ": " + geracao.message();
            log.warn("Melhor Envio não gerou a etiqueta do envio {}{}", envioId, motivo);
            throw new EtiquetaFalhouException("O Melhor Envio não gerou a etiqueta" + motivo);
        }
    }

    // Link privado (padrão do Melhor Envio): abre com a conta da loja logada no Melhor Envio, para os dados
    // da cliente na etiqueta não ficarem abertos a quem tiver o link
    @Override
    public String linkImpressao(String envioId) {
        Impressao impressao = chamar(() -> restClient.post().uri(IMPRESSAO)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PedidoImpressao("private", List.of(envioId)))
                .retrieve()
                .body(Impressao.class));
        if (impressao == null || impressao.url() == null || impressao.url().isBlank()) {
            log.warn("Melhor Envio não devolveu o link de impressão do envio {}", envioId);
            throw new EtiquetaFalhouException(FALHA_GENERICA);
        }
        return impressao.url();
    }

    // Situações do envio no Melhor Envio
    static Etapa etapa(String status) {
        if (status == null) return null;
        return switch (status) {
            case "pending" -> Etapa.NO_CARRINHO;
            case "released" -> Etapa.COMPRADO;
            case "generated", "posted", "delivered", "undelivered" -> Etapa.GERADO;
            case "canceled", "cancelled", "expired" -> Etapa.CANCELADO;
            default -> null;
        };
    }

    // delivered_at vem como "2026-10-17 14:46:00", no horário de Brasília (o da conta do Melhor Envio); se vier
    // com fuso (ISO 8601, ex.: "2026-10-17T17:46:00.000000Z"), é convertido para São Paulo. Sem data ou num
    // formato desconhecido: null, e a rotina usa o dia da consulta.
    static LocalDate diaDaEntrega(String envioId, String deliveredAt) {
        if (deliveredAt == null || deliveredAt.isBlank()) return null;
        String valor = deliveredAt.trim();
        try {
            return OffsetDateTime.parse(valor).atZoneSameInstant(PrazoTroca.FUSO_LOJA).toLocalDate();
        } catch (DateTimeParseException semFuso) {
            try {
                return LocalDateTime.parse(valor.replace(' ', 'T')).toLocalDate();
            } catch (DateTimeParseException formatoDesconhecido) {
                log.warn("Envio {} entregue com data num formato inesperado: {}", envioId, deliveredAt);
                return null;
            }
        }
    }

    private <T> T chamar(Chamada<T> chamada) {
        try {
            return chamada.executar();
        } catch (RestClientResponseException e) {
            throw traduzirErro(e);
        } catch (RestClientException e) {
            // Timeout, conexão recusada, JSON fora do formato
            log.warn("Melhor Envio sem resposta válida: {}", e.getMessage());
            throw new EtiquetaFalhouException(FALHA_GENERICA, e);
        }
    }

    private RuntimeException traduzirErro(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401 || status == 403) {
            log.error("Melhor Envio recusou o token ({}): confira MELHOR_ENVIO_TOKEN e MELHOR_ENVIO_URL", status);
            return new EtiquetaIndisponivelException("A etiqueta de envio está indisponível no momento.");
        }
        log.warn("Melhor Envio respondeu {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        // Envio recusado (saldo insuficiente, dado inválido...): a mensagem do Melhor Envio diz o que corrigir
        if (status >= 400 && status < 500) {
            String motivo = motivo(e);
            if (motivo != null) {
                return new EtiquetaFalhouException("O Melhor Envio recusou a etiqueta: " + motivo, e);
            }
        }
        return new EtiquetaFalhouException(FALHA_GENERICA, e);
    }

    // O Melhor Envio responde { "message": "...", "errors": { "campo": ["..."] } } ou { "error": "..." }:
    // vale a primeira mensagem de campo, senão a geral
    private static String motivo(RestClientResponseException e) {
        try {
            Map<String, Object> corpo = e.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
            });
            if (corpo == null) return null;
            String detalhe = primeiroTexto(corpo.get("errors"));
            if (detalhe == null) detalhe = primeiroTexto(corpo.get("error"));
            if (detalhe == null) detalhe = primeiroTexto(corpo.get("message"));
            return detalhe;
        } catch (RuntimeException ignorado) {
            return null;
        }
    }

    private static String primeiroTexto(Object valor) {
        if (valor instanceof String texto) return texto.isBlank() ? null : texto;
        Collection<?> filhos = valor instanceof Map<?, ?> mapa ? mapa.values()
                : valor instanceof Collection<?> lista ? lista : List.of();
        for (Object filho : filhos) {
            String texto = primeiroTexto(filho);
            if (texto != null) return texto;
        }
        return null;
    }

    @FunctionalInterface
    private interface Chamada<T> {
        T executar();
    }

    // ----- Formato da API (snake_case) -----

    record Carrinho(Integer service, Endereco from, Endereco to, List<Produto> products, List<Volume> volumes,
                    Opcoes options) {
    }

    // Pessoa física manda só document (CPF); empresa manda company_document (CNPJ)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Endereco(
            String name,
            String phone,
            String email,
            String document,
            @JsonProperty("company_document") String companyDocument,
            @JsonProperty("state_register") String stateRegister,
            String address,
            String complement,
            String number,
            String district,
            String city,
            @JsonProperty("country_id") String countryId,
            @JsonProperty("postal_code") String postalCode,
            @JsonProperty("state_abbr") String stateAbbr
    ) {

        static Endereco doRemetente(Remetente r) {
            String documento = r.documento();
            boolean empresa = documento.length() == 14;
            return new Endereco(r.nome(), r.telefone(), r.email(), empresa ? null : documento,
                    empresa ? documento : null, r.inscricaoEstadual(), r.logradouro(), r.complemento(), r.numero(),
                    r.bairro(), r.cidade(), "BR", r.cep(), r.uf());
        }

        static Endereco doDestinatario(Destinatario d) {
            return new Endereco(d.nome(), d.telefone(), d.email(), null, null, null, d.logradouro(),
                    d.complemento(), d.numero(), d.bairro(), d.cidade(), "BR", d.cep(), d.uf());
        }
    }

    // A documentação pede quantity e unitary_value como texto
    record Produto(String name, String quantity, @JsonProperty("unitary_value") String unitaryValue) {
    }

    // Medidas em cm, peso em kg
    record Volume(int height, int width, int length, BigDecimal weight) {
    }

    record Opcoes(
            @JsonProperty("insurance_value") BigDecimal insuranceValue,
            boolean receipt,
            @JsonProperty("own_hand") boolean ownHand,
            boolean reverse,
            @JsonProperty("non_commercial") boolean nonCommercial,
            List<Tag> tags
    ) {
    }

    // Aparece no painel do Melhor Envio, para a Roseane achar o envio pelo número do pedido
    record Tag(String tag) {
    }

    record Ordens(List<String> orders) {
    }

    record PedidoImpressao(String mode, List<String> orders) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EnvioCriado(String id, String protocol, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geracao(Boolean status, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Impressao(String url) {
    }

    // tracking é o código da transportadora; melhorenvio_tracking, o do Melhor Envio (também rastreável).
    // delivered_at fica null até a entrega.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Rastreio(
            String id,
            String status,
            String tracking,
            @JsonProperty("melhorenvio_tracking") String melhorEnvioTracking,
            @JsonProperty("delivered_at") String deliveredAt
    ) {

        String codigo() {
            if (tracking != null && !tracking.isBlank()) return tracking;
            return melhorEnvioTracking == null || melhorEnvioTracking.isBlank() ? null : melhorEnvioTracking;
        }
    }
}
