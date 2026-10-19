import { useEffect, useState } from 'react'
import { listarCategorias, sairDaLoja } from '../api.js'
import ContaDados from '../components/ContaDados.jsx'
import ContaEnderecos from '../components/ContaEnderecos.jsx'
import ContaPedidos from '../components/ContaPedidos.jsx'
import ContaTrocas from '../components/ContaTrocas.jsx'
import Footer from '../components/Footer.jsx'
import Nav from '../components/Nav.jsx'
import './MinhaConta.css'

const ABAS = [
  { id: 'pedidos', rotulo: 'Meus Pedidos', caminho: '/minha-conta', titulo: 'Meus pedidos' },
  { id: 'trocas', rotulo: 'Trocas', caminho: '/minha-conta/trocas', titulo: 'Minhas trocas' },
  { id: 'dados', rotulo: 'Dados Pessoais', caminho: '/minha-conta/dados', titulo: 'Dados pessoais' },
  { id: 'enderecos', rotulo: 'Endereços', caminho: '/minha-conta/enderecos', titulo: 'Endereços' },
]

// Tela "Minha Conta" do Figma (#26), com a aba Trocas (#27). Só com cliente logado (App.jsx manda para /entrar antes).
// Cada aba tem o seu caminho (os links recarregam a página, como no resto da loja); "Sair" esquece a
// sessão e volta para a Home. No celular as abas viram uma faixa no topo, como o menu da Nav.
export default function MinhaConta({ aba, pedidoId }) {
  const [categorias, setCategorias] = useState([])
  const atual = ABAS.find((a) => a.id === aba) ?? ABAS[0]

  useEffect(() => {
    document.title = `${pedidoId ? `Pedido #${pedidoId}` : atual.titulo} — Rosie`
  }, [atual, pedidoId])

  useEffect(() => {
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then(setCategorias)
      .catch(() => {}) // Sem categorias a Nav fica só com logo e ações
    return () => controle.abort()
  }, [])

  function sair() {
    sairDaLoja()
    window.location.assign('/')
  }

  return (
    <>
      <Nav categorias={categorias} />

      <div className="conta">
        <nav className="conta__menu" aria-labelledby="conta-menu-titulo">
          <p className="eyebrow conta__menu-titulo" id="conta-menu-titulo">
            Minha conta
          </p>
          <ul className="conta__abas">
            {ABAS.map((a) => (
              <li key={a.id}>
                <a className="conta__aba" href={a.caminho} aria-current={a.id === atual.id ? 'page' : undefined}>
                  {a.rotulo}
                </a>
              </li>
            ))}
            <li>
              <button type="button" className="conta__aba" onClick={sair}>
                Sair
              </button>
            </li>
          </ul>
        </nav>

        <main className="conta__conteudo">
          {atual.id === 'pedidos' && <ContaPedidos pedidoId={pedidoId} />}
          {atual.id === 'trocas' && <ContaTrocas />}
          {atual.id === 'dados' && <ContaDados />}
          {atual.id === 'enderecos' && <ContaEnderecos />}
        </main>
      </div>

      <Footer />
    </>
  )
}
