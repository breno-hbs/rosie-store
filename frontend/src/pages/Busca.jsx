import { useEffect, useState } from 'react'
import { listarCategorias, listarProdutos } from '../api.js'
import { ROTA_BUSCA, TAMANHO_MAXIMO_BUSCA } from '../busca.js'
import Footer from '../components/Footer.jsx'
import Nav from '../components/Nav.jsx'
import ProductCard from '../components/ProductCard.jsx'
import './Busca.css'

// Resultados da busca do header (#82), em /busca?q=vestido+midi. A API procura cada palavra no nome,
// na descrição, na composição e na categoria (GET /produtos?busca=). Sem resultado, ou sem texto,
// sugere as categorias.
const TAMANHO_PAGINA = 12

function termoDaUrl() {
  return (new URLSearchParams(window.location.search).get('q') ?? '').trim().slice(0, TAMANHO_MAXIMO_BUSCA)
}

function totalDePecas(total) {
  return `${total} ${total === 1 ? 'peça encontrada' : 'peças encontradas'}`
}

export default function Busca() {
  const [termo] = useState(termoDaUrl)
  const [categorias, setCategorias] = useState([])
  const [tentativa, setTentativa] = useState(0)
  // chave diz de qual tentativa é a lista; enquanto não for a atual, está carregando
  const [lista, setLista] = useState({ chave: null, produtos: [], pagina: 0, totalPaginas: 0, totalItens: 0, erro: null })
  const [carregandoMais, setCarregandoMais] = useState(false)
  const [erroMais, setErroMais] = useState(null)

  const carregando = Boolean(termo) && lista.chave !== tentativa

  useEffect(() => {
    document.title = termo ? `${termo} — Busca — Rosie` : 'Busca — Rosie'
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then(setCategorias)
      .catch(() => {}) // Sem categorias a busca vazia fica só com o aviso
    return () => controle.abort()
  }, [termo])

  useEffect(() => {
    if (!termo) return
    const controle = new AbortController()
    listarProdutos({ busca: termo, pagina: 0, tamanho: TAMANHO_PAGINA }, { signal: controle.signal })
      .then((resposta) =>
        setLista({
          chave: tentativa,
          produtos: resposta.conteudo,
          pagina: 0,
          totalPaginas: resposta.totalPaginas,
          totalItens: resposta.totalItens,
          erro: null,
        }),
      )
      .catch((erro) => {
        if (erro.name !== 'AbortError') {
          setLista({ chave: tentativa, produtos: [], pagina: 0, totalPaginas: 0, totalItens: 0, erro })
        }
      })
    return () => controle.abort()
  }, [termo, tentativa])

  async function carregarMais() {
    setCarregandoMais(true)
    setErroMais(null)
    try {
      const resposta = await listarProdutos({ busca: termo, pagina: lista.pagina + 1, tamanho: TAMANHO_PAGINA })
      setLista((atual) => ({
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

  const semResultado = termo && !carregando && !lista.erro && lista.produtos.length === 0
  const temMais = !carregando && lista.pagina + 1 < lista.totalPaginas

  return (
    <>
      <Nav categorias={categorias} />

      <main className="busca">
        <header className="busca__topo">
          <p className="eyebrow">Busca</p>
          <h1 className="titulo-2">{termo ? `“${termo}”` : 'O que você procura?'}</h1>
          {termo && !carregando && !lista.erro && lista.produtos.length > 0 && (
            <p className="busca__total" role="status">
              {totalDePecas(lista.totalItens)}
            </p>
          )}
          <form className="busca__form" role="search" action={ROTA_BUSCA} method="get">
            <input
              className="busca__campo"
              type="search"
              name="q"
              defaultValue={termo}
              placeholder="Vestido midi, linho, preto…"
              aria-label="Buscar peças"
              maxLength={TAMANHO_MAXIMO_BUSCA}
              enterKeyHint="search"
              autoComplete="off"
              required
            />
            <button type="submit" className="botao botao--contorno busca__enviar">
              Buscar
            </button>
          </form>
        </header>

        <section className="busca__resultados" aria-busy={carregando} aria-label="Resultados">
          {carregando ? (
            <div className="busca__grade">
              {Array.from({ length: 4 }, (_, i) => (
                <ProductCard key={i} />
              ))}
            </div>
          ) : lista.erro ? (
            <div className="busca__aviso" role="alert">
              <p>Não foi possível buscar agora.</p>
              <button type="button" className="botao botao--contorno" onClick={() => setTentativa((t) => t + 1)}>
                Tentar de novo
              </button>
            </div>
          ) : !termo || semResultado ? (
            <div className="busca__aviso">
              {semResultado ? (
                <p>
                  Não encontramos peças para “{termo}”. Tente outra palavra, como o nome da peça, o tecido ou a cor.
                </p>
              ) : (
                <p>Busque pelo nome da peça, pelo tecido ou pela cor.</p>
              )}
              {categorias.length > 0 && (
                <nav className="busca__categorias" aria-label="Categorias">
                  <p className="eyebrow">Ou veja as categorias</p>
                  <ul>
                    {categorias.map((categoria) => (
                      <li key={categoria.id}>
                        <a className="busca__categoria" href={`/?categoria=${categoria.id}`}>
                          {categoria.nome}
                        </a>
                      </li>
                    ))}
                  </ul>
                </nav>
              )}
            </div>
          ) : (
            <div className="busca__grade">
              {lista.produtos.map((produto) => (
                <ProductCard key={produto.id} produto={produto} />
              ))}
            </div>
          )}

          {temMais && (
            <div className="busca__mais">
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
