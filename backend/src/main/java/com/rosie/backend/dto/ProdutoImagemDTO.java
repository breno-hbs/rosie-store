package com.rosie.backend.dto;

import com.rosie.backend.model.ProdutoImagem;

// Foto do produto no painel: o id serve para remover e reordenar; ordem 0 é a principal
public record ProdutoImagemDTO(Long id, String url, Integer ordem) {

    public static ProdutoImagemDTO from(ProdutoImagem imagem) {
        return new ProdutoImagemDTO(imagem.getId(), imagem.getUrl(), imagem.getOrdem());
    }
}
