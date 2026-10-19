package com.rosie.backend.model;

// Ciclo do pedido. Quem muda o status do pagamento é o webhook do Mercado Pago (#24), nunca o retorno
// do navegador. ENVIADO vem da etiqueta (#25) e ENTREGUE do painel (#27).
public enum StatusPedido {
    // Criado no checkout, esperando a cliente pagar no Mercado Pago (PIX pendente também fica aqui)
    AGUARDANDO_PAGAMENTO,
    // O cartão foi recusado; a cliente pode tentar de novo pelo mesmo link de pagamento
    PAGAMENTO_RECUSADO,
    // Pagamento aprovado: o estoque já baixou e as peças saíram da sacola
    PAGO,
    // Pagamento aprovado, mas algo impediu de fechar sozinho (estoque acabou entre o checkout e a
    // aprovação, ou valor pago diferente do total): o estoque não baixou e a Roseane resolve (campo pendencia)
    PAGO_COM_PENDENCIA,
    // Etiqueta comprada e gerada no Melhor Envio pelo painel (#25): tem codigoRastreio (assim que o
    // Melhor Envio devolver) e a peça segue para a postagem
    ENVIADO,
    // A Roseane marcou no painel que a peça chegou (#27), com a data da entrega (dataEntrega): daí conta o
    // prazo de troca. Acompanhar o rastreio para marcar sozinho fica para outra issue.
    ENTREGUE,
    // Pagamento cancelado ou expirado no Mercado Pago (ex.: PIX não pago no prazo)
    CANCELADO
}
