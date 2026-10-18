import { useEffect, useState } from 'react'
import { buscarPedido, simularPagamento } from '../api.js'
import { formatarPreco } from '../formato.js'
import './PedidoRetorno.css'

// Só desenvolvimento (PAGAMENTO_SIMULADO=true no back-end): faz o papel da página do Mercado Pago.
// Cada botão registra um pagamento simulado, que a API processa como um webhook, e segue para a
// página de volta, como o Mercado Pago faria.
const ESCOLHAS = [
  { status: 'APROVADO', rotulo: 'Aprovar', destino: 'sucesso', primario: true },
  { status: 'PENDENTE', rotulo: 'Deixar pendente', destino: 'pendente' },
  { status: 'RECUSADO', rotulo: 'Recusar', destino: 'falha' },
  { status: 'CANCELADO', rotulo: 'Cancelar', destino: 'falha' },
]

export default function PagamentoSimulado({ id }) {
  const [resultado, setResultado] = useState({ pedido: null, erro: null })
  const [envio, setEnvio] = useState({ enviando: false, erro: null })

  useEffect(() => {
    document.title = 'Pagamento simulado — Rosie'
    const controle = new AbortController()
    buscarPedido(id, { signal: controle.signal })
      .then((pedido) => setResultado({ pedido, erro: null }))
      .catch((erro) => {
        if (erro.name !== 'AbortError') setResultado({ pedido: null, erro })
      })
    return () => controle.abort()
  }, [id])

  function pagar({ status, destino }) {
    setEnvio({ enviando: true, erro: null })
    simularPagamento(id, status)
      .then(() => window.location.assign(`/pedido/${id}/${destino}`))
      .catch((erro) => setEnvio({ enviando: false, erro }))
  }

  const { pedido, erro } = resultado

  return (
    <main className="retorno retorno--simulado">
      <section className="retorno__status">
        <p className="eyebrow">Ambiente de desenvolvimento</p>
        <h1 className="titulo-2">Pagamento simulado</h1>
        {erro ? (
          <p className="retorno__texto" role="alert">
            {erro.status === 404 ? 'Pedido não encontrado.' : 'Não foi possível carregar o pedido.'}
          </p>
        ) : !pedido ? (
          <p className="retorno__texto" aria-busy="true">
            Carregando o pedido…
          </p>
        ) : (
          <>
            <p className="retorno__texto">
              Pedido #{pedido.id} · {pedido.formaPagamento === 'PIX' ? 'PIX' : 'Cartão de crédito'} ·{' '}
              {formatarPreco(pedido.valorTotal)}. Nada é cobrado: o servidor está com PAGAMENTO_SIMULADO ligado.
            </p>
            <div className="retorno__acoes">
              {ESCOLHAS.map((escolha) => (
                <button
                  key={escolha.status}
                  type="button"
                  className={`botao ${escolha.primario ? 'botao--primario' : 'botao--contorno'}`}
                  disabled={envio.enviando}
                  onClick={() => pagar(escolha)}
                >
                  {escolha.rotulo}
                </button>
              ))}
            </div>
            {envio.erro && (
              <p className="retorno__texto" role="alert">
                {envio.erro.status === 404
                  ? 'O pagamento simulado está desligado neste servidor.'
                  : envio.erro.message}
              </p>
            )}
          </>
        )}
      </section>
    </main>
  )
}
