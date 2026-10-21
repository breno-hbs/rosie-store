package com.rosie.backend.repository;

import com.rosie.backend.model.CategoriaSlugAntigo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CategoriaSlugAntigoRepository extends JpaRepository<CategoriaSlugAntigo, Long> {

    // GET /categorias/{slug}: link de antes de a categoria ser renomeada
    Optional<CategoriaSlugAntigo> findBySlug(String slug);

    // Uma categoria passou a usar o slug: o link vai para ela, não mais para a dona antiga
    @Modifying(flushAutomatically = true)
    @Query("delete from CategoriaSlugAntigo s where s.slug = :slug")
    void liberar(String slug);

    // Remover a categoria leva os slugs antigos junto
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CategoriaSlugAntigo s where s.categoria.id = :categoriaId")
    void removerDaCategoria(Long categoriaId);
}
