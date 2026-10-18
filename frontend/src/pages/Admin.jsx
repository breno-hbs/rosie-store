import { useEffect, useState } from 'react'
import { buscarUsuarioAdmin, lerSessaoAdmin, sairDoPainel } from '../api.js'
import AdminProdutoForm from '../components/AdminProdutoForm.jsx'
import AdminProdutos from '../components/AdminProdutos.jsx'
import AdminSidebar from '../components/AdminSidebar.jsx'
import { SECOES_ADMIN } from '../components/adminSecoes.js'
import './Admin.css'

// Casca do painel. Produtos (#14) já tem tela; estoque (#16) e categorias (#17)
// ainda mostram só um aviso. produtoId: 'novo' ou o id do produto em edição.
export default function Admin({ secao, produtoId }) {
  const [usuario, setUsuario] = useState(() => lerSessaoAdmin()?.usuario ?? null)
  const nomeSecao = SECOES_ADMIN.find((s) => s.id === secao)?.nome

  // Confere o token na API ao abrir o painel; se venceu, o api.js volta para o login
  useEffect(() => {
    const controle = new AbortController()
    buscarUsuarioAdmin({ signal: controle.signal })
      .then(setUsuario)
      .catch(() => {})
    return () => controle.abort()
  }, [])

  let conteudo
  if (secao === 'produtos' && produtoId) {
    conteudo = <AdminProdutoForm id={produtoId === 'novo' ? null : produtoId} />
  } else if (secao === 'produtos') {
    conteudo = <AdminProdutos />
  } else {
    conteudo = (
      <>
        <h1 className="titulo-2 admin__titulo">{nomeSecao}</h1>
        <p className="admin__aviso">Em breve por aqui.</p>
      </>
    )
  }

  return (
    <div className="admin">
      <AdminSidebar secaoAtiva={secao} onSair={sairDoPainel} />

      <main className="admin__conteudo">
        {usuario && <p className="eyebrow">Olá, {usuario.nome}</p>}
        {conteudo}
      </main>
    </div>
  )
}
