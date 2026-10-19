package com.rosie.backend.service;

import com.rosie.backend.dto.TrocaAdminDTO;
import com.rosie.backend.dto.TrocaDTO;
import com.rosie.backend.dto.TrocaRequestDTO;
import com.rosie.backend.dto.TrocaStatusRequestDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.PedidoItem;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.model.StatusTroca;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Troca;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.TrocaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

// Troca e devolução (RF18, RNF08, #27). A cliente pede a troca de uma peça de um pedido entregue, dentro do
// prazo (PrazoTroca); quem paga o frete de volta sai do motivo, nunca da requisição. A Roseane acompanha e
// muda o status pelo painel. Estoque e estorno ficam com a Roseane (Gestão de Estoque e Mercado Pago).
@Service
@RequiredArgsConstructor
@Transactional
public class TrocaService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TrocaRepository trocaRepository;
    private final PedidoRepository pedidoRepository;
    private final PrazoTroca prazoTroca;

    public TrocaDTO solicitar(Long clienteId, TrocaRequestDTO requisicao) {
        // A trava na linha do pedido faz dois cliques seguidos virarem uma troca só (o segundo espera e
        // já encontra a primeira)
        Pedido pedido = pedidoRepository.buscarParaAtualizar(requisicao.pedidoId())
                .filter(p -> p.getUsuario().getId().equals(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + requisicao.pedidoId() + " não encontrado"));

        if (pedido.getStatus() != StatusPedido.ENTREGUE) {
            throw new ConflitoException("A troca pode ser pedida depois que o pedido for entregue",
                    "PEDIDO_NAO_ENTREGUE");
        }
        if (!prazoTroca.aberto(pedido)) {
            throw new ConflitoException("O prazo para pedir troca deste pedido terminou em "
                    + prazoTroca.ate(pedido).format(DATA), "TROCA_FORA_DO_PRAZO");
        }

        List<PedidoItem> doProduto = pedido.getItens().stream()
                .filter(item -> item.getProduto().getId().equals(requisicao.produtoId()))
                .toList();
        Tamanho tamanho = tamanho(requisicao, doProduto);
        int comprada = doProduto.stream()
                .filter(item -> item.getTamanho() == tamanho)
                .mapToInt(PedidoItem::getQuantidade)
                .sum();
        if (comprada == 0) {
            throw new DadosInvalidosException("Essa peça não está no pedido");
        }
        int quantidade = requisicao.quantidade() == null ? comprada : requisicao.quantidade();
        if (quantidade > comprada) {
            throw new DadosInvalidosException("O pedido tem " + comprada + (comprada == 1 ? " unidade" : " unidades")
                    + " dessa peça");
        }

        if (trocaRepository.existsByPedidoIdAndProdutoIdAndTamanhoAndStatusNot(pedido.getId(),
                requisicao.produtoId(), tamanho, StatusTroca.RECUSADA)) {
            throw new ConflitoException("Você já pediu a troca dessa peça", "TROCA_JA_SOLICITADA");
        }

        Troca troca = trocaRepository.save(Troca.builder()
                .pedido(pedido)
                .produto(doProduto.get(0).getProduto())
                .tamanho(tamanho)
                .quantidade(quantidade)
                .motivo(requisicao.motivo())
                .observacao(textoOuNulo(requisicao.observacao()))
                .build());
        return TrocaDTO.from(troca);
    }

    @Transactional(readOnly = true)
    public List<TrocaDTO> listar(Long clienteId) {
        return trocaRepository.findByPedidoUsuarioIdOrderByCreatedAtDescIdDesc(clienteId).stream()
                .map(TrocaDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrocaAdminDTO> listarTodas() {
        return trocaRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(TrocaAdminDTO::from)
                .toList();
    }

    // SOLICITADA → APROVADA ou RECUSADA; APROVADA → CONCLUIDA. Repetir o status atual só troca o recado.
    public TrocaAdminDTO atualizarStatus(Long trocaId, TrocaStatusRequestDTO requisicao) {
        Troca troca = trocaRepository.findById(trocaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Troca " + trocaId + " não encontrada"));

        StatusTroca destino = requisicao.status();
        if (destino != troca.getStatus() && !troca.getStatus().podeIrPara(destino)) {
            throw new ConflitoException("Uma troca " + nome(troca.getStatus()) + " não pode passar para "
                    + nome(destino), "STATUS_TROCA_INVALIDO");
        }
        troca.setStatus(destino);
        String resposta = textoOuNulo(requisicao.resposta());
        if (resposta != null) {
            troca.setResposta(resposta);
        }
        trocaRepository.flush();
        return TrocaAdminDTO.from(troca);
    }

    // Sem tamanho, vale o único tamanho em que o produto veio no pedido
    private static Tamanho tamanho(TrocaRequestDTO requisicao, List<PedidoItem> doProduto) {
        if (doProduto.isEmpty()) {
            throw new DadosInvalidosException("Essa peça não está no pedido");
        }
        if (requisicao.tamanho() != null) return requisicao.tamanho();
        List<Tamanho> tamanhos = doProduto.stream().map(PedidoItem::getTamanho).distinct().toList();
        if (tamanhos.size() > 1) {
            throw new DadosInvalidosException("Informe o tamanho da peça");
        }
        return tamanhos.get(0);
    }

    private static String nome(StatusTroca status) {
        return status == StatusTroca.CONCLUIDA ? "concluída" : status.name().toLowerCase();
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
