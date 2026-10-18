package com.rosie.backend.exception;

// Storage das fotos fora do ar ou não configurado (sem CLOUDINARY_URL): HTTP 503 ARMAZENAMENTO_INDISPONIVEL
public class ArmazenamentoIndisponivelException extends RuntimeException {

    public ArmazenamentoIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public ArmazenamentoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
