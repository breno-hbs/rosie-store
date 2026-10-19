package com.rosie.backend.repository;

import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Usado no login (RF08/RF13) — busca pelo e-mail em vez do id
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    // Usado na criação do admin inicial
    boolean existsByRole(Role role);

    // Token de conta excluída deixa de valer na hora (#31), sem esperar o JWT vencer
    boolean existsByIdAndExcluidaEmIsNotNull(Long id);
}
