package com.rosie.backend.repository;

import com.rosie.backend.model.Endereco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnderecoRepository extends JpaRepository<Endereco, Long> {

    // Lista os endereços salvos de um cliente (tela de checkout)
    List<Endereco> findByUsuarioId(Long usuarioId);
}
