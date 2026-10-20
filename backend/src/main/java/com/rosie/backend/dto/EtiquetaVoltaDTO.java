package com.rosie.backend.dto;

import com.rosie.backend.model.SituacaoEtiquetaVolta;
import com.rosie.backend.model.Troca;

import java.time.LocalDateTime;

// Etiqueta de volta da troca como a cliente vê (#63). situacao: SEM_ETIQUETA, GERADA ou POR_CONTA_DA_CLIENTE
// (uma falha na geração é assunto do painel: para a cliente, continua sem etiqueta). urlEtiqueta abre a
// etiqueta para imprimir; codigoRastreio pode vir null logo depois de gerar.
public record EtiquetaVoltaDTO(String situacao, String urlEtiqueta, String codigoRastreio, LocalDateTime geradaEm) {

    public static EtiquetaVoltaDTO from(Troca troca) {
        SituacaoEtiquetaVolta situacao = troca.getSituacaoEtiquetaVolta();
        if (situacao == SituacaoEtiquetaVolta.FALHOU) situacao = SituacaoEtiquetaVolta.SEM_ETIQUETA;
        return new EtiquetaVoltaDTO(situacao.name(), troca.getUrlEtiquetaVolta(), troca.getCodigoRastreioVolta(),
                troca.getEtiquetaVoltaGeradaEm());
    }
}
