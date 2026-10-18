package com.rosie.backend.repository;

import com.rosie.backend.dto.CategoriaAdminDTO;
import com.rosie.backend.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    // Evita categoria duplicada ao cadastrar (RF17 — admin cria categoria)
    boolean existsByNomeIgnoreCase(String nome);

    // Mesma checagem ao renomear, ignorando a própria categoria
    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);

    // GET /admin/categorias — todas, com quantos produtos (ativos ou não) cada uma tem
    @Query("""
            select new com.rosie.backend.dto.CategoriaAdminDTO(c.id, c.nome, count(p.id))
            from Categoria c left join Produto p on p.categoria = c
            group by c.id, c.nome
            order by c.nome
            """)
    List<CategoriaAdminDTO> listarComQuantidadeDeProdutos();
}
