import { Fragment, useEffect, useState } from 'react'
import { atualizarStatusTroca, listarTrocasAdmin } from '../api.js'
import { STATUS_TROCA_ADMIN, nomeMotivo, textoFrete } from '../troca.js'
import { formatarDataHora, mensagemDeErro } from './pedidosAdmin.js'
import './AdminPedidos.css'
import './AdminTrocas.css'

// O que a Roseane pode fazer em cada status (SOLICITADA → APROVADA/RECUSADA; APROVADA → CONCLUIDA)
const ACOES = {
  SOLICITADA: [
    { status: 'APROVADA', rotulo: 'Aprovar', confirmar: 'Aprovar a troca' },
    { status: 'RECUSADA', rotulo: 'Recusar', confirmar: 'Recusar a troca' },
  ],
  APROVADA: [{ status: 'CONCLUIDA', rotulo: 'Concluir', confirmar: 'Marcar como concluída' }],
}

const DICA_RESPOSTA = {
  APROVADA: 'Ex.: como mandar a peça de volta',
  RECUSADA: 'Conte à cliente por que a troca não foi aprovada',
  CONCLUIDA: 'Ex.: a peça nova já foi enviada',
}

// Trocas no painel (#27). Sem tela no Figma: segue a tabela de pedidos (#25). Cada ação abre, logo abaixo da
// troca, um recado opcional que a cliente lê na Minha Conta; a troca só muda ao confirmar.
export default function AdminTrocas() {
  const [trocas, setTrocas] = useState(null)
  const [erro, setErro] = useState(null)
  const [tentativa, setTentativa] = useState(0)
  const [acao, setAcao] = useState(null) // { trocaId, status, confirmar }
  const [resposta, setResposta] = useState('')
  const [envio, setEnvio] = useState({ salvando: false, erro: null })

  useEffect(() => {
    const controle = new AbortController()
    listarTrocasAdmin({ signal: controle.signal })
      .then((lista) => {
        setTrocas(lista)
        setErro(null)
      })
      .catch((e) => {
        if (e.name !== 'AbortError') setErro(mensagemDeErro(e, 'Não foi possível carregar as trocas.'))
      })
    return () => controle.abort()
  }, [tentativa])

  function tentarDeNovo() {
    setErro(null)
    setTrocas(null)
    setTentativa((t) => t + 1)
  }

  function abrir(troca, opcao) {
    setAcao({ trocaId: troca.id, ...opcao })
    setResposta('')
    setEnvio({ salvando: false, erro: null })
  }

  async function confirmar(evento) {
    evento.preventDefault()
    setEnvio({ salvando: true, erro: null })
    try {
      const atualizada = await atualizarStatusTroca(acao.trocaId, acao.status, resposta.trim() || undefined)
      setTrocas((lista) => lista.map((t) => (t.id === atualizada.id ? atualizada : t)))
      setAcao(null)
      setEnvio({ salvando: false, erro: null })
    } catch (e) {
      setEnvio({ salvando: false, erro: mensagemDeErro(e, 'Não foi possível mudar o status da troca.') })
    }
  }

  const esperando = trocas?.filter((t) => t.status === 'SOLICITADA').length ?? 0

  return (
    <section className="admin-pedidos">
      <header className="admin-pedidos__topo">
        <h1 className="titulo-2">Trocas</h1>
        {esperando > 0 && (
          <p className="admin-pedidos__resumo" role="status">
            {esperando === 1 ? '1 troca espera resposta' : `${esperando} trocas esperam resposta`}
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
      ) : trocas === null ? (
        <p className="admin-pedidos__estado">Carregando trocas…</p>
      ) : trocas.length === 0 ? (
        <p className="admin-pedidos__estado">
          Nenhuma troca pedida ainda. Elas aparecem aqui quando uma cliente pedir pela Minha Conta.
        </p>
      ) : (
        <table className="admin-pedidos__tabela admin-trocas__tabela">
          <thead>
            <tr className="rotulo">
              <th scope="col">Troca</th>
              <th scope="col">Cliente</th>
              <th scope="col">Peça</th>
              <th scope="col">Motivo</th>
              <th scope="col">Status</th>
              <th scope="col">Ações</th>
            </tr>
          </thead>
          <tbody>
            {trocas.map((troca) => (
              <Fragment key={troca.id}>
                <tr>
                  <td className="admin-pedidos__numero">
                    #{troca.id}
                    <span className="admin-pedidos__data">{formatarDataHora(troca.createdAt)}</span>
                  </td>
                  <td>
                    {troca.cliente.nome}
                    <a href={`/admin/pedidos/${troca.pedidoId}`} className="admin-pedidos__acao admin-trocas__pedido">
                      Pedido #{troca.pedidoId}
                    </a>
                  </td>
                  <td>
                    <span className="admin-trocas__peca">{troca.nome}</span>
                    <span className="admin-trocas__detalhe">
                      Tamanho {troca.tamanho} · {troca.quantidade} {troca.quantidade === 1 ? 'peça' : 'peças'}
                    </span>
                    {troca.observacao && <span className="admin-trocas__observacao">“{troca.observacao}”</span>}
                  </td>
                  <td>
                    {nomeMotivo(troca.motivo)}
                    <span className="admin-trocas__detalhe">{textoFrete(troca.freteConta, { painel: true })}</span>
                  </td>
                  <td data-rotulo="Status">
                    <span className="rotulo admin-pedidos__status admin-trocas__status" data-status={troca.status}>
                      {STATUS_TROCA_ADMIN[troca.status] ?? troca.status}
                    </span>
                    {troca.resposta && <span className="admin-trocas__detalhe">Recado: {troca.resposta}</span>}
                  </td>
                  <td className="admin-pedidos__acoes">
                    {ACOES[troca.status] ? (
                      <span className="admin-trocas__botoes">
                        {ACOES[troca.status].map((opcao) => (
                          <button
                            key={opcao.status}
                            type="button"
                            className="admin-pedidos__acao admin-trocas__botao"
                            aria-expanded={acao?.trocaId === troca.id && acao.status === opcao.status}
                            onClick={() => abrir(troca, opcao)}
                          >
                            {opcao.rotulo}
                          </button>
                        ))}
                      </span>
                    ) : (
                      <span className="admin-trocas__detalhe">Encerrada</span>
                    )}
                  </td>
                </tr>
                {acao?.trocaId === troca.id && (
                  <tr className="admin-trocas__confirmar">
                    <td colSpan={6}>
                      <form className="admin-trocas__form" onSubmit={confirmar}>
                        <label className="campo">
                          <span className="rotulo">Recado para a cliente (opcional)</span>
                          <textarea
                            className="campo__caixa"
                            rows={2}
                            maxLength={1000}
                            placeholder={DICA_RESPOSTA[acao.status]}
                            value={resposta}
                            onChange={(e) => setResposta(e.target.value)}
                          />
                        </label>
                        <div className="admin-pedido__botoes">
                          <button type="submit" className="botao botao--primario" disabled={envio.salvando}>
                            {envio.salvando ? 'Salvando…' : acao.confirmar}
                          </button>
                          <button
                            type="button"
                            className="botao botao--contorno"
                            onClick={() => setAcao(null)}
                            disabled={envio.salvando}
                          >
                            Cancelar
                          </button>
                        </div>
                        {envio.erro && (
                          <p className="admin-pedido__alerta" role="alert">
                            {envio.erro}
                          </p>
                        )}
                      </form>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
