package com.rosie.backend.repository;

import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.StatusPedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
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

    // Exclusão da conta (#31): pedidos ainda em andamento (a pagar, a enviar, a caminho)
    List<Pedido> findByUsuarioIdAndStatusIn(Long usuarioId, Collection<StatusPedido> status);

    // Painel (#25): todos os pedidos, do mais recente para o mais antigo, já com a cliente e as peças
    @EntityGraph(attributePaths = {"usuario", "itens"})
    List<Pedido> findAllByOrderByCreatedAtDescIdDesc();

    // Rotina do rastreio (#64): pedidos ENVIADO que têm envio no Melhor Envio e código de rastreio, começando
    // pelos consultados há mais tempo (nunca consultado vem antes: no MySQL, null fica no começo da ordem
    // crescente). O tamanho da página é o lote da rodada.
    @Query("""
            select p from Pedido p
            where p.status = com.rosie.backend.model.StatusPedido.ENVIADO
              and p.envioId is not null and p.codigoRastreio is not null
            order by p.rastreioConsultadoEm asc, p.id asc""")
    List<Pedido> buscarParaRastrear(Pageable lote);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update Pedido p set p.rastreioConsultadoEm = :quando where p.id = :id")
    void marcarRastreioConsultado(Long id, LocalDateTime quando);

    // Webhook (#24): trava a linha do pedido até o fim da transação, para dois avisos do mesmo pagamento
    // chegando juntos não baixarem o estoque duas vezes (o segundo espera e já encontra o pedido pago)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> buscarParaAtualizar(Long id);
}
