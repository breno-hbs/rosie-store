package com.rosie.backend.service;

import com.rosie.backend.exception.EtiquetaIndisponivelException;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.service.EmissorEtiqueta.SituacaoEnvio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// Entrega pelo rastreio (#64): consulta no Melhor Envio os pedidos ENVIADO com código de rastreio e, quando a
// transportadora informa a entrega, confirma pelo mesmo caminho do botão "Marcar como entregue" (#27), com a
// data de lá. Roda agendada (RastreioConfig), a cada rosie.rastreio.intervalo.
//
// Limites da API: no máximo rosie.rastreio.lote pedidos por rodada, um de cada vez, com rosie.rastreio.pausa
// entre as consultas; os de fora do lote ficam para a rodada seguinte (a fila começa pelos consultados há mais
// tempo). Erro num pedido (Melhor Envio fora do ar, resposta estranha, data inválida) vai para o log e a rodada
// segue; o pedido é consultado de novo na próxima.
@Service
@Slf4j
public class RastreioEntregas {

    private final PedidoRepository pedidoRepository;
    private final PedidoAdminService pedidoAdminService;
    private final EmissorEtiqueta emissor;
    private final Clock relogio;
    private final int lote;
    private final Duration pausa;

    // Uma rodada por vez nesta instância; entre instâncias, quem segura é a trava da linha do pedido
    private final AtomicBoolean rodando = new AtomicBoolean();
    private final AtomicBoolean avisouDesligada = new AtomicBoolean();

    public RastreioEntregas(PedidoRepository pedidoRepository, PedidoAdminService pedidoAdminService,
                            EmissorEtiqueta emissor, Clock relogio,
                            @Value("${rosie.rastreio.lote:50}") int lote,
                            @Value("${rosie.rastreio.pausa:1s}") Duration pausa) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoAdminService = pedidoAdminService;
        this.emissor = emissor;
        this.relogio = relogio;
        this.lote = lote;
        this.pausa = pausa;
    }

    public record Rodada(int consultados, int entregues, int falhas) {

        static final Rodada VAZIA = new Rodada(0, 0, 0);
    }

    public Rodada executar() {
        if (!emissor.disponivel()) {
            if (avisouDesligada.compareAndSet(false, true)) {
                log.warn("Melhor Envio não configurado (MELHOR_ENVIO_TOKEN): a entrega pelo rastreio não roda. "
                        + "Os pedidos ficam para o botão \"Marcar como entregue\" do painel.");
            }
            return Rodada.VAZIA;
        }
        if (!rodando.compareAndSet(false, true)) {
            log.info("Rastreio: a rodada anterior ainda não terminou; esta fica para a próxima");
            return Rodada.VAZIA;
        }
        try {
            return rodar();
        } finally {
            rodando.set(false);
        }
    }

    private Rodada rodar() {
        List<Pedido> pedidos = pedidoRepository.buscarParaRastrear(PageRequest.of(0, lote));
        int consultados = 0;
        int entregues = 0;
        int falhas = 0;
        for (Pedido pedido : pedidos) {
            if (consultados++ > 0) esperar();
            try {
                if (confirmarSeEntregue(pedido.getId(), pedido.getEnvioId())) entregues++;
            } catch (EtiquetaIndisponivelException e) {
                // Token recusado pelo Melhor Envio (o motivo já foi para o log): os outros pedidos dariam o mesmo
                falhas++;
                log.warn("Rastreio interrompido: o Melhor Envio recusou o acesso. Tenta de novo na próxima rodada.");
                break;
            } catch (RuntimeException e) {
                falhas++;
                log.warn("Rastreio do pedido {} (envio {}) falhou; tenta de novo na próxima rodada: {}",
                        pedido.getId(), pedido.getEnvioId(), e.getMessage());
            }
        }
        if (consultados > 0) {
            log.info("Rastreio: {} pedido(s) consultado(s), {} entregue(s), {} com erro",
                    consultados, entregues, falhas);
        }
        return new Rodada(consultados, entregues, falhas);
    }

    private boolean confirmarSeEntregue(Long pedidoId, String envioId) {
        // Marca antes de consultar: um pedido que sempre falha também vai para o fim da fila
        pedidoRepository.marcarRastreioConsultado(pedidoId, LocalDateTime.now(relogio));
        SituacaoEnvio situacao = emissor.consultar(envioId);
        if (situacao == null || !situacao.entregue()) {
            return false;
        }
        LocalDate data = situacao.entregueEm();
        if (data == null) {
            data = LocalDate.now(relogio.withZone(PrazoTroca.FUSO_LOJA));
            log.info("Pedido {}: o Melhor Envio informou a entrega sem a data; vale o dia da consulta ({})",
                    pedidoId, data);
        }
        boolean mudou = pedidoAdminService.confirmarEntregaPeloRastreio(pedidoId, data);
        if (mudou) {
            log.info("Pedido {}: entregue em {} pelo rastreio", pedidoId, data);
        }
        return mudou;
    }

    private void esperar() {
        if (pausa.isZero() || pausa.isNegative()) return;
        try {
            Thread.sleep(pausa.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
