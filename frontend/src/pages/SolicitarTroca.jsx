import { useEffect, useState } from 'react'
import { ApiError, buscarPedido, listarCategorias, listarPedidos, listarTrocas, solicitarTroca } from '../api.js'
import Footer from '../components/Footer.jsx'
import Nav from '../components/Nav.jsx'
import { contatoLoja, formatarData } from '../formato.js'
import { MOTIVOS_TROCA, POLITICA_TROCA, dentroDoPrazo, textoFrete, trocaAberta } from '../troca.js'
import useCarregar from '../useCarregar.js'
import './SolicitarTroca.css'

// Tela "Solicitação de Troca" do Figma (#27), em /trocas (link "Trocas e devoluções" do rodapé) ou
// /trocas?pedido={id} (botão "Solicitar troca" do detalhe do pedido na Minha Conta). Só com cliente logado
// (App.jsx). Lista os pedidos entregues ainda no prazo; a cliente escolhe o pedido, a peça, o motivo
// (os dois cartões, que dizem quem paga o frete de volta) e conta o que aconteceu.
function carregarDados(opcoes) {
  return Promise.all([listarPedidos(opcoes), listarTrocas(opcoes)]).then(([pedidos, trocas]) => ({
    pedidos: pedidos.filter((p) => p.status === 'ENTREGUE' && dentroDoPrazo(p.prazoTroca)),
    trocas,
  }))
}

export default function SolicitarTroca() {
  const [categorias, setCategorias] = useState([])
  const [pedidoInicial] = useState(() => Number(new URLSearchParams(window.location.search).get('pedido')) || null)
  const { carregando, dados, erro, tentarDeNovo } = useCarregar(carregarDados)
  const [enviada, setEnviada] = useState(null)

  useEffect(() => {
    document.title = 'Trocas e devoluções — Rosie'
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then(setCategorias)
      .catch(() => {}) // Sem categorias a Nav fica só com logo e ações
    return () => controle.abort()
  }, [])

  let conteudo
  if (enviada) {
    conteudo = <Enviada troca={enviada} />
  } else if (carregando) {
    conteudo = (
      <p className="troca__aviso" aria-busy="true">
        Carregando seus pedidos…
      </p>
    )
  } else if (erro) {
    conteudo = (
      <div className="troca__aviso" role="alert">
        <p>
          {erro.codigo === 'SEM_CONEXAO'
            ? 'Sem conexão com a loja. Confira a internet e tente de novo.'
            : 'Não foi possível carregar seus pedidos.'}
        </p>
        <button type="button" className="botao botao--contorno" onClick={tentarDeNovo}>
          Tentar de novo
        </button>
      </div>
    )
  } else if (dados.pedidos.length === 0) {
    conteudo = <SemPedidos pedidoInicial={pedidoInicial} />
  } else {
    conteudo = (
      <Formulario pedidos={dados.pedidos} trocas={dados.trocas} pedidoInicial={pedidoInicial} onEnviada={setEnviada} />
    )
  }

  return (
    <>
      <Nav categorias={categorias} />

      <main className="troca">
        <header className="troca__topo">
          <p className="eyebrow">Trocas e devoluções</p>
          <h1 className="titulo-2">{enviada ? 'Solicitação enviada' : 'Solicitar troca ou devolução'}</h1>
        </header>

        <div className="troca__corpo">{conteudo}</div>
      </main>

      <Footer />
    </>
  )
}

function SemPedidos({ pedidoInicial }) {
  const contato = contatoLoja(
    pedidoInicial ? `Olá! Quero falar sobre uma troca do pedido #${pedidoInicial}.` : 'Olá! Quero falar sobre uma troca.',
  )
  return (
    <div className="troca__aviso">
      <p>
        {pedidoInicial
          ? `O pedido #${pedidoInicial} não está no prazo de troca.`
          : 'Você não tem pedidos no prazo de troca.'}
      </p>
      <p className="troca__nota">
        A troca pode ser pedida até 7 dias depois da entrega. A peça veio com defeito e o prazo passou? Fale com a
        Rosie.
      </p>
      <div className="troca__acoes">
        <a className="botao botao--contorno" href="/minha-conta">
          Ver meus pedidos
        </a>
        <a className="botao botao--contorno" href={contato.href} target="_blank" rel="noopener noreferrer">
          {contato.texto}
        </a>
      </div>
    </div>
  )
}

function Formulario({ pedidos, trocas, pedidoInicial, onEnviada }) {
  const inicial = pedidos.some((p) => p.id === pedidoInicial)
    ? pedidoInicial
    : pedidos.length === 1
      ? pedidos[0].id
      : null
  const [pedidoId, setPedidoId] = useState(inicial)
  const [detalhe, setDetalhe] = useState({ id: null, pedido: null, erro: null })
  const [tentativa, setTentativa] = useState(0)
  const [escolha, setEscolha] = useState({ pedidoId: null, peca: '', quantidade: null })
  const [motivo, setMotivo] = useState(null)
  const [observacao, setObservacao] = useState('')
  const [envio, setEnvio] = useState({ enviando: false, erro: null })

  // As peças vêm do detalhe do pedido escolhido
  useEffect(() => {
    if (!pedidoId) return
    const controle = new AbortController()
    buscarPedido(pedidoId, { signal: controle.signal })
      .then((pedido) => setDetalhe({ id: pedidoId, pedido, erro: null }))
      .catch((erro) => {
        if (erro.name !== 'AbortError') setDetalhe({ id: pedidoId, pedido: null, erro })
      })
    return () => controle.abort()
  }, [pedidoId, tentativa])

  const carregandoPecas = pedidoId !== null && detalhe.id !== pedidoId
  const pecas = detalhe.id === pedidoId && detalhe.pedido ? pecasDoPedido(detalhe.pedido, trocas) : []
  const livres = pecas.filter((p) => !p.trocaPedida)
  // Pedido com uma peça só (que ainda dá para trocar) já vem com ela escolhida
  const chavePeca = escolha.pedidoId === pedidoId ? escolha.peca : livres.length === 1 ? livres[0].chave : ''
  const peca = pecas.find((p) => p.chave === chavePeca && !p.trocaPedida) ?? null
  const quantidade = escolha.pedidoId === pedidoId && escolha.quantidade ? escolha.quantidade : peca?.quantidade

  const escolhido = pedidos.find((p) => p.id === pedidoId)
  // Pedido com todas as peças já em troca: não há o que enviar
  const semPecaLivre = detalhe.id === pedidoId && detalhe.pedido !== null && livres.length === 0
  const urlInvalida = pedidoInicial !== null && !pedidos.some((p) => p.id === pedidoInicial)

  function escolherPedido(valor) {
    setPedidoId(valor ? Number(valor) : null)
    setEnvio({ enviando: false, erro: null })
  }

  async function enviar(evento) {
    evento.preventDefault()
    if (envio.enviando) return
    const falta = !pedidoId
      ? 'Escolha o pedido.'
      : !peca
        ? 'Escolha a peça que você quer trocar.'
        : !motivo
          ? 'Escolha o motivo da troca.'
          : null
    if (falta) {
      setEnvio({ enviando: false, erro: falta })
      return
    }

    setEnvio({ enviando: true, erro: null })
    try {
      const troca = await solicitarTroca({
        pedidoId,
        produtoId: peca.produtoId,
        tamanho: peca.tamanho,
        quantidade,
        motivo,
        observacao: observacao.trim() || undefined,
      })
      window.scrollTo({ top: 0 })
      onEnviada(troca)
    } catch (erro) {
      setEnvio({
        enviando: false,
        erro:
          erro instanceof ApiError && erro.codigo === 'SEM_CONEXAO'
            ? 'Sem conexão com a loja. Confira a internet e tente de novo.'
            : erro instanceof ApiError && erro.status < 500
              ? erro.message
              : 'Não foi possível enviar a solicitação agora. Tente de novo em instantes.',
      })
    }
  }

  return (
    <form className="troca__form" onSubmit={enviar} noValidate>
      {urlInvalida && (
        <p className="troca__nota" role="status">
          O pedido #{pedidoInicial} não está no prazo de troca. Escolha outro pedido abaixo.
        </p>
      )}

      <label className="campo">
        <span className="eyebrow">Pedido</span>
        <span className="troca__select">
          <select
            className="campo__caixa"
            value={pedidoId ?? ''}
            onChange={(e) => escolherPedido(e.target.value)}
          >
            <option value="">Selecionar pedido...</option>
            {pedidos.map((p) => (
              <option key={p.id} value={p.id}>
                Pedido #{p.id} · troca até {formatarData(p.prazoTroca)}
              </option>
            ))}
          </select>
        </span>
      </label>

      {pedidoId !== null && (
        <PecaDoPedido
          carregando={carregandoPecas}
          erro={detalhe.id === pedidoId ? detalhe.erro : null}
          onTentarDeNovo={() => setTentativa((t) => t + 1)}
          pecas={pecas}
          livres={livres}
          chavePeca={chavePeca}
          peca={peca}
          quantidade={quantidade}
          onEscolher={(chave, qtd) => setEscolha({ pedidoId, peca: chave, quantidade: qtd })}
        />
      )}

      {!semPecaLivre && (
        <>
          <fieldset className="troca__motivos">
            <legend className="eyebrow">Motivo da troca</legend>
            <div className="troca__cartoes">
              {MOTIVOS_TROCA.map((m) => (
                <label key={m.id} className={`troca__motivo${motivo === m.id ? ' troca__motivo--escolhido' : ''}`}>
                  <input
                    type="radio"
                    name="motivo"
                    value={m.id}
                    checked={motivo === m.id}
                    onChange={() => setMotivo(m.id)}
                    className="troca__radio"
                  />
                  <span className="troca__motivo-nome">{m.nome}</span>
                  <span className="troca__motivo-frete">{m.frete}</span>
                </label>
              ))}
            </div>
          </fieldset>

          <label className="campo">
            <span className="eyebrow">Descreva o problema</span>
            <textarea
              className="campo__caixa troca__descricao"
              rows={4}
              maxLength={1000}
              placeholder="Conte com detalhes o que aconteceu..."
              value={observacao}
              onChange={(e) => setObservacao(e.target.value)}
            />
          </label>

          {envio.erro && (
            <p className="troca__erro" role="alert">
              {envio.erro}
            </p>
          )}

          <button type="submit" className="botao botao--primario troca__enviar" disabled={envio.enviando}>
            {envio.enviando ? 'Enviando…' : 'Enviar solicitação'}
          </button>
        </>
      )}

      <p className="troca__nota">
        {POLITICA_TROCA} Pedidos podem ser trocados até 7 dias depois da entrega
        {escolhido ? ` (este, até ${formatarData(escolhido.prazoTroca)})` : ''}.
      </p>
    </form>
  )
}

function PecaDoPedido({ carregando, erro, onTentarDeNovo, pecas, livres, chavePeca, peca, quantidade, onEscolher }) {
  if (carregando) {
    return (
      <p className="troca__nota" aria-busy="true">
        Carregando as peças do pedido…
      </p>
    )
  }
  if (erro) {
    return (
      <div className="troca__aviso" role="alert">
        <p>Não foi possível carregar as peças do pedido.</p>
        <button type="button" className="botao botao--contorno" onClick={onTentarDeNovo}>
          Tentar de novo
        </button>
      </div>
    )
  }
  if (livres.length === 0) {
    return (
      <p className="troca__nota" role="status">
        Você já pediu a troca de todas as peças deste pedido. Acompanhe em{' '}
        <a className="troca__link" href="/minha-conta/trocas">
          Minhas trocas
        </a>
        .
      </p>
    )
  }

  return (
    <div className="troca__peca">
      <label className="campo troca__peca-escolha">
        <span className="eyebrow">Peça</span>
        <span className="troca__select">
          <select
            className="campo__caixa"
            value={chavePeca}
            onChange={(e) => onEscolher(e.target.value, null)}
          >
            <option value="">Selecionar peça...</option>
            {pecas.map((p) => (
              <option key={p.chave} value={p.chave} disabled={p.trocaPedida}>
                {p.nome} · tamanho {p.tamanho}
                {p.trocaPedida ? ' (troca já pedida)' : ''}
              </option>
            ))}
          </select>
        </span>
      </label>
      {peca && peca.quantidade > 1 && (
        <label className="campo troca__quantidade">
          <span className="eyebrow">Quantidade</span>
          <span className="troca__select">
            <select
              className="campo__caixa"
              value={quantidade}
              onChange={(e) => onEscolher(chavePeca, Number(e.target.value))}
            >
              {Array.from({ length: peca.quantidade }, (_, i) => i + 1).map((n) => (
                <option key={n} value={n}>
                  {n} {n === 1 ? 'peça' : 'peças'}
                </option>
              ))}
            </select>
          </span>
        </label>
      )}
    </div>
  )
}

function Enviada({ troca }) {
  return (
    <div className="troca__aviso troca__enviada" role="status">
      <p>
        Recebemos o seu pedido de troca de {troca.nome} (tamanho {troca.tamanho}) do pedido #{troca.pedidoId}.
      </p>
      <p className="troca__frete">{textoFrete(troca.freteConta)}.</p>
      <p>A Rosie vai analisar e responder por aqui. Você acompanha o andamento em Minhas trocas.</p>
      <div className="troca__acoes">
        <a className="botao botao--primario" href="/minha-conta/trocas">
          Ver minhas trocas
        </a>
        <a className="botao botao--contorno" href="/">
          Voltar à loja
        </a>
      </div>
    </div>
  )
}

// Peças do pedido (produto + tamanho), marcando as que já têm troca em andamento
function pecasDoPedido(pedido, trocas) {
  return pedido.itens.map((item) => ({
    chave: `${item.produtoId}-${item.tamanho}`,
    produtoId: item.produtoId,
    nome: item.nome,
    tamanho: item.tamanho,
    quantidade: item.quantidade,
    trocaPedida: trocas.some(
      (t) =>
        t.pedidoId === pedido.id && t.produtoId === item.produtoId && t.tamanho === item.tamanho && trocaAberta(t),
    ),
  }))
}
