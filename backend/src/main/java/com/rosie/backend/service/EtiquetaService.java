package com.rosie.backend.service;

import com.rosie.backend.dto.PedidoAdminDTO;
import com.rosie.backend.dto.TrocaAdminDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.EtiquetaFalhouException;
import com.rosie.backend.exception.EtiquetaIndisponivelException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.FreteConta;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.PedidoItem;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.model.StatusTroca;
import com.rosie.backend.model.Troca;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.TrocaRepository;
import com.rosie.backend.service.EmissorEtiqueta.Destinatario;
import com.rosie.backend.service.EmissorEtiqueta.Etapa;
import com.rosie.backend.service.EmissorEtiqueta.Item;
import com.rosie.backend.service.EmissorEtiqueta.NovoEnvio;
import com.rosie.backend.service.EmissorEtiqueta.SituacaoEnvio;
import com.rosie.backend.service.EmissorEtiqueta.Volume;
import lombok.extern.slf4j.Slf4j;
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
import java.util.function.Supplier;

// Etiqueta de envio (RF12, #25), gerada pelo botão "Gerar etiqueta" do painel, nunca sozinha: comprar a
// etiqueta debita o saldo da conta do Melhor Envio, e a Roseane confere o pedido antes. A etiqueta de volta da
// troca (logística reversa, #63) usa os mesmos passos, pelo botão "Gerar etiqueta de volta" de uma troca
// aprovada com o frete por conta da Rosie.
//
// Passos no Melhor Envio: carrinho → compra → geração → link de impressão e rastreio. Cada resultado é
// gravado (e confirmado no banco) assim que chega, sem uma transação em volta das chamadas: se algo falhar no
// meio, o próximo clique retoma de onde parou. Antes de comprar, a situação do envio é consultada no Melhor
// Envio, então uma compra que deu certo lá mas cuja resposta se perdeu (timeout) não é paga de novo.
//
// Dois cliques ao mesmo tempo no mesmo pedido (ou na mesma troca): o segundo espera o primeiro terminar e já
// encontra a etiqueta pronta. A trava é da memória da API (há uma instância só no Railway); com mais de uma,
// ela teria que ir para o banco.
@Service
@Slf4j
public class EtiquetaService {

    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(90);
    private static final int TAMANHO_ERRO = 500;

    private final PedidoRepository pedidoRepository;
    private final TrocaRepository trocaRepository;
    private final EmissorEtiqueta emissor;
    private final TransactionTemplate transacao;
    private final PacotePeca pacotePeca;
    private final EmailTroca emailTroca;
    private final Map<String, ReentrantLock> travas = new ConcurrentHashMap<>();

    public EtiquetaService(PedidoRepository pedidoRepository,
                           TrocaRepository trocaRepository,
                           EmissorEtiqueta emissor,
                           TransactionTemplate transacao,
                           PacotePeca pacotePeca,
                           EmailTroca emailTroca) {
        this.pedidoRepository = pedidoRepository;
        this.trocaRepository = trocaRepository;
        this.emissor = emissor;
        this.transacao = transacao;
        this.pacotePeca = pacotePeca;
        this.emailTroca = emailTroca;
    }

    // Idempotente: com a etiqueta e o rastreio já no pedido, só devolve o pedido, sem chamar o Melhor Envio.
    // Etiqueta pronta mas sem rastreio ainda: só consulta o rastreio de novo.
    public PedidoAdminDTO gerar(Long pedidoId) {
        return comTrava("pedido-" + pedidoId, "deste pedido", () -> gerarComTrava(pedidoId));
    }

    // Etiqueta de volta da troca (#63), com a cliente como remetente e a loja como destinatária. Idempotente
    // como a de ida. Uma falha do Melhor Envio fica gravada na troca (o painel mostra "com falha") e a troca
    // continua APROVADA: o próximo clique tenta de novo de onde parou.
    public TrocaAdminDTO gerarVolta(Long trocaId) {
        return comTrava("troca-" + trocaId, "desta troca", () -> gerarVoltaComTrava(trocaId));
    }

    private <T> T comTrava(String chave, String deQuem, Supplier<T> acao) {
        ReentrantLock trava = travas.computeIfAbsent(chave, id -> new ReentrantLock());
        try {
            if (!trava.tryLock(ESPERA_MAXIMA.toSeconds(), TimeUnit.SECONDS)) {
                throw emAndamento(deQuem);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw emAndamento(deQuem);
        }
        try {
            return acao.get();
        } finally {
            trava.unlock();
        }
    }

    // ----- Ida (#25) -----

    private PedidoAdminDTO gerarComTrava(Long pedidoId) {
        Preparo preparo = transacao.execute(status -> preparar(pedidoId));
        if (!preparo.pronto()) {
            executarPassos("Pedido " + pedidoId, preparo, false, new Registro() {
                @Override
                public void envio(String envioId) {
                    atualizar(pedidoId, pedido -> pedido.setEnvioId(envioId));
                }

                @Override
                public ConflitoException cancelado() {
                    atualizar(pedidoId, pedido -> {
                        pedido.setEnvioId(null);
                        pedido.setUrlEtiqueta(null);
                        pedido.setCodigoRastreio(null);
                        pedido.setEtiquetaGeradaEm(null);
                        if (pedido.getStatus() == StatusPedido.ENVIADO) pedido.setStatus(StatusPedido.PAGO);
                    });
                    return new ConflitoException("O envio deste pedido foi cancelado no Melhor Envio. Clique em "
                            + "\"Gerar etiqueta\" de novo para comprar outra.", "ETIQUETA_CANCELADA");
                }

                @Override
                public void pronta(String url, String codigoRastreio) {
                    atualizar(pedidoId, pedido -> {
                        pedido.setUrlEtiqueta(url);
                        if (codigoRastreio != null) pedido.setCodigoRastreio(codigoRastreio);
                        if (pedido.getEtiquetaGeradaEm() == null) pedido.setEtiquetaGeradaEm(LocalDateTime.now());
                        if (pedido.getStatus() == StatusPedido.PAGO) pedido.setStatus(StatusPedido.ENVIADO);
                    });
                }
            });
        }
        return transacao.execute(status -> PedidoAdminDTO.from(buscar(pedidoId)));
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
        exigirServico(pedido);
        List<Item> itens = pedido.getItens().stream()
                .map(item -> new Item(item.getProduto().getNome() + " (" + item.getTamanho() + ")",
                        item.getQuantidade(), item.getPrecoUnitario()))
                .toList();
        Volume volume = volume(pedido.getItens().stream()
                .map(item -> new Peca(item.getProduto(), item.getQuantidade()))
                .toList());
        BigDecimal valorDeclarado = pedido.getValorTotal().subtract(pedido.getValorFrete());
        NovoEnvio envio = new NovoEnvio(pedido.getId(), pedido.getServicoFreteId(), cliente(pedido), itens, volume,
                valorDeclarado);
        return new Preparo(false, null, null, envio);
    }

    // ----- Volta da troca (#63) -----

    private TrocaAdminDTO gerarVoltaComTrava(Long trocaId) {
        Preparo preparo = transacao.execute(status -> prepararVolta(trocaId));
        if (!preparo.pronto()) {
            try {
                executarPassos("Troca " + trocaId, preparo, true, new Registro() {
                    @Override
                    public void envio(String envioId) {
                        atualizarTroca(trocaId, troca -> troca.setEnvioVoltaId(envioId));
                    }

                    @Override
                    public ConflitoException cancelado() {
                        ConflitoException erro = new ConflitoException("O envio de volta desta troca foi cancelado "
                                + "no Melhor Envio. Clique em \"Gerar etiqueta de volta\" de novo para comprar outra.",
                                "ETIQUETA_CANCELADA");
                        atualizarTroca(trocaId, troca -> {
                            troca.setEnvioVoltaId(null);
                            troca.setUrlEtiquetaVolta(null);
                            troca.setCodigoRastreioVolta(null);
                            troca.setEtiquetaVoltaGeradaEm(null);
                            troca.setErroEtiquetaVolta(erro.getMessage());
                        });
                        return erro;
                    }

                    @Override
                    public void pronta(String url, String codigoRastreio) {
                        atualizarTroca(trocaId, troca -> {
                            boolean primeiraVez = troca.getEtiquetaVoltaGeradaEm() == null;
                            troca.setUrlEtiquetaVolta(url);
                            if (codigoRastreio != null) troca.setCodigoRastreioVolta(codigoRastreio);
                            if (primeiraVez) troca.setEtiquetaVoltaGeradaEm(LocalDateTime.now());
                            troca.setErroEtiquetaVolta(null);
                            // Avisa a cliente uma vez só, depois do commit; buscar o rastreio de novo não reenvia
                            if (primeiraVez) emailTroca.avisarEtiquetaVolta(troca);
                        });
                    }
                });
            } catch (EtiquetaFalhouException | EtiquetaIndisponivelException e) {
                String motivo = e.getMessage().length() > TAMANHO_ERRO
                        ? e.getMessage().substring(0, TAMANHO_ERRO) : e.getMessage();
                atualizarTroca(trocaId, troca -> troca.setErroEtiquetaVolta(motivo));
                throw e;
            }
        }
        return transacao.execute(status -> TrocaAdminDTO.from(buscarTroca(trocaId)));
    }

    // Só troca aprovada com o frete da Rosie (defeito, RNF08) tem etiqueta comprada pela loja. A volta sai do
    // endereço do pedido para a loja, pelo mesmo serviço da ida, com a peça da troca no pacote dela (#56).
    private Preparo prepararVolta(Long trocaId) {
        Troca troca = buscarTroca(trocaId);
        if (troca.getFreteConta() == FreteConta.CLIENTE) {
            throw new ConflitoException("Nesta troca o frete de volta é por conta da cliente (a peça não serviu): "
                    + "ela manda a peça pelo endereço da loja que aparece na Minha Conta.", "TROCA_FRETE_DA_CLIENTE");
        }
        boolean temEtiqueta = troca.getUrlEtiquetaVolta() != null;
        // Pronta com rastreio, ou pronta e a troca já saiu de APROVADA: só devolve a troca
        if (temEtiqueta && (troca.getCodigoRastreioVolta() != null || troca.getStatus() != StatusTroca.APROVADA)) {
            return Preparo.etiquetaPronta();
        }
        if (troca.getStatus() != StatusTroca.APROVADA) {
            throw new ConflitoException("Só troca aprovada tem etiqueta de volta: esta está "
                    + troca.getStatus().name() + ".", "TROCA_NAO_APROVADA");
        }
        if (troca.getEnvioVoltaId() != null) {
            return new Preparo(false, troca.getEnvioVoltaId(), troca.getUrlEtiquetaVolta(), null);
        }

        Pedido pedido = troca.getPedido();
        exigirServico(pedido);
        Produto produto = troca.getProduto();
        BigDecimal precoUnitario = pedido.getItens().stream()
                .filter(item -> item.getProduto().getId().equals(produto.getId())
                        && item.getTamanho() == troca.getTamanho())
                .map(PedidoItem::getPrecoUnitario)
                .findFirst()
                .orElse(produto.getPreco());
        List<Item> itens = List.of(new Item(produto.getNome() + " (" + troca.getTamanho() + ")",
                troca.getQuantidade(), precoUnitario));
        Volume volume = volume(List.of(new Peca(produto, troca.getQuantidade())));
        BigDecimal valorDeclarado = precoUnitario.multiply(BigDecimal.valueOf(troca.getQuantidade()));
        NovoEnvio envio = new NovoEnvio(pedido.getId(), pedido.getServicoFreteId(), cliente(pedido), itens, volume,
                valorDeclarado, troca.getId());
        return new Preparo(false, null, null, envio);
    }

    // ----- Passos comuns -----

    // Onde cada passo fica gravado: no pedido (ida) ou na troca (volta)
    private interface Registro {

        void envio(String envioId);

        // Esquece o envio cancelado no Melhor Envio e devolve o erro para a Roseane
        ConflitoException cancelado();

        void pronta(String url, String codigoRastreio);
    }

    private void executarPassos(String quem, Preparo preparo, boolean linkPublico, Registro registro) {
        String envioId = preparo.envioId();
        Etapa etapa;
        if (envioId == null) {
            envioId = emissor.adicionarAoCarrinho(preparo.envio());
            registro.envio(envioId);
            log.info("{}: envio {} posto no carrinho do Melhor Envio", quem, envioId);
            etapa = Etapa.NO_CARRINHO;
        } else {
            etapa = emissor.consultar(envioId).etapa();
        }

        if (etapa == Etapa.CANCELADO) {
            // Cancelado no painel do Melhor Envio (ou expirado): o próximo clique começa um envio novo
            log.warn("{}: envio {} cancelado ou expirado no Melhor Envio", quem, envioId);
            throw registro.cancelado();
        }
        if (etapa == Etapa.NO_CARRINHO) {
            emissor.comprar(envioId);
            log.info("{}: envio {} comprado no Melhor Envio", quem, envioId);
        }
        if (etapa == Etapa.NO_CARRINHO || etapa == Etapa.COMPRADO) {
            emissor.gerar(envioId);
            log.info("{}: etiqueta do envio {} gerada", quem, envioId);
        }

        String url = preparo.urlEtiqueta() != null ? preparo.urlEtiqueta()
                : linkPublico ? emissor.linkImpressaoPublico(envioId) : emissor.linkImpressao(envioId);
        SituacaoEnvio situacao = emissor.consultar(envioId);
        registro.pronta(url, situacao.codigoRastreio());
    }

    private static void exigirServico(Pedido pedido) {
        if (pedido.getServicoFreteId() == null) {
            throw new ConflitoException("O pedido não tem forma de entrega do Melhor Envio.", "PEDIDO_SEM_FRETE");
        }
    }

    // A cliente e o endereço do pedido: destinatária na ida, remetente na volta
    private static Destinatario cliente(Pedido pedido) {
        Usuario cliente = pedido.getUsuario();
        Endereco endereco = pedido.getEndereco();
        return new Destinatario(cliente.getNome(), digitosOuNulo(cliente.getTelefone()),
                cliente.getEmail(), endereco.getCep().replaceAll("\\D", ""), endereco.getLogradouro(),
                endereco.getNumero(), endereco.getComplemento(), endereco.getBairro(), endereco.getCidade(),
                endereco.getEstado());
    }

    // O pacote de cada peça vem do PacotePeca, o mesmo da cotação (#56). As peças vão empilhadas numa caixa
    // só (Correios não aceita mais de um volume por envio): alturas e pesos somados, largura e comprimento da
    // maior peça.
    private Volume volume(List<Peca> pecas) {
        int altura = 0;
        int largura = 0;
        int comprimento = 0;
        BigDecimal peso = BigDecimal.ZERO;
        for (Peca peca : pecas) {
            PacotePeca.Medidas pacote = pacotePeca.de(peca.produto());
            altura += pacote.alturaCm() * peca.quantidade();
            largura = Math.max(largura, pacote.larguraCm());
            comprimento = Math.max(comprimento, pacote.comprimentoCm());
            peso = peso.add(pacote.pesoKg().multiply(BigDecimal.valueOf(peca.quantidade())));
        }
        return new Volume(altura, largura, comprimento, peso);
    }

    private void atualizar(Long pedidoId, Consumer<Pedido> mudanca) {
        transacao.executeWithoutResult(status -> mudanca.accept(buscar(pedidoId)));
    }

    private void atualizarTroca(Long trocaId, Consumer<Troca> mudanca) {
        transacao.executeWithoutResult(status -> mudanca.accept(buscarTroca(trocaId)));
    }

    private Pedido buscar(Long pedidoId) {
        return pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + pedidoId + " não encontrado"));
    }

    private Troca buscarTroca(Long trocaId) {
        return trocaRepository.findById(trocaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Troca " + trocaId + " não encontrada"));
    }

    private static String digitosOuNulo(String telefone) {
        if (telefone == null) return null;
        String digitos = telefone.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    private static ConflitoException emAndamento(String deQuem) {
        return new ConflitoException("A etiqueta " + deQuem + " já está sendo gerada. Aguarde e atualize a página.",
                "ETIQUETA_EM_ANDAMENTO");
    }

    private record Peca(Produto produto, int quantidade) {
    }

    // pronto: etiqueta e rastreio já gravados. envio só vem quando ainda não há envio no Melhor Envio.
    private record Preparo(boolean pronto, String envioId, String urlEtiqueta, NovoEnvio envio) {

        static Preparo etiquetaPronta() {
            return new Preparo(true, null, null, null);
        }
    }
}
