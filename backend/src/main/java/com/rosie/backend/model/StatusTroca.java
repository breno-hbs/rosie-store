package com.rosie.backend.model;

import java.util.Set;

// Ciclo da troca (#27). A cliente pede (SOLICITADA); a Roseane aprova ou recusa pelo painel; quando a peça
// volta e a troca (ou a devolução do dinheiro) é resolvida, a Roseane marca CONCLUIDA. RECUSADA e
// CONCLUIDA são finais.
public enum StatusTroca {
    // Pedido da cliente, esperando a Roseane olhar
    SOLICITADA,
    // A Roseane aceitou: a cliente manda a peça de volta (o frete é de quem diz freteConta)
    APROVADA,
    // A Roseane não aceitou (a resposta diz o porquê); a cliente pode pedir de novo dentro do prazo
    RECUSADA,
    // A peça voltou e a troca foi resolvida
    CONCLUIDA;

    public boolean podeIrPara(StatusTroca destino) {
        return switch (this) {
            case SOLICITADA -> Set.of(APROVADA, RECUSADA).contains(destino);
            case APROVADA -> destino == CONCLUIDA;
            case RECUSADA, CONCLUIDA -> false;
        };
    }
}
