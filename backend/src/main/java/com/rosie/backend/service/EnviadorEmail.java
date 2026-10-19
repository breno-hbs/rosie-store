package com.rosie.backend.service;

// Envio de e-mail transacional da loja (#65, reaproveitado na #61). Implementações: o Resend (ResendEnviador),
// o modo log sem RESEND_API_KEY (EmailSoNoLog) e a falsa dos testes. Quem dispara um e-mail não chama isto
// direto: usa a FilaEmail, que espera o commit e não deixa uma falha de envio derrubar a ação.
public interface EnviadorEmail {

    // Falha (provedor fora do ar, recusa, tempo esgotado) sai como EmailFalhouException
    void enviar(Email email);

    // html e texto são a mesma mensagem (ModeloEmail monta as duas). chave (opcional) evita e-mail repetido
    // se o mesmo envio for tentado duas vezes: o Resend ignora a repetição por 24 horas.
    record Email(String para, String assunto, String html, String texto, String chave) {

        // Destinatário para o log, sem expor o endereço inteiro (LGPD): "ma***@gmail.com"
        public String paraMascarado() {
            return mascarar(para);
        }

        public static String mascarar(String endereco) {
            if (endereco == null) return null;
            int arroba = endereco.indexOf('@');
            if (arroba <= 0) return "***";
            String local = endereco.substring(0, arroba);
            return local.substring(0, Math.min(2, Math.max(1, local.length() - 1))) + "***" + endereco.substring(arroba);
        }
    }
}
