import { destinoSeguro, lerSessaoAdmin, lerSessaoCliente } from './api.js'
import Admin from './pages/Admin.jsx'
import AdminLogin from './pages/AdminLogin.jsx'
import Cadastro from './pages/Cadastro.jsx'
import Entrar from './pages/Entrar.jsx'
import Home from './pages/Home.jsx'
import Produto from './pages/Produto.jsx'

// Rotas pelo caminho da URL; os links recarregam a página, sem roteador por enquanto.
// Em produção o Netlify serve o index.html para qualquer caminho (public/_redirects).
function rotaAtual() {
  const caminho = window.location.pathname
  const produto = caminho.match(/^\/produtos\/(\d+)\/?$/)
  if (produto) return { pagina: 'produto', id: Number(produto[1]) }
  if (/^\/entrar\/?$/.test(caminho)) return { pagina: 'entrar' }
  if (/^\/cadastro\/?$/.test(caminho)) return { pagina: 'cadastro' }
  if (/^\/admin\/login\/?$/.test(caminho)) return { pagina: 'admin-login' }
  const produtoAdmin = caminho.match(/^\/admin\/produtos\/(novo|\d+)\/?$/)
  if (produtoAdmin) {
    const produtoId = produtoAdmin[1] === 'novo' ? 'novo' : Number(produtoAdmin[1])
    return { pagina: 'admin', secao: 'produtos', produtoId }
  }
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

  // Cliente já logado não vê o login nem o cadastro: segue para onde ia
  if ((rota.pagina === 'entrar' || rota.pagina === 'cadastro') && lerSessaoCliente() !== null) {
    window.location.replace(destinoSeguro(new URLSearchParams(window.location.search).get('voltar')))
    return null
  }

  if (rota.pagina === 'produto') return <Produto id={rota.id} />
  if (rota.pagina === 'entrar') return <Entrar />
  if (rota.pagina === 'cadastro') return <Cadastro />
  if (rota.pagina === 'admin-login') return <AdminLogin />
  if (rota.pagina === 'admin') return <Admin secao={rota.secao} produtoId={rota.produtoId} />
  return <Home />
}
