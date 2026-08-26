package com.rosie.backend.dto;

import com.rosie.backend.model.ProdutoEstoque;

public record EstoqueDTO(String tamanho, Integer quantidade) {

    public static EstoqueDTO from(ProdutoEstoque estoque) {
        return new EstoqueDTO(estoque.getTamanho().name(), estoque.getQuantidade());
    }
}
