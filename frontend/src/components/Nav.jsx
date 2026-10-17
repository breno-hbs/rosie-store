import LogoBadge from './LogoBadge.jsx'
import './Nav.css'

export default function Nav({ categorias, categoriaSelecionada, onSelecionarCategoria }) {
  return (
    <header className="nav">
      <a href="/" className="nav__logo" aria-label="Rosie — página inicial">
        <LogoBadge tamanho={44} />
      </a>

      <nav className="nav__menu" aria-label="Categorias">
        {categorias.map((categoria) => (
          <button
            key={categoria.id}
            type="button"
            className="nav__link"
            aria-pressed={categoria.id === categoriaSelecionada}
            onClick={() => onSelecionarCategoria(categoria.id)}
          >
            {categoria.nome}
          </button>
        ))}
      </nav>

      {/* Conta, busca e sacola ainda não têm tela; ficam visíveis como no Figma */}
      <div className="nav__acoes">
        <span>Conta</span>
        <span>Buscar</span>
        <span className="nav__sacola">Sacola</span>
      </div>
    </header>
  )
}
