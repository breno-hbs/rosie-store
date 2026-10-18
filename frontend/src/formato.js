const formatoPreco = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function formatarPreco(valor) {
  return formatoPreco.format(valor)
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
