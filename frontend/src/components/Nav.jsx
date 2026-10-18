import { useEffect, useState } from 'react'
import { buscarCarrinho, EVENTO_CARRINHO, lerSessaoCliente, rotaLoginCliente, sairDaLoja } from '../api.js'
import LogoBadge from './LogoBadge.jsx'
import './Nav.css'

// "Conta": sem login leva a /entrar (voltando depois para a página atual); logado mostra o
// primeiro nome e "Sair". A tela Minha Conta (#26) ainda não existe.
function Conta({ sessao, onSair }) {
  if (!sessao) {
    return <a className="nav__conta" href={rotaLoginCliente()}>Conta</a>
  }

  const nome = sessao.usuario.nome
  return (
    <>
      <span className="nav__cliente" title={nome}>
        {nome.trim().split(/\s+/)[0]}
      </span>
      <button type="button" className="nav__sair" onClick={onSair}>
        Sair
      </button>
    </>
  )
}

// "Sacola" leva ao carrinho (sem login, a rota manda para /entrar e volta). Logado, mostra quantas
// peças há nele: busca uma vez e depois acompanha cada resposta do carrinho (EVENTO_CARRINHO).
function Sacola({ logado }) {
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

  const numero = logado && quantidade > 0 ? quantidade : null
  return (
    <a
      className="nav__sacola"
      href="/carrinho"
      aria-label={numero ? `Sacola, ${numero} ${numero === 1 ? 'peça' : 'peças'}` : 'Sacola'}
    >
      Sacola{numero && <span className="nav__contador"> ({numero})</span>}
    </a>
  )
}

export default function Nav({ categorias, categoriaSelecionada, onSelecionarCategoria }) {
  const [sessao, setSessao] = useState(lerSessaoCliente)

  return (
    <header className="nav">
      <a href="/" className="nav__logo" aria-label="Rosie — página inicial">
        <LogoBadge tamanho={44} />
      </a>

      <nav className="nav__menu" aria-label="Categorias">
        {/* Na Home o clique filtra a lista; nas outras páginas leva para a Home já filtrada */}
        {categorias.map((categoria) =>
          onSelecionarCategoria ? (
            <button
              key={categoria.id}
              type="button"
              className="nav__link"
              aria-pressed={categoria.id === categoriaSelecionada}
              onClick={() => onSelecionarCategoria(categoria.id)}
            >
              {categoria.nome}
            </button>
          ) : (
            <a key={categoria.id} className="nav__link" href={`/?categoria=${categoria.id}`}>
              {categoria.nome}
            </a>
          ),
        )}
      </nav>

      {/* Busca ainda não tem tela; fica visível como no Figma */}
      <div className="nav__acoes">
        <Conta
          sessao={sessao}
          onSair={() => {
            sairDaLoja()
            setSessao(null)
          }}
        />
        <span>Buscar</span>
        <Sacola logado={sessao !== null} />
      </div>
    </header>
  )
}
