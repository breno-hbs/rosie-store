package com.rosie.backend.repository;

import com.rosie.backend.model.EmailPendente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EmailPendenteRepository extends JpaRepository<EmailPendente, Long> {

    // A troca pendente da cliente (#61): mostrada na Minha Conta e substituída por um pedido novo
    Optional<EmailPendente> findByUsuarioId(Long usuarioId);

    // Confirmação pelo link: trava a linha, para o mesmo token não ser usado duas vezes ao mesmo tempo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "usuario")
    Optional<EmailPendente> findByTokenHash(String tokenHash);

    // Envio do link falhou: desfaz o pedido, se ainda for o mesmo (um pedido mais novo fica)
    @Modifying
    @Query("delete from EmailPendente p where p.tokenHash = :tokenHash")
    int apagarPeloTokenHash(String tokenHash);

    // Exclusão da conta (#31)
    @Modifying
    @Query("delete from EmailPendente p where p.usuario.id = :usuarioId")
    int apagarDaCliente(Long usuarioId);
}
