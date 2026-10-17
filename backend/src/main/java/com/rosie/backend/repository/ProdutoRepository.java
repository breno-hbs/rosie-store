package com.rosie.backend.repository;

import com.rosie.backend.model.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // GET /produtos?categoriaId=1&pagina=0&tamanho=12 (Contrato da API) — só os ativos, RF01
    Page<Produto> findByCategoriaIdAndAtivoTrue(Long categoriaId, Pageable pageable);

    // GET /produtos (sem filtro de categoria) — só os ativos, RF01
    Page<Produto> findByAtivoTrue(Pageable pageable);
}
