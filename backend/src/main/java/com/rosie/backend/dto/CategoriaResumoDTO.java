package com.rosie.backend.dto;

import com.rosie.backend.model.Categoria;

public record CategoriaResumoDTO(Long id, String nome) {

    public static CategoriaResumoDTO from(Categoria categoria) {
        return new CategoriaResumoDTO(categoria.getId(), categoria.getNome());
    }
}
