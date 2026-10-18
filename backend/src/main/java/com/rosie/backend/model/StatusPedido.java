package com.rosie.backend.model;

// Ciclo do pedido. Quem muda o status do pagamento é o webhook do Mercado Pago (#24), nunca o retorno
// do navegador. ENVIADO e ENTREGUE ficam para a etiqueta e o rastreio (#25).
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
    ENVIADO,
    ENTREGUE,
    // Pagamento cancelado ou expirado no Mercado Pago (ex.: PIX não pago no prazo)
    CANCELADO
}
