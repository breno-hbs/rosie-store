package com.rosie.backend.service;

import com.rosie.backend.model.Role;
import com.rosie.backend.service.EnviadorEmail.Email;
import com.rosie.backend.service.ModeloEmail.Detalhe;
import com.rosie.backend.service.ModeloEmail.Link;
import com.rosie.backend.service.ModeloEmail.Mensagem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Os dois e-mails da senha (#93), no layout do ModeloEmail: o link de "esqueci minha senha" e o aviso de que a
// senha foi trocada. Servem à cliente e ao admin: cada um recebe o link da página da sua área, que depois leva
// ao seu login.
@Component
public class EmailSenha {

    static final String CAMINHO_REDEFINICAO_LOJA = "/redefinir-senha";
    static final String CAMINHO_REDEFINICAO_PAINEL = "/admin/redefinir-senha";

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final DateTimeFormatter CHAVE_HORA = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String urlLoja;
    private final String whatsapp;

    public EmailSenha(@Value("${rosie.loja.url:http://localhost:5173}") String urlLoja,
                      @Value("${rosie.loja.whatsapp:}") String whatsapp) {
        this.urlLoja = urlLoja.trim().replaceAll("/+$", "");
        this.whatsapp = whatsapp == null ? "" : whatsapp.replaceAll("\\D", "");
    }

    // Link de uso único para a página de nova senha. chave: o hash do token (o token em si não sai daqui a não
    // ser dentro do link)
    public Email link(String nome, String email, Role role, String token, LocalDateTime expiraEm, String chave) {
        String caminho = role == Role.ADMIN ? CAMINHO_REDEFINICAO_PAINEL : CAMINHO_REDEFINICAO_LOJA;
        String link = urlLoja + caminho + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        String conta = role == Role.ADMIN ? "do painel da Rosie" : "na Rosie";
        Mensagem mensagem = new Mensagem(saudacao(nome), "Crie uma nova senha",
                List.of("Recebemos um pedido para redefinir a senha da sua conta " + conta + ". Para criar uma "
                                + "senha nova, abra o link abaixo.",
                        "O link vale até " + DATA_HORA.format(expiraEm) + " (horário de Brasília) e só pode ser "
                                + "usado uma vez. Se você pedir outro, só o mais novo vale."),
                null, List.of(),
                List.of("Não foi você? É só ignorar este e-mail: a sua senha continua a mesma."),
                List.of(new Link("Criar nova senha", link)),
                "Você recebeu este e-mail porque alguém pediu para redefinir a senha da conta com este endereço.");
        return new Email(email, "Redefinir a sua senha na Rosie", ModeloEmail.html(mensagem),
                ModeloEmail.texto(mensagem), "senha-link-" + chave);
    }

    // Aviso depois da troca (pelo link ou na Minha Conta), com o caminho para recuperar a conta se não foi a dona
    public Email aviso(Long usuarioId, String nome, String email, Role role, LocalDateTime trocadaEm) {
        List<Link> links = new ArrayList<>();
        String fechamento;
        if (role == Role.ADMIN) {
            fechamento = "Não foi você? Peça um link novo em \"Esqueci minha senha\" no login do painel para criar "
                    + "outra senha.";
        } else {
            if (!whatsapp.isEmpty()) links.add(new Link("Falar no WhatsApp", "https://wa.me/" + whatsapp));
            links.add(new Link("Instagram @rosie.sttore", EmailTroca.INSTAGRAM));
            String canais = whatsapp.isEmpty() ? "pelo Instagram" : "pelo WhatsApp ou pelo Instagram";
            fechamento = "Não foi você? Peça um link novo em \"Esqueci minha senha\" no login da loja e fale com a "
                    + "Rosie " + canais + ".";
        }
        Mensagem mensagem = new Mensagem(saudacao(nome), "A sua senha foi trocada",
                List.of("A senha da sua conta " + (role == Role.ADMIN ? "do painel da Rosie" : "na Rosie")
                        + " foi trocada. Por segurança, as sessões abertas com a senha antiga foram encerradas: "
                        + "nos outros aparelhos, entre de novo com a senha nova."),
                null,
                List.of(new Detalhe("Quando", DATA_HORA.format(trocadaEm))),
                List.of(fechamento),
                links,
                "Você recebeu este aviso porque este é o e-mail de acesso da sua conta na Rosie.");
        return new Email(email, "A sua senha na Rosie foi trocada", ModeloEmail.html(mensagem),
                ModeloEmail.texto(mensagem), "senha-aviso-" + usuarioId + "-" + CHAVE_HORA.format(trocadaEm));
    }

    private static String saudacao(String nome) {
        String primeiro = EmailTroca.primeiroNome(nome);
        return primeiro.isEmpty() ? "Olá!" : "Olá, " + primeiro + "!";
    }
}
