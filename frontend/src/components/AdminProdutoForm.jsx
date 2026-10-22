import { useEffect, useState } from 'react'
import { ApiError, atualizarProduto, buscarProdutoAdmin, criarProduto, listarCategorias } from '../api.js'
import AdminProdutoFotos from './AdminProdutoFotos.jsx'
import './AdminProdutos.css'

const TAMANHOS = ['P', 'M', 'G', 'GG']

// Medidas da embalagem para o frete (#56), na ordem em que aparecem no formulário
const MEDIDAS = [
  { campo: 'alturaCm', rotulo: 'Altura' },
  { campo: 'larguraCm', rotulo: 'Largura' },
  { campo: 'comprimentoCm', rotulo: 'Comprimento' },
]

const VAZIO = {
  nome: '',
  categoriaId: '',
  preco: '',
  descricao: '',
  composicao: '',
  destaque: false,
  ativo: true,
  estoque: { P: '', M: '', G: '', GG: '' },
  pacote: { pesoKg: '', alturaCm: '', larguraCm: '', comprimentoCm: '' },
}

// "189,90", "1.234,50" ou "R$ 189.90" → número; texto que não é preço → NaN
function lerPreco(texto) {
  let limpo = texto.replace(/R\$|\s/g, '')
  if (limpo.includes(',')) limpo = limpo.replace(/\./g, '').replace(',', '.')
  return limpo === '' ? null : Number(limpo)
}

// Peso em kg: "0,45" ou "0.45" → número; vazio → null; texto que não é número → NaN
function lerPeso(texto) {
  const limpo = texto.replace(/\s/g, '').replace(',', '.')
  return limpo === '' ? null : Number(limpo)
}

// Medida em cm inteiros; vazio → null; decimal ou texto → NaN
function lerMedida(texto) {
  if (texto.trim() === '') return null
  const numero = Number(texto)
  return Number.isInteger(numero) ? numero : NaN
}

function mostrarPreco(valor) {
  return Number(valor).toFixed(2).replace('.', ',')
}

function doProduto(produto) {
  const estoque = { ...VAZIO.estoque }
  for (const { tamanho, quantidade } of produto.estoque) estoque[tamanho] = String(quantidade)
  return {
    nome: produto.nome,
    categoriaId: String(produto.categoria.id),
    preco: mostrarPreco(produto.preco),
    descricao: produto.descricao ?? '',
    composicao: produto.composicao ?? '',
    destaque: produto.destaque,
    ativo: produto.ativo,
    estoque,
    pacote: {
      pesoKg: produto.pacote.pesoKg == null ? '' : String(produto.pacote.pesoKg).replace('.', ','),
      alturaCm: produto.pacote.alturaCm == null ? '' : String(produto.pacote.alturaCm),
      larguraCm: produto.pacote.larguraCm == null ? '' : String(produto.pacote.larguraCm),
      comprimentoCm: produto.pacote.comprimentoCm == null ? '' : String(produto.pacote.comprimentoCm),
    },
  }
}

function mensagemDeErro(erro, padrao) {
  if (erro instanceof ApiError && erro.codigo === 'SEM_CONEXAO') {
    return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  }
  return erro instanceof ApiError ? erro.message : padrao
}

// Cadastro e edição de produto (Figma: "Admin — Cadastro de Produto"). id null = produto novo.
// No cadastro, tamanho em branco não é vendido e 0 aparece na loja como esgotado. Na edição o
// estoque só aparece para leitura: quem muda é a Gestão de Estoque (/admin/estoque).
// As fotos só existem para produto salvo: o cadastro novo salva e abre a edição para as fotos.
export default function AdminProdutoForm({ id }) {
  const editando = id !== null
  const [form, setForm] = useState(editando ? null : VAZIO)
  const [imagens, setImagens] = useState([])
  const [recemCriado] = useState(() => editando && new URLSearchParams(window.location.search).has('novo'))
  const [categorias, setCategorias] = useState([])
  const [erroCarga, setErroCarga] = useState(null)
  const [erro, setErro] = useState(null)
  const [salvando, setSalvando] = useState(false)

  useEffect(() => {
    const controle = new AbortController()
    const { signal } = controle
    Promise.all([listarCategorias({ signal }), editando ? buscarProdutoAdmin(id, { signal }) : null])
      .then(([listaCategorias, produto]) => {
        setCategorias(listaCategorias)
        if (produto) {
          setForm(doProduto(produto))
          setImagens(produto.imagens)
        }
      })
      .catch((e) => {
        if (e.name === 'AbortError') return
        setErroCarga(
          e instanceof ApiError && e.status === 404
            ? 'Produto não encontrado.'
            : mensagemDeErro(e, 'Não foi possível carregar o formulário.'),
        )
      })
    return () => controle.abort()
  }, [id, editando])

  function alterar(campo, valor) {
    setForm((atual) => ({ ...atual, [campo]: valor }))
  }

  function alterarEstoque(tamanho, valor) {
    setForm((atual) => ({ ...atual, estoque: { ...atual.estoque, [tamanho]: valor } }))
  }

  function alterarPacote(campo, valor) {
    setForm((atual) => ({ ...atual, pacote: { ...atual.pacote, [campo]: valor } }))
  }

  async function salvar(evento) {
    evento.preventDefault()
    const preco = lerPreco(form.preco)
    if (Number.isNaN(preco)) {
      setErro('Preço inválido: use um valor como 189,90')
      return
    }
    // Vai sempre inteiro: campo vazio (null) usa o pacote padrão do frete
    const pacote = {
      pesoKg: lerPeso(form.pacote.pesoKg),
      alturaCm: lerMedida(form.pacote.alturaCm),
      larguraCm: lerMedida(form.pacote.larguraCm),
      comprimentoCm: lerMedida(form.pacote.comprimentoCm),
    }
    if (Number.isNaN(pacote.pesoKg)) {
      setErro('Peso inválido: use um valor em kg como 0,45')
      return
    }
    if (MEDIDAS.some(({ campo }) => Number.isNaN(pacote[campo]))) {
      setErro('Medidas da embalagem inválidas: use centímetros inteiros, como 25')
      return
    }

    const dados = {
      nome: form.nome,
      descricao: form.descricao,
      composicao: form.composicao,
      preco,
      categoriaId: form.categoriaId === '' ? null : Number(form.categoriaId),
      destaque: form.destaque,
      pacote,
    }
    // Na edição o estoque não vai junto (#47): ele muda só pela tela de estoque
    if (editando) {
      dados.ativo = form.ativo
    } else {
      dados.estoque = TAMANHOS.filter((t) => form.estoque[t] !== '').map((tamanho) => ({
        tamanho,
        quantidade: Number(form.estoque[tamanho]),
      }))
    }

    setSalvando(true)
    setErro(null)
    try {
      if (editando) {
        await atualizarProduto(id, dados)
        window.location.assign('/admin/produtos?salvo=1')
      } else {
        // Produto novo: abre a edição dele para liberar as fotos
        const criado = await criarProduto(dados)
        window.location.assign(`/admin/produtos/${criado.id}?novo=1`)
      }
    } catch (e) {
      setErro(mensagemDeErro(e, 'Não foi possível salvar o produto.'))
      setSalvando(false)
    }
  }

  const titulo = editando ? 'Editar produto' : 'Novo produto'

  return (
    <section className="admin-produtos">
      <a href="/admin/produtos" className="eyebrow produto-form__voltar">
        ← Produtos
      </a>
      <h1 className="titulo-2">{titulo}</h1>
      {recemCriado && (
        <p className="admin-produtos__aviso" role="status">
          Produto salvo. Agora adicione as fotos.
        </p>
      )}

      {erroCarga ? (
        <p className="admin-produtos__estado" role="alert">
          {erroCarga}
        </p>
      ) : form === null ? (
        <p className="admin-produtos__estado">Carregando produto…</p>
      ) : (
        <form className="produto-form" onSubmit={salvar} noValidate>
          <AdminProdutoFotos produtoId={editando ? id : null} imagensIniciais={imagens} />

          <div className="produto-form__campos">
            <label className="campo">
              <span className="eyebrow">Nome do produto</span>
              <input
                className="campo__caixa"
                name="nome"
                placeholder="Ex: Conjunto Serpente"
                maxLength={150}
                required
                value={form.nome}
                onChange={(e) => alterar('nome', e.target.value)}
                disabled={salvando}
              />
            </label>

            <div className="produto-form__linha">
              <label className="campo produto-form__select">
                <span className="eyebrow">Categoria</span>
                <select
                  className="campo__caixa"
                  name="categoriaId"
                  required
                  value={form.categoriaId}
                  onChange={(e) => alterar('categoriaId', e.target.value)}
                  disabled={salvando}
                >
                  <option value="" disabled>
                    Selecionar categoria...
                  </option>
                  {categorias.map((categoria) => (
                    <option key={categoria.id} value={categoria.id}>
                      {categoria.nome}
                    </option>
                  ))}
                </select>
              </label>

              <label className="campo">
                <span className="eyebrow">Preço</span>
                <input
                  className="campo__caixa"
                  name="preco"
                  inputMode="decimal"
                  placeholder="R$ 0,00"
                  required
                  value={form.preco}
                  onChange={(e) => alterar('preco', e.target.value)}
                  disabled={salvando}
                />
              </label>
            </div>

            <label className="campo">
              <span className="eyebrow">Descrição</span>
              <textarea
                className="campo__caixa produto-form__descricao"
                name="descricao"
                placeholder="Tecido, caimento, ocasião de uso..."
                rows={3}
                value={form.descricao}
                onChange={(e) => alterar('descricao', e.target.value)}
                disabled={salvando}
              />
            </label>

            <label className="campo">
              <span className="eyebrow">Composição e cuidados</span>
              <textarea
                className="campo__caixa produto-form__descricao"
                name="composicao"
                placeholder={'Ex: 100% linho\nLavar à mão em água fria'}
                rows={3}
                value={form.composicao}
                onChange={(e) => alterar('composicao', e.target.value)}
                disabled={salvando}
              />
            </label>

            {editando ? (
              <div className="produto-form__grupo">
                <p className="eyebrow" id="estoque-atual">
                  Estoque por tamanho
                </p>
                <dl className="produto-form__tamanhos" aria-labelledby="estoque-atual">
                  {TAMANHOS.map((tamanho) => (
                    <div key={tamanho} className="produto-form__tamanho">
                      <dt className="eyebrow">{tamanho}</dt>
                      <dd className="produto-form__quantidade">
                        {form.estoque[tamanho] === '' ? (
                          <>
                            <span aria-hidden="true">—</span>
                            <span className="produto-form__oculto">não cadastrado</span>
                          </>
                        ) : (
                          form.estoque[tamanho]
                        )}
                      </dd>
                    </div>
                  ))}
                </dl>
                <p className="produto-form__dica">
                  Estoque de quando a página abriu. Para alterar, use a{' '}
                  <a href="/admin/estoque" className="produto-form__link">
                    Gestão de Estoque
                  </a>
                  .
                </p>
              </div>
            ) : (
              <fieldset className="produto-form__grupo" disabled={salvando}>
                <legend className="eyebrow">Estoque por tamanho</legend>
                <div className="produto-form__tamanhos">
                  {TAMANHOS.map((tamanho) => (
                    <label key={tamanho} className="produto-form__tamanho">
                      <span className="eyebrow">{tamanho}</span>
                      <input
                        type="number"
                        name={`estoque-${tamanho}`}
                        min="0"
                        step="1"
                        inputMode="numeric"
                        placeholder="—"
                        value={form.estoque[tamanho]}
                        onChange={(e) => alterarEstoque(tamanho, e.target.value)}
                      />
                    </label>
                  ))}
                </div>
                <p className="produto-form__dica">Deixe em branco os tamanhos que a peça não tem. 0 aparece como esgotado.</p>
              </fieldset>
            )}

            <fieldset className="produto-form__grupo" disabled={salvando}>
              <legend className="eyebrow">Peso e embalagem</legend>
              <div className="produto-form__pacote">
                <label className="produto-form__tamanho">
                  <span className="eyebrow">Peso (kg)</span>
                  <input
                    name="pesoKg"
                    inputMode="decimal"
                    placeholder="Padrão"
                    value={form.pacote.pesoKg}
                    onChange={(e) => alterarPacote('pesoKg', e.target.value)}
                  />
                </label>
                {MEDIDAS.map(({ campo, rotulo }) => (
                  <label key={campo} className="produto-form__tamanho">
                    <span className="eyebrow">{rotulo} (cm)</span>
                    <input
                      type="number"
                      name={campo}
                      min="1"
                      max="100"
                      step="1"
                      inputMode="numeric"
                      placeholder="Padrão"
                      value={form.pacote[campo]}
                      onChange={(e) => alterarPacote(campo, e.target.value)}
                    />
                  </label>
                ))}
              </div>
              <p className="produto-form__dica">
                Opcional, para o cálculo do frete: peso da peça já embalada e medidas da embalagem. Campo vazio usa o
                pacote padrão da loja.
              </p>
            </fieldset>

            <label className="produto-form__opcao">
              <input
                type="checkbox"
                name="destaque"
                checked={form.destaque}
                onChange={(e) => alterar('destaque', e.target.checked)}
                disabled={salvando}
              />
              <span className="produto-form__opcao-texto">
                Destaque
                <span className="produto-form__dica">Aparece no carrossel e no Best Sellers da página inicial.</span>
              </span>
            </label>

            {editando && (
              <label className="produto-form__opcao">
                <input
                  type="checkbox"
                  name="ativo"
                  checked={form.ativo}
                  onChange={(e) => alterar('ativo', e.target.checked)}
                  disabled={salvando}
                />
                <span className="produto-form__opcao-texto">
                  Ativo na loja
                  <span className="produto-form__dica">Desmarcado, o produto fica arquivado: some da loja, mas continua aqui.</span>
                </span>
              </label>
            )}

            {erro && (
              <p className="produto-form__erro" role="alert">
                {erro}
              </p>
            )}

            <button type="submit" className="botao botao--primario produto-form__salvar" disabled={salvando}>
              {salvando ? 'Salvando…' : 'Salvar produto'}
            </button>
          </div>
        </form>
      )}
    </section>
  )
}
