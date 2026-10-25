import LogoBadge from './LogoBadge.jsx'
import '../pages/AcessoCliente.css'

// Coluna das telas de senha (#93), no layout do login da loja. No painel, o logo não leva à loja e o título vem
// com o rótulo "Painel administrativo", como no login do painel.
export default function MolduraAcesso({ area, titulo, children }) {
  return (
    <main className="acesso">
      {area === 'admin' ? (
        <>
          <LogoBadge tamanho={72} />
          <p className="rotulo acesso__area">Painel administrativo</p>
        </>
      ) : (
        <a href="/" aria-label="Rosie — página inicial">
          <LogoBadge tamanho={72} />
        </a>
      )}
      <h1 className={`titulo-2 acesso__titulo${area === 'admin' ? ' acesso__titulo--painel' : ''}`}>{titulo}</h1>
      {children}
    </main>
  )
}
