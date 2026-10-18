// Todas as chamadas à API do Rosie passam por aqui (contrato em docs/contrato-api.md).
const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '')

// Sessões: { token, usuario: { id, nome, role } }, uma por área, em chaves separadas.
// Painel: sessionStorage, some ao fechar a aba (computador compartilhado não continua logado).
// Loja: localStorage, o cliente segue logado entre abas e ao reabrir o navegador.
// O token vale 2 horas nos dois casos.
const SESSOES = {
  admin: { chave: 'rosie.admin.sessao', armazenamento: () => sessionStorage },
  cliente: { chave: 'rosie.cliente.sessao', armazenamento: () => localStorage },
}
const ROTA_LOGIN_ADMIN = '/admin/login'
const ROTA_LOGIN_CLIENTE = '/entrar'

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

// Sessão válida (token ainda no prazo) da área ('admin' ou 'cliente'), ou null
function lerSessao(area) {
  const { chave, armazenamento } = SESSOES[area]
  try {
    const sessao = JSON.parse(armazenamento().getItem(chave))
    if (sessao?.token && !tokenExpirado(sessao.token)) return sessao
  } catch {
    // Armazenamento indisponível ou valor corrompido: trata como deslogado
  }
  limparSessao(area)
  return null
}

function guardarSessao(area, sessao) {
  const { chave, armazenamento } = SESSOES[area]
  try {
    armazenamento().setItem(chave, JSON.stringify(sessao))
  } catch {
    throw new ApiError('O navegador bloqueou o armazenamento da sessão', 'SEM_ARMAZENAMENTO', 0)
  }
}

function limparSessao(area) {
  const { chave, armazenamento } = SESSOES[area]
  try {
    armazenamento().removeItem(chave)
  } catch {
    // nada a limpar
  }
}

export function lerSessaoAdmin() {
  return lerSessao('admin')
}

export function lerSessaoCliente() {
  return lerSessao('cliente')
}

// Destino depois do login da loja: só caminhos deste site ("/produtos/1"), nunca outro
// domínio ("//site.com" ou "https://..."), para o ?voltar= não virar redirecionamento aberto
export function destinoSeguro(voltar) {
  return typeof voltar === 'string' && /^\/(?![/\\])/.test(voltar) ? voltar : '/'
}

// Leva o ?voltar= adiante (ex.: do login para o cadastro), para a pessoa voltar à mesma página
export function comVoltar(caminho, voltar) {
  return destinoSeguro(voltar) === '/' ? caminho : `${caminho}?voltar=${encodeURIComponent(voltar)}`
}

// Login da loja levando a página atual em ?voltar=, para a pessoa voltar para ela depois de entrar
export function rotaLoginCliente({ motivo, voltar = window.location.pathname + window.location.search } = {}) {
  const rota = comVoltar(ROTA_LOGIN_CLIENTE, voltar)
  if (!motivo) return rota
  return `${rota}${rota.includes('?') ? '&' : '?'}motivo=${motivo}`
}

function voltarParaLogin(area, motivo) {
  limparSessao(area)
  if (area === 'cliente') {
    window.location.assign(rotaLoginCliente({ motivo }))
  } else {
    window.location.assign(motivo ? `${ROTA_LOGIN_ADMIN}?motivo=${motivo}` : ROTA_LOGIN_ADMIN)
  }
}

// `autenticado` diz de qual área é o token: 'admin' (rotas /admin/**) ou 'cliente' (conta,
// carrinho, pedidos, trocas). Sem ele, a chamada vai sem Authorization: as rotas públicas do
// catálogo não recebem token, porque um token vencido faria a API responder 401 mesmo nelas.
// `silencioso`: no 401 só esquece a sessão, sem levar ao login (chamadas de fundo, como a da Nav).
async function requisicao(caminho, { metodo = 'GET', params = {}, corpo, autenticado, silencioso, signal } = {}) {
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
    const sessao = lerSessao(autenticado)
    if (!sessao) {
      if (!silencioso) voltarParaLogin(autenticado, 'expirou')
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
    // Token vencido ou inválido no meio do uso: volta para o login da área
    if (autenticado && resposta.status === 401) {
      if (silencioso) limparSessao(autenticado)
      else voltarParaLogin(autenticado, 'expirou')
    }
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
  guardarSessao('admin', sessao)
  return sessao.usuario
}

// GET /admin/auth/me → { id, nome, role }. Serve para conferir se o token ainda vale.
export function buscarUsuarioAdmin(opcoes) {
  return requisicao('/admin/auth/me', { ...opcoes, autenticado: 'admin' })
}

// Sem logout no servidor (JWT sem estado): basta esquecer o token
export function sairDoPainel() {
  voltarParaLogin('admin')
}

// Helpers genéricos das rotas /admin/** (sempre com token). Erro vira ApiError; 401 volta ao login.
export function getAdmin(caminho, opcoes) {
  return requisicao(caminho, { ...opcoes, autenticado: 'admin' })
}

export function postAdmin(caminho, corpo, opcoes) {
  return requisicao(caminho, { ...opcoes, metodo: 'POST', corpo, autenticado: 'admin' })
}

export function putAdmin(caminho, corpo, opcoes) {
  return requisicao(caminho, { ...opcoes, metodo: 'PUT', corpo, autenticado: 'admin' })
}

// Respostas 204 (sem corpo) resolvem com null
export function deleteAdmin(caminho, opcoes) {
  return requisicao(caminho, { ...opcoes, metodo: 'DELETE', autenticado: 'admin' })
}

// POST /auth/registrar; dados: { nome, email, senha, telefone (só dígitos), aceitePrivacidade }
// → { token, usuario: { id, nome, role } }; guarda a sessão (quem se cadastra sai logado).
// E-mail já cadastrado: ApiError com codigo EMAIL_JA_CADASTRADO (409); campo inválido: DADOS_INVALIDOS (400)
export async function cadastrarCliente(dados) {
  const sessao = await requisicao('/auth/registrar', { metodo: 'POST', corpo: dados })
  guardarSessao('cliente', sessao)
  return sessao.usuario
}

// POST /auth/login → { token, usuario: { id, nome, role } }; guarda a sessão.
// Credencial errada (ou conta de admin): ApiError com codigo CREDENCIAIS_INVALIDAS (401)
export async function entrarNaLoja(email, senha) {
  const sessao = await requisicao('/auth/login', { metodo: 'POST', corpo: { email, senha } })
  guardarSessao('cliente', sessao)
  return sessao.usuario
}

// GET /auth/me → { id, nome, role }. Confere se o token de cliente ainda vale.
export function buscarCliente(opcoes) {
  return getCliente('/auth/me', opcoes)
}

// Sem logout no servidor: esquece o token e continua na página
export function sairDaLoja() {
  limparSessao('cliente')
  esquecerFrete()
}

// Helpers das rotas de cliente (conta, carrinho, pedidos, trocas), sempre com o token de cliente.
// Erro vira ApiError; 401 limpa a sessão e leva a /entrar?voltar=<página atual>.
export function getCliente(caminho, opcoes) {
  return requisicao(caminho, { ...opcoes, autenticado: 'cliente' })
}

export function postCliente(caminho, corpo, opcoes) {
  return requisicao(caminho, { ...opcoes, metodo: 'POST', corpo, autenticado: 'cliente' })
}

export function putCliente(caminho, corpo, opcoes) {
  return requisicao(caminho, { ...opcoes, metodo: 'PUT', corpo, autenticado: 'cliente' })
}

export function deleteCliente(caminho, opcoes) {
  return requisicao(caminho, { ...opcoes, metodo: 'DELETE', autenticado: 'cliente' })
}

// Carrinho (#22). Toda rota responde o carrinho atualizado:
// { itens: [{ id, produto: { id, nome, preco, imagemPrincipal }, tamanho, quantidade, subtotal,
//   estoqueDisponivel, disponivel, motivoIndisponivel }], quantidadeItens, subtotal, temIndisponiveis }
// motivoIndisponivel: null, PRODUTO_INATIVO, TAMANHO_ESGOTADO ou ESTOQUE_INSUFICIENTE.
// Cada resposta avisa a Nav (evento EVENTO_CARRINHO, com quantidadeItens) para o número da "Sacola".
export const EVENTO_CARRINHO = 'rosie:carrinho'

function avisarCarrinho(carrinho) {
  window.dispatchEvent(new CustomEvent(EVENTO_CARRINHO, { detail: carrinho.quantidadeItens }))
  return carrinho
}

// GET /carrinho
export function buscarCarrinho(opcoes) {
  return getCliente('/carrinho', opcoes).then(avisarCarrinho)
}

// POST /carrinho/itens; mesmo produto e tamanho somam no item que já existe.
// Acima do estoque do tamanho (ou esgotado): ApiError com codigo ESTOQUE_INSUFICIENTE (409) e a
// mensagem pronta ("Só temos 3 no tamanho P"); produto que saiu da loja: PRODUTO_INDISPONIVEL (409)
export function adicionarAoCarrinho(produtoId, tamanho, quantidade = 1, opcoes) {
  return postCliente('/carrinho/itens', { produtoId, tamanho, quantidade }, opcoes).then(avisarCarrinho)
}

// PUT /carrinho/itens/{itemId}; mesmos erros 409 do POST, e 404 se o item não está no carrinho
export function alterarQuantidadeCarrinho(itemId, quantidade, opcoes) {
  return putCliente(`/carrinho/itens/${itemId}`, { quantidade }, opcoes).then(avisarCarrinho)
}

// DELETE /carrinho/itens/{itemId}
export function removerDoCarrinho(itemId, opcoes) {
  return deleteCliente(`/carrinho/itens/${itemId}`, opcoes).then(avisarCarrinho)
}

// Frete (#23). POST /frete/calcular cota as peças disponíveis do carrinho no Melhor Envio:
// { cep: '03925050', opcoes: [{ id, transportadora, servico, preco, prazoDias }] }, do mais barato
// ao mais caro (lista vazia: nenhuma transportadora atende o CEP). Erros: DADOS_INVALIDOS (400, CEP),
// CARRINHO_VAZIO (409), FRETE_FALHOU (502, Melhor Envio fora do ar) e FRETE_INDISPONIVEL (503).
export function calcularFrete(cep, opcoes) {
  return postCliente('/frete/calcular', { cep }, opcoes)
}

// CEP e opção escolhidos no carrinho, para o checkout (#24) começar com eles:
// { cep: '03925050', opcao: { id, transportadora, servico, preco, prazoDias } | null }.
// sessionStorage: vale para a aba. O checkout cota de novo pelo opcao.id; o preço guardado é só da tela.
const CHAVE_FRETE = 'rosie.frete'

export function lerFrete() {
  try {
    const frete = JSON.parse(sessionStorage.getItem(CHAVE_FRETE))
    if (typeof frete?.cep === 'string') return { cep: frete.cep, opcao: frete.opcao ?? null }
  } catch {
    // Armazenamento indisponível ou valor corrompido: começa sem frete
  }
  return null
}

export function guardarFrete(cep, opcao) {
  try {
    sessionStorage.setItem(CHAVE_FRETE, JSON.stringify({ cep, opcao }))
  } catch {
    // Sem armazenamento a escolha vale só nesta tela
  }
}

export function esquecerFrete() {
  try {
    sessionStorage.removeItem(CHAVE_FRETE)
  } catch {
    // nada a limpar
  }
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
