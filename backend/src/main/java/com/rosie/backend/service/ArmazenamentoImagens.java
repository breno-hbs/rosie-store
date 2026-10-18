package com.rosie.backend.service;

// Onde as fotos dos produtos ficam (o disco do Railway é efêmero). Em produção é o Cloudinary;
// nos testes, uma implementação falsa, para o CI não depender das chaves nem da rede.
public interface ArmazenamentoImagens {

    // Guarda a foto (já validada: JPG, PNG ou WEBP) e devolve a URL https pública e o id para removê-la
    ImagemArmazenada enviar(byte[] conteudo);

    void remover(String publicId);

    record ImagemArmazenada(String url, String publicId) {
    }
}
