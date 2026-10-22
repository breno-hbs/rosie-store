package com.rosie.backend.repository;

import com.rosie.backend.model.StatusTroca;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Troca;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TrocaRepository extends JpaRepository<Troca, Long> {

    List<Troca> findByPedidoId(Long pedidoId);

    // Produto com troca não pode ser excluído definitivamente, só arquivado (#87)
    boolean existsByProdutoId(Long produtoId);

    // GET /trocas (#27): as trocas da cliente, mais recentes primeiro
    @EntityGraph(attributePaths = {"pedido", "produto"})
    List<Troca> findByPedidoUsuarioIdOrderByCreatedAtDescIdDesc(Long usuarioId);

    // Exclusão da conta (#31): troca ainda em aberto segura a exclusão
    boolean existsByPedidoUsuarioIdAndStatusIn(Long usuarioId, Collection<StatusTroca> status);

    // Painel (#27): todas as trocas, já com a cliente
    @EntityGraph(attributePaths = {"pedido", "pedido.usuario", "produto"})
    List<Troca> findAllByOrderByCreatedAtDescIdDesc();

    // Uma troca por peça do pedido: só uma recusada libera pedir de novo
    boolean existsByPedidoIdAndProdutoIdAndTamanhoAndStatusNot(Long pedidoId, Long produtoId, Tamanho tamanho,
                                                               StatusTroca status);
}
