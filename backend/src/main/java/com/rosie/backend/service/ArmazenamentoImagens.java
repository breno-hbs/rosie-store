package com.rosie.backend.service;

// Onde as fotos dos produtos e as imagens dos banners ficam (o disco do Railway é efêmero). Em produção é o
// Cloudinary; nos testes, uma implementação falsa, para o CI não depender das chaves nem da rede.
public interface ArmazenamentoImagens {

    String PASTA_PRODUTOS = "rosie/produtos";
    String PASTA_BANNERS = "rosie/banners";

    // Guarda a foto (já validada: JPG, PNG ou WEBP) na pasta e devolve a URL https pública e o id para removê-la
    ImagemArmazenada enviar(byte[] conteudo, String pasta);

    default ImagemArmazenada enviar(byte[] conteudo) {
        return enviar(conteudo, PASTA_PRODUTOS);
    }

    void remover(String publicId);

    record ImagemArmazenada(String url, String publicId) {
    }
}
