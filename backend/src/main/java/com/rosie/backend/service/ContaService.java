package com.rosie.backend.service;

import com.rosie.backend.dto.ContaDTO;
import com.rosie.backend.dto.ContaRequestDTO;
import com.rosie.backend.dto.SenhaRequestDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.model.StatusTroca;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.TrocaRepository;
import com.rosie.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

// Dados pessoais da Minha Conta (#26): nome, telefone e troca de senha da cliente do token.
// O e-mail não muda por aqui (é o login; trocar exigiria confirmar o novo endereço).
// Direitos do titular (LGPD, #31): reaceite da política em vigor e exclusão da conta.
@Service
@RequiredArgsConstructor
@Transactional
public class ContaService {

    // O BCrypt só aceita até 72 bytes; acentos ocupam 2, então o @Size do DTO não basta (como no cadastro)
    private static final int BYTES_MAXIMOS_SENHA = 72;

    // Pedidos que a loja ainda precisa entregar: os dados de entrega (nome, telefone, endereço) ainda vão
    // para a etiqueta e para a transportadora, então a exclusão espera
    private static final Set<StatusPedido> PEDIDO_EM_ANDAMENTO =
            Set.of(StatusPedido.PAGO, StatusPedido.PAGO_COM_PENDENCIA, StatusPedido.ENVIADO);

    // Pedidos que ainda não foram pagos: a exclusão cancela
    private static final Set<StatusPedido> PEDIDO_NAO_PAGO =
            Set.of(StatusPedido.AGUARDANDO_PAGAMENTO, StatusPedido.PAGAMENTO_RECUSADO);

    private static final Set<StatusTroca> TROCA_EM_ABERTO = Set.of(StatusTroca.SOLICITADA, StatusTroca.APROVADA);

    static final String NOME_EXCLUIDA = "Conta excluída";

    private final UsuarioRepository usuarioRepository;
    private final EnderecoRepository enderecoRepository;
    private final CarrinhoItemRepository carrinhoItemRepository;
    private final PedidoRepository pedidoRepository;
    private final TrocaRepository trocaRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public ContaDTO buscar(Long clienteId) {
        return ContaDTO.from(cliente(clienteId));
    }

    public ContaDTO atualizar(Long clienteId, ContaRequestDTO requisicao) {
        Usuario usuario = cliente(clienteId);
        usuario.setNome(requisicao.nome().trim());
        usuario.setTelefone(requisicao.telefone());
        return ContaDTO.from(usuario);
    }

    // Senha atual errada é 400 (não 401): a sessão continua valendo, só o formulário está errado.
    // Tokens já emitidos seguem válidos até vencer (JWT sem estado, 2 horas).
    public void trocarSenha(Long clienteId, SenhaRequestDTO requisicao) {
        if (requisicao.novaSenha().getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS_SENHA) {
            throw new DadosInvalidosException("A senha precisa ter de 8 a 72 caracteres");
        }
        Usuario usuario = cliente(clienteId);
        if (!passwordEncoder.matches(requisicao.senhaAtual(), usuario.getSenhaHash())) {
            throw new DadosInvalidosException("Senha atual incorreta");
        }
        usuario.setSenhaHash(passwordEncoder.encode(requisicao.novaSenha()));
    }

    // Aceite da versão em vigor da política (#31), por quem tinha aceitado uma antiga
    public ContaDTO aceitarPrivacidade(Long clienteId) {
        Usuario usuario = cliente(clienteId);
        usuario.setPrivacidadeAceitaEm(LocalDateTime.now());
        usuario.setPrivacidadeVersao(PoliticaPrivacidade.VERSAO);
        return ContaDTO.from(usuario);
    }

    // Exclusão da conta a pedido da cliente (LGPD, art. 18, VI). Não apaga a linha: anonimiza o que
    // identifica a pessoa (nome, e-mail, telefone, endereços, sacola e o texto das trocas) e bloqueia o
    // login. Pedidos, peças e valores ficam, sem dono identificável, porque a loja precisa guardá-los
    // (obrigação fiscal). O aceite da política também fica, como registro do consentimento dado.
    // O e-mail fica livre: a pessoa pode criar uma conta nova com ele depois.
    public void excluir(Long clienteId, String senha) {
        Usuario usuario = cliente(clienteId);
        if (!passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new DadosInvalidosException("Senha incorreta");
        }
        if (!pedidoRepository.findByUsuarioIdAndStatusIn(clienteId, PEDIDO_EM_ANDAMENTO).isEmpty()) {
            throw new ConflitoException("Você tem um pedido a caminho. A conta pode ser excluída depois que ele "
                    + "for entregue.", "CONTA_COM_PEDIDO_EM_ANDAMENTO");
        }
        if (trocaRepository.existsByPedidoUsuarioIdAndStatusIn(clienteId, TROCA_EM_ABERTO)) {
            throw new ConflitoException("Você tem uma troca em andamento. A conta pode ser excluída depois que ela "
                    + "for concluída.", "CONTA_COM_PEDIDO_EM_ANDAMENTO");
        }

        // Link de pagamento ainda aberto: o pedido é cancelado. Se mesmo assim o pagamento for aprovado,
        // o PagamentoService deixa o pedido com pendência para a Roseane devolver o dinheiro.
        pedidoRepository.findByUsuarioIdAndStatusIn(clienteId, PEDIDO_NAO_PAGO)
                .forEach(pedido -> pedido.setStatus(StatusPedido.CANCELADO));

        carrinhoItemRepository.deleteByUsuarioId(clienteId);
        for (Endereco endereco : enderecoRepository.findByUsuarioId(clienteId)) {
            if (pedidoRepository.existsByEnderecoId(endereco.getId())) {
                anonimizar(endereco);
            } else {
                enderecoRepository.delete(endereco);
            }
        }
        trocaRepository.findByPedidoUsuarioIdOrderByCreatedAtDescIdDesc(clienteId)
                .forEach(troca -> troca.setObservacao(null));

        usuario.setNome(NOME_EXCLUIDA);
        usuario.setEmail("excluida-" + usuario.getId() + "@conta-excluida.invalid");
        usuario.setTelefone(null);
        // Hash de uma senha que ninguém sabe: mesmo sem a checagem de excluidaEm, o login não entraria
        usuario.setSenhaHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setExcluidaEm(LocalDateTime.now());
    }

    // Endereço de um pedido: fica só a cidade e a UF (o pedido continua dizendo para onde foi, sem a rua)
    private static void anonimizar(Endereco endereco) {
        endereco.setCep("00000000");
        endereco.setLogradouro("Endereço removido");
        endereco.setNumero("-");
        endereco.setComplemento(null);
        endereco.setBairro("-");
        endereco.setArquivado(true);
    }

    private Usuario cliente(Long clienteId) {
        return usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
    }
}
