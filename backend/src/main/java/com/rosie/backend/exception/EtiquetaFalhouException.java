package com.rosie.backend.exception;

// O Melhor Envio recusou a etiqueta (ex.: saldo insuficiente, dado do envio inválido), não respondeu a
// tempo ou devolveu algo inesperado: HTTP 502 ETIQUETA_FALHOU. A mensagem diz o que o Melhor Envio
// respondeu, para a Roseane saber o que fazer.
public class EtiquetaFalhouException extends RuntimeException {

    public EtiquetaFalhouException(String mensagem) {
        super(mensagem);
    }

    public EtiquetaFalhouException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
