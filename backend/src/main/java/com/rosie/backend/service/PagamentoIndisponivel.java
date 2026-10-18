package com.rosie.backend.service;

import com.rosie.backend.exception.PagamentoIndisponivelException;

// Usado quando falta alguma variável do Mercado Pago (dev local sem conta, CI): a API sobe normalmente,
// o checkout responde 503 e o webhook também (o Mercado Pago tenta de novo mais tarde)
public class PagamentoIndisponivel implements GatewayPagamento {

    static final String MENSAGEM = "O pagamento não está configurado no servidor";

    @Override
    public Preferencia criarPreferencia(NovaPreferencia dados) {
        throw new PagamentoIndisponivelException(MENSAGEM);
    }

    @Override
    public Pagamento buscarPagamento(String pagamentoId) {
        throw new PagamentoIndisponivelException(MENSAGEM);
    }

    @Override
    public void validarAssinatura(String xSignature, String xRequestId, String dataId) {
        throw new PagamentoIndisponivelException(MENSAGEM);
    }
}
