package com.rosie.backend.repository;

import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Usado no login (RF08/RF13) — busca pelo e-mail em vez do id
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    // Usado na criação do admin inicial
    boolean existsByRole(Role role);

    // Conferido em cada requisição com token (JwtConfig): o token de conta excluída (#31) ou de antes da última
    // troca de senha (#93) deixa de valer na hora, sem esperar o JWT vencer
    @Query("select u.excluidaEm as excluidaEm, u.versaoCredencial as versaoCredencial from Usuario u where u.id = :id")
    Optional<EstadoCredencial> buscarEstadoCredencial(Long id);

    interface EstadoCredencial {
        LocalDateTime getExcluidaEm();

        int getVersaoCredencial();
    }
}
