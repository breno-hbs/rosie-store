import { useEffect, useRef, useState } from 'react'
import {
  adicionarAoCarrinho,
  buscarProduto,
  buscarTabelaMedidas,
  lerSessaoCliente,
  listarCategorias,
  listarProdutos,
  rotaLoginCliente,
} from '../api.js'
import Nav from '../components/Nav.jsx'
import ProductCard from '../components/ProductCard.jsx'
import TabelaMedidasModal from '../components/TabelaMedidasModal.jsx'
import AvisoSacola from '../components/AvisoSacola.jsx'
import Footer from '../components/Footer.jsx'
import { formatarPreco, linkWhatsApp } from '../formato.js'
import { POLITICA_TROCA } from '../troca.js'
import './Produto.css'

const QTD_RELACIONADOS = 4

export default function Produto({ id }) {
  const [categorias, setCategorias] = useState([])
  const [tentativa, setTentativa] = useState(0)
  const [resultado, setResultado] = useState({ chave: null, produto: null, erro: null })
  const [relacionados, setRelacionados] = useState([])

  // Mesmo padrão da Home: enquanto o resultado não for desta chave, está carregando
  const chave = `${id}:${tentativa}`
  const carregando = resultado.chave !== chave
  const produto = carregando ? null : resultado.produto

  useEffect(() => {
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then(setCategorias)
      .catch(() => {}) // Sem categorias a Nav fica só com logo e ações
    return () => controle.abort()
  }, [])

  useEffect(() => {
    const controle = new AbortController()
    buscarProduto(id, { signal: controle.signal })
      .then((dados) => setResultado({ chave, produto: dados, erro: null }))
      .catch((erro) => {
        if (erro.name !== 'AbortError') setResultado({ chave, produto: null, erro })
      })
    return () => controle.abort()
  }, [chave, id])

  const categoriaId = produto?.categoria.id
  useEffect(() => {
    if (!categoriaId) return
    const controle = new AbortController()
    // Pede um a mais porque o próprio produto pode vir na lista
    listarProdutos({ categoriaId, tamanho: QTD_RELACIONADOS + 1 }, { signal: controle.signal })
      .then((resposta) =>
        setRelacionados(resposta.conteudo.filter((outro) => outro.id !== id).slice(0, QTD_RELACIONADOS)),
      )
      .catch(() => {}) // Sem relacionados a seção não aparece
    return () => controle.abort()
  }, [categoriaId, id])

  useEffect(() => {
    if (produto) document.title = `${produto.nome} — Rosie`
  }, [produto])

  return (
    <>
      <Nav categorias={categorias} />

      <main>
        {carregando ? (
          <EsqueletoProduto />
        ) : resultado.erro ? (
          <div className="produto__aviso" role="alert">
            {resultado.erro.status === 404 ? (
              <>
                <p>Produto não encontrado.</p>
                <a className="botao botao--contorno" href="/">
                  Voltar para a loja
                </a>
              </>
            ) : (
              <>
                <p>Não foi possível carregar o produto.</p>
                <button type="button" className="botao botao--contorno" onClick={() => setTentativa((t) => t + 1)}>
                  Tentar de novo
                </button>
              </>
            )}
          </div>
        ) : (
          <>
            <nav className="produto__trilha" aria-label="Você está em">
              <a href="/">Loja</a> / <a href={`/?categoria=${produto.categoria.id}`}>{produto.categoria.nome}</a> /{' '}
              <span aria-current="page">{produto.nome}</span>
            </nav>
            <DetalheProduto produto={produto} />
          </>
        )}

        {relacionados.length > 0 && (
          <section className="relacionados">
            <div className="relacionados__titulo">
              <p className="eyebrow">Combina com</p>
              <h2 className="titulo-2">Você também vai gostar</h2>
            </div>
            <div className="relacionados__grade">
              {relacionados.map((outro) => (
                <ProductCard key={outro.id} produto={outro} />
              ))}
            </div>
          </section>
        )}
      </main>

      <Footer />
    </>
  )
}

function DetalheProduto({ produto }) {
  const [indiceFoto, setIndiceFoto] = useState(0)
  const [tamanho, setTamanho] = useState(null)
  const [pedirTamanho, setPedirTamanho] = useState(false)
  // "Adicionar à sacola" (#22): o painel AvisoSacola confirma (#79); o erro fica junto ao botão
  const [adicionando, setAdicionando] = useState(false)
  const [erroSacola, setErroSacola] = useState(null)
  const [adicionado, setAdicionado] = useState(null) // { tamanho, carrinho } da última adição
  const [avisoAberto, setAvisoAberto] = useState(false)
  const botaoSacola = useRef(null)
  const opcoesTamanho = useRef(null)
  // Tabela de medidas da categoria (#20): o botão só aparece quando ela tem medidas
  const [tabelaMedidas, setTabelaMedidas] = useState(null)
  const [medidasAbertas, setMedidasAbertas] = useState(false)
  const botaoMedidas = useRef(null)

  const categoriaId = produto.categoria.id
  useEffect(() => {
    const controle = new AbortController()
    buscarTabelaMedidas(categoriaId, { signal: controle.signal })
      .then(setTabelaMedidas)
      .catch(() => {}) // Sem a tabela (ou com erro) o link não aparece
    return () => controle.abort()
  }, [categoriaId])
  const temMedidas = tabelaMedidas?.categoriaId === categoriaId && tabelaMedidas.medidas.length > 0

  function fecharMedidas() {
    setMedidasAbertas(false)
    botaoMedidas.current?.focus()
  }

  const fotos = produto.imagens
  const disponiveis = produto.estoque.filter((item) => item.quantidade > 0)
  const esgotado = disponiveis.length === 0

  const mensagem = [
    `Olá! Tenho interesse no produto ${produto.nome}`,
    tamanho && `(tamanho ${tamanho})`,
    `— ${window.location.origin}/produtos/${produto.id}`,
  ]
    .filter(Boolean)
    .join(' ')
  const whatsapp = linkWhatsApp(mensagem)

  function escolherTamanho(novo) {
    setTamanho(novo)
    setPedirTamanho(false)
    setErroSacola(null)
  }

  // Sem login vai para /entrar e volta para este produto; o estoque é conferido pela API
  function adicionarNaSacola() {
    if (!tamanho) {
      setPedirTamanho(true)
      // Leva a cliente (e o foco do teclado) aos tamanhos; o focus() também rola até eles
      opcoesTamanho.current?.querySelector('button:not(:disabled)')?.focus()
      return
    }
    if (!lerSessaoCliente()) {
      window.location.assign(rotaLoginCliente())
      return
    }
    setAdicionando(true)
    setErroSacola(null)
    adicionarAoCarrinho(produto.id, tamanho, 1)
      .then((carrinho) => {
        setAdicionado({ tamanho, carrinho })
        setAvisoAberto(true)
      })
      .catch(setErroSacola)
      .finally(() => setAdicionando(false))
  }

  function fecharAviso() {
    setAvisoAberto(false)
    botaoSacola.current?.focus()
  }

  function comprarPeloWhatsapp(evento) {
    // Com estoque, o tamanho precisa ir na mensagem
    if (!esgotado && !tamanho) {
      evento.preventDefault()
      setPedirTamanho(true)
    }
  }

  return (
    <div className="produto">
      <div className="produto__galeria">
        <div className="produto__foto">
          {fotos.length > 0 && (
            <img
              key={fotos[indiceFoto]}
              src={fotos[indiceFoto]}
              alt={fotos.length > 1 ? `${produto.nome} — foto ${indiceFoto + 1} de ${fotos.length}` : produto.nome}
            />
          )}
        </div>

        {fotos.length > 1 && (
          <div className="produto__miniaturas">
            {fotos.map((url, i) => (
              <button
                key={url}
                type="button"
                className="produto__miniatura"
                aria-label={`Ver foto ${i + 1} de ${fotos.length}`}
                aria-current={i === indiceFoto}
                onClick={() => setIndiceFoto(i)}
              >
                <img src={url} alt="" loading="lazy" />
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="produto__detalhe">
        <p className="eyebrow">{produto.categoria.nome}</p>
        <h1 className="titulo-2 produto__nome">{produto.nome}</h1>
        <p className="produto__preco">{formatarPreco(produto.preco)}</p>

        <div className="produto__tamanhos">
          <p className="eyebrow" id="rotulo-tamanho">
            Tamanho
          </p>
          {esgotado ? (
            <p className="produto__esgotado">Esgotado no momento.</p>
          ) : (
            <div
              ref={opcoesTamanho}
              className="produto__opcoes"
              role="group"
              aria-labelledby="rotulo-tamanho"
              aria-describedby={pedirTamanho ? 'pedir-tamanho' : undefined}
              data-pedir={pedirTamanho || undefined}
            >
              {produto.estoque.map((item) => (
                <button
                  key={item.tamanho}
                  type="button"
                  className="produto__tamanho"
                  aria-pressed={item.tamanho === tamanho}
                  disabled={item.quantidade <= 0}
                  aria-label={item.quantidade <= 0 ? `${item.tamanho} (esgotado)` : item.tamanho}
                  onClick={() => escolherTamanho(item.tamanho)}
                >
                  {item.tamanho}
                </button>
              ))}
            </div>
          )}
          {pedirTamanho && (
            <p id="pedir-tamanho" className="produto__alerta" role="alert">
              Escolha um tamanho.
            </p>
          )}
          {temMedidas && (
            <>
              <button
                ref={botaoMedidas}
                type="button"
                className="produto__medidas"
                aria-haspopup="dialog"
                onClick={() => setMedidasAbertas(true)}
              >
                Ver tabela de medidas
              </button>
              <TabelaMedidasModal tabela={tabelaMedidas} aberto={medidasAbertas} onFechar={fecharMedidas} />
            </>
          )}
        </div>

        <div className="produto__acoes">
          <button
            ref={botaoSacola}
            type="button"
            className="botao botao--primario"
            disabled={esgotado || adicionando}
            aria-busy={adicionando || undefined}
            aria-haspopup="dialog"
            onClick={adicionarNaSacola}
          >
            {adicionando ? 'Adicionando…' : 'Adicionar à sacola'}
          </button>
          {erroSacola && <ErroSacola erro={erroSacola} />}
          {adicionado && (
            <AvisoSacola
              aberto={avisoAberto}
              produto={produto}
              tamanho={adicionado.tamanho}
              carrinho={adicionado.carrinho}
              onFechar={fecharAviso}
            />
          )}
          <a
            className="botao botao--contorno"
            href={whatsapp}
            aria-disabled={!whatsapp}
            target="_blank"
            rel="noreferrer"
            onClick={comprarPeloWhatsapp}
          >
            Comprar pelo WhatsApp
          </a>
        </div>

        <div className="produto__acordeao">
          {produto.descricao && (
            <details open>
              <summary>Descrição</summary>
              <p className="produto__texto">{produto.descricao}</p>
            </details>
          )}
          {produto.composicao && (
            <details>
              <summary>Composição e cuidados</summary>
              <p className="produto__texto">{produto.composicao}</p>
            </details>
          )}
          <details>
            <summary>Envio e trocas</summary>
            <p className="produto__texto">{POLITICA_TROCA}</p>
          </details>
        </div>
      </div>
    </div>
  )
}

// Erro de "Adicionar à sacola" junto ao botão. Estoque: a API já manda a frase pronta ("Só temos 3 no
// tamanho P, e você já tem 3 na sacola"), e o link leva a quem já tem a peça na sacola.
function ErroSacola({ erro }) {
  const mensagem =
    erro.codigo === 'SEM_CONEXAO' ? 'Sem conexão com a loja. Confira a internet e tente de novo.' : erro.message
  return (
    <p className="produto__retorno" role="alert">
      {mensagem.replace(/\.?$/, '.')}
      {erro.codigo === 'ESTOQUE_INSUFICIENTE' && (
        <>
          {' '}
          <a href="/carrinho">Ver sacola</a>
        </>
      )}
    </p>
  )
}

function EsqueletoProduto() {
  return (
    <>
      <p className="produto__trilha" aria-hidden="true">
        &nbsp;
      </p>
      <div className="produto" aria-busy="true" aria-label="Carregando produto">
        <div className="produto__galeria">
          <div className="produto__foto" />
        </div>
        <div className="produto__detalhe" aria-hidden="true">
          <p className="eyebrow">&nbsp;</p>
          <p className="titulo-2 produto__nome produto__esqueleto">&nbsp;</p>
          <p className="produto__preco produto__esqueleto">&nbsp;</p>
        </div>
      </div>
    </>
  )
}
