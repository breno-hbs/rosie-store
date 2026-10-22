import { useEffect, useRef, useState } from 'react'
import { buscarCarrinho, EVENTO_CARRINHO, EVENTO_CONTA, lerSessaoCliente, rotaLoginCliente } from '../api.js'
import { ROTA_BUSCA, TAMANHO_MAXIMO_BUSCA } from '../busca.js'
import { rotaCategoria } from '../categoria.js'
import LogoBadge from './LogoBadge.jsx'
import './Nav.css'

// Header da loja (#82), como nas lojas minimalistas de referência (COS, Arket, & Other Stories):
// - celular e tablet: uma linha só, com menu e busca à esquerda, logo no centro e conta e sacola à direita
//   (ícones); as categorias ficam no menu, um painel que abre da esquerda;
// - desktop: logo, categorias centradas e as ações em texto ("Buscar", "Entrar"/"Minha conta", "Sacola (1)").
// Busca e menu são <dialog> modais: prendem o foco, fecham no Esc, no "Fechar" e no clique fora.

function Icone({ children }) {
  return (
    <svg className="nav__icone" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true" focusable="false">
      {children}
    </svg>
  )
}

const IconeMenu = () => (
  <Icone>
    <path d="M3 8.5h18M3 15.5h18" />
  </Icone>
)

const IconeBusca = () => (
  <Icone>
    <circle cx="10.5" cy="10.5" r="6.5" />
    <path d="m15.3 15.3 5.2 5.2" />
  </Icone>
)

const IconeConta = () => (
  <Icone>
    <circle cx="12" cy="8" r="4" />
    <path d="M4.5 20.5c1.3-3.6 4.1-5.5 7.5-5.5s6.2 1.9 7.5 5.5" />
  </Icone>
)

const IconeSacola = () => (
  <Icone>
    <path d="M4.5 8.5h15l-1 12h-13z" />
    <path d="M9 11V7a3 3 0 0 1 6 0v4" />
  </Icone>
)

function primeiroNome(sessao) {
  return sessao.usuario.nome.trim().split(/\s+/)[0]
}

function termoNaUrl() {
  if (window.location.pathname.replace(/\/$/, '') !== ROTA_BUSCA) return ''
  return new URLSearchParams(window.location.search).get('q') ?? ''
}

// Peças na sacola da cliente logada: busca uma vez e depois acompanha cada resposta do carrinho
// (EVENTO_CARRINHO). null sem login ou enquanto não chegou.
function useQuantidadeSacola(logado) {
  const [quantidade, setQuantidade] = useState(null)

  useEffect(() => {
    if (!logado) return
    const atualizar = (evento) => setQuantidade(evento.detail)
    window.addEventListener(EVENTO_CARRINHO, atualizar)
    const controle = new AbortController()
    // Silencioso: um token recusado aqui não deve tirar a pessoa da página que ela está vendo
    buscarCarrinho({ signal: controle.signal, silencioso: true }).catch(() => {})
    return () => {
      window.removeEventListener(EVENTO_CARRINHO, atualizar)
      controle.abort()
    }
  }, [logado])

  return logado && quantidade > 0 ? quantidade : null
}

function rotuloSacola(numero) {
  return numero ? `Sacola, ${numero} ${numero === 1 ? 'peça' : 'peças'}` : 'Sacola'
}

// Leva para a página da categoria (#88); na própria página, fica marcada como atual
function LinkCategoria({ categoria, categoriaAtual, className }) {
  return (
    <a
      className={className}
      href={rotaCategoria(categoria)}
      aria-current={categoria.id === categoriaAtual ? 'page' : undefined}
    >
      {categoria.nome}
    </a>
  )
}

// <dialog> aberto com showModal(); `aberto` vai para o aria-expanded dos botões. Fechar atualiza o estado na
// hora: o evento "close" do <dialog> chega numa tarefa depois (e só serve de reserva)
function useDialogo() {
  const ref = useRef(null)
  const [aberto, setAberto] = useState(false)

  function fechar() {
    ref.current?.close()
    setAberto(false)
  }

  return {
    aberto,
    abrir() {
      ref.current?.showModal()
      setAberto(true)
    },
    fechar,
    props: {
      ref,
      onCancel: () => setAberto(false), // Esc
      onClose: () => setAberto(false),
      // O conteúdo ocupa o <dialog> inteiro: clique no próprio <dialog> é clique no fundo escurecido
      onClick: (evento) => {
        if (evento.target === evento.currentTarget) fechar()
      },
    },
  }
}

// categoriaAtual: id da categoria da página aberta (/categoria/{slug}), para marcar no menu
export default function Nav({ categorias, categoriaAtual }) {
  const [sessao, setSessao] = useState(lerSessaoCliente)
  const quantidade = useQuantidadeSacola(sessao !== null)
  const menu = useDialogo()
  const busca = useDialogo()
  const campoBusca = useRef(null)

  // Nome trocado na Minha Conta aparece na hora
  useEffect(() => {
    const atualizar = () => setSessao(lerSessaoCliente())
    window.addEventListener(EVENTO_CONTA, atualizar)
    return () => window.removeEventListener(EVENTO_CONTA, atualizar)
  }, [])

  function abrirBusca() {
    busca.abrir()
    campoBusca.current?.focus()
    campoBusca.current?.select()
  }

  // Busca em branco não sai da página: o campo continua aberto esperando o texto
  function enviarBusca(evento) {
    const campo = campoBusca.current
    campo.value = campo.value.trim()
    if (!campo.value) {
      evento.preventDefault()
      campo.focus()
    }
  }

  const conta = sessao
    ? { href: '/minha-conta', rotulo: 'Minha conta' }
    : { href: rotaLoginCliente(), rotulo: 'Entrar' }

  return (
    <header className="nav">
      <div className="nav__lado">
        <button
          type="button"
          className="nav__botao"
          aria-label="Menu"
          aria-haspopup="dialog"
          aria-expanded={menu.aberto}
          aria-controls="nav-menu"
          onClick={menu.abrir}
        >
          <IconeMenu />
        </button>
        <button
          type="button"
          className="nav__botao"
          aria-label="Buscar"
          aria-haspopup="dialog"
          aria-expanded={busca.aberto}
          aria-controls="nav-busca"
          onClick={abrirBusca}
        >
          <IconeBusca />
        </button>
      </div>

      <a href="/" className="nav__logo" aria-label="Rosie — página inicial">
        <LogoBadge tamanho={44} />
      </a>

      <nav className="nav__categorias" aria-label="Categorias">
        {categorias.map((categoria) => (
          <LinkCategoria key={categoria.id} categoria={categoria} categoriaAtual={categoriaAtual} className="nav__link" />
        ))}
      </nav>

      <div className="nav__acoes">
        <button
          type="button"
          className="nav__botao nav__botao--texto nav__so-desktop"
          aria-haspopup="dialog"
          aria-expanded={busca.aberto}
          aria-controls="nav-busca"
          onClick={abrirBusca}
        >
          Buscar
        </button>
        <a className="nav__botao" href={conta.href} aria-label={conta.rotulo}>
          <IconeConta />
          <span className="nav__texto">{conta.rotulo}</span>
        </a>
        <a className="nav__botao nav__sacola" href="/carrinho" aria-label={rotuloSacola(quantidade)}>
          <span className="nav__sacola-icone">
            <IconeSacola />
            {quantidade && <span className="nav__contador">{quantidade}</span>}
          </span>
          <span className="nav__texto">
            Sacola{quantidade && ` (${quantidade})`}
          </span>
        </a>
      </div>

      <dialog id="nav-menu" className="nav__painel" aria-label="Menu" {...menu.props}>
        <div className="nav__painel-conteudo">
          <div className="nav__painel-topo">
            <a href="/" className="nav__logo" aria-label="Rosie — página inicial">
              <LogoBadge tamanho={36} />
            </a>
            <button type="button" className="nav__fechar" onClick={menu.fechar}>
              Fechar
            </button>
          </div>

          <nav aria-label="Categorias">
            <p className="eyebrow nav__painel-titulo">Categorias</p>
            <ul className="nav__painel-lista">
              {categorias.map((categoria) => (
                <li key={categoria.id}>
                  <LinkCategoria categoria={categoria} categoriaAtual={categoriaAtual} className="nav__painel-categoria" />
                </li>
              ))}
            </ul>
          </nav>

          <ul className="nav__painel-links">
            {sessao && <li className="nav__ola">Olá, {primeiroNome(sessao)}</li>}
            <li>
              <a href={conta.href}>{conta.rotulo}</a>
            </li>
            <li>
              <a href="/carrinho" aria-label={rotuloSacola(quantidade)}>
                Sacola{quantidade && ` (${quantidade})`}
              </a>
            </li>
            <li>
              <a href="/trocas">Trocas e devoluções</a>
            </li>
          </ul>
        </div>
      </dialog>

      <dialog id="nav-busca" className="nav__busca" aria-label="Buscar" {...busca.props}>
        <div className="nav__busca-conteudo">
          <form className="nav__busca-form" role="search" action={ROTA_BUSCA} method="get" onSubmit={enviarBusca}>
            <IconeBusca />
            <input
              ref={campoBusca}
              className="nav__busca-campo"
              type="search"
              name="q"
              defaultValue={termoNaUrl()}
              placeholder="O que você procura?"
              aria-label="Buscar peças"
              maxLength={TAMANHO_MAXIMO_BUSCA}
              enterKeyHint="search"
              autoComplete="off"
            />
            <button type="button" className="nav__fechar" onClick={busca.fechar}>
              Fechar
            </button>
          </form>

          {categorias.length > 0 && (
            <div className="nav__busca-sugestoes">
              <p className="eyebrow">Categorias</p>
              <ul>
                {categorias.map((categoria) => (
                  <li key={categoria.id}>
                    <LinkCategoria categoria={categoria} categoriaAtual={categoriaAtual} className="nav__sugestao" />
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </dialog>
    </header>
  )
}
