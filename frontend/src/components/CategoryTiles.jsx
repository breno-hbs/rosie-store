import './CategoryTiles.css'

export default function CategoryTiles({ categorias, categoriaSelecionada, onSelecionarCategoria }) {
  if (categorias.length === 0) return null

  return (
    <section className="categorias" aria-label="Categorias">
      {categorias.map((categoria) => (
        <button
          key={categoria.id}
          type="button"
          className="categorias__item"
          aria-pressed={categoria.id === categoriaSelecionada}
          onClick={() => onSelecionarCategoria(categoria.id)}
        >
          <span className="titulo-3">{categoria.nome}</span>
        </button>
      ))}
    </section>
  )
}
