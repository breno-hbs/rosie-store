// Troca e devolução (#27): textos da loja e do painel. Quem paga o frete de volta é decidido pela API a
// partir do motivo (RNF08); aqui só se mostra.

// RF07: a política de troca aparece em toda página de produto e na tela de troca
export const POLITICA_TROCA =
  'Trocas aceitas em caso de defeito ou produto que não serviu. Frete de devolução por conta da Rosie em caso de defeito; por conta do cliente nos demais casos.'

// Os dois cartões de motivo da tela "Solicitação de Troca" (Figma)
export const MOTIVOS_TROCA = [
  { id: 'DEFEITO', nome: 'Defeito', frete: 'Frete de volta por conta da Rosie' },
  { id: 'NAO_SERVIU', nome: 'Não serviu', frete: 'Frete de volta por conta do cliente' },
]

export function nomeMotivo(motivo) {
  return MOTIVOS_TROCA.find((m) => m.id === motivo)?.nome ?? motivo
}

// Status como a cliente lê (Minha Conta) e como a Roseane lê (painel)
const STATUS_CLIENTE = {
  SOLICITADA: 'Em análise',
  APROVADA: 'Aprovada',
  RECUSADA: 'Recusada',
  CONCLUIDA: 'Concluída',
}

export const STATUS_TROCA_ADMIN = {
  SOLICITADA: 'Solicitada',
  APROVADA: 'Aprovada',
  RECUSADA: 'Recusada',
  CONCLUIDA: 'Concluída',
}

export function rotuloStatusTroca(status) {
  return STATUS_CLIENTE[status] ?? status
}

// O que acontece agora, para a cliente
export function textoStatusTroca(troca) {
  switch (troca.status) {
    case 'SOLICITADA':
      return 'A Rosie vai analisar o pedido e responder por aqui.'
    case 'APROVADA':
      return troca.freteConta === 'ROSIE'
        ? 'Troca aprovada. A Rosie vai combinar com você o envio da peça, com o frete por conta dela.'
        : 'Troca aprovada. A Rosie vai combinar com você o envio da peça; o frete de volta é por sua conta.'
    case 'RECUSADA':
      return 'A troca não foi aprovada.'
    case 'CONCLUIDA':
      return 'Troca concluída.'
    default:
      return null
  }
}

export function textoFrete(freteConta, { painel = false } = {}) {
  if (freteConta === 'ROSIE') return 'Frete de volta por conta da Rosie'
  return painel ? 'Frete de volta por conta da cliente' : 'Frete de volta por sua conta'
}

// Hoje em São Paulo, no formato da API ("2026-10-19"): o prazo é contado no dia da loja
export function hojeNaLoja() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date())
}

// prazoTroca é o último dia (inteiro) para pedir troca; null antes da entrega
export function dentroDoPrazo(prazoTroca) {
  return Boolean(prazoTroca) && prazoTroca >= hojeNaLoja()
}

// Troca que segura a peça: só uma recusada libera pedir de novo
export function trocaAberta(troca) {
  return troca.status !== 'RECUSADA'
}
