package com.rosie.backend.repository;

import com.rosie.backend.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    // Evita categoria duplicada ao cadastrar (RF17 — admin cria categoria)
    boolean existsByNomeIgnoreCase(String nome);
}
