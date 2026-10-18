package com.rosie.backend.repository;

import com.rosie.backend.model.MedidaCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MedidaCategoriaRepository extends JpaRepository<MedidaCategoria, Long> {

    // Tabela de medidas da categoria, na ordem em que a Roseane montou
    List<MedidaCategoria> findByCategoriaIdOrderByOrdem(Long categoriaId);

    // Salvar a tabela troca todas as linhas de uma vez; remover a categoria leva as medidas junto
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MedidaCategoria m where m.categoria.id = :categoriaId")
    void removerDaCategoria(Long categoriaId);
}
