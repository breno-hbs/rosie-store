package com.rosie.backend.service;

import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.PedidoItem;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.ProdutoEstoqueRepository;
import com.rosie.backend.service.GatewayPagamento.Pagamento;
import com.rosie.backend.service.GatewayPagamento.StatusPagamento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Atualiza o pedido a partir de um pagamento do Mercado Pago (#24). O webhook só diz "o pagamento X mudou":
// o status vem sempre da consulta ao gateway, e o pedido sai do external_reference. Idempotente: o
// Mercado Pago repete avisos, e o mesmo pagamento aprovado duas vezes baixa o estoque uma vez só (o pedido
// fica travado durante o processamento e o segundo aviso já o encontra pago).
@Service
@RequiredArgsConstructor
@Slf4j
public class PagamentoService {

    // Pedido que ainda espera um pagamento: só nesses o webhook muda o status
    private static final Set<StatusPedido> EM_ABERTO =
            EnumSet.of(StatusPedido.AGUARDANDO_PAGAMENTO, StatusPedido.PAGAMENTO_RECUSADO);

    private final GatewayPagamento gatewayPagamento;
    private final PedidoRepository pedidoRepository;
    private final ProdutoEstoqueRepository produtoEstoqueRepository;
    private final CarrinhoItemRepository carrinhoItemRepository;

    @Transactional
    public void processar(String pagamentoId) {
        Pagamento pagamento = gatewayPagamento.buscarPagamento(pagamentoId);

        Optional<Pedido> encontrado = idDoPedido(pagamento).flatMap(pedidoRepository::buscarParaAtualizar);
        if (encontrado.isEmpty()) {
            // Pagamento de outra coisa na mesma conta, ou pedido desfeito: não há o que atualizar
            log.warn("Pagamento {} sem pedido correspondente (external_reference = {})", pagamento.id(),
                    pagamento.referenciaExterna());
            return;
        }
        Pedido pedido = encontrado.get();

        switch (pagamento.status()) {
            case APROVADO -> aprovar(pedido, pagamento);
            case RECUSADO -> mudarSeEmAberto(pedido, StatusPedido.PAGAMENTO_RECUSADO);
            case CANCELADO -> mudarSeEmAberto(pedido, StatusPedido.CANCELADO);
            case PENDENTE -> {
                // PIX gerado, cartão em análise: o pedido continua aguardando
            }
            // Devolução e contestação ficam com a Roseane, no painel do Mercado Pago (estoque não volta sozinho)
            case ESTORNADO -> log.warn("Pagamento {} do pedido {} foi estornado ou contestado", pagamento.id(),
                    pedido.getId());
        }
    }

    // Modo de desenvolvimento: cria um pagamento simulado com o valor do pedido e processa como um webhook.
    // Fora do modo simulado a rota não existe (404).
    @Transactional
    public void simular(Long clienteId, Long pedidoId, StatusPagamento status) {
        if (!(gatewayPagamento instanceof PagamentoSimulado simulado)) {
            throw new RecursoNaoEncontradoException("Pagamento simulado desligado neste servidor");
        }
        Pedido pedido = pedidoRepository.findByIdAndUsuarioId(pedidoId, clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + pedidoId + " não encontrado"));
        processar(simulado.registrar(pedido.getId(), status, pedido.getValorTotal()));
    }

    // Tudo ou nada, na mesma transação: confere o valor e o estoque de todas as peças, baixa, marca PAGO e
    // tira as peças da sacola. Se faltar estoque (outra cliente pagou antes a última peça) ou o valor não
    // bater, nada baixa e o pedido fica PAGO_COM_PENDENCIA para a Roseane resolver (enviar depois, trocar
    // ou devolver o dinheiro). O estoque nunca fica negativo.
    private void aprovar(Pedido pedido, Pagamento pagamento) {
        if (!EM_ABERTO.contains(pedido.getStatus()) && pedido.getStatus() != StatusPedido.CANCELADO) {
            log.info("Pedido {} já estava {}: aviso do pagamento {} ignorado", pedido.getId(), pedido.getStatus(),
                    pagamento.id());
            return;
        }
        pedido.setPagamentoId(pagamento.id());
        pedido.setPagoEm(LocalDateTime.now());

        if (pagamento.valor() == null || pagamento.valor().compareTo(pedido.getValorTotal()) != 0) {
            marcarPendencia(pedido, "Valor pago (" + pagamento.valor() + ") diferente do total do pedido ("
                    + pedido.getValorTotal() + "). Confira o pagamento " + pagamento.id() + " no Mercado Pago.");
            return;
        }

        // Ordem fixa das travas (produto, tamanho) para dois pedidos simultâneos não se travarem
        List<PedidoItem> itens = new ArrayList<>(pedido.getItens());
        itens.sort(Comparator.comparing((PedidoItem item) -> item.getProduto().getId())
                .thenComparing(PedidoItem::getTamanho));

        List<ProdutoEstoque> estoques = new ArrayList<>();
        List<String> faltas = new ArrayList<>();
        for (PedidoItem item : itens) {
            Optional<ProdutoEstoque> estoque = produtoEstoqueRepository
                    .buscarParaBaixa(item.getProduto().getId(), item.getTamanho());
            int disponivel = estoque.map(ProdutoEstoque::getQuantidade).orElse(0);
            if (disponivel < item.getQuantidade()) {
                faltas.add(item.getProduto().getNome() + " " + item.getTamanho() + " (pedido " + item.getQuantidade()
                        + ", em estoque " + disponivel + ")");
            }
            estoques.add(estoque.orElse(null));
        }

        esvaziarSacola(pedido);
        if (!faltas.isEmpty()) {
            marcarPendencia(pedido, "Pago, mas sem estoque suficiente: " + String.join("; ", faltas)
                    + ". O estoque não foi baixado.");
            return;
        }

        for (int i = 0; i < itens.size(); i++) {
            ProdutoEstoque estoque = estoques.get(i);
            estoque.setQuantidade(estoque.getQuantidade() - itens.get(i).getQuantidade());
        }
        pedido.setStatus(StatusPedido.PAGO);
        log.info("Pedido {} pago (pagamento {}): estoque baixado", pedido.getId(), pagamento.id());
    }

    private void marcarPendencia(Pedido pedido, String motivo) {
        pedido.setStatus(StatusPedido.PAGO_COM_PENDENCIA);
        pedido.setPendencia(motivo.length() > 500 ? motivo.substring(0, 500) : motivo);
        log.warn("Pedido {} pago com pendência: {}", pedido.getId(), motivo);
    }

    // Tira da sacola só o que foi comprado: se a cliente pôs outras peças depois do checkout, elas ficam
    private void esvaziarSacola(Pedido pedido) {
        Long clienteId = pedido.getUsuario().getId();
        for (PedidoItem item : pedido.getItens()) {
            carrinhoItemRepository
                    .findByUsuarioIdAndProdutoIdAndTamanho(clienteId, item.getProduto().getId(), item.getTamanho())
                    .ifPresent(naSacola -> {
                        int resto = naSacola.getQuantidade() - item.getQuantidade();
                        if (resto > 0) naSacola.setQuantidade(resto);
                        else carrinhoItemRepository.delete(naSacola);
                    });
        }
    }

    private void mudarSeEmAberto(Pedido pedido, StatusPedido novo) {
        if (EM_ABERTO.contains(pedido.getStatus())) {
            pedido.setStatus(novo);
        }
    }

    private static Optional<Long> idDoPedido(Pagamento pagamento) {
        try {
            return Optional.of(Long.valueOf(pagamento.referenciaExterna()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
