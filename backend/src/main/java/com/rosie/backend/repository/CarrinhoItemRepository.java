package com.rosie.backend.repository;

import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.model.Tamanho;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CarrinhoItemRepository extends JpaRepository<CarrinhoItem, Long> {

    // GET /carrinho (Contrato da API), na ordem em que os itens entraram
    List<CarrinhoItem> findByUsuarioIdOrderByIdAsc(Long usuarioId);

    // Checa se o mesmo produto+tamanho já está no carrinho, pra somar quantidade
    // em vez de criar linha duplicada (bate com a constraint única do banco)
    Optional<CarrinhoItem> findByUsuarioIdAndProdutoIdAndTamanho(Long usuarioId, Long produtoId, Tamanho tamanho);

    // PUT/DELETE /carrinho/itens/{itemId}: item de outro cliente fica invisível (404)
    Optional<CarrinhoItem> findByIdAndUsuarioId(Long id, Long usuarioId);
}
