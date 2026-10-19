package com.rosie.backend.service;

import com.rosie.backend.dto.PedidoAdminDTO;
import com.rosie.backend.dto.PedidoAdminResumoDTO;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Pedidos no painel da Roseane (#25): todos os pedidos de todas as clientes, em qualquer status
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PedidoAdminService {

    private final PedidoRepository pedidoRepository;

    public List<PedidoAdminResumoDTO> listar() {
        return pedidoRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(PedidoAdminResumoDTO::from)
                .toList();
    }

    public PedidoAdminDTO buscar(Long pedidoId) {
        return pedidoRepository.findById(pedidoId)
                .map(PedidoAdminDTO::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + pedidoId + " não encontrado"));
    }
}
