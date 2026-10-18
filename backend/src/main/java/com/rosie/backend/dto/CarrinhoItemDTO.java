package com.rosie.backend.dto;

import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.Tamanho;

import java.math.BigDecimal;

// Item do carrinho. Um item que ficou indisponível depois de adicionado (produto desativado ou estoque
// menor que a quantidade) continua no carrinho com disponivel = false e o motivo, para a cliente decidir.
public record CarrinhoItemDTO(
        Long id,
        CarrinhoProdutoDTO produto,
        String tamanho,
        Integer quantidade,
        BigDecimal subtotal,
        Integer estoqueDisponivel,
        boolean disponivel,
        String motivoIndisponivel
) {

    public static final String PRODUTO_INATIVO = "PRODUTO_INATIVO";
    public static final String TAMANHO_ESGOTADO = "TAMANHO_ESGOTADO";
    public static final String ESTOQUE_INSUFICIENTE = "ESTOQUE_INSUFICIENTE";

    public static CarrinhoItemDTO from(CarrinhoItem item) {
        Produto produto = item.getProduto();
        int estoque = estoqueDoTamanho(produto, item.getTamanho());

        String motivo = null;
        if (!Boolean.TRUE.equals(produto.getAtivo())) motivo = PRODUTO_INATIVO;
        else if (estoque == 0) motivo = TAMANHO_ESGOTADO;
        else if (estoque < item.getQuantidade()) motivo = ESTOQUE_INSUFICIENTE;

        return new CarrinhoItemDTO(
                item.getId(),
                CarrinhoProdutoDTO.from(produto),
                item.getTamanho().name(),
                item.getQuantidade(),
                produto.getPreco().multiply(BigDecimal.valueOf(item.getQuantidade())),
                estoque,
                motivo == null,
                motivo
        );
    }

    // Tamanho sem linha de estoque cadastrada conta como esgotado
    public static int estoqueDoTamanho(Produto produto, Tamanho tamanho) {
        return produto.getEstoque().stream()
                .filter(estoque -> estoque.getTamanho() == tamanho)
                .mapToInt(ProdutoEstoque::getQuantidade)
                .findFirst()
                .orElse(0);
    }
}
