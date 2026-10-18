package com.rosie.backend.repository;

import com.rosie.backend.model.Produto;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // GET /produtos?categoriaId=1&pagina=0&tamanho=12 (Contrato da API) — só os ativos, RF01
    Page<Produto> findByCategoriaIdAndAtivoTrue(Long categoriaId, Pageable pageable);

    // GET /produtos (sem filtro de categoria) — só os ativos, RF01
    Page<Produto> findByAtivoTrue(Pageable pageable);

    // GET /produtos/destaques?limite=12 — ativos marcados como destaque, mais recentes primeiro (RF01)
    List<Produto> findByAtivoTrueAndDestaqueTrueOrderByIdDesc(Limit limite);

    // GET /produtos/{id} — produto inativo responde 404, assim como some da listagem (RF03)
    Optional<Produto> findByIdAndAtivoTrue(Long id);

    // GET /admin/produtos — todos, inclusive os inativos, mais recentes primeiro (RF14)
    @EntityGraph(attributePaths = "categoria")
    List<Produto> findAllByOrderByIdDesc();
}
