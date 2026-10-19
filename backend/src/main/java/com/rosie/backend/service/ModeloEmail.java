package com.rosie.backend.service;

import org.springframework.web.util.HtmlUtils;

import java.util.List;

// Layout dos e-mails da loja (#65): a mesma mensagem em HTML e em texto puro. Segue a marca do site (nome
// ROSIE, texto escuro sobre papel claro, sem sombra, gradiente nem imagem), mas com o que e-mail aceita:
// cores da paleta em hex inline (sem variáveis CSS), tabelas para o layout e Jost só se o leitor tiver a
// fonte, com Helvetica/Arial no lugar. Todo texto que entra aqui é escapado: o recado da Roseane e o nome
// da cliente nunca viram HTML.
public final class ModeloEmail {

    // Paleta do front-end (frontend/src/index.css)
    static final String TINTA = "#141311";
    static final String PAPEL = "#faf8f4";
    static final String ACENTO = "#3b372e";
    static final String LINHA = "#dedace";
    private static final String FONTE = "Jost, 'Helvetica Neue', Helvetica, Arial, sans-serif";

    private ModeloEmail() {
    }

    // Na ordem em que aparece: saudacao ("Olá, Maria!"), titulo (o assunto em linguagem simples),
    // paragrafos, um destaque opcional (ex.: o recado da Roseane, com quebras de linha), detalhes (rótulo e
    // valor, ex.: Pedido #270), fechamento (ex.: como falar com a loja), links e o rodapé. Tudo texto puro.
    public record Mensagem(String saudacao, String titulo, List<String> paragrafos, Destaque destaque,
                           List<Detalhe> detalhes, List<String> fechamento, List<Link> links, String rodape) {
    }

    public record Detalhe(String rotulo, String valor) {
    }

    public record Destaque(String rotulo, String texto) {
    }

    public record Link(String texto, String url) {
    }

    public static String html(Mensagem mensagem) {
        StringBuilder corpo = new StringBuilder();
        if (mensagem.saudacao() != null) {
            corpo.append("<p style=\"margin:0 0 8px;font-size:15px;color:").append(ACENTO).append(";\">")
                    .append(escapar(mensagem.saudacao())).append("</p>");
        }
        corpo.append("<h1 style=\"margin:0 0 24px;font-size:22px;line-height:1.3;font-weight:500;color:")
                .append(TINTA).append(";\">").append(escapar(mensagem.titulo())).append("</h1>");

        paragrafos(corpo, mensagem.paragrafos());

        Destaque destaque = mensagem.destaque();
        if (destaque != null) {
            corpo.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                    .append("style=\"margin:8px 0 24px;\"><tr><td style=\"padding:16px;border:1px solid ")
                    .append(LINHA).append(";\"><p style=\"margin:0 0 8px;font-size:12px;letter-spacing:1px;")
                    .append("text-transform:uppercase;color:").append(ACENTO).append(";\">")
                    .append(escapar(destaque.rotulo())).append("</p><p style=\"margin:0;font-size:15px;")
                    .append("line-height:1.6;color:").append(TINTA).append(";\">")
                    .append(escapar(destaque.texto()).replace("\n", "<br>")).append("</p></td></tr></table>");
        }

        if (!mensagem.detalhes().isEmpty()) {
            corpo.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                    .append("style=\"margin:8px 0 24px;border-top:1px solid ").append(LINHA).append(";\">");
            for (Detalhe detalhe : mensagem.detalhes()) {
                corpo.append("<tr><td style=\"padding:8px 0;border-bottom:1px solid ").append(LINHA)
                        .append(";font-size:14px;color:").append(ACENTO).append(";\">")
                        .append(escapar(detalhe.rotulo()))
                        .append("</td><td align=\"right\" style=\"padding:8px 0;border-bottom:1px solid ")
                        .append(LINHA).append(";font-size:14px;color:").append(TINTA).append(";\">")
                        .append(escapar(detalhe.valor())).append("</td></tr>");
            }
            corpo.append("</table>");
        }

        paragrafos(corpo, mensagem.fechamento());

        for (Link link : mensagem.links()) {
            corpo.append("<p style=\"margin:0 0 12px;font-size:15px;\"><a href=\"").append(escapar(link.url()))
                    .append("\" style=\"color:").append(TINTA).append(";text-decoration:underline;\">")
                    .append(escapar(link.texto())).append("</a></p>");
        }

        return "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + escapar(mensagem.titulo()) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:" + PAPEL + ";color:" + TINTA + ";font-family:" + FONTE + ";\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:" + PAPEL + ";\">"
                + "<tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;font-family:" + FONTE + ";\">"
                + "<tr><td style=\"padding:0 0 20px;border-bottom:1px solid " + LINHA + ";font-size:22px;letter-spacing:6px;font-weight:500;color:" + TINTA + ";\">ROSIE</td></tr>"
                + "<tr><td style=\"padding:28px 0 12px;\">" + corpo + "</td></tr>"
                + "<tr><td style=\"padding:20px 0 0;border-top:1px solid " + LINHA + ";font-size:12px;line-height:1.6;color:" + ACENTO + ";\">"
                + escapar(mensagem.rodape()) + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    public static String texto(Mensagem mensagem) {
        StringBuilder texto = new StringBuilder("ROSIE\n\n");
        if (mensagem.saudacao() != null) texto.append(mensagem.saudacao()).append("\n\n");
        texto.append(mensagem.titulo()).append("\n\n");
        for (String paragrafo : mensagem.paragrafos()) {
            texto.append(paragrafo).append("\n\n");
        }
        if (mensagem.destaque() != null) {
            texto.append(mensagem.destaque().rotulo()).append(":\n")
                    .append(mensagem.destaque().texto().replace("\r\n", "\n")).append("\n\n");
        }
        if (!mensagem.detalhes().isEmpty()) {
            for (Detalhe detalhe : mensagem.detalhes()) {
                texto.append(detalhe.rotulo()).append(": ").append(detalhe.valor()).append('\n');
            }
            texto.append('\n');
        }
        for (String paragrafo : mensagem.fechamento()) {
            texto.append(paragrafo).append("\n\n");
        }
        for (Link link : mensagem.links()) {
            texto.append(link.texto()).append(": ").append(link.url()).append('\n');
        }
        if (!mensagem.links().isEmpty()) texto.append('\n');
        texto.append("--\n").append(mensagem.rodape()).append('\n');
        return texto.toString();
    }

    private static void paragrafos(StringBuilder corpo, List<String> paragrafos) {
        for (String paragrafo : paragrafos) {
            corpo.append("<p style=\"margin:0 0 16px;font-size:15px;line-height:1.6;color:").append(TINTA)
                    .append(";\">").append(escapar(paragrafo)).append("</p>");
        }
    }

    // Escapa < > & " e ' (HtmlUtils), e normaliza as quebras de linha
    static String escapar(String texto) {
        return HtmlUtils.htmlEscape(texto.replace("\r\n", "\n"), "UTF-8");
    }
}
