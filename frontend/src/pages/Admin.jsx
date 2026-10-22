import { useEffect, useState } from 'react'
import { buscarUsuarioAdmin, lerSessaoAdmin, sairDoPainel } from '../api.js'
import AdminBannerForm from '../components/AdminBannerForm.jsx'
import AdminBanners from '../components/AdminBanners.jsx'
import AdminCategorias from '../components/AdminCategorias.jsx'
import AdminEstoque from '../components/AdminEstoque.jsx'
import AdminMedidas from '../components/AdminMedidas.jsx'
import AdminPedidoDetalhe from '../components/AdminPedidoDetalhe.jsx'
import AdminPedidos from '../components/AdminPedidos.jsx'
import AdminProdutoForm from '../components/AdminProdutoForm.jsx'
import AdminProdutos from '../components/AdminProdutos.jsx'
import AdminSidebar from '../components/AdminSidebar.jsx'
import AdminTrocas from '../components/AdminTrocas.jsx'
import { SECOES_ADMIN } from '../components/adminSecoes.js'
import './Admin.css'

// Casca do painel: produtos (#14), estoque (#16), categorias (#17), tabela de medidas (#20), banners (#89),
// pedidos (#25) e trocas (#27).
// produtoId: 'novo' ou o id do produto em edição; categoriaId: categoria com a tabela de medidas aberta;
// pedidoId: pedido aberto no detalhe; bannerId: 'novo' ou o id do banner em edição.
export default function Admin({ secao, produtoId, categoriaId, pedidoId, bannerId }) {
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
  } else if (secao === 'estoque') {
    conteudo = <AdminEstoque />
  } else if (secao === 'categorias' && categoriaId) {
    conteudo = <AdminMedidas categoriaId={categoriaId} />
  } else if (secao === 'pedidos' && pedidoId) {
    conteudo = <AdminPedidoDetalhe id={pedidoId} />
  } else if (secao === 'pedidos') {
    conteudo = <AdminPedidos />
  } else if (secao === 'banners' && bannerId) {
    conteudo = <AdminBannerForm id={bannerId === 'novo' ? null : bannerId} />
  } else if (secao === 'banners') {
    conteudo = (
      <>
        <h1 className="titulo-2 admin__titulo">{nomeSecao}</h1>
        <AdminBanners />
      </>
    )
  } else if (secao === 'trocas') {
    conteudo = <AdminTrocas />
  } else if (secao === 'categorias') {
    conteudo = (
      <>
        <h1 className="titulo-2 admin__titulo">{nomeSecao}</h1>
        <AdminCategorias />
      </>
    )
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
