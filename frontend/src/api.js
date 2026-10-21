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
  // A base vale para VITE_API_URL relativa (/api na demonstração, #77); com URL completa ela é ignorada
  const url = new URL(API_URL + caminho, window.location.origin)
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
// `busca` (#82): termos no nome, descrição, composição ou categoria; vazia lista sem busca
export function listarProdutos({ categoriaId, busca, pagina = 0, tamanho = 12 } = {}, opcoes) {
  return requisicao('/produtos', { ...opcoes, params: { categoriaId, busca, pagina, tamanho } })
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

// POST /auth/login → { token, usuario: { id, nome, role }, privacidadePendente }; guarda a sessão.
// privacidadePendente (#31): a cliente aceitou uma versão antiga da política e precisa aceitar a nova.
// Credencial errada, conta de admin ou conta excluída: ApiError com codigo CREDENCIAIS_INVALIDAS (401)
export async function entrarNaLoja(email, senha) {
  const sessao = await requisicao('/auth/login', { metodo: 'POST', corpo: { email, senha } })
  guardarSessao('cliente', sessao)
  return sessao.usuario
}

// GET /auth/me → { id, nome, role }. Confere se o token de cliente ainda vale.
export function buscarCliente(opcoes) {
  return getCliente('/auth/me', opcoes)
}

// Dados pessoais da Minha Conta (#26). GET /conta → { id, nome, email, telefone (só dígitos),
// privacidadeVersao, privacidadeAceitaEm, emailPendente } (o aceite da política, #31, e a troca de e-mail
// esperando confirmação, #61: { novoEmail, expiraEm } ou null)
export function buscarConta(opcoes) {
  return getCliente('/conta', opcoes)
}

// PUT /conta; dados: { nome, telefone (só dígitos) } → a conta atualizada. O nome novo também vai para a
// sessão guardada e para a Nav (EVENTO_CONTA), sem precisar entrar de novo. Campo inválido: DADOS_INVALIDOS (400)
export const EVENTO_CONTA = 'rosie:conta'

export async function atualizarConta(dados, opcoes) {
  const conta = await putCliente('/conta', dados, opcoes)
  const sessao = lerSessao('cliente')
  if (sessao) {
    try {
      guardarSessao('cliente', { ...sessao, usuario: { ...sessao.usuario, nome: conta.nome } })
    } catch {
      // Sem armazenamento a Nav só mostra o nome novo no próximo login
    }
  }
  window.dispatchEvent(new CustomEvent(EVENTO_CONTA, { detail: conta }))
  return conta
}

// PUT /conta/senha; { senhaAtual, novaSenha } → null. Senha atual errada ou nova fora de 8 a 72
// caracteres: ApiError com codigo DADOS_INVALIDOS (400) e a mensagem pronta
export function trocarSenha(senhaAtual, novaSenha, opcoes) {
  return putCliente('/conta/senha', { senhaAtual, novaSenha }, opcoes)
}

// Troca de e-mail (#61). PUT /conta/email; { novoEmail, senhaAtual } → { novoEmail, expiraEm }: o e-mail novo
// só vale depois da confirmação pelo link mandado a ele; até lá o login continua com o antigo e o GET /conta
// traz a troca em emailPendente. Senha errada: SENHA_INCORRETA (403, a sessão continua); e-mail já cadastrado:
// EMAIL_JA_CADASTRADO (409); igual ao atual ou inválido: DADOS_INVALIDOS (400); falha no envio: EMAIL_FALHOU (502)
export function solicitarTrocaEmail(novoEmail, senhaAtual, opcoes) {
  return putCliente('/conta/email', { novoEmail, senhaAtual }, opcoes)
}

// POST /conta/email/confirmar; { token } → { email }. Pública (sem token de sessão): o link pode ser aberto em
// outro aparelho. Link que não existe ou já foi usado: TOKEN_INVALIDO; vencido: TOKEN_EXPIRADO (os dois 400);
// alguém se cadastrou com o e-mail novo nesse meio-tempo: EMAIL_JA_CADASTRADO (409)
export function confirmarTrocaEmail(token, opcoes) {
  return requisicao('/conta/email/confirmar', { ...opcoes, metodo: 'POST', corpo: { token } })
}

// Política de privacidade (LGPD, #31). GET /privacidade → { versao, vigencia } da política em vigor (público):
// a versão vem só da API, a mesma que o cadastro e o aceite gravam.
export function buscarPoliticaPrivacidade(opcoes) {
  return requisicao('/privacidade', opcoes)
}

// A sessão da loja diz se falta aceitar a versão em vigor da política (login com versão antiga)
export function privacidadePendente() {
  return lerSessao('cliente')?.privacidadePendente === true
}

// POST /conta/privacidade → a conta com a versão aceita; a sessão deixa de pedir o aceite
export async function aceitarPrivacidade(opcoes) {
  const conta = await postCliente('/conta/privacidade', { aceitePrivacidade: true }, opcoes)
  const sessao = lerSessao('cliente')
  if (sessao) {
    try {
      guardarSessao('cliente', { ...sessao, privacidadePendente: false })
    } catch {
      // Sem armazenamento, o checkout ainda confere no servidor
    }
  }
  return conta
}

// POST /conta/exclusao; { senha } → null, e a sessão é esquecida (a API já não aceita o token).
// Senha errada: DADOS_INVALIDOS (400); pedido a caminho ou troca aberta: CONTA_COM_PEDIDO_EM_ANDAMENTO (409)
export async function excluirConta(senha, opcoes) {
  await postCliente('/conta/exclusao', { senha }, opcoes)
  sairDaLoja()
  window.dispatchEvent(new CustomEvent(EVENTO_CONTA, { detail: null }))
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

// Endereços de entrega (#24): { id, cep (só dígitos), logradouro, numero, complemento, bairro, cidade, estado (UF) }
// GET /enderecos → lista, na ordem de cadastro
export function listarEnderecos(opcoes) {
  return getCliente('/enderecos', opcoes)
}

// POST /enderecos; dados: { cep, logradouro, numero, complemento, bairro, cidade, estado } → o endereço salvo.
// Campo inválido: ApiError com codigo DADOS_INVALIDOS (400) e a mensagem do campo
export function criarEndereco(dados, opcoes) {
  return postCliente('/enderecos', dados, opcoes)
}

// PUT /enderecos/{id}; mesmo corpo do POST → o endereço salvo. Endereço já usado num pedido não muda:
// o antigo é arquivado e a resposta vem com id novo (troque o da lista pelo da resposta).
export function atualizarEndereco(id, dados, opcoes) {
  return putCliente(`/enderecos/${id}`, dados, opcoes)
}

// DELETE /enderecos/{id} → null (já usado em pedido, só é arquivado; some da lista do mesmo jeito)
export function removerEndereco(id, opcoes) {
  return deleteCliente(`/enderecos/${id}`, opcoes)
}

// Rua, bairro, cidade e UF do CEP pelo ViaCEP (serviço público, sem token), para preencher o formulário.
// → { logradouro, bairro, cidade, estado }, ou null se o CEP não existe. A API confere tudo de novo.
export async function buscarEnderecoPorCep(cep, { signal } = {}) {
  let resposta
  try {
    resposta = await fetch(`https://viacep.com.br/ws/${cep}/json/`, { signal })
  } catch (e) {
    if (e.name === 'AbortError') throw e
    throw new ApiError('Não foi possível buscar o CEP', 'SEM_CONEXAO', 0)
  }
  const dados = await resposta.json().catch(() => null)
  if (!resposta.ok || !dados || dados.erro) return null
  return { logradouro: dados.logradouro, bairro: dados.bairro, cidade: dados.localidade, estado: dados.uf }
}

// Checkout (#24). POST /pedidos fecha a sacola inteira: { enderecoId, servicoFreteId (id da opção de
// frete), formaPagamento: 'CARTAO' | 'PIX' } → { id, status, subtotal, valorFrete, valorTotal, urlPagamento }.
// O servidor cota o frete de novo e confere o estoque. Erros: ESTOQUE_INSUFICIENTE, PRODUTO_INDISPONIVEL,
// CARRINHO_VAZIO e FRETE_ALTERADO (409, mensagem pronta); FRETE_*/PAGAMENTO_FALHOU (502) e *_INDISPONIVEL (503)
export function criarPedido(dados, opcoes) {
  return postCliente('/pedidos', dados, opcoes)
}

// GET /pedidos (Minha Conta, #26) → [{ id, status, valorTotal, createdAt, prazoTroca }], do mais recente ao
// mais antigo. prazoTroca ('2026-10-24', #27): último dia para pedir troca; null antes da entrega
export function listarPedidos(opcoes) {
  return getCliente('/pedidos', opcoes)
}

// GET /pedidos/{id} → { id, status, formaPagamento, subtotal, valorFrete, valorTotal, transportadora,
// servicoFrete, prazoFreteDias, codigoRastreio, dataEntrega, prazoTroca, urlPagamento, createdAt, endereco, itens: [{ produtoId,
// nome, imagemPrincipal, tamanho, quantidade, precoUnitario, subtotal }] }. Pedido de outra cliente: 404.
// status: AGUARDANDO_PAGAMENTO, PAGAMENTO_RECUSADO, PAGO, PAGO_COM_PENDENCIA, ENVIADO, ENTREGUE, CANCELADO
export function buscarPedido(id, opcoes) {
  return getCliente(`/pedidos/${id}`, opcoes)
}

// Só com PAGAMENTO_SIMULADO=true no back-end (desenvolvimento): status APROVADO, PENDENTE, RECUSADO
// ou CANCELADO → o pedido atualizado. Fora do modo simulado: 404.
export function simularPagamento(id, status, opcoes) {
  return postCliente(`/pedidos/${id}/pagamento-simulado`, { status }, opcoes)
}

// Para onde o "Finalizar pedido" e o "Pagar" levam (urlPagamento). A página do pagamento simulado é da própria
// loja: abre no endereço em que a cliente já está (localhost ou o link do túnel da demonstração, #77), onde
// ela está logada, e não no ROSIE_LOJA_URL da API. O link do Mercado Pago segue como veio.
export function linkPagamento(urlPagamento) {
  try {
    const { pathname } = new URL(urlPagamento)
    if (/^\/pedido\/\d+\/pagamento-simulado$/.test(pathname)) return pathname
  } catch {
    // URL relativa ou inválida: segue como veio
  }
  return urlPagamento
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

// ----- Pedidos no painel (#25) -----

// GET /admin/pedidos → [{ id, status, cliente: { id, nome, email, telefone }, quantidadePecas, valorTotal,
// codigoRastreio, createdAt }], do mais recente para o mais antigo, de todas as clientes
export function listarPedidosAdmin(opcoes) {
  return getAdmin('/admin/pedidos', opcoes)
}

// GET /admin/pedidos/{id} → o PedidoDTO da cliente + { cliente, pagamentoId, pagoEm, pendencia, envioId,
// urlEtiqueta, etiquetaGeradaEm, dataEntrega }
export function buscarPedidoAdmin(id, opcoes) {
  return getAdmin(`/admin/pedidos/${id}`, opcoes)
}

// POST /admin/pedidos/{id}/etiqueta → o pedido atualizado (ENVIADO, com urlEtiqueta e codigoRastreio).
// Compra a etiqueta no Melhor Envio (debita o saldo da conta); repetir não compra outra e, sem rastreio
// ainda, só busca o rastreio de novo. Erros: PEDIDO_NAO_PAGO, PEDIDO_COM_PENDENCIA, ETIQUETA_CANCELADA e
// ETIQUETA_EM_ANDAMENTO (409, mensagem pronta); ETIQUETA_FALHOU (502); ETIQUETA_INDISPONIVEL (503)
export function gerarEtiqueta(id, opcoes) {
  return postAdmin(`/admin/pedidos/${id}/etiqueta`, undefined, opcoes)
}

// POST /admin/pedidos/{id}/entregue → o pedido atualizado (ENTREGUE, com dataEntrega), #27. dataEntrega
// ('2026-10-17') é opcional: sem ela, vale hoje. Pedido que não está ENVIADO: PEDIDO_NAO_ENVIADO (409);
// data no futuro ou antes do envio: DADOS_INVALIDOS (400). Repetir num pedido já entregue não muda a data.
export function marcarEntregue(id, dataEntrega, opcoes) {
  return postAdmin(`/admin/pedidos/${id}/entregue`, dataEntrega ? { dataEntrega } : undefined, opcoes)
}

// ----- Troca e devolução (#27) -----

// POST /trocas; { pedidoId, produtoId, tamanho, quantidade?, motivo: 'DEFEITO' | 'NAO_SERVIU',
// observacao? } → a troca: { id, pedidoId, produtoId, nome, imagemPrincipal, tamanho, quantidade, motivo,
// freteConta: 'ROSIE' | 'CLIENTE' (sai do motivo, no servidor), status, observacao, resposta, createdAt,
// etiquetaVolta, enderecoDevolucao, updatedAt }. Erros com mensagem pronta: PEDIDO_NAO_ENTREGUE,
// TROCA_FORA_DO_PRAZO e TROCA_JA_SOLICITADA (409); DADOS_INVALIDOS (400); pedido de outra cliente: 404.
// Como a peça volta (#63): etiquetaVolta: { situacao: 'SEM_ETIQUETA' | 'GERADA' | 'POR_CONTA_DA_CLIENTE',
// urlEtiqueta, codigoRastreio, geradaEm }; enderecoDevolucao: { nome, logradouro, numero, complemento,
// bairro, cidade, uf, cep } só na troca aprovada com o frete por conta da cliente (senão null)
export function solicitarTroca(dados, opcoes) {
  return postCliente('/trocas', dados, opcoes)
}

// GET /trocas → as trocas da cliente, da mais recente à mais antiga
export function listarTrocas(opcoes) {
  return getCliente('/trocas', opcoes)
}

// GET /admin/trocas → as trocas de todas as clientes (cada uma com cliente: { id, nome, email, telefone } e
// etiquetaVolta: { situacao: 'SEM_ETIQUETA' | 'FALHOU' | 'GERADA' | 'POR_CONTA_DA_CLIENTE', envioId,
// urlEtiqueta, codigoRastreio, geradaEm, erro, valorEstimado }, #63)
export function listarTrocasAdmin(opcoes) {
  return getAdmin('/admin/trocas', opcoes)
}

// PUT /admin/trocas/{id}/status; status SOLICITADA → APROVADA ou RECUSADA, APROVADA → CONCLUIDA, com
// resposta (recado para a cliente) opcional → a troca atualizada. Fora do caminho: STATUS_TROCA_INVALIDO (409)
export function atualizarStatusTroca(id, status, resposta, opcoes) {
  return putAdmin(`/admin/trocas/${id}/status`, { status, resposta }, opcoes)
}

// POST /admin/trocas/{id}/etiqueta-volta → a troca atualizada, com etiquetaVolta GERADA (#63). Compra a
// etiqueta de volta no Melhor Envio (debita o saldo da conta); repetir não compra outra e, sem rastreio ainda,
// só busca o rastreio. Erros com mensagem pronta: TROCA_NAO_APROVADA, TROCA_FRETE_DA_CLIENTE,
// ETIQUETA_CANCELADA e ETIQUETA_EM_ANDAMENTO (409); ETIQUETA_FALHOU (502); ETIQUETA_INDISPONIVEL (503)
export function gerarEtiquetaVolta(id, opcoes) {
  return postAdmin(`/admin/trocas/${id}/etiqueta-volta`, undefined, opcoes)
}

// ---------- Banners da Home (#89) ----------

// GET /banners → [{ id, titulo, texto, textoBotao, link, textoAlternativo, imagemDesktop: { url, srcset },
// imagemCelular: { url, srcset } }]: só os ativos dentro do período, na ordem. Lista vazia = sem banner no ar
export function listarBanners(opcoes) {
  return requisicao('/banners', opcoes)
}

// GET /admin/banners → todos, na ordem, cada um com { ativo, inicio, fim, ordem, situacao: 'NO_AR' |
// 'AGENDADO' | 'ENCERRADO' | 'DESATIVADO', previaDesktop, previaCelular } além do que a Home recebe
export function listarBannersAdmin(opcoes) {
  return getAdmin('/admin/banners', opcoes)
}

// Multipart do banner: "dados" em JSON e as imagens que foram escolhidas (as outras ficam como estão)
function corpoBanner(dados, { imagemDesktop, imagemCelular } = {}) {
  const corpo = new FormData()
  corpo.append('dados', new Blob([JSON.stringify(dados)], { type: 'application/json' }))
  if (imagemDesktop) corpo.append('imagemDesktop', imagemDesktop)
  if (imagemCelular) corpo.append('imagemCelular', imagemCelular)
  return corpo
}

// POST /admin/banners; dados: { titulo, texto, textoBotao, link, textoAlternativo, ativo, inicio, fim }
// (datas "2026-12-01T00:00", horário de São Paulo); imagemDesktop obrigatória → o banner, no fim do carrossel.
// Recusa: DADOS_INVALIDOS (link fora da loja, período invertido...), FORMATO_NAO_SUPORTADO e
// ARQUIVO_MUITO_GRANDE (400); ARMAZENAMENTO_INDISPONIVEL (503)
export function criarBanner(dados, imagens, opcoes) {
  return postAdmin('/admin/banners', corpoBanner(dados, imagens), opcoes)
}

// PUT /admin/banners/{id}; mesmos dados do POST mais removerImagemCelular; imagens opcionais
export function atualizarBanner(id, dados, imagens, opcoes) {
  return putAdmin(`/admin/banners/${id}`, corpoBanner(dados, imagens), opcoes)
}

// PUT /admin/banners/{id}/ativo → o banner atualizado
export function alterarAtivoBanner(id, ativo, opcoes) {
  return putAdmin(`/admin/banners/${id}/ativo`, { ativo }, opcoes)
}

// PUT /admin/banners/ordem; ids de todos os banners, o primeiro do carrossel primeiro → a lista na nova ordem
export function reordenarBanners(ids, opcoes) {
  return putAdmin('/admin/banners/ordem', { banners: ids }, opcoes)
}

// DELETE /admin/banners/{id} → null; as imagens também saem do Cloudinary
export function removerBanner(id, opcoes) {
  return deleteAdmin(`/admin/banners/${id}`, opcoes)
}
