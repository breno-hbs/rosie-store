package com.rosie.backend.service;

import com.rosie.backend.exception.EtiquetaIndisponivelException;

// Usado quando falta MELHOR_ENVIO_TOKEN, ROSIE_CEP_ORIGEM ou algum dado do remetente (dev local, CI): a API
// sobe normalmente e só a geração de etiqueta responde 503
public class EtiquetaIndisponivel implements EmissorEtiqueta {

    static final String MENSAGEM = "A etiqueta de envio não está configurada no servidor";

    @Override
    public String adicionarAoCarrinho(NovoEnvio envio) {
        throw new EtiquetaIndisponivelException(MENSAGEM);
    }

    @Override
    public SituacaoEnvio consultar(String envioId) {
        throw new EtiquetaIndisponivelException(MENSAGEM);
    }

    @Override
    public void comprar(String envioId) {
        throw new EtiquetaIndisponivelException(MENSAGEM);
    }

    @Override
    public void gerar(String envioId) {
        throw new EtiquetaIndisponivelException(MENSAGEM);
    }

    @Override
    public String linkImpressao(String envioId) {
        throw new EtiquetaIndisponivelException(MENSAGEM);
    }

    @Override
    public String linkImpressaoPublico(String envioId) {
        throw new EtiquetaIndisponivelException(MENSAGEM);
    }

    @Override
    public boolean disponivel() {
        return false;
    }
}
