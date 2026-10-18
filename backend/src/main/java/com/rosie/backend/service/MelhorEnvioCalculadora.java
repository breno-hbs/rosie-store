package com.rosie.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.FreteFalhouException;
import com.rosie.backend.exception.FreteIndisponivelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Cotação pela API do Melhor Envio: POST /api/v2/me/shipment/calculate ("cálculo de fretes por produtos").
// Doc: https://docs.melhorenvio.com.br/reference/calculo-de-fretes-por-produtos
// O RestClient.Builder chega com os timeouts já definidos (FreteConfig); aqui entram a URL e os cabeçalhos.
@Slf4j
public class MelhorEnvioCalculadora implements CalculadoraFrete {

    static final String CAMINHO = "/api/v2/me/shipment/calculate";

    private final RestClient restClient;
    private final String cepOrigem;

    public MelhorEnvioCalculadora(RestClient.Builder builder, String urlBase, String token, String userAgent,
                                  String cepOrigem) {
        this.restClient = builder
                .baseUrl(urlBase)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                // O Melhor Envio exige User-Agent com o nome da aplicação e um e-mail de contato
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
        this.cepOrigem = cepOrigem;
    }

    @Override
    public List<OpcaoFrete> calcular(String cepDestino, List<PecaFrete> pecas) {
        Cotacao cotacao = new Cotacao(
                new Cep(cepOrigem),
                new Cep(cepDestino),
                pecas.stream().map(Produto::from).toList(),
                new Opcoes(false, false));

        List<Servico> servicos;
        try {
            servicos = restClient.post()
                    .uri(CAMINHO)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cotacao)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Servico>>() {
                    });
        } catch (RestClientResponseException e) {
            throw traduzirErro(e);
        } catch (RestClientException e) {
            // Timeout, conexão recusada, JSON que não é uma lista de serviços
            log.warn("Melhor Envio sem resposta válida: {}", e.getMessage());
            throw new FreteFalhouException("Não foi possível calcular o frete agora. Tente de novo em instantes.", e);
        }

        if (servicos == null) {
            throw new FreteFalhouException("Não foi possível calcular o frete agora. Tente de novo em instantes.");
        }
        // Serviço que não atende o trecho volta com "error" e sem preço: fica de fora
        return servicos.stream()
                .filter(Objects::nonNull)
                .filter(servico -> servico.error() == null && servico.preco() != null)
                .map(Servico::paraOpcao)
                .toList();
    }

    private RuntimeException traduzirErro(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401 || status == 403) {
            log.error("Melhor Envio recusou o token ({}): confira MELHOR_ENVIO_TOKEN e MELHOR_ENVIO_URL", status);
            return new FreteIndisponivelException("O cálculo de frete está indisponível no momento.");
        }
        // 422 apontando o CEP de destino: CEP com 8 dígitos, mas que não existe
        if (status == 422 && apontaCepDestino(e)) {
            return new DadosInvalidosException("CEP não encontrado. Confira os números.");
        }
        log.warn("Melhor Envio respondeu {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        return new FreteFalhouException("Não foi possível calcular o frete agora. Tente de novo em instantes.", e);
    }

    private static boolean apontaCepDestino(RestClientResponseException e) {
        try {
            ErroValidacao erro = e.getResponseBodyAs(ErroValidacao.class);
            return erro != null && erro.errors() != null
                    && erro.errors().keySet().stream().anyMatch(campo -> campo.startsWith("to."));
        } catch (RuntimeException ignorado) {
            return false;
        }
    }

    // ----- Formato da API (snake_case) -----

    record Cotacao(Cep from, Cep to, List<Produto> products, Opcoes options) {
    }

    record Cep(@JsonProperty("postal_code") String postalCode) {
    }

    record Opcoes(boolean receipt, @JsonProperty("own_hand") boolean ownHand) {
    }

    // Medidas em cm, peso em kg; insurance_value é por unidade (a API multiplica pela quantidade)
    record Produto(String id, int width, int height, int length, BigDecimal weight,
                   @JsonProperty("insurance_value") BigDecimal insuranceValue, int quantity) {

        static Produto from(PecaFrete peca) {
            return new Produto(peca.id(), peca.larguraCm(), peca.alturaCm(), peca.comprimentoCm(),
                    peca.pesoKg(), peca.valorUnitario(), peca.quantidade());
        }
    }

    // Preços chegam como texto ("22.90"). custom_price e custom_delivery_time já trazem as taxas e
    // descontos da conta: a documentação manda usar esses.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Servico(
            Integer id,
            String name,
            String error,
            BigDecimal price,
            @JsonProperty("custom_price") BigDecimal customPrice,
            @JsonProperty("delivery_time") Integer deliveryTime,
            @JsonProperty("custom_delivery_time") Integer customDeliveryTime,
            Empresa company
    ) {

        BigDecimal preco() {
            return customPrice != null ? customPrice : price;
        }

        int prazo() {
            if (customDeliveryTime != null) return customDeliveryTime;
            return deliveryTime != null ? deliveryTime : 0;
        }

        OpcaoFrete paraOpcao() {
            String transportadora = company != null && company.name() != null ? company.name() : name;
            return new OpcaoFrete(id, transportadora, name, preco(), prazo());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Empresa(Integer id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErroValidacao(String message, Map<String, Object> errors) {
    }
}
