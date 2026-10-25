package com.rosie.backend.service;

import com.rosie.backend.exception.MuitasTentativasException;
import com.rosie.backend.exception.TokenInvalidoException;
import com.rosie.backend.model.RedefinicaoSenha;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.RedefinicaoSenhaRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.EnviadorEmail.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

// "Esqueci minha senha" (#93), o mesmo fluxo para a cliente e para o admin: o pedido manda ao e-mail da conta um
// link com token de uso único e prazo curto (o desenho da troca de e-mail, #61), e o link leva à página de nova
// senha. Trocar a senha, por aqui ou na Minha Conta, encerra as sessões abertas e manda um aviso por e-mail.
@Service
@Slf4j
public class RedefinicaoSenhaService {

    private final UsuarioRepository usuarioRepository;
    private final RedefinicaoSenhaRepository redefinicaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final FilaEmail fila;
    private final EmailSenha emails;
    private final LimitePedidosSenha limite;
    private final Clock relogio;
    private final Duration validade;

    public RedefinicaoSenhaService(UsuarioRepository usuarioRepository,
                                   RedefinicaoSenhaRepository redefinicaoRepository,
                                   PasswordEncoder passwordEncoder, FilaEmail fila, EmailSenha emails,
                                   LimitePedidosSenha limite, Clock relogio,
                                   @Value("${rosie.senha.redefinicao.validade:1h}") Duration validade) {
        this.usuarioRepository = usuarioRepository;
        this.redefinicaoRepository = redefinicaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.fila = fila;
        this.emails = emails;
        this.limite = limite;
        this.relogio = relogio;
        this.validade = validade;
    }

    // Pedido do link. Quem chama sempre recebe a mesma resposta, exista ou não a conta: e-mail sem conta, conta
    // excluída (#31, o e-mail dela já foi anonimizado) e conta ativa terminam igual, e o e-mail sai pela fila
    // depois do commit, sem a resposta esperar o Resend (o tempo de resposta não denuncia a conta, e uma falha
    // do Resend também não). Um pedido novo substitui o anterior, cujo link deixa de valer.
    @Transactional
    public void solicitar(String email, String ip) {
        String normalizado = AuthService.normalizarEmail(email);
        if (!limite.permitir(normalizado, ip)) throw new MuitasTentativasException();

        Optional<Usuario> encontrado = usuarioRepository.findByEmail(normalizado)
                .filter(u -> u.getExcluidaEm() == null);
        if (encontrado.isEmpty()) {
            log.info("Pedido de redefinição de senha para um e-mail sem conta ativa: {}", Email.mascarar(normalizado));
            return;
        }
        Usuario usuario = encontrado.get();

        String token = TokenLink.novo();
        String tokenHash = TokenLink.hash(token);
        // Sem frações de segundo, como na troca de e-mail
        LocalDateTime agora = LocalDateTime.now(relogio).truncatedTo(ChronoUnit.SECONDS);
        RedefinicaoSenha pedido = redefinicaoRepository.findByUsuarioId(usuario.getId())
                .orElseGet(() -> RedefinicaoSenha.builder().usuario(usuario).build());
        pedido.setTokenHash(tokenHash);
        pedido.setSolicitadoEm(agora);
        pedido.setExpiraEm(agora.plus(validade));
        redefinicaoRepository.save(pedido);

        fila.enviarDepoisDoCommit(emails.link(usuario.getNome(), usuario.getEmail(), usuario.getRole(), token,
                pedido.getExpiraEm(), tokenHash.substring(0, 32)));
    }

    // Nova senha pelo link (público: o token prova que a pessoa recebeu o e-mail da conta). A linha do pedido fica
    // travada até o commit, para o mesmo token não ser usado duas vezes ao mesmo tempo, e é apagada: um segundo
    // uso responde TOKEN_INVALIDO. Não entra na conta: a pessoa volta ao login da sua área com a senha nova.
    @Transactional
    public void redefinir(String token, String novaSenha) {
        AuthService.conferirTamanhoSenha(novaSenha);
        if (token == null || token.isBlank()) throw TokenInvalidoException.invalido();
        RedefinicaoSenha pedido = redefinicaoRepository.findByTokenHash(TokenLink.hash(token.trim()))
                .orElseThrow(TokenInvalidoException::invalido);
        Usuario usuario = pedido.getUsuario();
        // A exclusão da conta (#31) já apaga o pedido; isto só cobre o caso de as duas correrem juntas
        if (usuario.getExcluidaEm() != null) throw TokenInvalidoException.invalido();
        if (!LocalDateTime.now(relogio).isBefore(pedido.getExpiraEm())) {
            throw TokenInvalidoException.expirado("Este link expirou. Peça um link novo");
        }
        trocarSenha(usuario, novaSenha);
    }

    // Grava a senha nova (BCrypt) e encerra as sessões abertas: a versão da credencial sobe e o JwtConfig recusa
    // os tokens da versão anterior. O link de redefinição pendente deixa de valer, e a dona da conta recebe um
    // aviso depois do commit (uma falha no envio não desfaz a troca). Usado também pela Minha Conta (#26), que
    // devolve um token novo para o aparelho de onde a troca foi feita continuar logado.
    // Quem chama confere o tamanho da senha e abre a transação.
    public void trocarSenha(Usuario usuario, String novaSenha) {
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuario.setVersaoCredencial(usuario.getVersaoCredencial() + 1);
        usuarioRepository.save(usuario);
        redefinicaoRepository.apagarDoUsuario(usuario.getId());
        try {
            fila.enviarDepoisDoCommit(emails.aviso(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                    usuario.getRole(), LocalDateTime.now(relogio)));
        } catch (RuntimeException e) {
            log.error("Não foi possível preparar o aviso de troca de senha da conta {}: {}", usuario.getId(),
                    e.getMessage(), e);
        }
    }
}
