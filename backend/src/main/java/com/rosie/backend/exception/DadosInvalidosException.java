package com.rosie.backend.exception;

// Regra de negócio que o Bean Validation não cobre (ex.: categoria inexistente, tamanho repetido): 400 DADOS_INVALIDOS
public class DadosInvalidosException extends RuntimeException {

    public DadosInvalidosException(String mensagem) {
        super(mensagem);
    }
}
