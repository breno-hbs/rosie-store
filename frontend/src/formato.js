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

// "2026-08-20T14:30:00" → "20/08/2026 às 14:30" (horário da loja, como a API manda)
export function formatarDataHora(dataIso) {
  return `${formatarData(dataIso)} às ${dataIso.slice(11, 16)}`
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

export const LINK_INSTAGRAM = 'https://www.instagram.com/rosie.sttore/'

// DDI 55 + DDD (sem zero) + 8 ou 9 dígitos, ex.: 5511999999999
const FORMATO_WHATSAPP = /^55[1-9]{2}\d{8,9}$/

// VITE_WHATSAPP_NUMERO validado. Pontuação (+, espaço, hífen) é tirada com aviso; número fora do formato vira
// "sem WhatsApp", para a cliente nunca ver um botão que abre um número errado.
function numeroWhatsApp(valor) {
  const bruto = (valor ?? '').trim()
  if (!bruto) return null
  const numero = somenteDigitos(bruto)
  const avisar = import.meta.env.MODE !== 'production'
  if (!FORMATO_WHATSAPP.test(numero)) {
    if (avisar) {
      console.warn(
        `VITE_WHATSAPP_NUMERO="${bruto}" não é um WhatsApp válido: use DDI 55 + DDD + número, só dígitos ` +
          '(ex.: 5511999999999). Os botões do WhatsApp ficam escondidos.',
      )
    }
    return null
  }
  if (numero !== bruto && avisar) {
    console.warn(`VITE_WHATSAPP_NUMERO deve ter só dígitos: use ${numero} em vez de "${bruto}".`)
  }
  return numero
}

const WHATSAPP_NUMERO = numeroWhatsApp(import.meta.env.VITE_WHATSAPP_NUMERO)

// Link do WhatsApp da loja com a mensagem pronta, ou undefined sem número válido: nesse caso o botão não aparece
export function linkWhatsApp(mensagem) {
  if (!WHATSAPP_NUMERO) return undefined
  const texto = mensagem ? `?text=${encodeURIComponent(mensagem)}` : ''
  return `https://wa.me/${WHATSAPP_NUMERO}${texto}`
}

// Contato da loja para quando ele é a única saída da tela: o WhatsApp com a mensagem, ou o Instagram sem número
export function contatoLoja(mensagem) {
  const whatsapp = linkWhatsApp(mensagem)
  return whatsapp
    ? { href: whatsapp, texto: 'Falar no WhatsApp' }
    : { href: LINK_INSTAGRAM, texto: 'Falar no Instagram' }
}
