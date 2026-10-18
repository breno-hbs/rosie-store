package com.rosie.backend.dto;

import com.rosie.backend.model.Enquadramento;
import com.rosie.backend.model.ProdutoImagem;
import com.rosie.backend.service.UrlEntregaImagem;

// Foto do produto no painel: o id serve para remover e reordenar; ordem 0 é a principal.
// A url já é a versão cortada em 4:5 (#18), a mesma que a loja mostra.
public record ProdutoImagemDTO(Long id, String url, Integer ordem, Enquadramento enquadramento) {

    public static ProdutoImagemDTO from(ProdutoImagem imagem) {
        Enquadramento enquadramento = imagem.getEnquadramento() == null
                ? Enquadramento.AUTOMATICO
                : imagem.getEnquadramento();
        return new ProdutoImagemDTO(imagem.getId(), UrlEntregaImagem.de(imagem), imagem.getOrdem(), enquadramento);
    }
}
