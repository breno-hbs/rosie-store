package com.rosie.backend.service;

import java.math.BigDecimal;
import java.util.List;

// Modo de desenvolvimento (FRETE_SIMULADO=true): opções fixas, sem chamar o Melhor Envio, para testar
// a tela sem token. Nunca ligar em produção. O CEP 99999-999 responde sem opções (nenhuma transportadora
// atende), para ver esse estado na tela.
public class FreteSimulado implements CalculadoraFrete {

    static final String CEP_SEM_OPCOES = "99999999";

    @Override
    public List<OpcaoFrete> calcular(String cepDestino, List<PecaFrete> pecas) {
        if (CEP_SEM_OPCOES.equals(cepDestino)) return List.of();

        int extras = Math.max(0, pecas.stream().mapToInt(PecaFrete::quantidade).sum() - 1);
        return List.of(
                new OpcaoFrete(1, "Correios", "PAC", preco("19.90", "1.50", extras), 8),
                new OpcaoFrete(2, "Correios", "SEDEX", preco("34.90", "2.50", extras), 3),
                new OpcaoFrete(3, "Jadlog", ".Package", preco("24.50", "1.00", extras), 6)
        );
    }

    private static BigDecimal preco(String base, String porPecaExtra, int extras) {
        return new BigDecimal(base).add(new BigDecimal(porPecaExtra).multiply(BigDecimal.valueOf(extras)));
    }
}
