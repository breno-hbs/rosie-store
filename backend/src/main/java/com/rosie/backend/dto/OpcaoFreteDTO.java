package com.rosie.backend.dto;

import com.rosie.backend.service.CalculadoraFrete.OpcaoFrete;

import java.math.BigDecimal;

// Uma forma de entrega. id é o serviço no Melhor Envio: o checkout (#24) manda ele de volta e cota de novo.
public record OpcaoFreteDTO(
        Integer id,
        String transportadora,
        String servico,
        BigDecimal preco,
        int prazoDias
) {

    public static OpcaoFreteDTO from(OpcaoFrete opcao) {
        return new OpcaoFreteDTO(opcao.servicoId(), opcao.transportadora(), opcao.servico(), opcao.preco(),
                opcao.prazoDias());
    }
}
