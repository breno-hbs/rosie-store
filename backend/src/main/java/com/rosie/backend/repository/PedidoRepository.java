package com.rosie.backend.repository;

import com.rosie.backend.model.Pedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // Histórico de pedidos do cliente
    List<Pedido> findByUsuarioId(Long usuarioId);

    // GET /pedidos (#26): mais recentes primeiro; o id desempata pedidos do mesmo segundo
    List<Pedido> findByUsuarioIdOrderByCreatedAtDescIdDesc(Long usuarioId);

    // GET /pedidos/{id}: pedido de outra cliente fica invisível (404)
    Optional<Pedido> findByIdAndUsuarioId(Long id, Long usuarioId);

    // Endereço usado num pedido não pode ser apagado nem alterado (EnderecoService)
    boolean existsByEnderecoId(Long enderecoId);

    // Webhook (#24): trava a linha do pedido até o fim da transação, para dois avisos do mesmo pagamento
    // chegando juntos não baixarem o estoque duas vezes (o segundo espera e já encontra o pedido pago)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> buscarParaAtualizar(Long id);
}
