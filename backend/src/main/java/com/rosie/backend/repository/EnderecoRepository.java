package com.rosie.backend.repository;

import com.rosie.backend.model.Endereco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnderecoRepository extends JpaRepository<Endereco, Long> {

    // GET /enderecos: os endereços salvos da cliente (checkout e Minha Conta), sem os arquivados
    List<Endereco> findByUsuarioIdAndArquivadoFalseOrderByIdAsc(Long usuarioId);

    // Endereço de outra cliente, ou arquivado, fica invisível (404 nas rotas, 400 no POST /pedidos)
    Optional<Endereco> findByIdAndUsuarioIdAndArquivadoFalse(Long id, Long usuarioId);
}
