package com.rosie.backend.service;

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

// Os dois e-mails da troca de e-mail da Minha Conta (#61), no layout do ModeloEmail: o link de confirmação,
// mandado ao endereço novo, e o aviso ao endereço antigo depois que a troca vale.
@Component
public class EmailMudancaLogin {

    static final String CAMINHO_CONFIRMACAO = "/minha-conta/confirmar-email";

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final DateTimeFormatter CHAVE_HORA = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String urlLoja;
    private final String whatsapp;

    public EmailMudancaLogin(@Value("${rosie.loja.url:http://localhost:5173}") String urlLoja,
                             @Value("${rosie.loja.whatsapp:}") String whatsapp) {
        this.urlLoja = urlLoja.trim().replaceAll("/+$", "");
        this.whatsapp = whatsapp == null ? "" : whatsapp.replaceAll("\\D", "");
    }

    // Link de uso único para a página de confirmação da loja. chave: o hash do token (o token em si não sai
    // daqui a não ser dentro do link)
    public Email confirmacao(String nome, String novoEmail, String token, LocalDateTime expiraEm, String chave) {
        String link = urlLoja + CAMINHO_CONFIRMACAO + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        Mensagem mensagem = new Mensagem(saudacao(nome), "Confirme o seu novo e-mail",
                List.of("Você pediu para usar este endereço como o e-mail de acesso da sua conta na Rosie. "
                                + "Para confirmar, abra o link abaixo.",
                        "O link vale até " + DATA_HORA.format(expiraEm) + " (horário de Brasília) e só pode ser "
                                + "usado uma vez. Até a confirmação, você continua entrando com o e-mail antigo."),
                null, List.of(),
                List.of("Não foi você? É só ignorar este e-mail: nada muda na conta."),
                List.of(new Link("Confirmar novo e-mail", link)),
                "Você recebeu este e-mail porque alguém pediu para usar este endereço numa conta da Rosie.");
        return new Email(novoEmail, "Confirme o seu novo e-mail na Rosie", ModeloEmail.html(mensagem),
                ModeloEmail.texto(mensagem), "conta-email-" + chave);
    }

    // Aviso ao e-mail antigo: o novo vai mascarado (se a troca não foi da dona da conta, quem lê o aviso pode
    // não ser ela), com o caminho para pedir ajuda à loja
    public Email aviso(Long usuarioId, String nome, String emailAntigo, String novoEmail, LocalDateTime trocadoEm) {
        List<Link> links = new ArrayList<>();
        if (!whatsapp.isEmpty()) links.add(new Link("Falar no WhatsApp", "https://wa.me/" + whatsapp));
        links.add(new Link("Instagram @rosie.sttore", EmailTroca.INSTAGRAM));

        String canais = whatsapp.isEmpty() ? "pelo Instagram" : "pelo WhatsApp ou pelo Instagram";
        Mensagem mensagem = new Mensagem(saudacao(nome), "O e-mail da sua conta foi trocado",
                List.of("O e-mail de acesso da sua conta na Rosie foi trocado. Daqui em diante, entre na loja com o "
                        + "e-mail novo; este endereço não recebe mais os avisos da conta."),
                null,
                List.of(new Detalhe("Novo e-mail", Email.mascarar(novoEmail)),
                        new Detalhe("Quando", DATA_HORA.format(trocadoEm))),
                List.of("Não foi você? Fale com a Rosie agora " + canais + " para recuperar o acesso."),
                links,
                "Você recebeu este aviso porque este era o e-mail de acesso da sua conta na Rosie.");
        return new Email(emailAntigo, "O e-mail da sua conta na Rosie foi trocado", ModeloEmail.html(mensagem),
                ModeloEmail.texto(mensagem), "conta-email-aviso-" + usuarioId + "-" + CHAVE_HORA.format(trocadoEm));
    }

    private static String saudacao(String nome) {
        String primeiro = EmailTroca.primeiroNome(nome);
        return primeiro.isEmpty() ? "Olá!" : "Olá, " + primeiro + "!";
    }
}
