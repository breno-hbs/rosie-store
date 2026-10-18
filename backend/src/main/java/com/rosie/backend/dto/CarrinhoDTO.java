package com.rosie.backend.dto;

import com.rosie.backend.model.CarrinhoItem;

import java.math.BigDecimal;
import java.util.List;

// Resposta de todas as rotas do carrinho (#22). subtotal soma só os itens disponíveis (o que dá para
// comprar agora); quantidadeItens soma as quantidades de todos os itens e é o número da "Sacola" na Nav.
public record CarrinhoDTO(
        List<CarrinhoItemDTO> itens,
        int quantidadeItens,
        BigDecimal subtotal,
        boolean temIndisponiveis
) {

    public static CarrinhoDTO from(List<CarrinhoItem> itens) {
        List<CarrinhoItemDTO> dtos = itens.stream().map(CarrinhoItemDTO::from).toList();

        int quantidadeItens = dtos.stream().mapToInt(CarrinhoItemDTO::quantidade).sum();
        BigDecimal subtotal = dtos.stream()
                .filter(CarrinhoItemDTO::disponivel)
                .map(CarrinhoItemDTO::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean temIndisponiveis = dtos.stream().anyMatch(item -> !item.disponivel());

        return new CarrinhoDTO(dtos, quantidadeItens, subtotal, temIndisponiveis);
    }
}
