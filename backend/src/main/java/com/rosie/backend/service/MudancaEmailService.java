package com.rosie.backend.service;

import com.rosie.backend.dto.EmailConfirmadoDTO;
import com.rosie.backend.dto.EmailPendenteDTO;
import com.rosie.backend.dto.EmailRequestDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.EmailFalhouException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.exception.SenhaIncorretaException;
import com.rosie.backend.exception.TokenInvalidoException;
import com.rosie.backend.model.EmailPendente;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.EmailPendenteRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.EnviadorEmail.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

// Troca do e-mail de login da cliente na Minha Conta (#61). O e-mail novo não vale na hora: o pedido (com a
// senha atual) guarda uma troca pendente e manda ao endereço novo um link com token de uso único e prazo; só
// a confirmação pelo link troca o login, e aí o endereço antigo recebe um aviso.
// O JWT não leva o e-mail (sub = id do usuário), então a sessão continua valendo depois da troca.
@Service
@Slf4j
public class MudancaEmailService {

    // Mesma mensagem do cadastro: o 409 não revela mais do que o cadastro já revela
    private static final String MENSAGEM_EMAIL_DUPLICADO = "Este e-mail já está cadastrado. Entre com ele ou use outro.";

    // 256 bits aleatórios, em Base64 sem "=" e seguro para URL (43 caracteres)
    private static final int BYTES_TOKEN = 32;

    private final UsuarioRepository usuarioRepository;
    private final EmailPendenteRepository pendenteRepository;
    private final PasswordEncoder passwordEncoder;
    private final EnviadorEmail enviador;
    private final FilaEmail fila;
    private final EmailMudancaLogin emails;
    private final TransactionTemplate transacao;
    private final Clock relogio;
    private final Duration validade;
    private final SecureRandom aleatorio = new SecureRandom();

    public MudancaEmailService(UsuarioRepository usuarioRepository, EmailPendenteRepository pendenteRepository,
                               PasswordEncoder passwordEncoder, EnviadorEmail enviador, FilaEmail fila,
                               EmailMudancaLogin emails, TransactionTemplate transacao, Clock relogio,
                               @Value("${rosie.conta.troca-email.validade:24h}") Duration validade) {
        this.usuarioRepository = usuarioRepository;
        this.pendenteRepository = pendenteRepository;
        this.passwordEncoder = passwordEncoder;
        this.enviador = enviador;
        this.fila = fila;
        this.emails = emails;
        this.transacao = transacao;
        this.relogio = relogio;
        this.validade = validade;
    }

    // Grava a troca pendente (substituindo a anterior, cujo link deixa de valer) e manda o link na hora, fora
    // da transação. Diferente dos avisos da FilaEmail, aqui o envio faz parte da ação: se o Resend falhar, a
    // pendência é desfeita e a resposta é 502 EMAIL_FALHOU, em vez de dizer à cliente que o link foi enviado.
    // Ordem das conferências: a senha vem antes do e-mail, para o 409 só responder a quem é dona da conta.
    public EmailPendenteDTO solicitar(Long clienteId, EmailRequestDTO requisicao) {
        String token = novoToken();
        String tokenHash = hash(token);
        Solicitacao pedido = transacao.execute(status -> registrar(clienteId, requisicao, tokenHash));

        Email email = emails.confirmacao(pedido.nome(), pedido.pendente().novoEmail(), token,
                pedido.pendente().expiraEm(), tokenHash.substring(0, 32));
        try {
            enviador.enviar(email);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar o link de troca de e-mail para {}: {}", email.paraMascarado(), e.getMessage());
            transacao.executeWithoutResult(status -> pendenteRepository.apagarPeloTokenHash(tokenHash));
            throw e instanceof EmailFalhouException falha ? falha : new EmailFalhouException(e.getMessage(), e);
        }
        return pedido.pendente();
    }

    private record Solicitacao(String nome, EmailPendenteDTO pendente) {
    }

    private Solicitacao registrar(Long clienteId, EmailRequestDTO requisicao, String tokenHash) {
        Usuario usuario = usuarioRepository.findById(clienteId)
                .filter(u -> u.getExcluidaEm() == null)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
        if (!passwordEncoder.matches(requisicao.senhaAtual(), usuario.getSenhaHash())) {
            throw new SenhaIncorretaException();
        }

        String novoEmail = AuthService.normalizarEmail(requisicao.novoEmail());
        if (novoEmail.equalsIgnoreCase(usuario.getEmail())) {
            throw new DadosInvalidosException("O novo e-mail é igual ao atual");
        }
        if (usuarioRepository.existsByEmail(novoEmail)) {
            throw new ConflitoException(MENSAGEM_EMAIL_DUPLICADO, "EMAIL_JA_CADASTRADO");
        }

        // Sem frações de segundo: o que a resposta mostra é o que fica no banco
        LocalDateTime agora = LocalDateTime.now(relogio).truncatedTo(ChronoUnit.SECONDS);
        EmailPendente pendente = pendenteRepository.findByUsuarioId(clienteId)
                .orElseGet(() -> EmailPendente.builder().usuario(usuario).build());
        pendente.setNovoEmail(novoEmail);
        pendente.setTokenHash(tokenHash);
        pendente.setSolicitadoEm(agora);
        pendente.setExpiraEm(agora.plus(validade));
        pendenteRepository.save(pendente);
        return new Solicitacao(usuario.getNome(), EmailPendenteDTO.from(pendente));
    }

    // Confirmação pelo link (pública: o token prova que a pessoa recebeu o e-mail no endereço novo, e o pedido
    // já exigiu a senha). O e-mail novo é conferido de novo, para o caso de alguém ter se cadastrado com ele
    // nesse meio-tempo. O token usado é apagado: um segundo uso responde TOKEN_INVALIDO.
    @Transactional
    public EmailConfirmadoDTO confirmar(String token) {
        if (token == null || token.isBlank()) throw TokenInvalidoException.invalido();
        EmailPendente pendente = pendenteRepository.findByTokenHash(hash(token.trim()))
                .orElseThrow(TokenInvalidoException::invalido);
        Usuario usuario = pendente.getUsuario();
        // A exclusão da conta (#31) já apaga a pendência; isto só cobre o caso de as duas correrem juntas
        if (usuario.getExcluidaEm() != null) throw TokenInvalidoException.invalido();
        LocalDateTime agora = LocalDateTime.now(relogio);
        if (!agora.isBefore(pendente.getExpiraEm())) throw TokenInvalidoException.expirado();

        String novoEmail = pendente.getNovoEmail();
        if (usuarioRepository.existsByEmail(novoEmail)) {
            throw new ConflitoException(MENSAGEM_EMAIL_DUPLICADO, "EMAIL_JA_CADASTRADO");
        }

        String emailAntigo = usuario.getEmail();
        usuario.setEmail(novoEmail);
        pendenteRepository.delete(pendente);
        try {
            usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            // Cadastro com o mesmo e-mail ao mesmo tempo: o índice único segura um dos dois
            throw new ConflitoException(MENSAGEM_EMAIL_DUPLICADO, "EMAIL_JA_CADASTRADO");
        }

        // Aviso ao endereço antigo depois do commit; uma falha no envio não desfaz a troca
        try {
            fila.enviarDepoisDoCommit(emails.aviso(usuario.getId(), usuario.getNome(), emailAntigo, novoEmail, agora));
        } catch (RuntimeException e) {
            log.error("Não foi possível preparar o aviso de troca de e-mail da conta {}: {}", usuario.getId(),
                    e.getMessage(), e);
        }
        return new EmailConfirmadoDTO(novoEmail);
    }

    // Troca pendente ainda dentro do prazo, para a Minha Conta mostrar (vencida não aparece)
    @Transactional(readOnly = true)
    public Optional<EmailPendenteDTO> pendente(Long clienteId) {
        LocalDateTime agora = LocalDateTime.now(relogio);
        return pendenteRepository.findByUsuarioId(clienteId)
                .filter(p -> agora.isBefore(p.getExpiraEm()))
                .map(EmailPendenteDTO::from);
    }

    private String novoToken() {
        byte[] bytes = new byte[BYTES_TOKEN];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 basta (sem sal nem BCrypt): o token já é aleatório e longo, não dá para adivinhar por tentativa
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
