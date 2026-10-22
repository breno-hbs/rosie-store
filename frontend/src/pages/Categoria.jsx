import { useEffect, useState } from 'react'
import { buscarCategoria, listarCategorias, listarProdutos } from '../api.js'
import { rotaCategoria } from '../categoria.js'
import Footer from '../components/Footer.jsx'
import GradeProdutos from '../components/GradeProdutos.jsx'
import Nav from '../components/Nav.jsx'
import './Categoria.css'

// Página da categoria (#88), em /categoria/{slug}: o link que a Roseane divulga no Instagram e no WhatsApp.
// Nome, total de peças e a grade da Home, 12 peças por vez com "Ver mais" (GET /produtos?categoriaId=).
// Link de antes de a categoria ser renomeada também abre: a API devolve o slug atual e o endereço é trocado.
const TAMANHO_PAGINA = 12

function totalDePecas(total) {
  return `${total} ${total === 1 ? 'peça' : 'peças'}`
}

// <title> e meta description da página (os padrões da loja estão no index.html). Categoria inexistente
// pede para não entrar na busca do Google.
function definirMetadados({ titulo, descricao, indexar }) {
  document.title = titulo
  if (descricao) document.querySelector('meta[name="description"]')?.setAttribute('content', descricao)
  if (!indexar && !document.querySelector('meta[name="robots"]')) {
    const robots = document.createElement('meta')
    robots.name = 'robots'
    robots.content = 'noindex'
    document.head.append(robots)
  }
}

export default function Categoria({ slug }) {
  const [categorias, setCategorias] = useState([])
  const [tentativa, setTentativa] = useState(0)
  // chave diz de qual tentativa é o resultado; enquanto não for a atual, está carregando. `categoria` fica
  // preenchida quando só a listagem falhou.
  const [pagina, setPagina] = useState({
    chave: null,
    categoria: null,
    produtos: [],
    pagina: 0,
    totalPaginas: 0,
    totalItens: 0,
    erro: null,
  })
  const [carregandoMais, setCarregandoMais] = useState(false)
  const [erroMais, setErroMais] = useState(null)

  const carregando = pagina.chave !== tentativa
  const categoria = pagina.categoria
  const naoEncontrada = !carregando && !categoria && pagina.erro?.status === 404

  useEffect(() => {
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then(setCategorias)
      .catch(() => {}) // Sem categorias a Nav fica só com logo e ações
    return () => controle.abort()
  }, [])

  useEffect(() => {
    const controle = new AbortController()
    const opcoes = { signal: controle.signal }
    let encontrada = null
    buscarCategoria(slug, opcoes)
      .then((resposta) => {
        encontrada = resposta
        return listarProdutos({ categoriaId: resposta.id, pagina: 0, tamanho: TAMANHO_PAGINA }, opcoes)
      })
      .then((resposta) =>
        setPagina({
          chave: tentativa,
          categoria: encontrada,
          produtos: resposta.conteudo,
          pagina: 0,
          totalPaginas: resposta.totalPaginas,
          totalItens: resposta.totalItens,
          erro: null,
        }),
      )
      .catch((erro) => {
        if (erro.name !== 'AbortError') {
          setPagina({
            chave: tentativa,
            categoria: encontrada,
            produtos: [],
            pagina: 0,
            totalPaginas: 0,
            totalItens: 0,
            erro,
          })
        }
      })
    return () => controle.abort()
  }, [slug, tentativa])

  // Slug antigo (categoria renomeada) ou com maiúsculas: troca o endereço pelo atual, sem nova entrada no histórico
  useEffect(() => {
    if (!categoria || categoria.slug === slug) return
    const { search, hash } = window.location
    window.history.replaceState(null, '', rotaCategoria(categoria) + search + hash)
  }, [categoria, slug])

  useEffect(() => {
    if (categoria) {
      definirMetadados({
        titulo: `${categoria.nome} — Rosie`,
        descricao: `${categoria.nome} na Rosie: veja as peças da categoria, com fotos, tamanhos e preços, e compre online.`,
        indexar: true,
      })
    } else if (naoEncontrada) {
      definirMetadados({ titulo: 'Categoria não encontrada — Rosie', indexar: false })
    }
  }, [categoria, naoEncontrada])

  async function carregarMais() {
    setCarregandoMais(true)
    setErroMais(null)
    try {
      const resposta = await listarProdutos({
        categoriaId: categoria.id,
        pagina: pagina.pagina + 1,
        tamanho: TAMANHO_PAGINA,
      })
      setPagina((atual) => ({
        ...atual,
        produtos: [...atual.produtos, ...resposta.conteudo],
        pagina: atual.pagina + 1,
        totalPaginas: resposta.totalPaginas,
        totalItens: resposta.totalItens,
      }))
    } catch (erro) {
      setErroMais(erro)
    } finally {
      setCarregandoMais(false)
    }
  }

  const tentarDeNovo = () => setTentativa((t) => t + 1)
  const temMais = !carregando && !pagina.erro && pagina.pagina + 1 < pagina.totalPaginas

  if (naoEncontrada) {
    return (
      <>
        <Nav categorias={categorias} />
        <main className="categoria">
          <div className="categoria__aviso categoria__aviso--pagina">
            <p className="eyebrow">Categoria</p>
            <h1 className="titulo-2">Categoria não encontrada</h1>
            <p>O link pode estar incompleto, ou a categoria saiu da loja.</p>
            {categorias.length > 0 && (
              <nav className="categoria__outras" aria-label="Categorias">
                <p className="eyebrow">Veja as categorias</p>
                <ul>
                  {categorias.map((outra) => (
                    <li key={outra.id}>
                      <a className="categoria__outra" href={rotaCategoria(outra)}>
                        {outra.nome}
                      </a>
                    </li>
                  ))}
                </ul>
              </nav>
            )}
            <a className="botao botao--contorno" href="/">
              Voltar para a loja
            </a>
          </div>
        </main>
        <Footer />
      </>
    )
  }

  return (
    <>
      <Nav categorias={categorias} categoriaAtual={categoria?.id} />

      <main className="categoria">
        <header className="categoria__topo">
          <p className="eyebrow">Categoria</p>
          {categoria ? (
            <h1 className="titulo-2">{categoria.nome}</h1>
          ) : (
            <span className="categoria__titulo-vazio" aria-hidden="true" />
          )}
          {!carregando && !pagina.erro && pagina.totalItens > 0 && (
            <p className="categoria__total" role="status">
              {totalDePecas(pagina.totalItens)}
            </p>
          )}
        </header>

        <section className="categoria__produtos" aria-busy={carregando} aria-label="Peças">
          {carregando ? (
            <GradeProdutos />
          ) : pagina.erro ? (
            <div className="categoria__aviso" role="alert">
              <p>{categoria ? 'Não foi possível carregar as peças.' : 'Não foi possível carregar a categoria.'}</p>
              <button type="button" className="botao botao--contorno" onClick={tentarDeNovo}>
                Tentar de novo
              </button>
            </div>
          ) : pagina.produtos.length === 0 ? (
            <div className="categoria__aviso">
              <p>Nenhuma peça nesta categoria por enquanto.</p>
              <a className="botao botao--contorno" href="/">
                Ver a loja
              </a>
            </div>
          ) : (
            <GradeProdutos produtos={pagina.produtos} />
          )}

          {temMais && (
            <div className="categoria__mais">
              <button
                type="button"
                className="botao botao--contorno"
                onClick={carregarMais}
                disabled={carregandoMais}
              >
                {carregandoMais ? 'Carregando…' : 'Ver mais'}
              </button>
              {erroMais && <p role="alert">Não foi possível carregar mais peças.</p>}
            </div>
          )}
        </section>
      </main>

      <Footer />
    </>
  )
}
