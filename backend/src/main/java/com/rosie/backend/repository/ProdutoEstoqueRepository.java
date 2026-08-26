package com.rosie.backend.repository;

import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.Tamanho;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProdutoEstoqueRepository extends JpaRepository<ProdutoEstoque, Long> {

    List<ProdutoEstoque> findByProdutoId(Long produtoId);

    // Usado ao adicionar no carrinho, pra checar se tem estoque daquele tamanho (RF06)
    Optional<ProdutoEstoque> findByProdutoAndTamanho(Produto produto, Tamanho tamanho);
}
