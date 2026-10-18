import { useEffect, useState } from 'react'
import { buscarCarrinho, buscarPedido, listarCategorias } from '../api.js'
import Nav from '../components/Nav.jsx'
import Footer from '../components/Footer.jsx'
import { formatarPreco, linkWhatsApp, mascararCep } from '../formato.js'
import './PedidoRetorno.css'

// Páginas de volta do Mercado Pago (#24): /pedido/{id}/sucesso, /pendente e /falha. O que aparece vem
// do status do pedido na API (que só o webhook muda), não da página em que o Mercado Pago devolveu a
// cliente. Enquanto o pedido espera o pagamento, consulta de novo a cada 4 s por até 2 minutos.
const INTERVALO_MS = 4000
const MAX_CONSULTAS = 30
const PAGO = ['PAGO', 'ENVIADO', 'ENTREGUE']

export default function PedidoRetorno({ id, etapa }) {
  const [categorias, setCategorias] = useState([])
  const [consulta, setConsulta] = useState(0)
  const [resultado, setResultado] = useState({ chave: null, pedido: null, erro: null })

  // Só a primeira consulta (ou uma nova tentativa depois de erro) mostra "carregando"
  const carregando = resultado.chave !== consulta && resultado.pedido === null

  useEffect(() => {
    document.title = 'Seu pedido — Rosie'
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then(setCategorias)
      .catch(() => {})
    return () => controle.abort()
  }, [])

  useEffect(() => {
    const controle = new AbortController()
    buscarPedido(id, { signal: controle.signal })
      .then((pedido) => {
        setResultado({ chave: consulta, pedido, erro: null })
        // Pago: as peças saíram da sacola, a Nav precisa saber
        if (PAGO.includes(pedido.status) || pedido.status === 'PAGO_COM_PENDENCIA') {
          buscarCarrinho({ silencioso: true }).catch(() => {})
        }
      })
      .catch((erro) => {
        if (erro.name === 'AbortError') return
        // Falha numa consulta de acompanhamento não apaga o pedido da tela
        setResultado((atual) => (atual.pedido ? { ...atual, chave: consulta } : { chave: consulta, pedido: null, erro }))
      })
    return () => controle.abort()
  }, [id, consulta])

  const esperando = resultado.pedido?.status === 'AGUARDANDO_PAGAMENTO'
  useEffect(() => {
    if (!esperando || resultado.chave !== consulta || consulta >= MAX_CONSULTAS) return
    const espera = setTimeout(() => setConsulta((c) => c + 1), INTERVALO_MS)
    return () => clearTimeout(espera)
  }, [esperando, resultado.chave, consulta])

  return (
    <>
      <Nav categorias={categorias} />

      <main className="retorno">
        {carregando ? (
          <p className="retorno__aviso" aria-busy="true">
            Carregando o pedido…
          </p>
        ) : resultado.erro ? (
          <div className="retorno__aviso" role="alert">
            <p>
              {resultado.erro.status === 404 ? 'Não encontramos este pedido.' : 'Não foi possível carregar o pedido.'}
            </p>
            {resultado.erro.status !== 404 && (
              <button type="button" className="botao botao--contorno" onClick={() => setConsulta((c) => c + 1)}>
                Tentar de novo
              </button>
            )}
          </div>
        ) : (
          <Pedido pedido={resultado.pedido} etapa={etapa} atualizando={esperando && consulta < MAX_CONSULTAS} />
        )}
      </main>

      <Footer />
    </>
  )
}

function situacao(pedido, etapa) {
  const pix = pedido.formaPagamento === 'PIX'
  switch (pedido.status) {
    case 'PAGO':
    case 'ENVIADO':
    case 'ENTREGUE':
      return {
        titulo: 'Pedido confirmado',
        texto: 'Recebemos o seu pagamento. Vamos separar as peças e avisar quando o pedido sair para entrega.',
      }
    case 'PAGO_COM_PENDENCIA':
      return {
        titulo: 'Pagamento recebido',
        texto:
          'Recebemos o seu pagamento. Precisamos conferir a separação de uma das peças e vamos falar com você pelo e-mail ou telefone do cadastro.',
        whatsapp: true,
      }
    case 'PAGAMENTO_RECUSADO':
      return {
        titulo: 'Pagamento não aprovado',
        texto: 'O Mercado Pago não aprovou o pagamento. Você pode tentar de novo, se quiser com outro cartão.',
        acao: 'Tentar de novo',
      }
    case 'CANCELADO':
      return {
        titulo: 'Pedido cancelado',
        texto: 'O pagamento foi cancelado ou expirou. As peças continuam na sua sacola.',
        sacola: true,
      }
    default:
      // AGUARDANDO_PAGAMENTO: o webhook ainda não chegou ou a cliente ainda não pagou
      if (etapa === 'sucesso') {
        return {
          titulo: 'Confirmando o pagamento',
          texto: 'O Mercado Pago está confirmando o seu pagamento. Esta página se atualiza sozinha.',
        }
      }
      if (etapa === 'falha') {
        return {
          titulo: 'Pagamento não concluído',
          texto: 'O pagamento não foi concluído. Você pode tentar de novo.',
          acao: 'Tentar de novo',
        }
      }
      return {
        titulo: 'Aguardando pagamento',
        texto: pix
          ? 'Assim que o PIX for pago, o pedido é confirmado. Se ainda não pagou, o QR Code está no Mercado Pago.'
          : 'O pagamento está em análise no Mercado Pago. Esta página se atualiza sozinha.',
        acao: pix ? 'Ver o PIX' : null,
      }
  }
}

function Pedido({ pedido, etapa, atualizando }) {
  const { titulo, texto, acao, sacola, whatsapp } = situacao(pedido, etapa)
  const contato = whatsapp ? linkWhatsApp(`Olá! Sobre o meu pedido #${pedido.id}`) : undefined
  const { endereco } = pedido

  return (
    <div className="retorno__conteudo">
      <section className="retorno__status" aria-live="polite">
        <p className="eyebrow">Pedido #{pedido.id}</p>
        <h1 className="titulo-2">{titulo}</h1>
        <p className="retorno__texto">{texto}</p>
        {atualizando && <p className="retorno__nota">Atualizando…</p>}
        <div className="retorno__acoes">
          {acao && pedido.urlPagamento && (
            <a className="botao botao--primario" href={pedido.urlPagamento}>
              {acao}
            </a>
          )}
          {contato && (
            <a className="botao botao--contorno" href={contato} target="_blank" rel="noreferrer">
              Falar no WhatsApp
            </a>
          )}
          {sacola ? (
            <a className="botao botao--contorno" href="/carrinho">
              Ver a sacola
            </a>
          ) : (
            <a className="botao botao--contorno" href="/">
              Continuar comprando
            </a>
          )}
        </div>
      </section>

      <aside className="retorno__resumo" aria-labelledby="titulo-resumo-pedido">
        <h2 className="titulo-3" id="titulo-resumo-pedido">
          Resumo do pedido
        </h2>
        <ul className="retorno__itens">
          {pedido.itens.map((item) => (
            <li key={`${item.produtoId}-${item.tamanho}`} className="retorno__item">
              <span className="retorno__foto" aria-hidden="true">
                {item.imagemPrincipal && <img src={item.imagemPrincipal} alt="" loading="lazy" />}
              </span>
              <span className="retorno__item-info">
                <span className="retorno__item-nome">{item.nome}</span>
                <span className="retorno__nota">
                  Tam. {item.tamanho} · {item.quantidade} {item.quantidade === 1 ? 'peça' : 'peças'}
                </span>
              </span>
              <span className="retorno__item-preco">{formatarPreco(item.subtotal)}</span>
            </li>
          ))}
        </ul>
        <dl className="retorno__valores">
          <div className="retorno__linha">
            <dt>Subtotal</dt>
            <dd>{formatarPreco(pedido.subtotal)}</dd>
          </div>
          <div className="retorno__linha">
            <dt>Frete ({pedido.servicoFrete})</dt>
            <dd>{formatarPreco(pedido.valorFrete)}</dd>
          </div>
          <div className="retorno__linha retorno__linha--total">
            <dt>Total</dt>
            <dd>{formatarPreco(pedido.valorTotal)}</dd>
          </div>
        </dl>
        <div className="retorno__entrega">
          <p className="eyebrow">Entrega</p>
          <p className="retorno__nota">
            {endereco.logradouro}, {endereco.numero}
            {endereco.complemento ? ` — ${endereco.complemento}` : ''}
            <br />
            {endereco.bairro}, {endereco.cidade}/{endereco.estado} · {mascararCep(endereco.cep)}
            <br />
            {pedido.transportadora} {pedido.servicoFrete} · até {pedido.prazoFreteDias}{' '}
            {pedido.prazoFreteDias === 1 ? 'dia útil' : 'dias úteis'} depois do envio
          </p>
        </div>
      </aside>
    </div>
  )
}
