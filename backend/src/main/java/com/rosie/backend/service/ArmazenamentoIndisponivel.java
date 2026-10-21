package com.rosie.backend.service;

import com.rosie.backend.exception.ArmazenamentoIndisponivelException;

// Usado quando CLOUDINARY_URL não está definida (dev local sem conta, CI): a API sobe normalmente
// e só o envio de fotos responde 503
public class ArmazenamentoIndisponivel implements ArmazenamentoImagens {

    static final String MENSAGEM = "O envio de fotos não está configurado no servidor (falta CLOUDINARY_URL)";

    @Override
    public ImagemArmazenada enviar(byte[] conteudo, String pasta) {
        throw new ArmazenamentoIndisponivelException(MENSAGEM);
    }

    @Override
    public void remover(String publicId) {
        throw new ArmazenamentoIndisponivelException(MENSAGEM);
    }
}
