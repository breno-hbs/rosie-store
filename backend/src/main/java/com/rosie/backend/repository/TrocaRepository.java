package com.rosie.backend.repository;

import com.rosie.backend.model.StatusTroca;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Troca;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrocaRepository extends JpaRepository<Troca, Long> {

    List<Troca> findByPedidoId(Long pedidoId);

    // GET /trocas (#27): as trocas da cliente, mais recentes primeiro
    @EntityGraph(attributePaths = {"pedido", "produto"})
    List<Troca> findByPedidoUsuarioIdOrderByCreatedAtDescIdDesc(Long usuarioId);

    // Painel (#27): todas as trocas, já com a cliente
    @EntityGraph(attributePaths = {"pedido", "pedido.usuario", "produto"})
    List<Troca> findAllByOrderByCreatedAtDescIdDesc();

    // Uma troca por peça do pedido: só uma recusada libera pedir de novo
    boolean existsByPedidoIdAndProdutoIdAndTamanhoAndStatusNot(Long pedidoId, Long produtoId, Tamanho tamanho,
                                                               StatusTroca status);
}
