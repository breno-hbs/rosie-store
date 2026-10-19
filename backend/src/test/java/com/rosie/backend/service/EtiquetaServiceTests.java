package com.rosie.backend.service;

import com.rosie.backend.dto.PedidoAdminDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.EtiquetaFalhouException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.FormaPagamento;
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
import com.rosie.backend.service.EmissorEtiqueta.NovoEnvio;
import com.rosie.backend.service.EmissorEtiqueta.Volume;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Etiqueta de um pedido pago, com o Melhor Envio falso; MySQL local, desfeito ao fim
@SpringBootTest
@Transactional
@Import(EmissorEtiquetaFalso.Config.class)
class EtiquetaServiceTests {

    @Autowired
    private EtiquetaService etiquetaService;

    @Autowired
    private EmissorEtiquetaFalso emissor;

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

    @Autowired
    private EntityManager entityManager;

    private Usuario cliente;
    private Endereco endereco;
    private Produto vestido;
    private Produto calca;

    @BeforeEach
    void setUp() {
        emissor.limpar();
        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Etiqueta").email("cliente-etiqueta@rosie.test").telefone("(11) 98888-7777")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
        endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120").complemento("Casa 2")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Etiqueta " + System.nanoTime()).build());
        vestido = produtoRepository.save(Produto.builder()
                .nome("Vestido Aurora").preco(new BigDecimal("159.90")).categoria(categoria).build());
        calca = produtoRepository.save(Produto.builder()
                .nome("Calça Linho").preco(new BigDecimal("120.00")).categoria(categoria).build());
    }

    @Test
    void pedidoPagoCompraGeraEGuardaOLinkEORastreio() {
        Pedido pedido = salvarPedido(StatusPedido.PAGO);

        PedidoAdminDTO dto = etiquetaService.gerar(pedido.getId());

        assertThat(dto.status()).isEqualTo("ENVIADO");
        assertThat(dto.envioId()).isEqualTo("envio-1");
        assertThat(dto.urlEtiqueta()).isEqualTo(EmissorEtiquetaFalso.LINK + "envio-1");
        assertThat(dto.codigoRastreio()).isEqualTo("ME123456789BR");
        assertThat(dto.etiquetaGeradaEm()).isNotNull();
        assertThat(emissor.compras).containsExactly("envio-1");
        assertThat(emissor.geracoes).containsExactly("envio-1");

        entityManager.flush();
        entityManager.clear();
        Pedido salvo = pedidoRepository.findById(pedido.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusPedido.ENVIADO);
        assertThat(salvo.getEnvioId()).isEqualTo("envio-1");
        assertThat(salvo.getCodigoRastreio()).isEqualTo("ME123456789BR");
        assertThat(salvo.getUrlEtiqueta()).isEqualTo(EmissorEtiquetaFalso.LINK + "envio-1");
    }

    @Test
    void mandaOEnvioComOEnderecoAsPecasEOPacoteDoPedido() {
        Pedido pedido = salvarPedido(StatusPedido.PAGO);

        etiquetaService.gerar(pedido.getId());

        NovoEnvio envio = emissor.enviosNoCarrinho.get(0);
        assertThat(envio.pedidoId()).isEqualTo(pedido.getId());
        assertThat(envio.servicoId()).isEqualTo(2);
        assertThat(envio.destinatario().nome()).isEqualTo("Cliente Etiqueta");
        assertThat(envio.destinatario().telefone()).isEqualTo("11988887777");
        assertThat(envio.destinatario().cep()).isEqualTo("03925050");
        assertThat(envio.destinatario().complemento()).isEqualTo("Casa 2");
        assertThat(envio.destinatario().uf()).isEqualTo("SP");
        assertThat(envio.itens()).extracting(EmissorEtiqueta.Item::nome)
                .containsExactly("Vestido Aurora (M)", "Calça Linho (G)");
        assertThat(envio.itens().get(0).quantidade()).isEqualTo(2);
        assertThat(envio.itens().get(0).valorUnitario()).isEqualByComparingTo("159.90");
        // 3 peças de 0,3 kg, 20 x 5 x 30 cm empilhadas: 20 x 15 x 30 cm, 0,9 kg
        assertThat(envio.volume().alturaCm()).isEqualTo(15);
        assertThat(envio.volume().larguraCm()).isEqualTo(20);
        assertThat(envio.volume().comprimentoCm()).isEqualTo(30);
        assertThat(envio.volume().pesoKg()).isEqualByComparingTo("0.9");
        // Valor declarado = peças, sem o frete: 2 x 159,90 + 120,00
        assertThat(envio.valorDeclarado()).isEqualByComparingTo("439.80");
    }

    @Test
    void volumeUsaOPacoteDeCadaProdutoComoNaCotacao() {
        // calça com peso e medidas próprios; o vestido continua com o pacote padrão (#56)
        calca.setPesoKg(new BigDecimal("1.2"));
        calca.setAlturaCm(8);
        calca.setLarguraCm(25);
        calca.setComprimentoCm(35);
        produtoRepository.save(calca);
        Pedido pedido = salvarPedido(StatusPedido.PAGO);

        etiquetaService.gerar(pedido.getId());

        // 2 vestidos de 5 cm + 1 calça de 8 cm empilhados; largura e comprimento da maior peça;
        // 2 x 0,3 kg + 1,2 kg
        Volume volume = emissor.enviosNoCarrinho.get(0).volume();
        assertThat(volume.alturaCm()).isEqualTo(18);
        assertThat(volume.larguraCm()).isEqualTo(25);
        assertThat(volume.comprimentoCm()).isEqualTo(35);
        assertThat(volume.pesoKg()).isEqualByComparingTo("1.8");
    }

    @Test
    void gerarDeNovoNaoCompraOutraEtiqueta() {
        Pedido pedido = salvarPedido(StatusPedido.PAGO);
        etiquetaService.gerar(pedido.getId());
        int consultas = emissor.consultas;

        PedidoAdminDTO dto = etiquetaService.gerar(pedido.getId());

        assertThat(dto.codigoRastreio()).isEqualTo("ME123456789BR");
        assertThat(emissor.enviosNoCarrinho).hasSize(1);
        assertThat(emissor.compras).hasSize(1);
        // Etiqueta e rastreio já no pedido: nem consulta o Melhor Envio
        assertThat(emissor.consultas).isEqualTo(consultas);
    }

    @Test
    void falhaNoMeioRetomaDeOndeParouSemComprarDeNovo() {
        Pedido pedido = salvarPedido(StatusPedido.PAGO);
        emissor.falhas.put("gerar", new EtiquetaFalhouException("Melhor Envio fora do ar"));

        assertThatThrownBy(() -> etiquetaService.gerar(pedido.getId()))
                .isInstanceOf(EtiquetaFalhouException.class);
        Pedido meioCaminho = pedidoRepository.findById(pedido.getId()).orElseThrow();
        assertThat(meioCaminho.getEnvioId()).isEqualTo("envio-1");
        assertThat(meioCaminho.getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(meioCaminho.getUrlEtiqueta()).isNull();

        emissor.falhas.clear();
        PedidoAdminDTO dto = etiquetaService.gerar(pedido.getId());

        assertThat(dto.status()).isEqualTo("ENVIADO");
        assertThat(emissor.enviosNoCarrinho).hasSize(1);
        assertThat(emissor.compras).containsExactly("envio-1");
        assertThat(emissor.geracoes).containsExactly("envio-1");
    }

    @Test
    void compraQueDeuCertoLaMasPerdeuARespostaNaoEPagaDeNovo() {
        Pedido pedido = salvarPedido(StatusPedido.PAGO);
        // Gravado no pedido e comprado no Melhor Envio, mas a resposta da compra não chegou
        pedido.setEnvioId(emissor.adicionarAoCarrinho(null));
        emissor.comprar(pedido.getEnvioId());

        etiquetaService.gerar(pedido.getId());

        assertThat(emissor.compras).hasSize(1);
        assertThat(emissor.geracoes).containsExactly(pedido.getEnvioId());
    }

    @Test
    void semRastreioAindaFicaEnviadoEOProximoCliqueSoBuscaORastreio() {
        Pedido pedido = salvarPedido(StatusPedido.PAGO);
        emissor.rastreio = null;

        PedidoAdminDTO primeiro = etiquetaService.gerar(pedido.getId());
        assertThat(primeiro.status()).isEqualTo("ENVIADO");
        assertThat(primeiro.urlEtiqueta()).isNotNull();
        assertThat(primeiro.codigoRastreio()).isNull();

        emissor.liberarRastreio("envio-1", "ME999BR");
        PedidoAdminDTO segundo = etiquetaService.gerar(pedido.getId());

        assertThat(segundo.codigoRastreio()).isEqualTo("ME999BR");
        assertThat(segundo.etiquetaGeradaEm()).isEqualTo(primeiro.etiquetaGeradaEm());
        assertThat(emissor.compras).hasSize(1);
        assertThat(emissor.geracoes).hasSize(1);
    }

    @Test
    void envioCanceladoNoMelhorEnvioLiberaUmNovo() {
        Pedido pedido = salvarPedido(StatusPedido.PAGO);
        emissor.falhas.put("gerar", new EtiquetaFalhouException("Melhor Envio fora do ar"));
        assertThatThrownBy(() -> etiquetaService.gerar(pedido.getId())).isInstanceOf(EtiquetaFalhouException.class);
        emissor.falhas.clear();
        emissor.cancelar("envio-1");

        assertThatThrownBy(() -> etiquetaService.gerar(pedido.getId()))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("ETIQUETA_CANCELADA");
        assertThat(pedidoRepository.findById(pedido.getId()).orElseThrow().getEnvioId()).isNull();

        PedidoAdminDTO dto = etiquetaService.gerar(pedido.getId());
        assertThat(dto.envioId()).isEqualTo("envio-2");
        assertThat(dto.status()).isEqualTo("ENVIADO");
    }

    @Test
    void pedidoNaoPagoResponde409SemChamarOMelhorEnvio() {
        for (StatusPedido status : new StatusPedido[]{StatusPedido.AGUARDANDO_PAGAMENTO,
                StatusPedido.PAGAMENTO_RECUSADO, StatusPedido.CANCELADO}) {
            Pedido pedido = salvarPedido(status);
            assertThatThrownBy(() -> etiquetaService.gerar(pedido.getId()))
                    .isInstanceOf(ConflitoException.class)
                    .extracting("codigo").isEqualTo("PEDIDO_NAO_PAGO");
        }
        assertThat(emissor.enviosNoCarrinho).isEmpty();
    }

    @Test
    void pedidoComPendenciaResponde409() {
        Pedido pedido = salvarPedido(StatusPedido.PAGO_COM_PENDENCIA);
        pedido.setPendencia("Faltou estoque de Vestido Aurora (M)");

        assertThatThrownBy(() -> etiquetaService.gerar(pedido.getId()))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("Faltou estoque")
                .extracting("codigo").isEqualTo("PEDIDO_COM_PENDENCIA");
        assertThat(emissor.enviosNoCarrinho).isEmpty();
    }

    @Test
    void pedidoInexistenteResponde404() {
        assertThatThrownBy(() -> etiquetaService.gerar(-1L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private Pedido salvarPedido(StatusPedido status) {
        Pedido pedido = Pedido.builder()
                .usuario(cliente)
                .endereco(endereco)
                .status(status)
                .formaPagamento(FormaPagamento.PIX)
                .transportadora("Correios")
                .servicoFrete("SEDEX")
                .servicoFreteId(2)
                .prazoFreteDias(3)
                .valorFrete(new BigDecimal("34.90"))
                .valorTotal(new BigDecimal("474.70"))
                .pagoEm(status == StatusPedido.PAGO ? LocalDateTime.now() : null)
                .build();
        pedido.getItens().add(PedidoItem.builder().pedido(pedido).produto(vestido).tamanho(Tamanho.M)
                .quantidade(2).precoUnitario(new BigDecimal("159.90")).build());
        pedido.getItens().add(PedidoItem.builder().pedido(pedido).produto(calca).tamanho(Tamanho.G)
                .quantidade(1).precoUnitario(new BigDecimal("120.00")).build());
        return pedidoRepository.saveAndFlush(pedido);
    }
}
