package com.rosie.backend.service;

import com.rosie.backend.dto.PedidoAdminDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.PedidoItem;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.service.EmissorEtiqueta.Destinatario;
import com.rosie.backend.service.EmissorEtiqueta.Etapa;
import com.rosie.backend.service.EmissorEtiqueta.Item;
import com.rosie.backend.service.EmissorEtiqueta.NovoEnvio;
import com.rosie.backend.service.EmissorEtiqueta.SituacaoEnvio;
import com.rosie.backend.service.EmissorEtiqueta.Volume;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

// Etiqueta de envio (RF12, #25), gerada pelo botão "Gerar etiqueta" do painel, nunca sozinha: comprar a
// etiqueta debita o saldo da conta do Melhor Envio, e a Roseane confere o pedido antes.
//
// Passos no Melhor Envio: carrinho → compra → geração → link de impressão e rastreio. Cada resultado é
// gravado (e confirmado no banco) assim que chega, sem uma transação em volta das chamadas: se algo falhar no
// meio, o próximo clique retoma de onde parou. Antes de comprar, a situação do envio é consultada no Melhor
// Envio, então uma compra que deu certo lá mas cuja resposta se perdeu (timeout) não é paga de novo.
//
// Dois cliques ao mesmo tempo no mesmo pedido: o segundo espera o primeiro terminar e já encontra a etiqueta
// pronta. A trava é da memória da API (há uma instância só no Railway); com mais de uma, ela teria que ir
// para o banco.
@Service
@Slf4j
public class EtiquetaService {

    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(90);

    private final PedidoRepository pedidoRepository;
    private final EmissorEtiqueta emissor;
    private final TransactionTemplate transacao;
    private final BigDecimal pesoKg;
    private final int larguraCm;
    private final int alturaCm;
    private final int comprimentoCm;
    private final Map<Long, ReentrantLock> travas = new ConcurrentHashMap<>();

    public EtiquetaService(PedidoRepository pedidoRepository,
                           EmissorEtiqueta emissor,
                           TransactionTemplate transacao,
                           @Value("${rosie.frete.peca.peso-kg}") BigDecimal pesoKg,
                           @Value("${rosie.frete.peca.largura-cm}") int larguraCm,
                           @Value("${rosie.frete.peca.altura-cm}") int alturaCm,
                           @Value("${rosie.frete.peca.comprimento-cm}") int comprimentoCm) {
        this.pedidoRepository = pedidoRepository;
        this.emissor = emissor;
        this.transacao = transacao;
        this.pesoKg = pesoKg;
        this.larguraCm = larguraCm;
        this.alturaCm = alturaCm;
        this.comprimentoCm = comprimentoCm;
    }

    // Idempotente: com a etiqueta e o rastreio já no pedido, só devolve o pedido, sem chamar o Melhor Envio.
    // Etiqueta pronta mas sem rastreio ainda: só consulta o rastreio de novo.
    public PedidoAdminDTO gerar(Long pedidoId) {
        ReentrantLock trava = travas.computeIfAbsent(pedidoId, id -> new ReentrantLock());
        try {
            if (!trava.tryLock(ESPERA_MAXIMA.toSeconds(), TimeUnit.SECONDS)) {
                throw emAndamento();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw emAndamento();
        }
        try {
            return gerarComTrava(pedidoId);
        } finally {
            trava.unlock();
        }
    }

    private PedidoAdminDTO gerarComTrava(Long pedidoId) {
        Preparo preparo = transacao.execute(status -> preparar(pedidoId));
        if (preparo.pronto()) {
            return detalhe(pedidoId);
        }

        String envioId = preparo.envioId();
        Etapa etapa;
        if (envioId == null) {
            envioId = emissor.adicionarAoCarrinho(preparo.envio());
            String novoId = envioId;
            atualizar(pedidoId, pedido -> pedido.setEnvioId(novoId));
            log.info("Pedido {}: envio {} posto no carrinho do Melhor Envio", pedidoId, envioId);
            etapa = Etapa.NO_CARRINHO;
        } else {
            etapa = emissor.consultar(envioId).etapa();
        }

        if (etapa == Etapa.CANCELADO) {
            // Cancelado no painel do Melhor Envio (ou expirado): o próximo clique começa um envio novo
            String cancelado = envioId;
            atualizar(pedidoId, pedido -> {
                pedido.setEnvioId(null);
                pedido.setUrlEtiqueta(null);
                pedido.setCodigoRastreio(null);
                pedido.setEtiquetaGeradaEm(null);
                if (pedido.getStatus() == StatusPedido.ENVIADO) pedido.setStatus(StatusPedido.PAGO);
            });
            log.warn("Pedido {}: envio {} cancelado ou expirado no Melhor Envio", pedidoId, cancelado);
            throw new ConflitoException("O envio deste pedido foi cancelado no Melhor Envio. Clique em "
                    + "\"Gerar etiqueta\" de novo para comprar outra.", "ETIQUETA_CANCELADA");
        }
        if (etapa == Etapa.NO_CARRINHO) {
            emissor.comprar(envioId);
            log.info("Pedido {}: envio {} comprado no Melhor Envio", pedidoId, envioId);
        }
        if (etapa == Etapa.NO_CARRINHO || etapa == Etapa.COMPRADO) {
            emissor.gerar(envioId);
            log.info("Pedido {}: etiqueta do envio {} gerada", pedidoId, envioId);
        }

        String url = preparo.urlEtiqueta() != null ? preparo.urlEtiqueta() : emissor.linkImpressao(envioId);
        SituacaoEnvio situacao = emissor.consultar(envioId);
        atualizar(pedidoId, pedido -> {
            pedido.setUrlEtiqueta(url);
            if (situacao.codigoRastreio() != null) pedido.setCodigoRastreio(situacao.codigoRastreio());
            if (pedido.getEtiquetaGeradaEm() == null) pedido.setEtiquetaGeradaEm(LocalDateTime.now());
            if (pedido.getStatus() == StatusPedido.PAGO) pedido.setStatus(StatusPedido.ENVIADO);
        });
        return detalhe(pedidoId);
    }

    // Confere se o pedido pode ter etiqueta e monta o envio com o que ele guarda (endereço, peças, serviço)
    private Preparo preparar(Long pedidoId) {
        Pedido pedido = buscar(pedidoId);
        boolean temEtiqueta = pedido.getUrlEtiqueta() != null;
        switch (pedido.getStatus()) {
            case PAGO -> {
            }
            case ENVIADO, ENTREGUE -> {
                // Sem envio no Melhor Envio só se foi mudado à mão no banco: nunca compra etiqueta para ele
                boolean pronto = temEtiqueta && pedido.getCodigoRastreio() != null;
                if (pronto || pedido.getEnvioId() == null) return Preparo.etiquetaPronta();
            }
            case PAGO_COM_PENDENCIA -> throw new ConflitoException(
                    "Este pedido está com pendência" + (pedido.getPendencia() == null ? "" : ": " + pedido.getPendencia())
                            + ". Resolva antes de gerar a etiqueta.",
                    "PEDIDO_COM_PENDENCIA");
            default -> throw new ConflitoException(
                    "Só pedido pago tem etiqueta: este está " + pedido.getStatus().name() + ".", "PEDIDO_NAO_PAGO");
        }
        if (pedido.getEnvioId() != null) {
            return new Preparo(false, pedido.getEnvioId(), pedido.getUrlEtiqueta(), null);
        }
        if (pedido.getServicoFreteId() == null) {
            throw new ConflitoException("O pedido não tem forma de entrega do Melhor Envio.", "PEDIDO_SEM_FRETE");
        }
        return new Preparo(false, null, null, novoEnvio(pedido));
    }

    private NovoEnvio novoEnvio(Pedido pedido) {
        Usuario cliente = pedido.getUsuario();
        Endereco endereco = pedido.getEndereco();
        Destinatario destinatario = new Destinatario(cliente.getNome(), digitosOuNulo(cliente.getTelefone()),
                cliente.getEmail(), endereco.getCep().replaceAll("\\D", ""), endereco.getLogradouro(),
                endereco.getNumero(), endereco.getComplemento(), endereco.getBairro(), endereco.getCidade(),
                endereco.getEstado());

        List<Item> itens = pedido.getItens().stream()
                .map(item -> new Item(item.getProduto().getNome() + " (" + item.getTamanho() + ")",
                        item.getQuantidade(), item.getPrecoUnitario()))
                .toList();

        // Pacote padrão por peça (rosie.frete.peca.*, o mesmo da cotação), empilhadas numa caixa só: Correios
        // não aceita mais de um volume por envio. Até o produto ter peso e medidas (#56).
        int pecas = pedido.getItens().stream().mapToInt(PedidoItem::getQuantidade).sum();
        Volume volume = new Volume(alturaCm * pecas, larguraCm, comprimentoCm,
                pesoKg.multiply(BigDecimal.valueOf(pecas)));

        BigDecimal valorDeclarado = pedido.getValorTotal().subtract(pedido.getValorFrete());
        return new NovoEnvio(pedido.getId(), pedido.getServicoFreteId(), destinatario, itens, volume, valorDeclarado);
    }

    private void atualizar(Long pedidoId, Consumer<Pedido> mudanca) {
        transacao.executeWithoutResult(status -> mudanca.accept(buscar(pedidoId)));
    }

    private PedidoAdminDTO detalhe(Long pedidoId) {
        return transacao.execute(status -> PedidoAdminDTO.from(buscar(pedidoId)));
    }

    private Pedido buscar(Long pedidoId) {
        return pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + pedidoId + " não encontrado"));
    }

    private static String digitosOuNulo(String telefone) {
        if (telefone == null) return null;
        String digitos = telefone.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    private static ConflitoException emAndamento() {
        return new ConflitoException("A etiqueta deste pedido já está sendo gerada. Aguarde e atualize a página.",
                "ETIQUETA_EM_ANDAMENTO");
    }

    // pronto: etiqueta e rastreio já no pedido. envio só vem quando ainda não há envio no Melhor Envio.
    private record Preparo(boolean pronto, String envioId, String urlEtiqueta, NovoEnvio envio) {

        static Preparo etiquetaPronta() {
            return new Preparo(true, null, null, null);
        }
    }
}
