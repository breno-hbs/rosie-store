package com.rosie.backend.service;

import com.rosie.backend.exception.FreteIndisponivelException;

import java.util.List;

// Usado quando falta MELHOR_ENVIO_TOKEN ou ROSIE_CEP_ORIGEM (dev local sem conta, CI): a API sobe
// normalmente e só o cálculo de frete responde 503
public class FreteIndisponivel implements CalculadoraFrete {

    static final String MENSAGEM = "O cálculo de frete não está configurado no servidor";

    @Override
    public List<OpcaoFrete> calcular(String cepDestino, List<PecaFrete> pecas) {
        throw new FreteIndisponivelException(MENSAGEM);
    }
}
