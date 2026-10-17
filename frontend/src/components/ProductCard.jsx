import './ProductCard.css'

const formatoPreco = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export default function ProductCard({ produto }) {
  // Sem produto = esqueleto enquanto a listagem carrega
  if (!produto) {
    return (
      <article className="product-card" aria-hidden="true">
        <div className="product-card__foto" />
        <div className="product-card__info">
          <p className="product-card__nome">&nbsp;</p>
          <p className="product-card__preco">&nbsp;</p>
        </div>
      </article>
    )
  }

  return (
    <article className="product-card">
      <div className="product-card__foto">
        {produto.imagemPrincipal && (
          <img src={produto.imagemPrincipal} alt={produto.nome} loading="lazy" />
        )}
      </div>
      <div className="product-card__info">
        <h3 className="product-card__nome">{produto.nome}</h3>
        <p className="product-card__preco">{formatoPreco.format(produto.preco)}</p>
      </div>
    </article>
  )
}
