import { useEffect, useState } from 'react'
import { buscarUsuarioAdmin, lerSessaoAdmin, sairDoPainel } from '../api.js'
import AdminSidebar from '../components/AdminSidebar.jsx'
import { SECOES_ADMIN } from '../components/adminSecoes.js'
import './Admin.css'

// Casca do painel. As telas de cada seção chegam nas issues #14 (produtos),
// #16 (estoque) e #17 (categorias); até lá, só um aviso.
export default function Admin({ secao }) {
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

  return (
    <div className="admin">
      <AdminSidebar secaoAtiva={secao} onSair={sairDoPainel} />

      <main className="admin__conteudo">
        {usuario && <p className="eyebrow">Olá, {usuario.nome}</p>}
        <h1 className="titulo-2 admin__titulo">{nomeSecao}</h1>
        <p className="admin__aviso">Em breve por aqui.</p>
      </main>
    </div>
  )
}
