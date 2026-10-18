import LogoBadge from './LogoBadge.jsx'
import { SECOES_ADMIN } from './adminSecoes.js'
import './AdminSidebar.css'

export default function AdminSidebar({ secaoAtiva, onSair }) {
  return (
    <aside className="admin-sidebar">
      <LogoBadge tamanho={56} />
      <p className="rotulo admin-sidebar__titulo">Painel admin</p>

      <nav className="admin-sidebar__menu" aria-label="Painel admin">
        {SECOES_ADMIN.map((secao) => (
          <a
            key={secao.id}
            href={`/admin/${secao.id}`}
            className="admin-sidebar__item"
            aria-current={secao.id === secaoAtiva ? 'page' : undefined}
          >
            {secao.nome}
          </a>
        ))}
        <button type="button" className="admin-sidebar__item" onClick={onSair}>
          Sair
        </button>
      </nav>
    </aside>
  )
}
