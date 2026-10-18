import { useEffect, useState } from 'react'
import { listarPedidosAdmin } from '../api.js'
import { formatarPreco } from '../formato.js'
import { STATUS_PEDIDO, formatarDataHora, mensagemDeErro } from './pedidosAdmin.js'
import './AdminPedidos.css'

// Lista de pedidos do painel (#25). Sem tela no Figma: segue a tabela de "Admin — Lista de Produtos".
// Pedido pago ainda sem etiqueta aparece marcado, para a Roseane saber o que falta enviar.
export default function AdminPedidos() {
  const [pedidos, setPedidos] = useState(null)
  const [erro, setErro] = useState(null)
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    const controle = new AbortController()
    listarPedidosAdmin({ signal: controle.signal })
      .then((lista) => {
        setPedidos(lista)
        setErro(null)
      })
      .catch((e) => {
        if (e.name !== 'AbortError') setErro(mensagemDeErro(e, 'Não foi possível carregar os pedidos.'))
      })
    return () => controle.abort()
  }, [tentativa])

  function tentarDeNovo() {
    setErro(null)
    setPedidos(null)
    setTentativa((t) => t + 1)
  }

  const paraEnviar = pedidos?.filter((p) => p.status === 'PAGO').length ?? 0

  return (
    <section className="admin-pedidos">
      <header className="admin-pedidos__topo">
        <h1 className="titulo-2">Pedidos</h1>
        {paraEnviar > 0 && (
          <p className="admin-pedidos__resumo" role="status">
            {paraEnviar === 1 ? '1 pedido pago espera a etiqueta' : `${paraEnviar} pedidos pagos esperam a etiqueta`}
          </p>
        )}
      </header>

      {erro ? (
        <div className="admin-pedidos__estado" role="alert">
          <p>{erro}</p>
          <button type="button" className="botao botao--contorno" onClick={tentarDeNovo}>
            Tentar de novo
          </button>
        </div>
      ) : pedidos === null ? (
        <p className="admin-pedidos__estado">Carregando pedidos…</p>
      ) : pedidos.length === 0 ? (
        <p className="admin-pedidos__estado">Nenhum pedido ainda. Eles aparecem aqui assim que alguém finalizar uma compra.</p>
      ) : (
        <table className="admin-pedidos__tabela">
          <thead>
            <tr className="rotulo">
              <th scope="col">Pedido</th>
              <th scope="col">Cliente</th>
              <th scope="col">Peças</th>
              <th scope="col">Total</th>
              <th scope="col">Status</th>
              <th scope="col">Ações</th>
            </tr>
          </thead>
          <tbody>
            {pedidos.map((pedido) => (
              <tr key={pedido.id}>
                <td className="admin-pedidos__numero">
                  #{pedido.id}
                  <span className="admin-pedidos__data">{formatarDataHora(pedido.createdAt)}</span>
                </td>
                <td data-rotulo="Cliente">{pedido.cliente.nome}</td>
                <td data-rotulo="Peças">{pedido.quantidadePecas}</td>
                <td data-rotulo="Total">{formatarPreco(pedido.valorTotal)}</td>
                <td data-rotulo="Status">
                  <span className="rotulo admin-pedidos__status" data-status={pedido.status}>
                    {STATUS_PEDIDO[pedido.status] ?? pedido.status}
                  </span>
                </td>
                <td className="admin-pedidos__acoes">
                  <a href={`/admin/pedidos/${pedido.id}`} className="admin-pedidos__acao">
                    {pedido.status === 'PAGO' ? 'Gerar etiqueta' : 'Ver pedido'}
                  </a>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
