import { lerSessaoAdmin } from './api.js'
import Admin from './pages/Admin.jsx'
import AdminLogin from './pages/AdminLogin.jsx'
import Home from './pages/Home.jsx'
import Produto from './pages/Produto.jsx'

// Rotas pelo caminho da URL; os links recarregam a página, sem roteador por enquanto.
// Em produção o Netlify serve o index.html para qualquer caminho (public/_redirects).
function rotaAtual() {
  const caminho = window.location.pathname
  const produto = caminho.match(/^\/produtos\/(\d+)\/?$/)
  if (produto) return { pagina: 'produto', id: Number(produto[1]) }
  if (/^\/admin\/login\/?$/.test(caminho)) return { pagina: 'admin-login' }
  const produtoAdmin = caminho.match(/^\/admin\/produtos\/(novo|\d+)\/?$/)
  if (produtoAdmin) {
    const produtoId = produtoAdmin[1] === 'novo' ? 'novo' : Number(produtoAdmin[1])
    return { pagina: 'admin', secao: 'produtos', produtoId }
  }
  const medidasAdmin = caminho.match(/^\/admin\/categorias\/(\d+)\/medidas\/?$/)
  if (medidasAdmin) return { pagina: 'admin', secao: 'categorias', categoriaId: Number(medidasAdmin[1]) }
  const admin = caminho.match(/^\/admin(?:\/(produtos|estoque|categorias))?\/?$/)
  if (admin) return { pagina: 'admin', secao: admin[1] ?? 'produtos' }
  return { pagina: 'home' }
}

export default function App() {
  const rota = rotaAtual()

  // Painel só com sessão válida; quem já está logado não vê o login de novo
  if (rota.pagina === 'admin' || rota.pagina === 'admin-login') {
    const logado = lerSessaoAdmin() !== null
    if (rota.pagina === 'admin' && !logado) {
      window.location.replace('/admin/login')
      return null
    }
    if (rota.pagina === 'admin-login' && logado) {
      window.location.replace('/admin')
      return null
    }
  }

  if (rota.pagina === 'produto') return <Produto id={rota.id} />
  if (rota.pagina === 'admin-login') return <AdminLogin />
  if (rota.pagina === 'admin') {
    return <Admin secao={rota.secao} produtoId={rota.produtoId} categoriaId={rota.categoriaId} />
  }
  return <Home />
}
