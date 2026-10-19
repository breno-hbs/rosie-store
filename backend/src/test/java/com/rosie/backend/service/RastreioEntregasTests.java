package com.rosie.backend.service;

import com.rosie.backend.dto.TrocaDTO;
import com.rosie.backend.dto.TrocaRequestDTO;
import com.rosie.backend.exception.EtiquetaFalhouException;
import com.rosie.backend.exception.EtiquetaIndisponivelException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.FormaPagamento;
import com.rosie.backend.model.MotivoTroca;
import com.rosie.backend.model.OrigemEntrega;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.PedidoItem;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.RastreioEntregas.Rodada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// Entrega pelo rastreio (#64) contra o MySQL local, com o Melhor Envio falso e o relógio parado em
// 19/10/2026, 12h em São Paulo; cada teste é desfeito no fim. A rotina agendada fica desligada nos testes
// (pom.xml): aqui a rodada é chamada direto, sem pausa entre as consultas.
@SpringBootTest
@Transactional
@Import({EmissorEtiquetaFalso.Config.class, RastreioEntregasTests.RelogioParado.class})
class RastreioEntregasTests {

    static final LocalDate HOJE = LocalDate.of(2026, 10, 19);

    @TestConfiguration
    static class RelogioParado {

        @Bean
        @Primary
        Clock relogioParado() {
            return Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), PrazoTroca.FUSO_LOJA);
        }
    }

    @Autowired
    private EmissorEtiquetaFalso emissor;

    @Autowired
    private Clock relogio;

    @Autowired
    private PedidoAdminService pedidoAdminService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private TrocaService trocaService;

    @Autowired
    private RastreioEntregas rastreioEntregasDaAplicacao;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EnderecoRepository enderecoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    private RastreioEntregas rastreio;
    private Usuario cliente;
    private Endereco endereco;
    private Produto produto;

    @BeforeEach
    void setUp() {
        emissor.limpar();
        rastreio = rotina(emissor, 50);
        // Pedidos ENVIADO que já estejam no banco local vão para o fim da fila (desfeito no fim do teste)
        pedidoRepository.buscarParaRastrear(PageRequest.of(0, 10_000))
                .forEach(p -> pedidoRepository.marcarRastreioConsultado(p.getId(), LocalDateTime.now(relogio)));

        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Maria Cliente").email("rastreio-servico@rosie.test")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
        endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Rastreio " + System.nanoTime()).build());
        produto = produtoRepository.save(Produto.builder()
                .nome("Conjunto Serpente").preco(new BigDecimal("189.00")).categoria(categoria).build());
    }

    @Test
    void entreguePeloRastreioComADataDaTransportadoraEAbreATroca() {
        Long id = enviado("envio-r1");
        emissor.entregar("envio-r1", "AA000000001BR", LocalDate.of(2026, 10, 17));

        Rodada rodada = rastreio.executar();

        assertThat(rodada.entregues()).isEqualTo(1);
        Pedido pedido = recarregar(id);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.ENTREGUE);
        assertThat(pedido.getDataEntrega()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(pedido.getOrigemEntrega()).isEqualTo(OrigemEntrega.RASTREIO);
        assertThat(pedidoAdminService.buscar(id).origemEntrega()).isEqualTo("RASTREIO");
        // O prazo de troca conta da data da transportadora, e a cliente já pode pedir
        assertThat(pedidoService.buscar(cliente.getId(), id).prazoTroca()).isEqualTo(LocalDate.of(2026, 10, 24));
        TrocaDTO troca = trocaService.solicitar(cliente.getId(),
                new TrocaRequestDTO(id, produto.getId(), null, null, MotivoTroca.DEFEITO, null));
        assertThat(troca.status()).isEqualTo("SOLICITADA");
    }

    @Test
    void aindaEmTransitoContinuaEnviado() {
        Long id = enviado("envio-r1");
        emissor.emTransito("envio-r1", "AA000000001BR");

        Rodada rodada = rastreio.executar();

        assertThat(rodada.entregues()).isZero();
        Pedido pedido = recarregar(id);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.ENVIADO);
        assertThat(pedido.getDataEntrega()).isNull();
        assertThat(pedido.getOrigemEntrega()).isNull();
        assertThat(pedido.getRastreioConsultadoEm()).isEqualTo(LocalDateTime.of(2026, 10, 19, 12, 0));
    }

    @Test
    void semDataDaTransportadoraValeODiaDaConsulta() {
        Long id = enviado("envio-r1");
        emissor.entregar("envio-r1", "AA000000001BR", null);

        rastreio.executar();

        Pedido pedido = recarregar(id);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.ENTREGUE);
        assertThat(pedido.getDataEntrega()).isEqualTo(HOJE);
    }

    @Test
    void pedidoJaEntregueAMaoNemEhConsultado() {
        Long id = enviado("envio-r1");
        pedidoAdminService.marcarEntregue(id, LocalDate.of(2026, 10, 16));
        emissor.entregar("envio-r1", "AA000000001BR", LocalDate.of(2026, 10, 17));

        rastreio.executar();

        Pedido pedido = recarregar(id);
        assertThat(pedido.getDataEntrega()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(pedido.getOrigemEntrega()).isEqualTo(OrigemEntrega.MANUAL);
        assertThat(pedido.getRastreioConsultadoEm()).isNull();
    }

    @Test
    void cliqueDaRoseaneDuranteAConsultaVale() {
        Long id = enviado("envio-r1");
        emissor.entregar("envio-r1", "AA000000001BR", LocalDate.of(2026, 10, 17));
        // Entre a rodada escolher o pedido e confirmar, a Roseane marca à mão
        emissor.aoConsultar = () -> pedidoAdminService.marcarEntregue(id, LocalDate.of(2026, 10, 18));

        Rodada rodada = rastreio.executar();

        assertThat(rodada.entregues()).isZero();
        assertThat(rodada.falhas()).isZero();
        Pedido pedido = recarregar(id);
        assertThat(pedido.getDataEntrega()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(pedido.getOrigemEntrega()).isEqualTo(OrigemEntrega.MANUAL);
        assertThat(pedidoAdminService.buscar(id).origemEntrega()).isEqualTo("MANUAL");
    }

    @Test
    void segundaRodadaNaoMexeNoQueORastreioJaEntregou() {
        Long id = enviado("envio-r1");
        emissor.entregar("envio-r1", "AA000000001BR", LocalDate.of(2026, 10, 17));
        rastreio.executar();
        emissor.entregar("envio-r1", "AA000000001BR", LocalDate.of(2026, 10, 18));

        Rodada segunda = rastreio.executar();

        assertThat(segunda.entregues()).isZero();
        assertThat(recarregar(id).getDataEntrega()).isEqualTo(LocalDate.of(2026, 10, 17));
        // O botão também não muda a data (#27)
        assertThat(pedidoAdminService.marcarEntregue(id, HOJE).dataEntrega()).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    @Test
    void erroDoMelhorEnvioNumPedidoNaoParaOsOutros() {
        Long comErro = enviado("envio-r1");
        Long outro = enviado("envio-r2");
        emissor.entregar("envio-r1", "AA000000001BR", LocalDate.of(2026, 10, 17));
        emissor.entregar("envio-r2", "AA000000002BR", LocalDate.of(2026, 10, 18));
        emissor.falhasNaConsulta.put("envio-r1", new EtiquetaFalhouException("Melhor Envio fora do ar"));

        Rodada rodada = rastreio.executar();

        assertThat(rodada.falhas()).isEqualTo(1);
        assertThat(rodada.entregues()).isEqualTo(1);
        assertThat(recarregar(comErro).getStatus()).isEqualTo(StatusPedido.ENVIADO);
        assertThat(recarregar(outro).getStatus()).isEqualTo(StatusPedido.ENTREGUE);

        // Na rodada seguinte, com o Melhor Envio de volta, entrega o que faltou
        emissor.falhasNaConsulta.clear();
        rastreio.executar();
        assertThat(recarregar(comErro).getStatus()).isEqualTo(StatusPedido.ENTREGUE);
        assertThat(recarregar(comErro).getDataEntrega()).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    @Test
    void tokenRecusadoInterrompeARodada() {
        Long primeiro = enviado("envio-r1");
        Long segundo = enviado("envio-r2");
        emissor.entregar("envio-r2", "AA000000002BR", LocalDate.of(2026, 10, 18));
        emissor.falhas.put("consultar", new EtiquetaIndisponivelException("Token recusado"));

        Rodada rodada = rastreio.executar();

        assertThat(rodada.consultados()).isEqualTo(1);
        assertThat(emissor.consultas).isEqualTo(1);
        assertThat(recarregar(primeiro).getStatus()).isEqualTo(StatusPedido.ENVIADO);
        assertThat(recarregar(segundo).getStatus()).isEqualTo(StatusPedido.ENVIADO);
    }

    @Test
    void dataDoRastreioForaDaRegraNaoEntrega() {
        // Mesma validação do botão (#27): não antes do dia da etiqueta, não no futuro
        Long antesDaEtiqueta = enviado("envio-r1");
        Long noFuturo = enviado("envio-r2");
        emissor.entregar("envio-r1", "AA000000001BR", LocalDate.of(2026, 10, 10));
        emissor.entregar("envio-r2", "AA000000002BR", HOJE.plusDays(1));

        Rodada rodada = rastreio.executar();

        assertThat(rodada.falhas()).isEqualTo(2);
        assertThat(recarregar(antesDaEtiqueta).getStatus()).isEqualTo(StatusPedido.ENVIADO);
        assertThat(recarregar(noFuturo).getStatus()).isEqualTo(StatusPedido.ENVIADO);
    }

    @Test
    void loteLimitaARodadaEOsOutrosFicamParaAProxima() {
        Long primeiro = enviado("envio-r1");
        Long segundo = enviado("envio-r2");
        emissor.emTransito("envio-r1", "AA000000001BR");
        emissor.entregar("envio-r2", "AA000000002BR", LocalDate.of(2026, 10, 18));
        RastreioEntregas deUmEmUm = rotina(emissor, 1);

        Rodada primeira = deUmEmUm.executar();
        Rodada seguinte = deUmEmUm.executar();

        assertThat(primeira.consultados()).isEqualTo(1);
        assertThat(primeira.entregues()).isZero();
        assertThat(recarregar(primeiro).getStatus()).isEqualTo(StatusPedido.ENVIADO);
        // A segunda rodada começa pelo que ainda não foi consultado
        assertThat(seguinte.entregues()).isEqualTo(1);
        assertThat(recarregar(segundo).getStatus()).isEqualTo(StatusPedido.ENTREGUE);
    }

    @Test
    void pedidoSemCodigoDeRastreioFicaParaOBotao() {
        Long id = enviado("envio-r1");
        Pedido pedido = recarregar(id);
        pedido.setCodigoRastreio(null);
        pedidoRepository.saveAndFlush(pedido);
        emissor.entregar("envio-r1", "AA000000001BR", LocalDate.of(2026, 10, 17));

        rastreio.executar();

        assertThat(recarregar(id).getStatus()).isEqualTo(StatusPedido.ENVIADO);
        assertThat(recarregar(id).getRastreioConsultadoEm()).isNull();
    }

    @Test
    void desligadaSemTokenDoMelhorEnvio() {
        Long id = enviado("envio-r1");
        RastreioEntregas semToken = rotina(new EtiquetaIndisponivel(), 50);

        Rodada rodada = semToken.executar();

        assertThat(rodada).isEqualTo(new Rodada(0, 0, 0));
        Pedido pedido = recarregar(id);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.ENVIADO);
        assertThat(pedido.getRastreioConsultadoEm()).isNull();
    }

    @Test
    void aplicacaoTemARotinaComOEmissorConfigurado() {
        Long id = enviado("envio-r1");
        emissor.entregar("envio-r1", "AA000000001BR", LocalDate.of(2026, 10, 17));

        // O bean da aplicação (lote e pausa do application.properties): com um pedido só, não espera
        rastreioEntregasDaAplicacao.executar();

        assertThat(recarregar(id).getOrigemEntrega()).isEqualTo(OrigemEntrega.RASTREIO);
    }

    private RastreioEntregas rotina(EmissorEtiqueta comEmissor, int lote) {
        return new RastreioEntregas(pedidoRepository, pedidoAdminService, comEmissor, relogio, lote, Duration.ZERO);
    }

    // Pedido ENVIADO com etiqueta gerada em 14/10 e o envio no Melhor Envio falso
    private Long enviado(String envioId) {
        Pedido pedido = Pedido.builder()
                .usuario(cliente).endereco(endereco).status(StatusPedido.ENVIADO).formaPagamento(FormaPagamento.PIX)
                .transportadora("Correios").servicoFrete("PAC").servicoFreteId(1).prazoFreteDias(7)
                .valorFrete(new BigDecimal("22.90")).valorTotal(new BigDecimal("211.90"))
                .envioId(envioId).codigoRastreio("AA" + envioId).urlEtiqueta("https://etiqueta.test/" + envioId)
                .etiquetaGeradaEm(LocalDateTime.of(2026, 10, 14, 10, 0))
                .build();
        pedido.getItens().add(PedidoItem.builder().pedido(pedido).produto(produto).tamanho(Tamanho.M)
                .quantidade(1).precoUnitario(new BigDecimal("189.00")).build());
        return pedidoRepository.saveAndFlush(pedido).getId();
    }

    private Pedido recarregar(Long id) {
        return pedidoRepository.findById(id).orElseThrow();
    }
}
