import { useEffect, useState } from 'react'
import {
  buscarCarrinho,
  buscarEnderecoPorCep,
  calcularFrete,
  criarEndereco,
  criarPedido,
  guardarFrete,
  lerFrete,
  listarCategorias,
  listarEnderecos,
} from '../api.js'
import Nav from '../components/Nav.jsx'
import Footer from '../components/Footer.jsx'
import { formatarPreco, mascararCep, somenteDigitos } from '../formato.js'
import '../components/CalculoFrete.css'
import './Checkout.css'

// Tela "Checkout" do Figma (#24). Só com cliente logado (App.jsx manda para /entrar antes).
// Endereço (salvo ou novo, com o CEP preenchendo o resto pelo ViaCEP), frete cotado para o CEP do
// endereço, forma de pagamento e resumo. "Finalizar pedido" cria o pedido e leva ao Mercado Pago;
// o servidor refaz preços, estoque e frete, então os valores daqui são só para a cliente conferir.
export default function Checkout() {
  const [categorias, setCategorias] = useState([])
  const [tentativa, setTentativa] = useState(0)
  const [dados, setDados] = useState({ chave: null, carrinho: null, enderecos: null, erro: null })

  // Mesmo padrão do Carrinho: enquanto o resultado não for desta tentativa, está carregando
  const carregando = dados.chave !== tentativa

  useEffect(() => {
    document.title = 'Checkout — Rosie'
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then(setCategorias)
      .catch(() => {}) // Sem categorias a Nav fica só com logo e ações
    return () => controle.abort()
  }, [])

  useEffect(() => {
    const controle = new AbortController()
    Promise.all([buscarCarrinho({ signal: controle.signal }), listarEnderecos({ signal: controle.signal })])
      .then(([carrinho, enderecos]) => setDados({ chave: tentativa, carrinho, enderecos, erro: null }))
      .catch((erro) => {
        if (erro.name !== 'AbortError') setDados({ chave: tentativa, carrinho: null, enderecos: null, erro })
      })
    return () => controle.abort()
  }, [tentativa])

  return (
    <>
      <Nav categorias={categorias} />

      <main className="checkout">
        <h1 className="checkout__oculto">Finalizar compra</h1>

        {carregando ? (
          <p className="checkout__aviso" aria-busy="true">
            Carregando…
          </p>
        ) : dados.erro ? (
          <div className="checkout__aviso" role="alert">
            <p>Não foi possível carregar o checkout.</p>
            <button type="button" className="botao botao--contorno" onClick={() => setTentativa((t) => t + 1)}>
              Tentar de novo
            </button>
          </div>
        ) : dados.carrinho.itens.length === 0 ? (
          <div className="checkout__aviso">
            <p>Sua sacola está vazia.</p>
            <a className="botao botao--contorno" href="/">
              Ver a loja
            </a>
          </div>
        ) : dados.carrinho.temIndisponiveis ? (
          <div className="checkout__aviso" role="alert">
            <p>Algumas peças da sua sacola ficaram indisponíveis. Ajuste a sacola para continuar.</p>
            <a className="botao botao--contorno" href="/carrinho">
              Ver a sacola
            </a>
          </div>
        ) : (
          <FormularioCheckout carrinho={dados.carrinho} enderecosSalvos={dados.enderecos} />
        )}
      </main>

      <Footer />
    </>
  )
}

const ENDERECO_VAZIO = { cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', estado: '' }

const FORMAS_PAGAMENTO = [
  { valor: 'CARTAO', rotulo: 'Cartão de crédito' },
  { valor: 'PIX', rotulo: 'PIX' },
]

function FormularioCheckout({ carrinho, enderecosSalvos }) {
  const freteSalvo = lerFrete()
  const [enderecos, setEnderecos] = useState(enderecosSalvos)
  // Endereço escolhido: id de um salvo ou 'novo'. Começa pelo do CEP do carrinho, se houver.
  const [escolha, setEscolha] = useState(
    () => enderecosSalvos.find((e) => e.cep === freteSalvo?.cep)?.id ?? enderecosSalvos[0]?.id ?? 'novo',
  )
  const [novo, setNovo] = useState(() => ({ ...ENDERECO_VAZIO, cep: mascararCep(freteSalvo?.cep ?? '') }))
  const [forma, setForma] = useState('CARTAO')
  // Opção de frete escolhida, com o CEP para o qual foi cotada; vezFrete força uma nova cotação
  const [opcao, setOpcao] = useState(null)
  const [vezFrete, setVezFrete] = useState(0)
  const [envio, setEnvio] = useState({ enviando: false, erro: null })

  const enderecoSalvo = enderecos.find((e) => e.id === escolha)
  const cepNovo = somenteDigitos(novo.cep)
  const cepEntrega = escolha === 'novo' ? (cepNovo.length === 8 ? cepNovo : null) : (enderecoSalvo?.cep ?? null)
  // Opção cotada para outro CEP (o endereço mudou) não vale
  const frete = opcao && opcao.cep === cepEntrega ? opcao : null
  const total = Number(carrinho.subtotal) + (frete ? Number(frete.preco) : 0)
  const novoCompleto =
    cepNovo.length === 8 &&
    ['logradouro', 'numero', 'bairro', 'cidade'].every((campo) => novo[campo].trim() !== '') &&
    /^[A-Za-z]{2}$/.test(novo.estado.trim())
  const podeFinalizar = frete !== null && (escolha !== 'novo' || novoCompleto) && !envio.enviando

  async function finalizar(evento) {
    evento.preventDefault()
    if (!podeFinalizar) return
    setEnvio({ enviando: true, erro: null })
    try {
      let enderecoId = escolha
      if (escolha === 'novo') {
        const salvo = await criarEndereco({ ...novo, cep: cepNovo, estado: novo.estado.trim().toUpperCase() })
        // Se o pedido falhar depois, o endereço já fica na lista (não é salvo duas vezes)
        setEnderecos((atuais) => [...atuais, salvo])
        setEscolha(salvo.id)
        enderecoId = salvo.id
      }
      const pedido = await criarPedido({ enderecoId, servicoFreteId: frete.id, formaPagamento: forma })
      // Continua "enviando" enquanto o navegador vai para o Mercado Pago
      window.location.assign(pedido.urlPagamento)
    } catch (erro) {
      setEnvio({ enviando: false, erro })
      if (erro.codigo === 'FRETE_ALTERADO') setVezFrete((vez) => vez + 1)
    }
  }

  return (
    <form className="checkout__conteudo" onSubmit={finalizar} noValidate>
      <div className="checkout__etapas">
        <section className="checkout__secao" aria-labelledby="titulo-endereco">
          <h2 className="titulo-3" id="titulo-endereco">
            Endereço de entrega
          </h2>

          {enderecos.length > 0 && (
            <fieldset className="checkout__enderecos">
              <legend className="checkout__oculto">Escolha o endereço</legend>
              {enderecos.map((endereco) => (
                <label key={endereco.id} className="checkout__cartao">
                  <input
                    type="radio"
                    name="endereco"
                    checked={escolha === endereco.id}
                    onChange={() => setEscolha(endereco.id)}
                  />
                  <span className="checkout__endereco-texto">
                    <span className="checkout__endereco-rua">
                      {endereco.logradouro}, {endereco.numero}
                      {endereco.complemento ? ` — ${endereco.complemento}` : ''}
                    </span>
                    <span className="checkout__endereco-cidade">
                      {endereco.bairro}, {endereco.cidade}/{endereco.estado} · {mascararCep(endereco.cep)}
                    </span>
                  </span>
                </label>
              ))}
              <label className="checkout__cartao">
                <input type="radio" name="endereco" checked={escolha === 'novo'} onChange={() => setEscolha('novo')} />
                <span className="checkout__endereco-rua">Entregar em outro endereço</span>
              </label>
            </fieldset>
          )}

          {escolha === 'novo' && <NovoEndereco valor={novo} onMudar={setNovo} />}
        </section>

        <section className="checkout__secao" aria-labelledby="titulo-entrega">
          <h2 className="titulo-3" id="titulo-entrega">
            Entrega
          </h2>
          {cepEntrega ? (
            <OpcoesFrete cep={cepEntrega} vez={vezFrete} opcao={frete} onEscolher={setOpcao} />
          ) : (
            <p className="checkout__nota">Informe o CEP do endereço para ver as formas de entrega.</p>
          )}
        </section>

        <section className="checkout__secao" aria-labelledby="titulo-pagamento">
          <h2 className="titulo-3" id="titulo-pagamento">
            Pagamento
          </h2>
          <div className="checkout__formas" role="radiogroup" aria-labelledby="titulo-pagamento">
            {FORMAS_PAGAMENTO.map(({ valor, rotulo }) => (
              <button
                key={valor}
                type="button"
                role="radio"
                aria-checked={forma === valor}
                className="checkout__forma"
                onClick={() => setForma(valor)}
              >
                {rotulo}
              </button>
            ))}
          </div>
          <p className="checkout__nota">
            {forma === 'PIX'
              ? 'O QR Code do PIX aparece no Mercado Pago. O pedido é confirmado assim que o pagamento cair.'
              : 'Você digita o cartão no Mercado Pago, com parcelamento. A Rosie não vê nem guarda os dados do cartão.'}
          </p>
        </section>
      </div>

      <aside className="checkout__resumo" aria-labelledby="titulo-resumo">
        <h2 className="titulo-3" id="titulo-resumo">
          Resumo do pedido
        </h2>
        <p className="checkout__nota">
          {carrinho.quantidadeItens === 1 ? '1 peça' : `${carrinho.quantidadeItens} peças`} ·{' '}
          <a className="checkout__link" href="/carrinho">
            Editar sacola
          </a>
        </p>
        <dl className="checkout__valores">
          <div className="checkout__linha">
            <dt>Subtotal</dt>
            <dd>{formatarPreco(carrinho.subtotal)}</dd>
          </div>
          <div className="checkout__linha">
            <dt>{frete ? `Frete (${frete.servico})` : 'Frete'}</dt>
            <dd>{frete ? formatarPreco(frete.preco) : '—'}</dd>
          </div>
          <div className="checkout__linha checkout__linha--total">
            <dt>Total</dt>
            <dd>{formatarPreco(total)}</dd>
          </div>
        </dl>
        <button type="submit" className="botao botao--primario checkout__finalizar" disabled={!podeFinalizar}>
          {envio.enviando ? 'Indo para o pagamento…' : 'Finalizar pedido'}
        </button>
        {envio.erro && <ErroFinalizar erro={envio.erro} />}
        <p className="checkout__nota">O pagamento é feito no site do Mercado Pago.</p>
      </aside>
    </form>
  )
}

function ErroFinalizar({ erro }) {
  const naSacola = ['ESTOQUE_INSUFICIENTE', 'PRODUTO_INDISPONIVEL', 'CARRINHO_VAZIO'].includes(erro.codigo)
  return (
    <div className="checkout__erro" role="alert">
      <p>{mensagemErro(erro)}</p>
      {naSacola && (
        <a className="checkout__link" href="/carrinho">
          Ver a sacola
        </a>
      )}
    </div>
  )
}

// 400 e 409 já vêm com a mensagem pronta da API
function mensagemErro(erro) {
  if (erro.status === 400 || erro.status === 409) return erro.message
  if (erro.codigo === 'SEM_CONEXAO') return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  if (erro.codigo === 'PAGAMENTO_INDISPONIVEL') {
    return 'O pagamento pelo site está indisponível no momento. Tente mais tarde ou fale com a gente.'
  }
  if (erro.codigo === 'FRETE_INDISPONIVEL') return 'O cálculo de frete está indisponível no momento. Tente mais tarde.'
  return 'Não foi possível iniciar o pagamento agora. Tente de novo em instantes.'
}

// Campos do Figma: CEP e Rua; Número e Complemento; Bairro, Cidade e UF. Com os 8 números do CEP,
// rua, bairro, cidade e UF vêm do ViaCEP (a cliente pode corrigir).
function NovoEndereco({ valor, onMudar }) {
  const cep = somenteDigitos(valor.cep)
  const [busca, setBusca] = useState({ cep: null, resultado: null })
  const buscando = cep.length === 8 && busca.cep !== cep

  useEffect(() => {
    if (cep.length !== 8) return
    const controle = new AbortController()
    buscarEnderecoPorCep(cep, { signal: controle.signal })
      .then((encontrado) => {
        setBusca({ cep, resultado: encontrado ? 'ok' : 'nao-encontrado' })
        if (!encontrado) return
        onMudar((atual) => ({
          ...atual,
          logradouro: encontrado.logradouro || atual.logradouro,
          bairro: encontrado.bairro || atual.bairro,
          cidade: encontrado.cidade || atual.cidade,
          estado: encontrado.estado || atual.estado,
        }))
      })
      .catch((erro) => {
        // Sem ViaCEP a cliente preenche à mão
        if (erro.name !== 'AbortError') setBusca({ cep, resultado: 'erro' })
      })
    return () => controle.abort()
  }, [cep, onMudar])

  function campo(nome) {
    return {
      name: nome,
      value: valor[nome],
      onChange: (e) => onMudar((atual) => ({ ...atual, [nome]: e.target.value })),
    }
  }

  return (
    <div className="checkout__novo">
      <div className="checkout__fileira">
        <label className="campo checkout__campo--cep">
          <span className="eyebrow">CEP</span>
          <input
            className="campo__caixa"
            name="cep"
            inputMode="numeric"
            autoComplete="postal-code"
            placeholder="00000-000"
            maxLength={9}
            required
            value={valor.cep}
            onChange={(e) => onMudar((atual) => ({ ...atual, cep: mascararCep(e.target.value) }))}
          />
        </label>
        <label className="campo checkout__campo--rua">
          <span className="eyebrow">Rua</span>
          <input
            className="campo__caixa"
            autoComplete="address-line1"
            placeholder="Nome da rua"
            maxLength={200}
            required
            {...campo('logradouro')}
          />
        </label>
      </div>
      <div className="checkout__fileira">
        <label className="campo checkout__campo--numero">
          <span className="eyebrow">Número</span>
          <input className="campo__caixa" placeholder="000" maxLength={20} required {...campo('numero')} />
        </label>
        <label className="campo checkout__campo--complemento">
          <span className="eyebrow">Complemento</span>
          <input
            className="campo__caixa"
            autoComplete="address-line2"
            placeholder="Apto, bloco (opcional)"
            maxLength={100}
            {...campo('complemento')}
          />
        </label>
      </div>
      <div className="checkout__fileira">
        <label className="campo checkout__campo--bairro">
          <span className="eyebrow">Bairro</span>
          <input className="campo__caixa" placeholder="Bairro" maxLength={100} required {...campo('bairro')} />
        </label>
        <label className="campo checkout__campo--cidade">
          <span className="eyebrow">Cidade</span>
          <input
            className="campo__caixa"
            autoComplete="address-level2"
            placeholder="Cidade"
            maxLength={100}
            required
            {...campo('cidade')}
          />
        </label>
        <label className="campo checkout__campo--uf">
          <span className="eyebrow">UF</span>
          <input
            className="campo__caixa"
            autoComplete="address-level1"
            placeholder="SP"
            maxLength={2}
            required
            {...campo('estado')}
          />
        </label>
      </div>
      <p className="checkout__nota checkout__campo--aviso" aria-live="polite">
        {buscando
          ? 'Buscando o endereço do CEP…'
          : busca.cep === cep && busca.resultado === 'nao-encontrado'
            ? 'CEP não encontrado. Confira os números ou preencha o endereço.'
            : ''}
      </p>
    </div>
  )
}

// Formas de entrega para o CEP do endereço, cotadas no Melhor Envio pela API (como no carrinho).
// Mantém o serviço escolhido no carrinho se ele atende o CEP; senão, o mais barato. A escolha fica
// guardada (api.js: guardarFrete) e onEscolher recebe a opção com o CEP, ou null.
function OpcoesFrete({ cep, vez, opcao, onEscolher }) {
  const chave = `${cep}|${vez}`
  const [resultado, setResultado] = useState({ chave: null, opcoes: null, erro: null })
  const [tentativa, setTentativa] = useState(0)
  const carregando = resultado.chave !== `${chave}|${tentativa}`

  useEffect(() => {
    const atual = `${chave}|${tentativa}`
    const controle = new AbortController()
    calcularFrete(cep, { signal: controle.signal })
      .then(({ opcoes }) => {
        setResultado({ chave: atual, opcoes, erro: null })
        const anterior = lerFrete()?.opcao
        const escolhida = opcoes.find((o) => o.id === anterior?.id) ?? opcoes[0] ?? null
        guardarFrete(cep, escolhida)
        onEscolher(escolhida && { ...escolhida, cep })
      })
      .catch((erro) => {
        if (erro.name === 'AbortError') return
        setResultado({ chave: atual, opcoes: null, erro })
        onEscolher(null)
      })
    return () => controle.abort()
  }, [cep, chave, tentativa, onEscolher])

  function escolher(nova) {
    guardarFrete(cep, nova)
    onEscolher({ ...nova, cep })
  }

  if (carregando) {
    return (
      <p className="checkout__nota" aria-busy="true">
        Calculando o frete…
      </p>
    )
  }
  if (resultado.erro) {
    const { erro } = resultado
    return (
      <div className="checkout__erro" role="alert">
        <p>
          {erro.status === 400 || erro.status === 409
            ? erro.message
            : erro.status === 503
              ? 'O cálculo de frete está indisponível no momento. Tente mais tarde.'
              : 'Não foi possível calcular o frete agora.'}
        </p>
        {erro.status !== 400 && erro.status !== 409 && (
          <button type="button" className="checkout__link" onClick={() => setTentativa((t) => t + 1)}>
            Tentar de novo
          </button>
        )}
      </div>
    )
  }
  if (resultado.opcoes.length === 0) {
    return <p className="checkout__nota">Nenhuma transportadora entrega neste CEP. Confira o endereço ou fale com a gente.</p>
  }
  return (
    <fieldset className="frete__opcoes">
      <legend className="checkout__oculto">Escolha a entrega</legend>
      {resultado.opcoes.map((o) => (
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
          <span className="frete__prazo">{o.prazoDias === 1 ? 'Até 1 dia útil' : `Até ${o.prazoDias} dias úteis`}</span>
        </label>
      ))}
    </fieldset>
  )
}
