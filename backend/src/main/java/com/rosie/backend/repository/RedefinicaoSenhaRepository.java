package com.rosie.backend.repository;

import com.rosie.backend.model.RedefinicaoSenha;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RedefinicaoSenhaRepository extends JpaRepository<RedefinicaoSenha, Long> {

    // O link pendente do usuário (#93): substituído por um pedido novo
    Optional<RedefinicaoSenha> findByUsuarioId(Long usuarioId);

    // Redefinição pelo link: trava a linha, para o mesmo token não ser usado duas vezes ao mesmo tempo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "usuario")
    Optional<RedefinicaoSenha> findByTokenHash(String tokenHash);

    // Senha trocada (pelo link ou na Minha Conta) e exclusão da conta (#31): o link pendente deixa de valer
    @Modifying
    @Query("delete from RedefinicaoSenha r where r.usuario.id = :usuarioId")
    int apagarDoUsuario(Long usuarioId);
}
