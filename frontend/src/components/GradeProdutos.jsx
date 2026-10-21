import ProductCard from './ProductCard.jsx'
import './GradeProdutos.css'

const QTD_ESQUELETO = 4

// Grade de produtos da Home e da página da categoria (#88). Sem `produtos`, mostra cards vazios enquanto carrega.
export default function GradeProdutos({ produtos }) {
  return (
    <div className="grade-produtos">
      {produtos
        ? produtos.map((produto) => <ProductCard key={produto.id} produto={produto} />)
        : Array.from({ length: QTD_ESQUELETO }, (_, i) => <ProductCard key={i} />)}
    </div>
  )
}
