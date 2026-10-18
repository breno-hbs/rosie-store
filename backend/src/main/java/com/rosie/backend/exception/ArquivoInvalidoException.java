package com.rosie.backend.exception;

import lombok.Getter;
import org.springframework.util.unit.DataSize;

// Foto recusada no upload (HTTP 400): FORMATO_NAO_SUPORTADO ou ARQUIVO_MUITO_GRANDE
@Getter
public class ArquivoInvalidoException extends RuntimeException {

    private final String codigo;

    private ArquivoInvalidoException(String mensagem, String codigo) {
        super(mensagem);
        this.codigo = codigo;
    }

    public static ArquivoInvalidoException formatoNaoSuportado() {
        return new ArquivoInvalidoException("Formato não aceito: envie a foto em JPG, PNG ou WEBP",
                "FORMATO_NAO_SUPORTADO");
    }

    public static ArquivoInvalidoException muitoGrande(DataSize limite) {
        return new ArquivoInvalidoException("A foto passa de " + limite.toMegabytes() + " MB: diminua o arquivo e tente de novo",
                "ARQUIVO_MUITO_GRANDE");
    }
}
