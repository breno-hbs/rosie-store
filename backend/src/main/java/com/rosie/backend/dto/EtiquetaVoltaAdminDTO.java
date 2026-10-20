package com.rosie.backend.dto;

import com.rosie.backend.model.Troca;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Etiqueta de volta da troca no painel (#63): o que a cliente vê, mais a situação FALHOU com o motivo (erro),
// o id do envio no Melhor Envio e quanto deve custar (valorEstimado: o frete que a cliente pagou na ida, pelo
// mesmo serviço e entre os mesmos CEPs), para a Roseane saber antes de comprar
public record EtiquetaVoltaAdminDTO(
        String situacao,
        String envioId,
        String urlEtiqueta,
        String codigoRastreio,
        LocalDateTime geradaEm,
        String erro,
        BigDecimal valorEstimado
) {

    public static EtiquetaVoltaAdminDTO from(Troca troca) {
        return new EtiquetaVoltaAdminDTO(troca.getSituacaoEtiquetaVolta().name(), troca.getEnvioVoltaId(),
                troca.getUrlEtiquetaVolta(), troca.getCodigoRastreioVolta(), troca.getEtiquetaVoltaGeradaEm(),
                troca.getUrlEtiquetaVolta() == null ? troca.getErroEtiquetaVolta() : null,
                troca.getPedido().getValorFrete());
    }
}
