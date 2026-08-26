package com.rosie.backend.repository;

import com.rosie.backend.model.ProdutoImagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProdutoImagemRepository extends JpaRepository<ProdutoImagem, Long> {

    // Galeria do produto, já na ordem certa de exibição (RF03)
    List<ProdutoImagem> findByProdutoIdOrderByOrdemAsc(Long produtoId);
}
