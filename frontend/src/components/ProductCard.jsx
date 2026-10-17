import { formatarPreco } from '../formato.js'
import './ProductCard.css'

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
      <a className="product-card__link" href={`/produtos/${produto.id}`}>
        <div className="product-card__foto">
          {produto.imagemPrincipal && (
            <img src={produto.imagemPrincipal} alt={produto.nome} loading="lazy" />
          )}
        </div>
        <div className="product-card__info">
          <h3 className="product-card__nome">{produto.nome}</h3>
          <p className="product-card__preco">{formatarPreco(produto.preco)}</p>
        </div>
      </a>
    </article>
  )
}
