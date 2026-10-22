import { useEffect, useRef, useState } from 'react'
import { listarBanners, listarCategorias, listarDestaques, listarProdutos } from '../api.js'
import Nav from '../components/Nav.jsx'
import HeroCarousel from '../components/HeroCarousel.jsx'
import CategoryTiles from '../components/CategoryTiles.jsx'
import GradeProdutos from '../components/GradeProdutos.jsx'
import Footer from '../components/Footer.jsx'
import { contatoLoja } from '../formato.js'
import './Home.css'

const TAMANHO_PAGINA = 12
const QTD_SLIDES = 3
const CONTATO = contatoLoja('Olá! Vim pelo site da Rosie e quero tirar uma dúvida.')

// Só vitrine: as categorias têm página própria (/categoria/{slug}, #88), e o link antigo /?categoria={id}
// é redirecionado para ela antes de chegar aqui (App.jsx)
export default function Home() {
  const [categorias, setCategorias] = useState([])
  // Banners da Home (#89): null enquanto carrega; [] sem nenhum no ar (ou com erro): o carrossel usa os destaques
  const [banners, setBanners] = useState(null)
  // null enquanto carrega; [] quando não há destaques marcados ou a chamada falhou
  const [destaques, setDestaques] = useState(null)
  const [slidesReserva, setSlidesReserva] = useState([])
  const [verTodos, setVerTodos] = useState(false)
  const [tentativa, setTentativa] = useState(0)
  const [lista, setLista] = useState({ chave: null, produtos: [], pagina: 0, totalPaginas: 0, erro: null })
  const [carregandoMais, setCarregandoMais] = useState(false)
  const [erroMais, setErroMais] = useState(null)
  const secaoProdutos = useRef(null)

  // Muda a cada nova tentativa; enquanto a lista não for dessa chave, está carregando
  const chave = tentativa
  const carregando = lista.chave !== chave

  // O "Best Sellers" mostra os destaques; sem destaques (ou com erro) cai na listagem comum
  const temDestaques = destaques?.length > 0
  const mostrarDestaques = !verTodos && temDestaques
  const usarListagem = verTodos || destaques?.length === 0

  useEffect(() => {
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then(setCategorias)
      .catch(() => {}) // Sem categorias a Home segue só com a listagem
    listarBanners({ signal: controle.signal })
      .then(setBanners)
      .catch((erro) => {
        if (erro.name !== 'AbortError') setBanners([])
      })
    listarDestaques({ limite: TAMANHO_PAGINA }, { signal: controle.signal })
      .then(setDestaques)
      .catch((erro) => {
        if (erro.name !== 'AbortError') setDestaques([])
      })
    return () => controle.abort()
  }, [])

  // Hero sem destaques: primeiros produtos da listagem comum, como antes
  useEffect(() => {
    if (destaques?.length !== 0) return
    const controle = new AbortController()
    listarProdutos({ pagina: 0, tamanho: QTD_SLIDES }, { signal: controle.signal })
      .then((resposta) => setSlidesReserva(resposta.conteudo))
      .catch(() => {})
    return () => controle.abort()
  }, [destaques])

  useEffect(() => {
    if (!usarListagem) return
    const controle = new AbortController()
    listarProdutos({ pagina: 0, tamanho: TAMANHO_PAGINA }, { signal: controle.signal })
      .then((resposta) =>
        setLista({
          chave,
          produtos: resposta.conteudo,
          pagina: 0,
          totalPaginas: resposta.totalPaginas,
          erro: null,
        }),
      )
      .catch((erro) => {
        if (erro.name !== 'AbortError') {
          setLista({ chave, produtos: [], pagina: 0, totalPaginas: 0, erro })
        }
      })
    return () => controle.abort()
  }, [chave, usarListagem])

  async function carregarMais() {
    setCarregandoMais(true)
    setErroMais(null)
    try {
      const resposta = await listarProdutos({ pagina: lista.pagina + 1, tamanho: TAMANHO_PAGINA })
      setLista((atual) =>
        atual.chave === chave
          ? {
              ...atual,
              produtos: [...atual.produtos, ...resposta.conteudo],
              pagina: atual.pagina + 1,
              totalPaginas: resposta.totalPaginas,
            }
          : atual,
      )
    } catch (erro) {
      setErroMais(erro)
    } finally {
      setCarregandoMais(false)
    }
  }

  function alternarVerTodos(valor) {
    setVerTodos(valor)
    setErroMais(null)
    secaoProdutos.current?.scrollIntoView({ behavior: 'smooth' })
  }

  const carregandoGrade = !mostrarDestaques && carregando
  const produtos = mostrarDestaques ? destaques : lista.produtos
  const temMais = !mostrarDestaques && !carregando && lista.pagina + 1 < lista.totalPaginas
  const slides = temDestaques ? destaques.slice(0, QTD_SLIDES) : slidesReserva

  return (
    <>
      <Nav categorias={categorias} />

      <main>
        {/* Enquanto os banners carregam, o carrossel fica vazio (sem piscar os destaques antes) */}
        <HeroCarousel banners={banners ?? []} produtos={banners === null ? [] : slides} />

        <CategoryTiles categorias={categorias} destaques={destaques} />

        <section className="produtos" ref={secaoProdutos} aria-busy={carregandoGrade}>
          <div className="secao-titulo">
            {verTodos && temDestaques ? (
              <>
                <p className="eyebrow">Catálogo</p>
                <h2 className="titulo-2">Todos os produtos</h2>
                <button type="button" className="secao-titulo__limpar" onClick={() => alternarVerTodos(false)}>
                  Ver os destaques
                </button>
              </>
            ) : (
              <>
                <p className="eyebrow">Selecionados pra você</p>
                <h2 className="titulo-2">Best Sellers</h2>
              </>
            )}
          </div>

          {carregandoGrade ? (
            <GradeProdutos />
          ) : !mostrarDestaques && lista.erro ? (
            <div className="produtos__aviso" role="alert">
              <p>Não foi possível carregar os produtos.</p>
              <button type="button" className="botao botao--contorno" onClick={() => setTentativa((t) => t + 1)}>
                Tentar de novo
              </button>
            </div>
          ) : produtos.length === 0 ? (
            <div className="produtos__aviso">
              <p>Nenhum produto por aqui ainda.</p>
            </div>
          ) : (
            <GradeProdutos produtos={produtos} />
          )}

          {temMais && (
            <div className="produtos__mais">
              <button
                type="button"
                className="botao botao--contorno"
                onClick={carregarMais}
                disabled={carregandoMais}
              >
                {carregandoMais ? 'Carregando…' : 'Ver mais'}
              </button>
              {erroMais && <p role="alert">Não foi possível carregar mais produtos.</p>}
            </div>
          )}

          {mostrarDestaques && (
            <div className="produtos__mais">
              <button type="button" className="botao botao--contorno" onClick={() => alternarVerTodos(true)}>
                Ver todos os produtos
              </button>
            </div>
          )}
        </section>

        <section className="atendimento">
          <p className="eyebrow">Atendimento</p>
          <h2 className="titulo-2">Rosie</h2>
          <a className="botao botao--primario" href={CONTATO.href} target="_blank" rel="noreferrer">
            {CONTATO.texto}
          </a>
        </section>
      </main>

      <Footer />
    </>
  )
}
