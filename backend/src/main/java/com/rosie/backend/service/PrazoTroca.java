package com.rosie.backend.service;

import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.StatusPedido;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

// Prazo para pedir troca (#27): rosie.troca.prazo-dias dias corridos contados da data de entrega, com o
// último dia inteiro valendo. O padrão de 7 dias é o direito de arrependimento do CDC (art. 49) para compra
// fora da loja física. "Hoje" é sempre o dia em São Paulo, não o do servidor (o Railway roda em UTC).
@Component
public class PrazoTroca {

    public static final ZoneId FUSO_LOJA = ZoneId.of("America/Sao_Paulo");

    private final int dias;

    public PrazoTroca(@Value("${rosie.troca.prazo-dias:7}") int dias) {
        this.dias = dias;
    }

    public LocalDate hoje() {
        return LocalDate.now(FUSO_LOJA);
    }

    // Último dia para pedir troca, ou null enquanto o pedido não foi entregue
    public LocalDate ate(Pedido pedido) {
        if (pedido.getStatus() != StatusPedido.ENTREGUE || pedido.getDataEntrega() == null) return null;
        return pedido.getDataEntrega().plusDays(dias);
    }

    public boolean aberto(Pedido pedido) {
        LocalDate ate = ate(pedido);
        return ate != null && !hoje().isAfter(ate);
    }
}
