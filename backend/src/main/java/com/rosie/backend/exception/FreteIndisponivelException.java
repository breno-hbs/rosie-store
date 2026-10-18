package com.rosie.backend.exception;

// Cálculo de frete não configurado (sem MELHOR_ENVIO_TOKEN ou ROSIE_CEP_ORIGEM) ou token recusado
// pelo Melhor Envio: HTTP 503 FRETE_INDISPONIVEL
public class FreteIndisponivelException extends RuntimeException {

    public FreteIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
