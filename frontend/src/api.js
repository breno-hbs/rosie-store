// Todas as chamadas à API do Rosie passam por aqui (contrato em docs/contrato-api.md).
const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '')

// Sessão do painel admin: { token, usuario: { id, nome, role } }.
// Fica no sessionStorage: some ao fechar a aba, então um computador compartilhado não
// continua logado no painel. O token vale 2 horas de qualquer forma.
const CHAVE_SESSAO_ADMIN = 'rosie.admin.sessao'
const ROTA_LOGIN_ADMIN = '/admin/login'

// Erro no formato do contrato: {"erro": "...", "codigo": "..."}
export class ApiError extends Error {
  constructor(mensagem, codigo, status) {
    super(mensagem)
    this.name = 'ApiError'
    this.codigo = codigo
    this.status = status
  }
}

// O "exp" do JWT é só lido aqui (quem valida a assinatura é a API), para não usar token vencido
function tokenExpirado(token) {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const { exp } = JSON.parse(atob(base64))
    return !exp || exp * 1000 <= Date.now()
  } catch {
    return true
  }
}

// Sessão válida (token ainda no prazo) ou null
export function lerSessaoAdmin() {
  try {
    const sessao = JSON.parse(sessionStorage.getItem(CHAVE_SESSAO_ADMIN))
    if (sessao?.token && !tokenExpirado(sessao.token)) return sessao
  } catch {
    // sessionStorage indisponível ou valor corrompido: trata como deslogado
  }
  limparSessaoAdmin()
  return null
}

function limparSessaoAdmin() {
  try {
    sessionStorage.removeItem(CHAVE_SESSAO_ADMIN)
  } catch {
    // nada a limpar
  }
}

function voltarParaLogin(motivo) {
  limparSessaoAdmin()
  window.location.assign(motivo ? `${ROTA_LOGIN_ADMIN}?motivo=${motivo}` : ROTA_LOGIN_ADMIN)
}

// O token só vai nas chamadas `autenticado`: as rotas públicas do catálogo não recebem
// Authorization, porque um token vencido faria a API responder 401 mesmo nelas.
async function requisicao(caminho, { metodo = 'GET', params = {}, corpo, autenticado = false, signal } = {}) {
  const url = new URL(API_URL + caminho)
  for (const [chave, valor] of Object.entries(params)) {
    if (valor !== undefined && valor !== null && valor !== '') {
      url.searchParams.set(chave, valor)
    }
  }

  // FormData (upload de foto) vai como está: o navegador monta o Content-Type com o boundary
  const multipart = corpo instanceof FormData
  const headers = { Accept: 'application/json' }
  if (corpo !== undefined && !multipart) headers['Content-Type'] = 'application/json'
  if (autenticado) {
    const sessao = lerSessaoAdmin()
    if (!sessao) {
      voltarParaLogin('expirou')
      throw new ApiError('Sua sessão expirou', 'NAO_AUTENTICADO', 401)
    }
    headers.Authorization = `Bearer ${sessao.token}`
  }

  let resposta
  try {
    resposta = await fetch(url, {
      method: metodo,
      headers,
      body: corpo === undefined || multipart ? corpo : JSON.stringify(corpo),
      signal,
    })
  } catch (e) {
    if (e.name === 'AbortError') throw e
    throw new ApiError('Não foi possível conectar à loja', 'SEM_CONEXAO', 0)
  }

  const conteudo = await resposta.json().catch(() => null)
  if (!resposta.ok) {
    // Token vencido ou inválido no meio do uso do painel: volta para o login
    if (autenticado && resposta.status === 401) voltarParaLogin('expirou')
    throw new ApiError(
      conteudo?.erro ?? `Erro ${resposta.status}`,
      conteudo?.codigo ?? 'ERRO_DESCONHECIDO',
      resposta.status,
    )
  }
  return conteudo
}

// GET /categorias → [{ id, nome }]
export function listarCategorias(opcoes) {
  return requisicao('/categorias', opcoes)
}

// GET /produtos → { conteudo: [...], totalPaginas, totalItens }
export function listarProdutos({ categoriaId, pagina = 0, tamanho = 12 } = {}, opcoes) {
  return requisicao('/produtos', { ...opcoes, params: { categoriaId, pagina, tamanho } })
}

// GET /produtos/destaques → [{ id, nome, preco, categoria, imagemPrincipal, disponivel }]
// Lista vazia quando a Roseane não marcou nenhum destaque
export function listarDestaques({ limite = 12 } = {}, opcoes) {
  return requisicao('/produtos/destaques', { ...opcoes, params: { limite } })
}

// GET /produtos/{id} → { id, nome, descricao, preco, categoria, imagens: [...], estoque: [{ tamanho, quantidade }] }
// Produto inexistente ou inativo: ApiError com status 404
export function buscarProduto(id, opcoes) {
  return requisicao(`/produtos/${id}`, opcoes)
}

// POST /admin/auth/login → { token, usuario: { id, nome, role } }; guarda a sessão.
// Credencial errada: ApiError com codigo CREDENCIAIS_INVALIDAS (status 401)
export async function entrarNoPainel(email, senha) {
  const sessao = await requisicao('/admin/auth/login', { metodo: 'POST', corpo: { email, senha } })
  try {
    sessionStorage.setItem(CHAVE_SESSAO_ADMIN, JSON.stringify(sessao))
  } catch {
    throw new ApiError('O navegador bloqueou o armazenamento da sessão', 'SEM_ARMAZENAMENTO', 0)
  }
  return sessao.usuario
}

// GET /admin/auth/me → { id, nome, role }. Serve para conferir se o token ainda vale.
export function buscarUsuarioAdmin(opcoes) {
  return requisicao('/admin/auth/me', { ...opcoes, autenticado: true })
}

// Sem logout no servidor (JWT sem estado): basta esquecer o token
export function sairDoPainel() {
  voltarParaLogin()
}

// Helpers genéricos das rotas /admin/** (sempre com token). Erro vira ApiError; 401 volta ao login.
export function getAdmin(caminho, opcoes) {
  return requisicao(caminho, { ...opcoes, autenticado: true })
}

export function postAdmin(caminho, corpo, opcoes) {
  return requisicao(caminho, { ...opcoes, metodo: 'POST', corpo, autenticado: true })
}

export function putAdmin(caminho, corpo, opcoes) {
  return requisicao(caminho, { ...opcoes, metodo: 'PUT', corpo, autenticado: true })
}

// Respostas 204 (sem corpo) resolvem com null
export function deleteAdmin(caminho, opcoes) {
  return requisicao(caminho, { ...opcoes, metodo: 'DELETE', autenticado: true })
}

// GET /admin/produtos → [{ id, nome, preco, categoria, estoqueTotal, estoque: [{ tamanho, quantidade }], ativo, destaque }],
// inclusive inativos
export function listarProdutosAdmin(opcoes) {
  return getAdmin('/admin/produtos', opcoes)
}

// GET /admin/produtos/{id} → detalhe do produto + { ativo, destaque }; aqui imagens é
// [{ id, url, ordem, enquadramento }]
export function buscarProdutoAdmin(id, opcoes) {
  return getAdmin(`/admin/produtos/${id}`, opcoes)
}

// POST /admin/produtos/{id}/imagens (multipart, campos "arquivo" e "enquadramento": AUTOMATICO, CENTRO
// ou TOPO) → { id, url, ordem, enquadramento }, no fim da galeria; a url já vem cortada em 4:5.
// Recusa: ApiError com codigo FORMATO_NAO_SUPORTADO ou ARQUIVO_MUITO_GRANDE (400);
// ARMAZENAMENTO_INDISPONIVEL (503) quando o servidor está sem Cloudinary
export function enviarImagemProduto(id, arquivo, enquadramento, opcoes) {
  const corpo = new FormData()
  corpo.append('arquivo', arquivo)
  if (enquadramento) corpo.append('enquadramento', enquadramento)
  return postAdmin(`/admin/produtos/${id}/imagens`, corpo, opcoes)
}

// PUT /admin/produtos/{id}/imagens/ordem; body: ids de todas as fotos, a principal primeiro
// → [{ id, url, ordem, enquadramento }]
export function reordenarImagensProduto(id, idsImagens, opcoes) {
  return putAdmin(`/admin/produtos/${id}/imagens/ordem`, { imagens: idsImagens }, opcoes)
}

// DELETE /admin/produtos/{id}/imagens/{imagemId} → null; a foto também sai do Cloudinary
export function removerImagemProduto(id, imagemId, opcoes) {
  return deleteAdmin(`/admin/produtos/${id}/imagens/${imagemId}`, opcoes)
}

// POST /admin/produtos; body: { nome, descricao, preco, categoriaId, destaque, estoque: [{ tamanho, quantidade }] }
// Campo inválido: ApiError com codigo DADOS_INVALIDOS (status 400) e a mensagem do campo
export function criarProduto(dados, opcoes) {
  return postAdmin('/admin/produtos', dados, opcoes)
}

// PUT /admin/produtos/{id}; mesmo body do POST, mais `ativo` opcional (true reativa)
export function atualizarProduto(id, dados, opcoes) {
  return putAdmin(`/admin/produtos/${id}`, dados, opcoes)
}

// DELETE /admin/produtos/{id}: desativa (some da loja), não apaga
export function desativarProduto(id, opcoes) {
  return deleteAdmin(`/admin/produtos/${id}`, opcoes)
}

// PUT /admin/produtos/{id}/estoque → { produtoId, estoque: [{ tamanho, quantidade }] } (completo, em P, M, G, GG)
// Só mexe nos tamanhos enviados. Quantidade negativa: ApiError com codigo DADOS_INVALIDOS (400)
export function atualizarEstoque(id, estoque, opcoes) {
  return putAdmin(`/admin/produtos/${id}/estoque`, { estoque }, opcoes)
}

// GET /admin/categorias → [{ id, nome, quantidadeProdutos }], ordenado por nome
export function listarCategoriasAdmin(opcoes) {
  return getAdmin('/admin/categorias', opcoes)
}

// POST /admin/categorias → { id, nome, quantidadeProdutos }
// Nome repetido (sem diferenciar maiúsculas): ApiError com codigo CATEGORIA_DUPLICADA (409)
export function criarCategoria(nome, opcoes) {
  return postAdmin('/admin/categorias', { nome }, opcoes)
}

// PUT /admin/categorias/{id} → { id, nome, quantidadeProdutos }
export function renomearCategoria(id, nome, opcoes) {
  return putAdmin(`/admin/categorias/${id}`, { nome }, opcoes)
}

// DELETE /admin/categorias/{id} → null
// Categoria com produtos (ativos ou não): ApiError com codigo CATEGORIA_EM_USO (409)
export function removerCategoria(id, opcoes) {
  return deleteAdmin(`/admin/categorias/${id}`, opcoes)
}

// GET /categorias/{id}/medidas (pública) → { categoriaId, categoria, unidade: 'cm',
// medidas: [{ nome, valores: { P, M, G, GG } }] }; valor vazio vem como "". Sem tabela: medidas = []
export function buscarTabelaMedidas(categoriaId, opcoes) {
  return requisicao(`/categorias/${categoriaId}/medidas`, opcoes)
}

// GET /admin/categorias/{id}/medidas → mesma resposta da rota pública
export function buscarTabelaMedidasAdmin(categoriaId, opcoes) {
  return getAdmin(`/admin/categorias/${categoriaId}/medidas`, opcoes)
}

// PUT /admin/categorias/{id}/medidas; body: { medidas: [{ nome, valores: { P, M, G, GG } }] }, na ordem
// de exibição. Troca a tabela inteira. Nome vazio ou repetido: ApiError com codigo DADOS_INVALIDOS (400)
export function salvarTabelaMedidas(categoriaId, medidas, opcoes) {
  return putAdmin(`/admin/categorias/${categoriaId}/medidas`, { medidas }, opcoes)
}

// DELETE /admin/categorias/{id}/medidas → null; a categoria fica sem tabela
export function removerTabelaMedidas(categoriaId, opcoes) {
  return deleteAdmin(`/admin/categorias/${categoriaId}/medidas`, opcoes)
}
