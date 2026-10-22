package com.rosie.backend.dto;

import com.rosie.backend.model.Categoria;

// slug: endereço da página da categoria na loja, /categoria/{slug} (#88)
public record CategoriaResumoDTO(Long id, String nome, String slug) {

    public static CategoriaResumoDTO from(Categoria categoria) {
        return new CategoriaResumoDTO(categoria.getId(), categoria.getNome(), categoria.getSlug());
    }
}
