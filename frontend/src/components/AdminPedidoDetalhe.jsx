import { useEffect, useState } from 'react'
import { buscarPedidoAdmin, gerarEtiqueta, marcarEntregue } from '../api.js'
import { formatarData, formatarPreco, mascararTelefone } from '../formato.js'
import { hojeNaLoja } from '../troca.js'
import { FORMA_PAGAMENTO, STATUS_PEDIDO, formatarDataHora, mensagemDeErro } from './pedidosAdmin.js'
import './AdminPedidos.css'

function formatarCep(cep) {
  return cep?.length === 8 ? `${cep.slice(0, 5)}-${cep.slice(5)}` : cep
}

// Detalhe do pedido no painel (#25): peças, entrega, cliente, pagamento e a etiqueta do Melhor Envio.
// "Gerar etiqueta" pede confirmação, porque compra a etiqueta com o saldo da conta do Melhor Envio; depois
// vira "Imprimir etiqueta", com o código de rastreio. Enviado, a Roseane marca quando a peça chegou (#27).
export default function AdminPedidoDetalhe({ id }) {
  const [pedido, setPedido] = useState(null)
  const [erro, setErro] = useState(null)
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    const controle = new AbortController()
    buscarPedidoAdmin(id, { signal: controle.signal })
      .then((dados) => {
        setPedido(dados)
        setErro(null)
      })
      .catch((e) => {
        if (e.name === 'AbortError') return
        setErro(e.status === 404 ? 'Pedido não encontrado.' : mensagemDeErro(e, 'Não foi possível carregar o pedido.'))
      })
    return () => controle.abort()
  }, [id, tentativa])

  function tentarDeNovo() {
    setErro(null)
    setPedido(null)
    setTentativa((t) => t + 1)
  }

  return (
    <section className="admin-pedido">
      <a href="/admin/pedidos" className="rotulo admin-pedido__voltar">
        ← Pedidos
      </a>

      {erro ? (
        <div className="admin-pedidos__estado" role="alert">
          <p>{erro}</p>
          <button type="button" className="botao botao--contorno" onClick={tentarDeNovo}>
            Tentar de novo
          </button>
        </div>
      ) : pedido === null ? (
        <p className="admin-pedidos__estado">Carregando pedido…</p>
      ) : (
        <>
          <header className="admin-pedido__topo">
            <h1 className="titulo-2">Pedido #{pedido.id}</h1>
            <span className="rotulo admin-pedidos__status" data-status={pedido.status}>
              {STATUS_PEDIDO[pedido.status] ?? pedido.status}
            </span>
            <p className="admin-pedido__data">Feito em {formatarDataHora(pedido.createdAt)}</p>
          </header>

          <div className="admin-pedido__grade">
            <Etiqueta pedido={pedido} onAtualizar={setPedido} />
            {(pedido.status === 'ENVIADO' || pedido.status === 'ENTREGUE') && (
              <Entrega pedido={pedido} onAtualizar={setPedido} />
            )}

            <section className="admin-pedido__bloco" aria-labelledby="pedido-pecas">
              <h2 id="pedido-pecas" className="rotulo">Peças</h2>
              <ul className="admin-pedido__itens">
                {pedido.itens.map((item) => (
                  <li key={`${item.produtoId}-${item.tamanho}`} className="admin-pedido__item">
                    {item.imagemPrincipal ? (
                      <img src={item.imagemPrincipal} alt="" className="admin-pedido__foto" />
                    ) : (
                      <span className="admin-pedido__foto" aria-hidden="true" />
                    )}
                    <span className="admin-pedido__item-texto">
                      <span className="admin-pedido__item-nome">{item.nome}</span>
                      <span>
                        Tamanho {item.tamanho} · {item.quantidade} × {formatarPreco(item.precoUnitario)}
                      </span>
                    </span>
                    <span className="admin-pedido__item-valor">{formatarPreco(item.subtotal)}</span>
                  </li>
                ))}
              </ul>
              <dl className="admin-pedido__valores">
                <div>
                  <dt>Subtotal</dt>
                  <dd>{formatarPreco(pedido.subtotal)}</dd>
                </div>
                <div>
                  <dt>Frete</dt>
                  <dd>{formatarPreco(pedido.valorFrete)}</dd>
                </div>
                <div className="admin-pedido__total">
                  <dt>Total</dt>
                  <dd>{formatarPreco(pedido.valorTotal)}</dd>
                </div>
              </dl>
            </section>

            <section className="admin-pedido__bloco" aria-labelledby="pedido-entrega">
              <h2 id="pedido-entrega" className="rotulo">Entrega</h2>
              <address className="admin-pedido__texto">
                {pedido.endereco.logradouro}, {pedido.endereco.numero}
                {pedido.endereco.complemento && ` · ${pedido.endereco.complemento}`}
                <br />
                {pedido.endereco.bairro} · {pedido.endereco.cidade}/{pedido.endereco.estado}
                <br />
                CEP {formatarCep(pedido.endereco.cep)}
              </address>
              <p className="admin-pedido__texto">
                {pedido.transportadora} {pedido.servicoFrete}
                {pedido.prazoFreteDias != null && ` · até ${pedido.prazoFreteDias} dias úteis após a postagem`}
              </p>
            </section>

            <section className="admin-pedido__bloco" aria-labelledby="pedido-cliente">
              <h2 id="pedido-cliente" className="rotulo">Cliente</h2>
              <p className="admin-pedido__texto">
                {pedido.cliente.nome}
                <br />
                <a href={`mailto:${pedido.cliente.email}`} className="admin-pedidos__acao">
                  {pedido.cliente.email}
                </a>
                {pedido.cliente.telefone && (
                  <>
                    <br />
                    {mascararTelefone(pedido.cliente.telefone)}
                  </>
                )}
              </p>
            </section>

            <section className="admin-pedido__bloco" aria-labelledby="pedido-pagamento">
              <h2 id="pedido-pagamento" className="rotulo">Pagamento</h2>
              <p className="admin-pedido__texto">
                {FORMA_PAGAMENTO[pedido.formaPagamento] ?? pedido.formaPagamento}
                {pedido.pagoEm && ` · aprovado em ${formatarDataHora(pedido.pagoEm)}`}
                {pedido.pagamentoId && (
                  <>
                    <br />
                    Mercado Pago nº {pedido.pagamentoId}
                  </>
                )}
              </p>
              {pedido.pendencia && <p className="admin-pedido__alerta">Pendência: {pedido.pendencia}</p>}
            </section>
          </div>
        </>
      )}
    </section>
  )
}

function Etiqueta({ pedido, onAtualizar }) {
  const [confirmando, setConfirmando] = useState(false)
  const [gerando, setGerando] = useState(false)
  const [erro, setErro] = useState(null)

  const temEtiqueta = Boolean(pedido.urlEtiqueta)
  const podeGerar = pedido.status === 'PAGO' && !temEtiqueta
  const semRastreio = temEtiqueta && !pedido.codigoRastreio

  async function gerar() {
    setGerando(true)
    setErro(null)
    try {
      onAtualizar(await gerarEtiqueta(pedido.id))
    } catch (e) {
      setErro(mensagemDeErro(e, 'Não foi possível gerar a etiqueta.'))
    } finally {
      setGerando(false)
      setConfirmando(false)
    }
  }

  let conteudo
  if (temEtiqueta) {
    conteudo = (
      <>
        <p className="admin-pedido__texto">
          Etiqueta gerada em {formatarDataHora(pedido.etiquetaGeradaEm)}.
        </p>
        {pedido.codigoRastreio ? (
          <p className="admin-pedido__rastreio">
            <span className="rotulo">Rastreio</span>
            <span className="admin-pedido__codigo">{pedido.codigoRastreio}</span>
          </p>
        ) : (
          <p className="admin-pedido__texto">O Melhor Envio ainda não devolveu o código de rastreio.</p>
        )}
        <div className="admin-pedido__botoes">
          <a
            href={pedido.urlEtiqueta}
            target="_blank"
            rel="noopener noreferrer"
            className="botao botao--primario"
          >
            Imprimir etiqueta
          </a>
          {semRastreio && (
            <button type="button" className="botao botao--contorno" onClick={gerar} disabled={gerando}>
              {gerando ? 'Buscando…' : 'Buscar rastreio'}
            </button>
          )}
        </div>
        <p className="admin-pedido__dica">Abre no Melhor Envio: entre com a conta da loja, se ele pedir.</p>
      </>
    )
  } else if (podeGerar) {
    conteudo = (
      <>
        <p className="admin-pedido__texto">
          {pedido.envioId
            ? 'A geração parou no meio. Tentar de novo continua de onde parou, sem comprar outra etiqueta.'
            : 'Confira as peças e o endereço antes de gerar.'}
        </p>
        {confirmando ? (
          <div className="admin-pedido__confirmar" role="group" aria-label="Confirmar compra da etiqueta">
            <p className="admin-pedido__texto">
              A etiqueta sai do saldo do Melhor Envio (perto de {formatarPreco(pedido.valorFrete)}). Gerar agora?
            </p>
            <div className="admin-pedido__botoes">
              <button type="button" className="botao botao--primario" onClick={gerar} disabled={gerando}>
                {gerando ? 'Gerando etiqueta…' : 'Sim, gerar'}
              </button>
              <button
                type="button"
                className="botao botao--contorno"
                onClick={() => setConfirmando(false)}
                disabled={gerando}
              >
                Cancelar
              </button>
            </div>
          </div>
        ) : (
          <div className="admin-pedido__botoes">
            <button type="button" className="botao botao--primario" onClick={() => setConfirmando(true)}>
              Gerar etiqueta
            </button>
          </div>
        )}
      </>
    )
  } else if (pedido.status === 'PAGO_COM_PENDENCIA') {
    conteudo = <p className="admin-pedido__texto">Resolva a pendência do pagamento antes de gerar a etiqueta.</p>
  } else {
    conteudo = <p className="admin-pedido__texto">A etiqueta fica disponível quando o pagamento for aprovado.</p>
  }

  return (
    <section className="admin-pedido__bloco admin-pedido__etiqueta" aria-labelledby="pedido-etiqueta">
      <h2 id="pedido-etiqueta" className="rotulo">Etiqueta de envio</h2>
      {conteudo}
      {erro && (
        <p className="admin-pedido__alerta" role="alert">
          {erro}
        </p>
      )}
    </section>
  )
}

// A peça chegou (#27): a Roseane confere o rastreio e marca a data da entrega (hoje, ou o dia em que o
// rastreio mostrou a entrega). Dela conta o prazo de 7 dias para a cliente pedir troca.
function Entrega({ pedido, onAtualizar }) {
  const hoje = hojeNaLoja()
  const [data, setData] = useState(hoje)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState(null)

  async function marcar(evento) {
    evento.preventDefault()
    setSalvando(true)
    setErro(null)
    try {
      onAtualizar(await marcarEntregue(pedido.id, data || undefined))
    } catch (e) {
      setErro(mensagemDeErro(e, 'Não foi possível marcar o pedido como entregue.'))
    } finally {
      setSalvando(false)
    }
  }

  if (pedido.status === 'ENTREGUE') {
    return (
      <section className="admin-pedido__bloco" aria-labelledby="pedido-entregue">
        <h2 id="pedido-entregue" className="rotulo">Entregue</h2>
        <p className="admin-pedido__texto">
          Entregue em {pedido.dataEntrega ? formatarData(pedido.dataEntrega) : '—'}. A cliente pode pedir troca até 7
          dias depois.
        </p>
      </section>
    )
  }

  return (
    <section className="admin-pedido__bloco admin-pedido__entrega" aria-labelledby="pedido-entrega-marcar">
      <h2 id="pedido-entrega-marcar" className="rotulo">Confirmar entrega</h2>
      <p className="admin-pedido__texto">
        Quando o rastreio mostrar que a peça chegou, marque aqui. O prazo de troca da cliente (7 dias) conta a partir
        dessa data.
      </p>
      <form className="admin-pedido__entrega-form" onSubmit={marcar}>
        <label className="campo admin-pedido__entrega-data">
          <span className="rotulo">Data da entrega</span>
          <input
            type="date"
            className="campo__caixa"
            value={data}
            max={hoje}
            min={pedido.etiquetaGeradaEm?.slice(0, 10)}
            onChange={(e) => setData(e.target.value)}
            required
          />
        </label>
        <button type="submit" className="botao botao--primario" disabled={salvando}>
          {salvando ? 'Salvando…' : 'Marcar como entregue'}
        </button>
      </form>
      {erro && (
        <p className="admin-pedido__alerta" role="alert">
          {erro}
        </p>
      )}
    </section>
  )
}
