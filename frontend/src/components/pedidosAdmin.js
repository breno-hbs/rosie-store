import { ApiError } from '../api.js'

// Nomes dos status do pedido no painel (#25), na ordem do ciclo do pedido
export const STATUS_PEDIDO = {
  AGUARDANDO_PAGAMENTO: 'Aguardando pagamento',
  PAGAMENTO_RECUSADO: 'Pagamento recusado',
  PAGO: 'Pago',
  PAGO_COM_PENDENCIA: 'Pago com pendência',
  ENVIADO: 'Enviado',
  ENTREGUE: 'Entregue',
  CANCELADO: 'Cancelado',
}

export const FORMA_PAGAMENTO = { CARTAO: 'Cartão de crédito', PIX: 'PIX' }

const formatoData = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

// A API manda LocalDateTime sem fuso ("2026-10-18T14:30:00"): o navegador lê como hora local
export function formatarDataHora(valor) {
  return valor ? formatoData.format(new Date(valor)) : ''
}

export function mensagemDeErro(erro, padrao) {
  if (erro instanceof ApiError && erro.codigo === 'SEM_CONEXAO') {
    return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  }
  return erro instanceof ApiError ? erro.message : padrao
}
