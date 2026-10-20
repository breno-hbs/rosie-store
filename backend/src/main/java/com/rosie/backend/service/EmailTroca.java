package com.rosie.backend.service;

import com.rosie.backend.model.FreteConta;
import com.rosie.backend.model.StatusTroca;
import com.rosie.backend.model.Troca;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.service.EnviadorEmail.Email;
import com.rosie.backend.service.ModeloEmail.Destaque;
import com.rosie.backend.service.ModeloEmail.Detalhe;
import com.rosie.backend.service.ModeloEmail.Link;
import com.rosie.backend.service.ModeloEmail.Mensagem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// E-mail à cliente quando a Roseane aprova, recusa ou conclui a troca (#65). Montado dentro da transação da
// mudança de status (a troca ainda está carregada) e enviado pela FilaEmail depois do commit. É só um aviso:
// a situação completa continua em /minha-conta/trocas.
@Component
@Slf4j
public class EmailTroca {

    static final String INSTAGRAM = "https://www.instagram.com/rosie.sttore/";

    private final FilaEmail fila;
    private final String urlLoja;
    private final String whatsapp;
    private final boolean respostaPorEmail;

    public EmailTroca(FilaEmail fila,
                      @Value("${rosie.loja.url:http://localhost:5173}") String urlLoja,
                      @Value("${rosie.loja.whatsapp:}") String whatsapp,
                      @Value("${rosie.email.responder-para:}") String responderPara) {
        this.fila = fila;
        this.urlLoja = urlLoja.trim().replaceAll("/+$", "");
        this.whatsapp = whatsapp == null ? "" : whatsapp.replaceAll("\\D", "");
        this.respostaPorEmail = responderPara != null && !responderPara.isBlank();
    }

    // recado: o que a Roseane escreveu nesta mudança (o recado de uma mudança anterior não se repete).
    // Nunca lança: um problema aqui não pode desfazer a mudança de status.
    public void avisarMudanca(Troca troca, String recado) {
        try {
            Usuario cliente = troca.getPedido().getUsuario();
            // Conta excluída (LGPD, #31): o e-mail foi anonimizado e a cliente não recebe mais nada
            if (cliente.getExcluidaEm() != null) {
                log.info("Troca {} mudou para {}, sem e-mail: a conta da cliente foi excluída", troca.getId(),
                        troca.getStatus());
                return;
            }
            fila.enviarDepoisDoCommit(montar(troca, recado));
        } catch (RuntimeException e) {
            log.error("Não foi possível preparar o e-mail da troca {}: {}", troca.getId(), e.getMessage(), e);
        }
    }

    Email montar(Troca troca, String recado) {
        Usuario cliente = troca.getPedido().getUsuario();
        Long pedidoId = troca.getPedido().getId();
        StatusTroca status = troca.getStatus();

        List<Detalhe> detalhes = List.of(
                new Detalhe("Pedido", "#" + pedidoId),
                new Detalhe("Peça", troca.getProduto().getNome()),
                new Detalhe("Tamanho", troca.getTamanho().name()),
                new Detalhe("Quantidade", String.valueOf(troca.getQuantidade())),
                new Detalhe("Situação", situacao(status)));

        List<String> paragrafos = new ArrayList<>();
        List<String> fechamento = new ArrayList<>();
        Destaque destaque = recado == null ? null : new Destaque("Recado da Rosie", recado);
        String titulo;
        String assunto;
        switch (status) {
            case APROVADA -> {
                titulo = "Sua troca foi aprovada";
                assunto = "Troca do pedido #" + pedidoId + " aprovada";
                paragrafos.add("A Rosie aprovou a troca da peça abaixo.");
                paragrafos.add(troca.getFreteConta() == FreteConta.ROSIE
                        ? "Como a peça veio com defeito, o frete de devolução é por conta da Rosie."
                        : "Como a peça não serviu, o frete de devolução é por sua conta.");
                paragrafos.add("Próximo passo: separe a peça, de preferência na embalagem em que ela chegou. "
                        + "A Rosie vai combinar com você como mandá-la de volta.");
            }
            case RECUSADA -> {
                titulo = "Sua troca não foi aprovada";
                assunto = "Troca do pedido #" + pedidoId + " não aprovada";
                paragrafos.add("A Rosie analisou o seu pedido de troca da peça abaixo e não pôde aprovar.");
                if (recado == null) {
                    paragrafos.add("Para saber o motivo, fale com a Rosie pelos contatos abaixo.");
                } else {
                    destaque = new Destaque("Motivo", recado);
                }
                fechamento.add("Se ainda estiver no prazo, você pode pedir a troca de novo pela sua conta.");
            }
            case CONCLUIDA -> {
                titulo = "Sua troca foi concluída";
                assunto = "Troca do pedido #" + pedidoId + " concluída";
                paragrafos.add("A peça voltou para a Rosie e a sua troca foi concluída. Obrigada pela paciência!");
            }
            default -> throw new IllegalArgumentException("Troca " + status + " não tem e-mail");
        }
        fechamento.add(contato());

        List<Link> links = new ArrayList<>();
        links.add(new Link("Ver minhas trocas", urlLoja + "/minha-conta/trocas"));
        if (!whatsapp.isEmpty()) links.add(new Link("Falar no WhatsApp", "https://wa.me/" + whatsapp));
        links.add(new Link("Instagram @rosie.sttore", INSTAGRAM));

        String nome = primeiroNome(cliente.getNome());
        Mensagem mensagem = new Mensagem(nome.isEmpty() ? "Olá!" : "Olá, " + nome + "!", titulo, paragrafos,
                destaque, detalhes, fechamento, links,
                "Você recebeu este e-mail porque pediu uma troca na Rosie. A situação completa fica sempre em "
                        + "Minha Conta, no site.");
        return new Email(cliente.getEmail(), assunto, ModeloEmail.html(mensagem), ModeloEmail.texto(mensagem),
                "troca-" + troca.getId() + "-" + status.name());
    }

    private String contato() {
        String canais = whatsapp.isEmpty() ? "pelo Instagram" : "pelo WhatsApp ou pelo Instagram";
        return respostaPorEmail
                ? "Dúvidas? Responda este e-mail ou fale com a Rosie " + canais + "."
                : "Dúvidas? Fale com a Rosie " + canais + ".";
    }

    private static String situacao(StatusTroca status) {
        return switch (status) {
            case SOLICITADA -> "Em análise";
            case APROVADA -> "Aprovada";
            case RECUSADA -> "Recusada";
            case CONCLUIDA -> "Concluída";
        };
    }

    static String primeiroNome(String nome) {
        return nome == null || nome.isBlank() ? "" : nome.strip().split("\\s+")[0];
    }
}
