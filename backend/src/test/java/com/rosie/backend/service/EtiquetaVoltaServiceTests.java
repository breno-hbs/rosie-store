package com.rosie.backend.service;

import com.rosie.backend.dto.TrocaAdminDTO;
import com.rosie.backend.dto.TrocaDTO;
import com.rosie.backend.dto.TrocaStatusRequestDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.EtiquetaFalhouException;
import com.rosie.backend.exception.EtiquetaIndisponivelException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.FormaPagamento;
import com.rosie.backend.model.MotivoTroca;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.PedidoItem;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.model.StatusTroca;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Troca;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.TrocaRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.EmissorEtiqueta.NovoEnvio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Etiqueta de volta da troca (logística reversa, #63) com o Melhor Envio falso; MySQL local, desfeito ao fim.
// O endereço da loja (remetente da ida, destinatário da volta) vem das propriedades abaixo.
@SpringBootTest(properties = {
        "rosie.etiqueta.remetente.nome=Rosie Store",
        "rosie.etiqueta.remetente.logradouro=Avenida Paulista",
        "rosie.etiqueta.remetente.numero=1000",
        "rosie.etiqueta.remetente.complemento=Sala 5",
        "rosie.etiqueta.remetente.bairro=Bela Vista",
        "rosie.etiqueta.remetente.cidade=São Paulo",
        "rosie.etiqueta.remetente.uf=SP",
        "rosie.frete.cep-origem=01310-100"
})
@Transactional
@Import(EmissorEtiquetaFalso.Config.class)
class EtiquetaVoltaServiceTests {

    @Autowired
    private EtiquetaService etiquetaService;

    @Autowired
    private TrocaService trocaService;

    @Autowired
    private EmissorEtiquetaFalso emissor;

    @Autowired
    private TrocaRepository trocaRepository;

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

    private Usuario cliente;
    private Pedido pedido;
    private Produto vestido;

    @BeforeEach
    void setUp() {
        emissor.limpar();
        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Volta").email("cliente-volta@rosie.test").telefone("(11) 98888-7777")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
        Endereco endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925-050").logradouro("Rua das Flores").numero("120").complemento("Casa 2")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Volta " + System.nanoTime()).build());
        vestido = produtoRepository.save(Produto.builder()
                .nome("Vestido Aurora").preco(new BigDecimal("199.90")).categoria(categoria).build());
        Produto calca = produtoRepository.save(Produto.builder()
                .nome("Calça Linho").preco(new BigDecimal("120.00")).categoria(categoria).build());

        Pedido novo = Pedido.builder()
                .usuario(cliente).endereco(endereco).status(StatusPedido.ENTREGUE).formaPagamento(FormaPagamento.PIX)
                .transportadora("Correios").servicoFrete("SEDEX").servicoFreteId(2).prazoFreteDias(3)
                .valorFrete(new BigDecimal("34.90")).valorTotal(new BigDecimal("474.70"))
                .envioId("envio-ida").urlEtiqueta("https://ida").codigoRastreio("IDA123BR")
                .build();
        // Preço pago no vestido (159,90) diferente do preço atual (199,90): a declaração usa o pago
        novo.getItens().add(PedidoItem.builder().pedido(novo).produto(vestido).tamanho(Tamanho.M)
                .quantidade(2).precoUnitario(new BigDecimal("159.90")).build());
        novo.getItens().add(PedidoItem.builder().pedido(novo).produto(calca).tamanho(Tamanho.G)
                .quantidade(1).precoUnitario(new BigDecimal("120.00")).build());
        pedido = pedidoRepository.saveAndFlush(novo);
    }

    @Test
    void trocaAprovadaPorDefeitoCompraGeraEGuardaNaTroca() {
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.APROVADA);

        TrocaAdminDTO dto = etiquetaService.gerarVolta(troca.getId());

        assertThat(dto.status()).isEqualTo("APROVADA");
        assertThat(dto.etiquetaVolta().situacao()).isEqualTo("GERADA");
        assertThat(dto.etiquetaVolta().envioId()).isEqualTo("envio-1");
        // Link público: quem imprime é a cliente, sem a conta da loja no Melhor Envio
        assertThat(dto.etiquetaVolta().urlEtiqueta()).isEqualTo(EmissorEtiquetaFalso.LINK_PUBLICO + "envio-1");
        assertThat(dto.etiquetaVolta().codigoRastreio()).isEqualTo("ME123456789BR");
        assertThat(dto.etiquetaVolta().geradaEm()).isNotNull();
        assertThat(dto.etiquetaVolta().erro()).isNull();
        assertThat(emissor.compras).containsExactly("envio-1");
        assertThat(emissor.geracoes).containsExactly("envio-1");

        // A etiqueta de ida do pedido fica como estava
        Pedido depois = pedidoRepository.findById(pedido.getId()).orElseThrow();
        assertThat(depois.getEnvioId()).isEqualTo("envio-ida");
        assertThat(depois.getCodigoRastreio()).isEqualTo("IDA123BR");
        assertThat(depois.getStatus()).isEqualTo(StatusPedido.ENTREGUE);
    }

    @Test
    void envioDeVoltaEReversoDaClienteParaALojaSoComAPecaDaTroca() {
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.APROVADA);

        etiquetaService.gerarVolta(troca.getId());

        NovoEnvio envio = emissor.enviosNoCarrinho.get(0);
        // Reverso: a cliente (endereço do pedido) é quem manda; o emissor põe a loja como destinatária
        assertThat(envio.reverso()).isTrue();
        assertThat(envio.trocaId()).isEqualTo(troca.getId());
        assertThat(envio.pedidoId()).isEqualTo(pedido.getId());
        assertThat(envio.servicoId()).isEqualTo(2);
        assertThat(envio.destinatario().nome()).isEqualTo("Cliente Volta");
        assertThat(envio.destinatario().telefone()).isEqualTo("11988887777");
        assertThat(envio.destinatario().cep()).isEqualTo("03925050");
        assertThat(envio.destinatario().logradouro()).isEqualTo("Rua das Flores");
        assertThat(envio.destinatario().complemento()).isEqualTo("Casa 2");
        // Só a peça que volta (1 dos 2 vestidos M), pelo preço pago, sem a calça
        assertThat(envio.itens()).singleElement().satisfies(item -> {
            assertThat(item.nome()).isEqualTo("Vestido Aurora (M)");
            assertThat(item.quantidade()).isEqualTo(1);
            assertThat(item.valorUnitario()).isEqualByComparingTo("159.90");
        });
        assertThat(envio.valorDeclarado()).isEqualByComparingTo("159.90");
        // Pacote padrão de uma peça (#56): 20 x 5 x 30 cm, 0,3 kg
        assertThat(envio.volume().alturaCm()).isEqualTo(5);
        assertThat(envio.volume().larguraCm()).isEqualTo(20);
        assertThat(envio.volume().comprimentoCm()).isEqualTo(30);
        assertThat(envio.volume().pesoKg()).isEqualByComparingTo("0.3");
    }

    @Test
    void pacoteUsaPesoEMedidasDoProdutoVezesAQuantidade() {
        vestido.setPesoKg(new BigDecimal("0.5"));
        vestido.setAlturaCm(6);
        vestido.setLarguraCm(25);
        vestido.setComprimentoCm(35);
        produtoRepository.save(vestido);
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.APROVADA);
        troca.setQuantidade(2);

        etiquetaService.gerarVolta(troca.getId());

        NovoEnvio envio = emissor.enviosNoCarrinho.get(0);
        assertThat(envio.volume().alturaCm()).isEqualTo(12);
        assertThat(envio.volume().larguraCm()).isEqualTo(25);
        assertThat(envio.volume().comprimentoCm()).isEqualTo(35);
        assertThat(envio.volume().pesoKg()).isEqualByComparingTo("1.0");
        assertThat(envio.valorDeclarado()).isEqualByComparingTo("319.80");
    }

    @Test
    void gerarDeNovoNaoCompraOutraEtiqueta() {
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.APROVADA);
        etiquetaService.gerarVolta(troca.getId());
        int consultas = emissor.consultas;

        TrocaAdminDTO dto = etiquetaService.gerarVolta(troca.getId());

        assertThat(dto.etiquetaVolta().codigoRastreio()).isEqualTo("ME123456789BR");
        assertThat(emissor.enviosNoCarrinho).hasSize(1);
        assertThat(emissor.compras).hasSize(1);
        // Etiqueta e rastreio já na troca: nem consulta o Melhor Envio
        assertThat(emissor.consultas).isEqualTo(consultas);
    }

    @Test
    void trocaNaoAprovadaResponde409SemChamarOMelhorEnvio() {
        for (StatusTroca status : new StatusTroca[]{StatusTroca.SOLICITADA, StatusTroca.RECUSADA,
                StatusTroca.CONCLUIDA}) {
            Troca troca = salvarTroca(MotivoTroca.DEFEITO, status);
            assertThatThrownBy(() -> etiquetaService.gerarVolta(troca.getId()))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessageContaining(status.name())
                    .extracting("codigo").isEqualTo("TROCA_NAO_APROVADA");
        }
        assertThat(emissor.enviosNoCarrinho).isEmpty();
    }

    @Test
    void naoServiuNaoGeraEtiquetaPagaPelaLoja() {
        Troca troca = salvarTroca(MotivoTroca.NAO_SERVIU, StatusTroca.APROVADA);

        assertThatThrownBy(() -> etiquetaService.gerarVolta(troca.getId()))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("TROCA_FRETE_DA_CLIENTE");
        assertThat(emissor.enviosNoCarrinho).isEmpty();
        assertThat(emissor.compras).isEmpty();
        assertThat(TrocaAdminDTO.from(troca).etiquetaVolta().situacao()).isEqualTo("POR_CONTA_DA_CLIENTE");
    }

    @Test
    void naoServiuAprovadaMostraAoClienteOEnderecoDaLoja() {
        salvarTroca(MotivoTroca.NAO_SERVIU, StatusTroca.APROVADA);

        TrocaDTO dto = trocaService.listar(cliente.getId()).get(0);

        assertThat(dto.etiquetaVolta().situacao()).isEqualTo("POR_CONTA_DA_CLIENTE");
        assertThat(dto.etiquetaVolta().urlEtiqueta()).isNull();
        assertThat(dto.enderecoDevolucao()).isNotNull();
        assertThat(dto.enderecoDevolucao().nome()).isEqualTo("Rosie Store");
        assertThat(dto.enderecoDevolucao().cep()).isEqualTo("01310-100");
        assertThat(dto.enderecoDevolucao().emUmaLinha())
                .isEqualTo("Rosie Store, Avenida Paulista, 1000, Sala 5, Bela Vista, São Paulo/SP, CEP 01310-100");
    }

    @Test
    void enderecoDaLojaSoApareceNaTrocaAprovadaPorContaDaCliente() {
        salvarTroca(MotivoTroca.NAO_SERVIU, StatusTroca.SOLICITADA);
        assertThat(trocaService.listar(cliente.getId()).get(0).enderecoDevolucao()).isNull();
    }

    @Test
    void clienteVeAEtiquetaDeVoltaNaMinhaConta() {
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.APROVADA);
        assertThat(trocaService.listar(cliente.getId()).get(0).etiquetaVolta().situacao()).isEqualTo("SEM_ETIQUETA");

        etiquetaService.gerarVolta(troca.getId());

        TrocaDTO dto = trocaService.listar(cliente.getId()).get(0);
        assertThat(dto.etiquetaVolta().situacao()).isEqualTo("GERADA");
        assertThat(dto.etiquetaVolta().urlEtiqueta()).isEqualTo(EmissorEtiquetaFalso.LINK_PUBLICO + "envio-1");
        assertThat(dto.etiquetaVolta().codigoRastreio()).isEqualTo("ME123456789BR");
        // Frete da Rosie: a cliente não precisa do endereço, a etiqueta já leva
        assertThat(dto.enderecoDevolucao()).isNull();
    }

    @Test
    void falhaDoMelhorEnvioDeixaATrocaAprovadaComFalhaERetomaSemComprarDeNovo() {
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.APROVADA);
        emissor.falhas.put("gerar", new EtiquetaFalhouException("O Melhor Envio recusou a etiqueta: Saldo insuficiente"));

        assertThatThrownBy(() -> etiquetaService.gerarVolta(troca.getId()))
                .isInstanceOf(EtiquetaFalhouException.class);
        Troca meioCaminho = trocaRepository.findById(troca.getId()).orElseThrow();
        assertThat(meioCaminho.getStatus()).isEqualTo(StatusTroca.APROVADA);
        assertThat(meioCaminho.getEnvioVoltaId()).isEqualTo("envio-1");
        assertThat(meioCaminho.getUrlEtiquetaVolta()).isNull();
        TrocaAdminDTO comFalha = TrocaAdminDTO.from(meioCaminho);
        assertThat(comFalha.etiquetaVolta().situacao()).isEqualTo("FALHOU");
        assertThat(comFalha.etiquetaVolta().erro()).contains("Saldo insuficiente");
        // Para a cliente, a falha é só "ainda sem etiqueta"
        assertThat(trocaService.listar(cliente.getId()).get(0).etiquetaVolta().situacao()).isEqualTo("SEM_ETIQUETA");

        emissor.falhas.clear();
        TrocaAdminDTO dto = etiquetaService.gerarVolta(troca.getId());

        assertThat(dto.etiquetaVolta().situacao()).isEqualTo("GERADA");
        assertThat(dto.etiquetaVolta().erro()).isNull();
        assertThat(emissor.enviosNoCarrinho).hasSize(1);
        assertThat(emissor.compras).containsExactly("envio-1");
        assertThat(emissor.geracoes).containsExactly("envio-1");
        assertThat(trocaRepository.findById(troca.getId()).orElseThrow().getErroEtiquetaVolta()).isNull();
    }

    @Test
    void melhorEnvioIndisponivelTambemFicaComoFalha() {
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.APROVADA);
        emissor.falhas.put("carrinho", new EtiquetaIndisponivelException("A etiqueta de envio não está configurada no servidor"));

        assertThatThrownBy(() -> etiquetaService.gerarVolta(troca.getId()))
                .isInstanceOf(EtiquetaIndisponivelException.class);

        Troca depois = trocaRepository.findById(troca.getId()).orElseThrow();
        assertThat(depois.getEnvioVoltaId()).isNull();
        assertThat(TrocaAdminDTO.from(depois).etiquetaVolta().situacao()).isEqualTo("FALHOU");
    }

    @Test
    void aprovarNaoDependeDoMelhorEnvio() {
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.SOLICITADA);
        emissor.falhas.put("carrinho", new EtiquetaFalhouException("fora do ar"));
        emissor.falhas.put("consultar", new EtiquetaFalhouException("fora do ar"));

        TrocaAdminDTO aprovada = trocaService.atualizarStatus(troca.getId(),
                new TrocaStatusRequestDTO(StatusTroca.APROVADA, null));

        assertThat(aprovada.status()).isEqualTo("APROVADA");
        assertThat(aprovada.etiquetaVolta().situacao()).isEqualTo("SEM_ETIQUETA");
        assertThat(aprovada.etiquetaVolta().valorEstimado()).isEqualByComparingTo("34.90");
        assertThat(emissor.consultas).isZero();
        assertThat(emissor.enviosNoCarrinho).isEmpty();
    }

    @Test
    void envioCanceladoNoMelhorEnvioLiberaUmNovo() {
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.APROVADA);
        emissor.falhas.put("gerar", new EtiquetaFalhouException("Melhor Envio fora do ar"));
        assertThatThrownBy(() -> etiquetaService.gerarVolta(troca.getId())).isInstanceOf(EtiquetaFalhouException.class);
        emissor.falhas.clear();
        emissor.cancelar("envio-1");

        assertThatThrownBy(() -> etiquetaService.gerarVolta(troca.getId()))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("Gerar etiqueta de volta")
                .extracting("codigo").isEqualTo("ETIQUETA_CANCELADA");
        assertThat(trocaRepository.findById(troca.getId()).orElseThrow().getEnvioVoltaId()).isNull();

        TrocaAdminDTO dto = etiquetaService.gerarVolta(troca.getId());
        assertThat(dto.etiquetaVolta().envioId()).isEqualTo("envio-2");
        assertThat(dto.etiquetaVolta().situacao()).isEqualTo("GERADA");
    }

    @Test
    void semRastreioAindaOProximoCliqueSoBuscaORastreio() {
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.APROVADA);
        emissor.rastreio = null;

        TrocaAdminDTO primeiro = etiquetaService.gerarVolta(troca.getId());
        assertThat(primeiro.etiquetaVolta().situacao()).isEqualTo("GERADA");
        assertThat(primeiro.etiquetaVolta().codigoRastreio()).isNull();

        emissor.liberarRastreio("envio-1", "ME999BR");
        TrocaAdminDTO segundo = etiquetaService.gerarVolta(troca.getId());

        assertThat(segundo.etiquetaVolta().codigoRastreio()).isEqualTo("ME999BR");
        assertThat(segundo.etiquetaVolta().geradaEm()).isEqualTo(primeiro.etiquetaVolta().geradaEm());
        assertThat(emissor.compras).hasSize(1);
    }

    @Test
    void trocaInexistenteResponde404() {
        assertThatThrownBy(() -> etiquetaService.gerarVolta(-1L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private Troca salvarTroca(MotivoTroca motivo, StatusTroca status) {
        return trocaRepository.saveAndFlush(Troca.builder()
                .pedido(pedido).produto(vestido).tamanho(Tamanho.M).quantidade(1)
                .motivo(motivo).status(status).build());
    }
}
