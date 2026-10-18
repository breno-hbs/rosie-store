// Todas as chamadas à API do Rosie passam por aqui (contrato em docs/contrato-api.md).
const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '')

// Erro no formato do contrato: {"erro": "...", "codigo": "..."}
export class ApiError extends Error {
  constructor(mensagem, codigo, status) {
    super(mensagem)
    this.name = 'ApiError'
    this.codigo = codigo
    this.status = status
  }
}

async function get(caminho, params = {}, { signal } = {}) {
  const url = new URL(API_URL + caminho)
  for (const [chave, valor] of Object.entries(params)) {
    if (valor !== undefined && valor !== null && valor !== '') {
      url.searchParams.set(chave, valor)
    }
  }

  let resposta
  try {
    resposta = await fetch(url, { headers: { Accept: 'application/json' }, signal })
  } catch (e) {
    if (e.name === 'AbortError') throw e
    throw new ApiError('Não foi possível conectar à loja', 'SEM_CONEXAO', 0)
  }

  const corpo = await resposta.json().catch(() => null)
  if (!resposta.ok) {
    throw new ApiError(
      corpo?.erro ?? `Erro ${resposta.status}`,
      corpo?.codigo ?? 'ERRO_DESCONHECIDO',
      resposta.status,
    )
  }
  return corpo
}

// GET /categorias → [{ id, nome }]
export function listarCategorias(opcoes) {
  return get('/categorias', {}, opcoes)
}

// GET /produtos → { conteudo: [...], totalPaginas, totalItens }
export function listarProdutos({ categoriaId, pagina = 0, tamanho = 12 } = {}, opcoes) {
  return get('/produtos', { categoriaId, pagina, tamanho }, opcoes)
}

// GET /produtos/destaques → [{ id, nome, preco, categoria, imagemPrincipal, disponivel }]
// Lista vazia quando a Roseane não marcou nenhum destaque
export function listarDestaques({ limite = 12 } = {}, opcoes) {
  return get('/produtos/destaques', { limite }, opcoes)
}

// GET /produtos/{id} → { id, nome, descricao, preco, categoria, imagens: [...], estoque: [{ tamanho, quantidade }] }
// Produto inexistente ou inativo: ApiError com status 404
export function buscarProduto(id, opcoes) {
  return get(`/produtos/${id}`, {}, opcoes)
}
