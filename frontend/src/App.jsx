import Home from './pages/Home.jsx'
import Produto from './pages/Produto.jsx'

// Rotas pelo caminho da URL; os links recarregam a página, sem roteador por enquanto.
// Em produção o Netlify serve o index.html para qualquer caminho (public/_redirects).
function rotaAtual() {
  const produto = window.location.pathname.match(/^\/produtos\/(\d+)\/?$/)
  if (produto) return { pagina: 'produto', id: Number(produto[1]) }
  return { pagina: 'home' }
}

export default function App() {
  const rota = rotaAtual()
  return rota.pagina === 'produto' ? <Produto id={rota.id} /> : <Home />
}
