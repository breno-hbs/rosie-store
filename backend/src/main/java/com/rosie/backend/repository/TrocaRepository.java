package com.rosie.backend.repository;

import com.rosie.backend.model.Troca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrocaRepository extends JpaRepository<Troca, Long> {

    List<Troca> findByPedidoId(Long pedidoId);
}
