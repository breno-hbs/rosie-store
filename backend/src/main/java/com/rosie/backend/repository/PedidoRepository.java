package com.rosie.backend.repository;

import com.rosie.backend.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // Histórico de pedidos do cliente
    List<Pedido> findByUsuarioId(Long usuarioId);

    // Endereço usado num pedido não pode ser apagado nem alterado (EnderecoService)
    boolean existsByEnderecoId(Long enderecoId);
}
