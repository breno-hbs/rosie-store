package com.rosie.backend.repository;

import com.rosie.backend.model.PedidoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {

    List<PedidoItem> findByPedidoId(Long pedidoId);

    // Produto que já entrou em pedido não pode ser excluído definitivamente, só arquivado (#87)
    boolean existsByProdutoId(Long produtoId);
}
