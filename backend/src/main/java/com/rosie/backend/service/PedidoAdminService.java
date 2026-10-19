package com.rosie.backend.service;

import com.rosie.backend.dto.PedidoAdminDTO;
import com.rosie.backend.dto.PedidoAdminResumoDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// Pedidos no painel da Roseane (#25): todos os pedidos de todas as clientes, em qualquer status
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PedidoAdminService {

    private final PedidoRepository pedidoRepository;
    private final PrazoTroca prazoTroca;

    public List<PedidoAdminResumoDTO> listar() {
        return pedidoRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(PedidoAdminResumoDTO::from)
                .toList();
    }

    public PedidoAdminDTO buscar(Long pedidoId) {
        return PedidoAdminDTO.from(pedido(pedidoId));
    }

    // A peça chegou (#27): ENVIADO → ENTREGUE, com o dia da entrega (hoje, se não vier), de onde conta o prazo
    // de troca. Repetir num pedido já entregue devolve o pedido sem mudar a data.
    @Transactional
    public PedidoAdminDTO marcarEntregue(Long pedidoId, LocalDate dataEntrega) {
        Pedido pedido = pedido(pedidoId);
        if (pedido.getStatus() == StatusPedido.ENTREGUE) {
            return PedidoAdminDTO.from(pedido);
        }
        if (pedido.getStatus() != StatusPedido.ENVIADO) {
            throw new ConflitoException("Só pedido enviado pode ser marcado como entregue", "PEDIDO_NAO_ENVIADO");
        }

        LocalDate data = dataEntrega == null ? prazoTroca.hoje() : dataEntrega;
        if (data.isAfter(prazoTroca.hoje())) {
            throw new DadosInvalidosException("A data da entrega não pode ser no futuro");
        }
        if (pedido.getEtiquetaGeradaEm() != null && data.isBefore(pedido.getEtiquetaGeradaEm().toLocalDate())) {
            throw new DadosInvalidosException("A data da entrega não pode ser antes do envio");
        }

        pedido.setStatus(StatusPedido.ENTREGUE);
        pedido.setDataEntrega(data);
        return PedidoAdminDTO.from(pedido);
    }

    private Pedido pedido(Long pedidoId) {
        return pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + pedidoId + " não encontrado"));
    }
}
