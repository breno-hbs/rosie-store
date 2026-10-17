const formatoPreco = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function formatarPreco(valor) {
  return formatoPreco.format(valor)
}

const WHATSAPP_NUMERO = import.meta.env.VITE_WHATSAPP_NUMERO

// Link do WhatsApp da loja, ou undefined se VITE_WHATSAPP_NUMERO não estiver definido
export function linkWhatsApp(mensagem) {
  if (!WHATSAPP_NUMERO) return undefined
  const texto = mensagem ? `?text=${encodeURIComponent(mensagem)}` : ''
  return `https://wa.me/${WHATSAPP_NUMERO}${texto}`
}
