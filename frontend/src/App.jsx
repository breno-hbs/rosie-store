import { destinoSeguro, lerSessaoAdmin, lerSessaoCliente, rotaLoginCliente } from './api.js'
import Admin from './pages/Admin.jsx'
import AdminLogin from './pages/AdminLogin.jsx'
import Cadastro from './pages/Cadastro.jsx'
import Carrinho from './pages/Carrinho.jsx'
import Checkout from './pages/Checkout.jsx'
import Entrar from './pages/Entrar.jsx'
import Home from './pages/Home.jsx'
import PagamentoSimulado from './pages/PagamentoSimulado.jsx'
import PedidoRetorno from './pages/PedidoRetorno.jsx'
import Produto from './pages/Produto.jsx'

// Rotas pelo caminho da URL; os links recarregam a página, sem roteador por enquanto.
// Em produção o Netlify serve o index.html para qualquer caminho (public/_redirects).
function rotaAtual() {
  const caminho = window.location.pathname
  const produto = caminho.match(/^\/produtos\/(\d+)\/?$/)
  if (produto) return { pagina: 'produto', id: Number(produto[1]) }
  if (/^\/entrar\/?$/.test(caminho)) return { pagina: 'entrar' }
  if (/^\/cadastro\/?$/.test(caminho)) return { pagina: 'cadastro' }
  if (/^\/carrinho\/?$/.test(caminho)) return { pagina: 'carrinho' }
  if (/^\/checkout\/?$/.test(caminho)) return { pagina: 'checkout' }
  // Volta do Mercado Pago (#24) e pagamento simulado de desenvolvimento
  const pedido = caminho.match(/^\/pedido\/(\d+)\/(sucesso|pendente|falha|pagamento-simulado)\/?$/)
  if (pedido) {
    const id = Number(pedido[1])
    if (pedido[2] === 'pagamento-simulado') return { pagina: 'pagamento-simulado', id }
    return { pagina: 'pedido', id, etapa: pedido[2] }
  }
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

  // Cliente já logado não vê o login nem o cadastro: segue para onde ia
  if ((rota.pagina === 'entrar' || rota.pagina === 'cadastro') && lerSessaoCliente() !== null) {
    window.location.replace(destinoSeguro(new URLSearchParams(window.location.search).get('voltar')))
    return null
  }

  // Carrinho, checkout e pedido só com cliente logado: sem sessão, entra e volta para a mesma página
  const daCliente = ['carrinho', 'checkout', 'pedido', 'pagamento-simulado']
  if (daCliente.includes(rota.pagina) && lerSessaoCliente() === null) {
    window.location.replace(rotaLoginCliente({ voltar: window.location.pathname }))
    return null
  }

  if (rota.pagina === 'produto') return <Produto id={rota.id} />
  if (rota.pagina === 'carrinho') return <Carrinho />
  if (rota.pagina === 'checkout') return <Checkout />
  if (rota.pagina === 'pedido') return <PedidoRetorno id={rota.id} etapa={rota.etapa} />
  if (rota.pagina === 'pagamento-simulado') return <PagamentoSimulado id={rota.id} />
  if (rota.pagina === 'entrar') return <Entrar />
  if (rota.pagina === 'cadastro') return <Cadastro />
  if (rota.pagina === 'admin-login') return <AdminLogin />
  if (rota.pagina === 'admin') {
    return <Admin secao={rota.secao} produtoId={rota.produtoId} categoriaId={rota.categoriaId} />
  }
  return <Home />
}
