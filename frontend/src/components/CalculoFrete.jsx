import { useEffect, useState } from 'react'
import { calcularFrete, esquecerFrete, guardarFrete, lerFrete } from '../api.js'
import { formatarPreco, mascararCep, somenteDigitos } from '../formato.js'
import './CalculoFrete.css'

const LINK_BUSCA_CEP = 'https://buscacepinter.correios.com.br/app/endereco/index.php'

// Cálculo de frete no resumo do carrinho (#23). Cota no Melhor Envio pela API e guarda o CEP e a
// opção escolhida no sessionStorage (api.js: lerFrete) para o checkout (#24). Mudou o carrinho
// (quantidade ou subtotal), cota de novo com o mesmo CEP. onEscolher recebe a opção ou null.
export default function CalculoFrete({ carrinho, opcao, onEscolher }) {
  const [cep, setCep] = useState(() => mascararCep(lerFrete()?.cep ?? ''))
  // CEP a cotar (só dígitos); vez força uma nova cotação do mesmo CEP ("Calcular" de novo)
  const [pedido, setPedido] = useState(() => {
    const salvo = lerFrete()
    return salvo ? { cep: salvo.cep, vez: 0 } : null
  })
  const [aviso, setAviso] = useState(null)
  const [resultado, setResultado] = useState({ chave: null, opcoes: null, erro: null })

  // Mesmo padrão da tela do carrinho: enquanto o resultado não for deste pedido, está carregando
  const chave = pedido ? `${pedido.cep}|${pedido.vez}|${carrinho.quantidadeItens}|${carrinho.subtotal}` : null
  const carregando = chave !== null && resultado.chave !== chave

  useEffect(() => {
    if (!pedido) return
    const controle = new AbortController()
    calcularFrete(pedido.cep, { signal: controle.signal })
      .then(({ opcoes }) => {
        setResultado({ chave, opcoes, erro: null })
        // Mantém o serviço que ela já tinha escolhido, se ainda atende; senão, o mais barato
        const anterior = lerFrete()?.opcao
        const escolhida = opcoes.find((o) => o.id === anterior?.id) ?? opcoes[0] ?? null
        guardarFrete(pedido.cep, escolhida)
        onEscolher(escolhida)
      })
      .catch((erro) => {
        if (erro.name === 'AbortError') return
        setResultado({ chave, opcoes: null, erro })
        // CEP recusado não fica guardado; com o serviço fora, o CEP fica para tentar de novo
        if (erro.status === 400) esquecerFrete()
        else guardarFrete(pedido.cep, null)
        onEscolher(null)
      })
    return () => controle.abort()
  }, [chave, pedido, onEscolher])

  function calcular(evento) {
    evento.preventDefault()
    const digitos = somenteDigitos(cep)
    if (digitos.length !== 8) {
      setAviso('Digite os 8 números do CEP.')
      return
    }
    setAviso(null)
    setPedido((atual) => ({ cep: digitos, vez: (atual?.vez ?? 0) + 1 }))
  }

  function escolher(nova) {
    guardarFrete(pedido.cep, nova)
    onEscolher(nova)
  }

  const { opcoes, erro } = carregando ? { opcoes: null, erro: null } : resultado

  return (
    <section className="frete" aria-labelledby="titulo-frete">
      <form className="frete__form" onSubmit={calcular} noValidate>
        <label className="eyebrow" id="titulo-frete" htmlFor="frete-cep">
          Calcular frete
        </label>
        <div className="frete__linha">
          <input
            id="frete-cep"
            className="campo__caixa"
            inputMode="numeric"
            autoComplete="postal-code"
            placeholder="00000-000"
            maxLength={9}
            value={cep}
            onChange={(e) => setCep(mascararCep(e.target.value))}
            aria-invalid={aviso ? true : undefined}
            aria-describedby={aviso ? 'frete-aviso' : undefined}
          />
          <button type="submit" className="botao botao--contorno frete__calcular" disabled={carregando}>
            Calcular
          </button>
        </div>
        {aviso && (
          <p className="frete__mensagem" id="frete-aviso" role="alert">
            {aviso}
          </p>
        )}
        <a className="frete__link" href={LINK_BUSCA_CEP} target="_blank" rel="noreferrer">
          Não sei meu CEP
        </a>
      </form>

      <div aria-live="polite">
        {carregando ? (
          <p className="frete__mensagem" aria-busy="true">
            Calculando o frete…
          </p>
        ) : erro ? (
          <div className="frete__erro" role="alert">
            <p className="frete__mensagem">{mensagemErro(erro)}</p>
            {erro.status !== 400 && erro.status !== 409 && (
              <button
                type="button"
                className="frete__link"
                onClick={() => setPedido((atual) => ({ ...atual, vez: atual.vez + 1 }))}
              >
                Tentar de novo
              </button>
            )}
          </div>
        ) : opcoes?.length === 0 ? (
          <p className="frete__mensagem">Nenhuma transportadora entrega neste CEP. Confira os números ou fale com a gente.</p>
        ) : opcoes ? (
          <fieldset className="frete__opcoes">
            <legend className="frete__legenda">Escolha a entrega</legend>
            {opcoes.map((o) => (
              <label key={o.id} className="frete__opcao">
                <input
                  type="radio"
                  name="opcao-frete"
                  value={o.id}
                  checked={opcao?.id === o.id}
                  onChange={() => escolher(o)}
                />
                <span className="frete__servico">
                  {o.transportadora === o.servico ? o.servico : `${o.transportadora} · ${o.servico}`}
                </span>
                <span className="frete__preco">{formatarPreco(o.preco)}</span>
                <span className="frete__prazo">{textoPrazo(o.prazoDias)}</span>
              </label>
            ))}
          </fieldset>
        ) : null}
      </div>
    </section>
  )
}

function textoPrazo(dias) {
  return dias === 1 ? 'Até 1 dia útil' : `Até ${dias} dias úteis`
}

// 400 (CEP) e 409 (sacola sem peças disponíveis) já vêm com a mensagem pronta da API
function mensagemErro(erro) {
  if (erro.status === 400 || erro.status === 409) return erro.message
  if (erro.status === 503) return 'O cálculo de frete está indisponível no momento. Tente mais tarde.'
  return 'Não foi possível calcular o frete agora. Tente de novo em instantes.'
}
