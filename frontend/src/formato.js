const formatoPreco = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function formatarPreco(valor) {
  return formatoPreco.format(valor)
}

// "2026-08-20T14:30:00.123456" (data local da API, sem fuso) → "20/08/2026". Lido direto do texto: sem
// passar por Date, não há fuso nem diferença entre navegadores na leitura dos microssegundos.
export function formatarData(dataIso) {
  const [ano, mes, dia] = dataIso.slice(0, 10).split('-')
  return `${dia}/${mes}/${ano}`
}

// Status do pedido (StatusPedido no back-end) como a cliente lê. Status novo que ainda não esteja
// aqui aparece com o nome do enum em minúsculas ("EM_SEPARACAO" → "Em separacao").
const STATUS_PEDIDO = {
  AGUARDANDO_PAGAMENTO: 'Aguardando pagamento',
  PAGAMENTO_RECUSADO: 'Pagamento recusado',
  PAGO: 'Pago',
  // Pago, mas a Roseane precisa resolver algo (estoque ou valor) antes de enviar
  PAGO_COM_PENDENCIA: 'Pago, em análise',
  ENVIADO: 'Enviado',
  ENTREGUE: 'Entregue',
  CANCELADO: 'Cancelado',
}

export function rotuloStatusPedido(status) {
  if (STATUS_PEDIDO[status]) return STATUS_PEDIDO[status]
  const texto = status.replace(/_/g, ' ').toLowerCase()
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

// Pedido que não segue adiante: o selo fica apagado (fumaça), como "Cancelado" no Figma
export function statusEncerrado(status) {
  return status === 'CANCELADO' || status === 'PAGAMENTO_RECUSADO'
}

// Máscara do telefone enquanto a pessoa digita: (11) 98765-4321 ou (11) 3456-7890.
// A API recebe só os dígitos (somenteDigitos).
export function mascararTelefone(valor) {
  const d = somenteDigitos(valor).slice(0, 11)
  if (d.length === 0) return ''
  if (d.length <= 2) return `(${d}`
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`
}

// Máscara do CEP enquanto a pessoa digita: 03925-050. A API aceita com ou sem hífen.
export function mascararCep(valor) {
  const d = somenteDigitos(valor).slice(0, 8)
  return d.length <= 5 ? d : `${d.slice(0, 5)}-${d.slice(5)}`
}

export function somenteDigitos(valor) {
  return valor.replace(/\D/g, '')
}

const WHATSAPP_NUMERO = import.meta.env.VITE_WHATSAPP_NUMERO

// Link do WhatsApp da loja, ou undefined se VITE_WHATSAPP_NUMERO não estiver definido
export function linkWhatsApp(mensagem) {
  if (!WHATSAPP_NUMERO) return undefined
  const texto = mensagem ? `?text=${encodeURIComponent(mensagem)}` : ''
  return `https://wa.me/${WHATSAPP_NUMERO}${texto}`
}
