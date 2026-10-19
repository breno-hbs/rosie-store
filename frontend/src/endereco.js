import { somenteDigitos } from './formato.js'

// Formulário de endereço (CamposEndereco), usado no Checkout (#24) e na Minha Conta (#26)
export const ENDERECO_VAZIO = { cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', estado: '' }

// Todos os obrigatórios preenchidos e UF com duas letras (a API confere tudo de novo)
export function enderecoCompleto(endereco) {
  return (
    somenteDigitos(endereco.cep).length === 8 &&
    ['logradouro', 'numero', 'bairro', 'cidade'].every((campo) => endereco[campo].trim() !== '') &&
    /^[A-Za-z]{2}$/.test(endereco.estado.trim())
  )
}

// Corpo do POST/PUT /enderecos: CEP só com os dígitos e UF em maiúsculas
export function enderecoParaApi(endereco) {
  return { ...endereco, cep: somenteDigitos(endereco.cep), estado: endereco.estado.trim().toUpperCase() }
}
