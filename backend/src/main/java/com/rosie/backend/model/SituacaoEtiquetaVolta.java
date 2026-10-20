package com.rosie.backend.model;

// Etiqueta de volta da troca (logística reversa, #63)
public enum SituacaoEtiquetaVolta {
    // Frete da Rosie (defeito), etiqueta ainda não gerada
    SEM_ETIQUETA,
    // A última tentativa de gerar falhou (Melhor Envio fora do ar, saldo...): o painel oferece tentar de novo
    FALHOU,
    // Pronta: link para imprimir e, quando o Melhor Envio devolve, o rastreio
    GERADA,
    // Frete da cliente (não serviu): a loja não compra etiqueta, a cliente manda a peça por conta dela
    POR_CONTA_DA_CLIENTE
}
