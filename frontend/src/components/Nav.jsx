import { useState } from 'react'
import { lerSessaoCliente, rotaLoginCliente, sairDaLoja } from '../api.js'
import LogoBadge from './LogoBadge.jsx'
import './Nav.css'

// "Conta": sem login leva a /entrar (voltando depois para a página atual); logado mostra o
// primeiro nome e "Sair". A tela Minha Conta (#26) ainda não existe.
function Conta() {
  const [sessao, setSessao] = useState(lerSessaoCliente)

  if (!sessao) {
    return <a className="nav__conta" href={rotaLoginCliente()}>Conta</a>
  }

  const nome = sessao.usuario.nome
  return (
    <>
      <span className="nav__cliente" title={nome}>
        {nome.trim().split(/\s+/)[0]}
      </span>
      <button
        type="button"
        className="nav__sair"
        onClick={() => {
          sairDaLoja()
          setSessao(null)
        }}
      >
        Sair
      </button>
    </>
  )
}

export default function Nav({ categorias, categoriaSelecionada, onSelecionarCategoria }) {
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

      {/* Busca e sacola ainda não têm tela; ficam visíveis como no Figma */}
      <div className="nav__acoes">
        <Conta />
        <span>Buscar</span>
        <span className="nav__sacola">Sacola</span>
      </div>
    </header>
  )
}
