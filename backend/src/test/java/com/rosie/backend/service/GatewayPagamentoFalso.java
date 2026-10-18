package com.rosie.backend.service;

import com.rosie.backend.exception.AssinaturaInvalidaException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Substitui o Mercado Pago nos testes: guarda as preferências pedidas, devolve os pagamentos programados
// e aceita só a assinatura ASSINATURA_VALIDA. O bean é compartilhado entre os testes do mesmo contexto:
// chame limpar() no @BeforeEach.
public class GatewayPagamentoFalso implements GatewayPagamento {

    public static final String ASSINATURA_VALIDA = "ts=1700000000,v1=assinatura-de-teste";

    public final List<NovaPreferencia> preferencias = new ArrayList<>();
    public final List<String> consultas = new ArrayList<>();
    public final Map<String, Pagamento> pagamentos = new HashMap<>();
    // Se definido, criarPreferencia() e buscarPagamento() lançam esta exceção
    public RuntimeException falha;

    @Override
    public Preferencia criarPreferencia(NovaPreferencia dados) {
        preferencias.add(dados);
        if (falha != null) throw falha;
        return new Preferencia("pref-" + dados.pedidoId(),
                "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-" + dados.pedidoId());
    }

    @Override
    public Pagamento buscarPagamento(String pagamentoId) {
        consultas.add(pagamentoId);
        if (falha != null) throw falha;
        Pagamento pagamento = pagamentos.get(pagamentoId);
        if (pagamento == null) throw new IllegalStateException("Pagamento " + pagamentoId + " não programado no teste");
        return pagamento;
    }

    @Override
    public void validarAssinatura(String xSignature, String xRequestId, String dataId) {
        if (!ASSINATURA_VALIDA.equals(xSignature)) throw new AssinaturaInvalidaException("Assinatura de teste não confere");
    }

    public void programar(String pagamentoId, StatusPagamento status, Long pedidoId, BigDecimal valor) {
        pagamentos.put(pagamentoId, new Pagamento(pagamentoId, status, String.valueOf(pedidoId), valor));
    }

    public void limpar() {
        preferencias.clear();
        consultas.clear();
        pagamentos.clear();
        falha = null;
    }

    // @Import(GatewayPagamentoFalso.Config.class) no teste troca o Mercado Pago por este
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public GatewayPagamentoFalso gatewayPagamentoFalso() {
            return new GatewayPagamentoFalso();
        }
    }
}
