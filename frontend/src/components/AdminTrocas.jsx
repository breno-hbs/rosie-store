import { Fragment, useEffect, useState } from 'react'
import { atualizarStatusTroca, gerarEtiquetaVolta, listarTrocasAdmin } from '../api.js'
import { formatarPreco } from '../formato.js'
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
// troca, um recado opcional que a cliente lê na Minha Conta; a troca só muda ao confirmar. Na troca aprovada
// por defeito, "Gerar etiqueta de volta" (#63) também pede confirmação logo abaixo, porque compra a etiqueta
// com o saldo do Melhor Envio; depois vira "Imprimir etiqueta de volta", com o rastreio.
export default function AdminTrocas() {
  const [trocas, setTrocas] = useState(null)
  const [erro, setErro] = useState(null)
  const [tentativa, setTentativa] = useState(0)
  const [acao, setAcao] = useState(null) // { trocaId, status, confirmar } ou { trocaId, etiqueta: true }
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

  function abrirEtiqueta(troca) {
    setAcao({ trocaId: troca.id, etiqueta: true })
    setEnvio({ salvando: false, erro: null })
  }

  function atualizar(atualizada) {
    setTrocas((lista) => lista.map((t) => (t.id === atualizada.id ? atualizada : t)))
  }

  // Também serve para "Buscar rastreio": repetir não compra outra etiqueta
  async function gerarVolta(trocaId) {
    setEnvio({ salvando: true, erro: null, trocaId })
    try {
      atualizar(await gerarEtiquetaVolta(trocaId))
      setAcao(null)
      setEnvio({ salvando: false, erro: null })
    } catch (e) {
      setEnvio({ salvando: false, erro: mensagemDeErro(e, 'Não foi possível gerar a etiqueta de volta.'), trocaId })
      // A falha fica gravada na troca: recarrega para o painel mostrar "com falha"
      listarTrocasAdmin()
        .then(setTrocas)
        .catch(() => {})
    }
  }

  async function confirmar(evento) {
    evento.preventDefault()
    setEnvio({ salvando: true, erro: null })
    try {
      atualizar(await atualizarStatusTroca(acao.trocaId, acao.status, resposta.trim() || undefined))
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
                    <EtiquetaVolta troca={troca} />
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
                    <AcoesEtiquetaVolta
                      troca={troca}
                      aberta={acao?.trocaId === troca.id && acao.etiqueta}
                      buscando={envio.salvando && envio.trocaId === troca.id}
                      onGerar={() => abrirEtiqueta(troca)}
                      onBuscarRastreio={() => gerarVolta(troca.id)}
                    />
                    {!acao && envio.erro && envio.trocaId === troca.id && (
                      <p className="admin-pedido__alerta admin-trocas__detalhe" role="alert">
                        {envio.erro}
                      </p>
                    )}
                  </td>
                </tr>
                {acao?.trocaId === troca.id && acao.etiqueta && (
                  <tr className="admin-trocas__confirmar">
                    <td colSpan={6}>
                      <div className="admin-trocas__form" role="group" aria-label="Confirmar compra da etiqueta de volta">
                        <p className="admin-pedido__texto">
                          A etiqueta de volta sai do saldo do Melhor Envio (perto de{' '}
                          {formatarPreco(troca.etiquetaVolta.valorEstimado)}, o frete da ida), da cliente para a loja.
                          Gerar agora?
                        </p>
                        <div className="admin-pedido__botoes">
                          <button
                            type="button"
                            className="botao botao--primario"
                            onClick={() => gerarVolta(troca.id)}
                            disabled={envio.salvando}
                          >
                            {envio.salvando ? 'Gerando etiqueta…' : 'Sim, gerar'}
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
                      </div>
                    </td>
                  </tr>
                )}
                {acao?.trocaId === troca.id && !acao.etiqueta && (
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

// Em que pé está a etiqueta de volta (#63), abaixo do status
function EtiquetaVolta({ troca }) {
  const etiqueta = troca.etiquetaVolta
  if (!etiqueta) return null
  if (etiqueta.situacao === 'GERADA') {
    return (
      <span className="admin-trocas__detalhe admin-trocas__volta">
        Etiqueta de volta gerada em {formatarDataHora(etiqueta.geradaEm)}
        <span className="admin-trocas__rastreio">
          {etiqueta.codigoRastreio ? `Rastreio ${etiqueta.codigoRastreio}` : 'Rastreio ainda não saiu'}
        </span>
      </span>
    )
  }
  if (troca.status !== 'APROVADA') return null
  if (etiqueta.situacao === 'POR_CONTA_DA_CLIENTE') {
    return <span className="admin-trocas__detalhe admin-trocas__volta">A cliente manda a peça por conta própria</span>
  }
  if (etiqueta.situacao === 'FALHOU') {
    return (
      <span className="admin-trocas__detalhe admin-trocas__volta" data-situacao="FALHOU">
        Etiqueta de volta com falha{etiqueta.erro ? `: ${etiqueta.erro}` : ''}
      </span>
    )
  }
  return <span className="admin-trocas__detalhe admin-trocas__volta">Sem etiqueta de volta</span>
}

// Botões da etiqueta de volta, abaixo das ações de status
function AcoesEtiquetaVolta({ troca, aberta, buscando, onGerar, onBuscarRastreio }) {
  const etiqueta = troca.etiquetaVolta
  if (!etiqueta) return null
  if (etiqueta.situacao === 'GERADA') {
    return (
      <span className="admin-trocas__botoes admin-trocas__volta">
        <a
          href={etiqueta.urlEtiqueta}
          target="_blank"
          rel="noopener noreferrer"
          className="admin-pedidos__acao admin-trocas__botao"
        >
          Imprimir etiqueta de volta
        </a>
        {!etiqueta.codigoRastreio && troca.status === 'APROVADA' && (
          <button
            type="button"
            className="admin-pedidos__acao admin-trocas__botao"
            onClick={onBuscarRastreio}
            disabled={buscando}
          >
            {buscando ? 'Buscando…' : 'Buscar rastreio'}
          </button>
        )}
      </span>
    )
  }
  if (troca.status !== 'APROVADA' || etiqueta.situacao === 'POR_CONTA_DA_CLIENTE') return null
  return (
    <span className="admin-trocas__botoes admin-trocas__volta">
      <button type="button" className="admin-pedidos__acao admin-trocas__botao" aria-expanded={aberta} onClick={onGerar}>
        {etiqueta.situacao === 'FALHOU' ? 'Tentar gerar de novo' : 'Gerar etiqueta de volta'}
      </button>
    </span>
  )
}
