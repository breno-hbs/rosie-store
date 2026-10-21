import { useState } from 'react'
import { buscarPedido, linkPagamento, listarPedidos } from '../api.js'
import useCarregar from '../useCarregar.js'
import ContaAviso from './ContaAviso.jsx'
import { formatarData, formatarPreco, mascararCep, rotuloStatusPedido, statusEncerrado } from '../formato.js'
import { dentroDoPrazo } from '../troca.js'

// Aba "Meus Pedidos" da Minha Conta (#26): a tabela do Figma (pedido, data, status, total, "Ver
// detalhes") ou, com pedidoId, o detalhe do pedido (/minha-conta/pedidos/{id}).
export default function ContaPedidos({ pedidoId }) {
  return pedidoId ? <DetalhePedido id={pedidoId} /> : <ListaPedidos />
}

// Selo do status (borda de linha; cancelado e recusado em fumaça, como "Cancelado" no Figma)
function SeloStatus({ status }) {
  return (
    <span className={`conta__selo${statusEncerrado(status) ? ' conta__selo--encerrado' : ''}`}>
      {rotuloStatusPedido(status)}
    </span>
  )
}

function ListaPedidos() {
  const { carregando, dados: pedidos, erro, tentarDeNovo } = useCarregar(listarPedidos)

  return (
    <section className="conta__secao" aria-labelledby="titulo-pedidos">
      <h1 className="titulo-2" id="titulo-pedidos">
        Meus Pedidos
      </h1>

      {carregando || erro ? (
        <ContaAviso
          carregando={carregando}
          erro={erro}
          mensagemErro="Não foi possível carregar seus pedidos."
          onTentarDeNovo={tentarDeNovo}
        />
      ) : pedidos.length === 0 ? (
        <div className="conta__aviso">
          <p>Você ainda não fez pedidos.</p>
          <a className="botao botao--contorno" href="/">
            Ver a loja
          </a>
        </div>
      ) : (
        <table className="conta__pedidos">
          <thead>
            <tr className="rotulo">
              <th scope="col">Pedido</th>
              <th scope="col">Data</th>
              <th scope="col">Status</th>
              <th scope="col">Total</th>
              <th scope="col">
                <span className="conta__oculto">Detalhes</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {pedidos.map((pedido) => (
              <tr key={pedido.id}>
                <th scope="row" className="conta__pedido-numero">
                  #{pedido.id}
                </th>
                <td className="conta__pedido-data">{formatarData(pedido.createdAt)}</td>
                <td className="conta__pedido-status">
                  <SeloStatus status={pedido.status} />
                </td>
                <td className="conta__pedido-total">{formatarPreco(pedido.valorTotal)}</td>
                <td className="conta__pedido-detalhes">
                  <a className="conta__link" href={`/minha-conta/pedidos/${pedido.id}`}>
                    Ver detalhes<span className="conta__oculto"> do pedido #{pedido.id}</span>
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

// Rastreio: o link que a etiqueta (#25) guardar no pedido, se vier; senão o Melhor Rastreio, que
// acompanha os envios comprados pelo Melhor Envio
function linkRastreio(pedido) {
  return pedido.urlRastreio || `https://www.melhorrastreio.com.br/rastreio/${encodeURIComponent(pedido.codigoRastreio)}`
}

// O que a cliente precisa saber sobre a entrega em cada momento do pedido
function textoEntrega(status) {
  switch (status) {
    case 'AGUARDANDO_PAGAMENTO':
      return 'O pedido é preparado assim que o pagamento for confirmado.'
    case 'PAGAMENTO_RECUSADO':
      return 'O pagamento não foi aprovado. Você pode tentar de novo pelo botão abaixo.'
    case 'PAGO':
      return 'Pagamento confirmado. O código de rastreio aparece aqui quando o pedido for enviado.'
    case 'PAGO_COM_PENDENCIA':
      return 'Pagamento confirmado. A Rosie vai falar com você para combinar o envio.'
    case 'CANCELADO':
      return 'Este pedido foi cancelado.'
    default:
      return null
  }
}

function DetalhePedido({ id }) {
  const [carregar] = useState(() => (opcoes) => buscarPedido(id, opcoes))
  const { carregando, dados: pedido, erro, tentarDeNovo } = useCarregar(carregar)

  return (
    <section className="conta__secao" aria-labelledby="titulo-pedido">
      <a className="conta__link conta__voltar" href="/minha-conta">
        ← Meus pedidos
      </a>
      <h1 className="titulo-2" id="titulo-pedido">
        Pedido #{id}
      </h1>

      {carregando ? (
        <ContaAviso carregando />
      ) : erro?.status === 404 ? (
        <div className="conta__aviso" role="alert">
          <p>Pedido não encontrado.</p>
          <a className="botao botao--contorno" href="/minha-conta">
            Ver meus pedidos
          </a>
        </div>
      ) : erro ? (
        <ContaAviso erro={erro} mensagemErro="Não foi possível carregar o pedido." onTentarDeNovo={tentarDeNovo} />
      ) : (
        <ConteudoPedido pedido={pedido} />
      )}
    </section>
  )
}

function ConteudoPedido({ pedido }) {
  const { endereco } = pedido
  const entrega = textoEntrega(pedido.status)

  return (
    <div className="conta__detalhe">
      <div className="conta__detalhe-topo">
        <SeloStatus status={pedido.status} />
        <p className="conta__nota">Feito em {formatarData(pedido.createdAt)}</p>
      </div>

      <div className="conta__bloco">
        <h2 className="eyebrow">Entrega</h2>
        {pedido.codigoRastreio ? (
          <p className="conta__texto">
            Código de rastreio: <strong className="conta__rastreio">{pedido.codigoRastreio}</strong>
            <br />
            <a className="conta__link" href={linkRastreio(pedido)} target="_blank" rel="noopener noreferrer">
              Acompanhar a entrega
            </a>
          </p>
        ) : (
          entrega && <p className="conta__texto">{entrega}</p>
        )}
        {pedido.urlPagamento && (
          <a className="botao botao--primario conta__pagar" href={linkPagamento(pedido.urlPagamento)}>
            Pagar agora
          </a>
        )}
        {pedido.status === 'ENTREGUE' && <Troca pedido={pedido} />}
      </div>

      <div className="conta__bloco">
        <h2 className="eyebrow">Peças</h2>
        <ul className="conta__itens">
          {pedido.itens.map((item) => (
            <li key={`${item.produtoId}-${item.tamanho}`} className="conta__item">
              <a className="conta__item-foto" href={`/produtos/${item.produtoId}`} tabIndex={-1} aria-hidden="true">
                {item.imagemPrincipal && <img src={item.imagemPrincipal} alt="" loading="lazy" />}
              </a>
              <div className="conta__item-info">
                <a className="conta__item-nome" href={`/produtos/${item.produtoId}`}>
                  {item.nome}
                </a>
                <span className="conta__nota">
                  Tamanho {item.tamanho} · {item.quantidade} {item.quantidade === 1 ? 'peça' : 'peças'} ·{' '}
                  {formatarPreco(item.precoUnitario)}
                </span>
              </div>
              <span className="conta__item-preco">{formatarPreco(item.subtotal)}</span>
            </li>
          ))}
        </ul>
      </div>

      <div className="conta__colunas">
        <div className="conta__bloco">
          <h2 className="eyebrow">Endereço</h2>
          <p className="conta__texto">
            {endereco.logradouro}, {endereco.numero}
            {endereco.complemento ? ` — ${endereco.complemento}` : ''}
            <br />
            {endereco.bairro}, {endereco.cidade}/{endereco.estado}
            <br />
            CEP {mascararCep(endereco.cep)}
          </p>
          {pedido.servicoFrete && (
            <p className="conta__nota">
              {pedido.transportadora === pedido.servicoFrete
                ? pedido.servicoFrete
                : `${pedido.transportadora} · ${pedido.servicoFrete}`}
              {pedido.prazoFreteDias != null &&
                ` · até ${pedido.prazoFreteDias} ${pedido.prazoFreteDias === 1 ? 'dia útil' : 'dias úteis'} após o envio`}
            </p>
          )}
        </div>

        <div className="conta__bloco">
          <h2 className="eyebrow">Resumo</h2>
          <dl className="conta__valores">
            <div className="conta__linha">
              <dt>Subtotal</dt>
              <dd>{formatarPreco(pedido.subtotal)}</dd>
            </div>
            <div className="conta__linha">
              <dt>Frete</dt>
              <dd>{formatarPreco(pedido.valorFrete)}</dd>
            </div>
            <div className="conta__linha conta__linha--total">
              <dt>Total</dt>
              <dd>{formatarPreco(pedido.valorTotal)}</dd>
            </div>
          </dl>
          {pedido.formaPagamento && (
            <p className="conta__nota">
              Pagamento por {pedido.formaPagamento === 'PIX' ? 'PIX' : 'cartão de crédito'}
            </p>
          )}
        </div>
      </div>
    </div>
  )
}

// Pedido entregue (#27): a data da entrega e, dentro do prazo, o botão para a tela de troca
function Troca({ pedido }) {
  const noPrazo = dentroDoPrazo(pedido.prazoTroca)
  return (
    <>
      {pedido.dataEntrega && <p className="conta__texto">Entregue em {formatarData(pedido.dataEntrega)}.</p>}
      {pedido.prazoTroca && (
        <p className="conta__nota">
          {noPrazo
            ? `Defeito ou não serviu? Você pode pedir troca até ${formatarData(pedido.prazoTroca)}.`
            : `O prazo para pedir troca terminou em ${formatarData(pedido.prazoTroca)}.`}
        </p>
      )}
      {noPrazo && (
        <a className="botao botao--contorno conta__pagar" href={`/trocas?pedido=${pedido.id}`}>
          Solicitar troca
        </a>
      )}
    </>
  )
}
