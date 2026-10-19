package com.rosie.backend.service;

import com.rosie.backend.dto.TrocaAdminDTO;
import com.rosie.backend.dto.TrocaDTO;
import com.rosie.backend.dto.TrocaRequestDTO;
import com.rosie.backend.dto.TrocaStatusRequestDTO;
import com.rosie.backend.exception.EmailFalhouException;
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
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.TrocaRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.EnviadorEmail.Email;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// E-mail da troca (#65) de ponta a ponta, sem a transação do teste: o e-mail só sai depois do commit, então
// a mudança de status precisa ser confirmada no MySQL de verdade. Apaga tudo no fim.
@SpringBootTest
@Import(EnviadorEmailFalso.Config.class)
class EmailTrocaEnvioTests {

    @Autowired
    private TrocaService trocaService;

    @Autowired
    private EnviadorEmailFalso enviador;

    @Autowired
    private PrazoTroca prazoTroca;

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

    @Autowired
    private TransactionTemplate transacao;

    private Usuario cliente;
    private Endereco endereco;
    private Categoria categoria;
    private Produto produto;
    private Pedido pedido;
    private Long trocaId;

    @BeforeEach
    void setUp() {
        enviador.limpar();
        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Maria Cliente").email("email-troca-" + System.nanoTime() + "@rosie.test")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
        endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        categoria = categoriaRepository.save(Categoria.builder().nome("E-mail troca " + System.nanoTime()).build());
        produto = produtoRepository.save(Produto.builder()
                .nome("Conjunto Serpente").preco(new BigDecimal("189.00")).categoria(categoria).build());

        Pedido novo = Pedido.builder()
                .usuario(cliente).endereco(endereco).status(StatusPedido.ENTREGUE).formaPagamento(FormaPagamento.PIX)
                .transportadora("Correios").servicoFrete("PAC").servicoFreteId(1).prazoFreteDias(7)
                .valorFrete(new BigDecimal("22.90")).valorTotal(new BigDecimal("211.90"))
                .dataEntrega(prazoTroca.hoje())
                .build();
        novo.getItens().add(PedidoItem.builder().pedido(novo).produto(produto).tamanho(Tamanho.M)
                .quantidade(1).precoUnitario(new BigDecimal("189.00")).build());
        pedido = pedidoRepository.save(novo);

        TrocaDTO troca = trocaService.solicitar(cliente.getId(),
                new TrocaRequestDTO(pedido.getId(), produto.getId(), null, null, MotivoTroca.DEFEITO, null));
        trocaId = troca.id();
    }

    @AfterEach
    void limparBanco() {
        transacao.executeWithoutResult(status -> {
            trocaRepository.deleteAll(trocaRepository.findByPedidoId(pedido.getId()));
            pedidoRepository.deleteById(pedido.getId());
            produtoRepository.deleteById(produto.getId());
            categoriaRepository.deleteById(categoria.getId());
            enderecoRepository.deleteById(endereco.getId());
            usuarioRepository.deleteById(cliente.getId());
        });
    }

    @Test
    void pedirATrocaNaoMandaEmail() throws InterruptedException {
        // A solicitação (no setUp) está fora do escopo da #65; a aprovação abaixo é o primeiro e-mail
        mudar(StatusTroca.APROVADA, null);

        assertThat(enviador.aguardar(1)).extracting(Email::chave).containsExactly("troca-" + trocaId + "-APROVADA");
    }

    @Test
    void cadaMudancaAvisaAClienteNoEmailDaConta() throws InterruptedException {
        mudar(StatusTroca.APROVADA, "Pode mandar pelos Correios");
        mudar(StatusTroca.CONCLUIDA, null);

        List<Email> enviados = enviador.aguardar(2);
        assertThat(enviados).hasSize(2);
        assertThat(enviados).extracting(Email::para).containsOnly(cliente.getEmail());
        assertThat(enviados.get(0).assunto()).isEqualTo("Troca do pedido #" + pedido.getId() + " aprovada");
        assertThat(enviados.get(0).texto()).contains("Recado da Rosie:\nPode mandar pelos Correios");
        assertThat(enviados.get(1).assunto()).isEqualTo("Troca do pedido #" + pedido.getId() + " concluída");
        // O recado da aprovação não se repete na conclusão
        assertThat(enviados.get(1).texto()).doesNotContain("Pode mandar pelos Correios");
    }

    @Test
    void recusaAvisaComOMotivo() throws InterruptedException {
        mudar(StatusTroca.RECUSADA, "A peça foi usada");

        List<Email> enviados = enviador.aguardar(1);
        assertThat(enviados).singleElement().satisfies(email -> {
            assertThat(email.assunto()).endsWith("não aprovada");
            assertThat(email.texto()).contains("Motivo:\nA peça foi usada");
        });
    }

    @Test
    void repetirOMesmoStatusNaoReenvia() throws InterruptedException {
        mudar(StatusTroca.APROVADA, null);
        assertThat(enviador.aguardar(1)).hasSize(1);

        // A Roseane reenvia "Aprovada" só para mudar o recado: nada de e-mail novo. A fila manda um de cada
        // vez, na ordem: quando o e-mail da conclusão chega, um e-mail da repetição já teria chegado antes.
        mudar(StatusTroca.APROVADA, "Esqueci de dizer: mande pelos Correios");
        mudar(StatusTroca.CONCLUIDA, null);

        List<Email> enviados = enviador.aguardar(2);
        assertThat(enviados).extracting(Email::chave)
                .containsExactly("troca-" + trocaId + "-APROVADA", "troca-" + trocaId + "-CONCLUIDA");
    }

    @Test
    void falhaNoEnvioNaoDesfazAMudanca() throws InterruptedException {
        enviador.falharCom(new EmailFalhouException("O Resend recusou o e-mail (HTTP 500)"));

        TrocaAdminDTO aprovada = mudar(StatusTroca.APROVADA, "Pode mandar");

        assertThat(aprovada.status()).isEqualTo("APROVADA");
        enviador.aguardar(1);
        assertThat(enviador.tentativas()).isEqualTo(1);
        assertThat(trocaRepository.findById(trocaId).orElseThrow().getStatus()).isEqualTo(StatusTroca.APROVADA);
    }

    @Test
    void mudancaDesfeitaNaoAvisa() throws InterruptedException {
        transacao.executeWithoutResult(status -> {
            trocaService.atualizarStatus(trocaId, new TrocaStatusRequestDTO(StatusTroca.APROVADA, null));
            status.setRollbackOnly();
        });

        // Depois, uma mudança confirmada: só ela avisa (a fila manda na ordem)
        mudar(StatusTroca.RECUSADA, null);
        assertThat(enviador.aguardar(1)).extracting(Email::chave).containsExactly("troca-" + trocaId + "-RECUSADA");
        assertThat(enviador.tentativas()).isEqualTo(1);
    }

    @Test
    void contaExcluidaNaoRecebeEmail() throws InterruptedException {
        // Exclusão da LGPD (#31) com a troca em aberto não acontece pela Minha Conta; aqui é marcada direto
        cliente.setExcluidaEm(LocalDateTime.now());
        usuarioRepository.save(cliente);

        TrocaAdminDTO aprovada = mudar(StatusTroca.APROVADA, null);

        assertThat(aprovada.status()).isEqualTo("APROVADA");
        Thread.sleep(300);
        assertThat(enviador.tentativas()).isZero();
    }

    private TrocaAdminDTO mudar(StatusTroca status, String recado) {
        return trocaService.atualizarStatus(trocaId, new TrocaStatusRequestDTO(status, recado));
    }
}
