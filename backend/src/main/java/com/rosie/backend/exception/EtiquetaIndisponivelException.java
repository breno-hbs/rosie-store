package com.rosie.backend.exception;

// Etiqueta não configurada (sem MELHOR_ENVIO_TOKEN, ROSIE_CEP_ORIGEM ou os dados do remetente) ou token
// recusado pelo Melhor Envio: HTTP 503 ETIQUETA_INDISPONIVEL
public class EtiquetaIndisponivelException extends RuntimeException {

    public EtiquetaIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
