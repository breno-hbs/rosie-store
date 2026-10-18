import { useEffect, useState } from 'react'
import { alterarQuantidadeCarrinho, buscarCarrinho, listarCategorias, removerDoCarrinho } from '../api.js'
import Nav from '../components/Nav.jsx'
import Footer from '../components/Footer.jsx'
import { formatarPreco } from '../formato.js'
import './Carrinho.css'

// Tela "Carrinho" do Figma (#22). Só com cliente logado (App.jsx manda para /entrar antes).
// Preços e subtotal vêm da API, com o preço atual de cada produto.
export default function Carrinho() {
  const [categorias, setCategorias] = useState([])
  const [tentativa, setTentativa] = useState(0)
  const [resultado, setResultado] = useState({ chave: null, carrinho: null, erro: null })

  // Mesmo padrão da Página de Produto: enquanto o resultado não for desta tentativa, está carregando
  const carregando = resultado.chave !== tentativa

  useEffect(() => {
    document.title = 'Sacola — Rosie'
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then(setCategorias)
      .catch(() => {}) // Sem categorias a Nav fica só com logo e ações
    return () => controle.abort()
  }, [])

  useEffect(() => {
    const controle = new AbortController()
    buscarCarrinho({ signal: controle.signal })
      .then((carrinho) => setResultado({ chave: tentativa, carrinho, erro: null }))
      .catch((erro) => {
        if (erro.name !== 'AbortError') setResultado({ chave: tentativa, carrinho: null, erro })
      })
    return () => controle.abort()
  }, [tentativa])

  return (
    <>
      <Nav categorias={categorias} />

      <main className="carrinho">
        <div className="carrinho__titulo">
          <p className="eyebrow">Sua sacola</p>
          <h1 className="titulo-2">Carrinho</h1>
        </div>

        {carregando ? (
          <p className="carrinho__aviso" aria-busy="true">
            Carregando sua sacola…
          </p>
        ) : resultado.erro ? (
          <div className="carrinho__aviso" role="alert">
            <p>Não foi possível carregar sua sacola.</p>
            <button type="button" className="botao botao--contorno" onClick={() => setTentativa((t) => t + 1)}>
              Tentar de novo
            </button>
          </div>
        ) : resultado.carrinho.itens.length === 0 ? (
          <div className="carrinho__aviso">
            <p>Sua sacola está vazia.</p>
            <a className="botao botao--contorno" href="/">
              Ver a loja
            </a>
          </div>
        ) : (
          <ConteudoCarrinho
            carrinho={resultado.carrinho}
            onAtualizar={(carrinho) => setResultado({ chave: tentativa, carrinho, erro: null })}
            onRecarregar={() => setTentativa((t) => t + 1)}
          />
        )}
      </main>

      <Footer />
    </>
  )
}

function ConteudoCarrinho({ carrinho, onAtualizar, onRecarregar }) {
  // Item com uma mudança a caminho (controles travados) e erro da última mudança, por item
  const [pendente, setPendente] = useState(null)
  const [erros, setErros] = useState({})

  function mudar(itemId, chamada) {
    setPendente(itemId)
    setErros((atuais) => ({ ...atuais, [itemId]: null }))
    chamada()
      .then(onAtualizar)
      .catch((erro) => {
        // Item que já não está no carrinho (removido em outra aba): mostra o carrinho de agora
        if (erro.status === 404) onRecarregar()
        else setErros((atuais) => ({ ...atuais, [itemId]: erro.message }))
      })
      .finally(() => setPendente(null))
  }

  return (
    <div className="carrinho__conteudo">
      <ul className="carrinho__itens">
        {carrinho.itens.map((item) => (
          <ItemCarrinho
            key={item.id}
            item={item}
            pendente={pendente === item.id}
            erro={erros[item.id]}
            onQuantidade={(quantidade) => mudar(item.id, () => alterarQuantidadeCarrinho(item.id, quantidade))}
            onRemover={() => mudar(item.id, () => removerDoCarrinho(item.id))}
          />
        ))}
      </ul>

      <aside className="carrinho__resumo" aria-labelledby="titulo-resumo">
        <h2 className="titulo-3" id="titulo-resumo">
          Resumo do pedido
        </h2>
        <dl className="carrinho__valores">
          <div className="carrinho__linha">
            <dt>Subtotal</dt>
            <dd>{formatarPreco(carrinho.subtotal)}</dd>
          </div>
          <div className="carrinho__linha">
            <dt>Frete</dt>
            <dd>Calculado no checkout</dd>
          </div>
          <div className="carrinho__linha carrinho__linha--total">
            <dt>Total</dt>
            <dd>{formatarPreco(carrinho.subtotal)}</dd>
          </div>
        </dl>
        {carrinho.temIndisponiveis && (
          <p className="carrinho__nota">Peças indisponíveis não entram no total.</p>
        )}
        {/* O checkout com pagamento é a #24 */}
        <button type="button" className="botao botao--primario carrinho__finalizar" disabled>
          Finalizar compra
        </button>
        <p className="carrinho__nota">O pagamento pelo site chega em breve. Por enquanto, a compra é pelo WhatsApp.</p>
      </aside>
    </div>
  )
}

function avisoIndisponivel(item) {
  switch (item.motivoIndisponivel) {
    case 'PRODUTO_INATIVO':
      return 'Esta peça saiu da loja. Remova para continuar.'
    case 'TAMANHO_ESGOTADO':
      return `O tamanho ${item.tamanho} esgotou. Remova para continuar.`
    case 'ESTOQUE_INSUFICIENTE':
      return `Só temos ${item.estoqueDisponivel} no tamanho ${item.tamanho}: diminua a quantidade.`
    default:
      return null
  }
}

function ItemCarrinho({ item, pendente, erro, onQuantidade, onRemover }) {
  const { produto } = item
  const ativo = item.motivoIndisponivel !== 'PRODUTO_INATIVO'
  const linkProduto = ativo ? `/produtos/${produto.id}` : undefined
  const aviso = avisoIndisponivel(item)

  return (
    <li className="carrinho__item" aria-busy={pendente}>
      <a className="carrinho__foto" href={linkProduto} tabIndex={-1} aria-hidden="true">
        {produto.imagemPrincipal && <img src={produto.imagemPrincipal} alt="" loading="lazy" />}
      </a>

      <div className="carrinho__info">
        <a className="carrinho__nome" href={linkProduto}>
          {produto.nome}
        </a>
        <p className="carrinho__detalhe">Tam. {item.tamanho}</p>
        {aviso && <p className="carrinho__alerta">{aviso}</p>}
        {erro && (
          <p className="carrinho__alerta" role="alert">
            {erro}
          </p>
        )}
        <button type="button" className="carrinho__remover" disabled={pendente} onClick={onRemover}>
          Remover
        </button>
      </div>

      <div className="carrinho__quantidade" role="group" aria-label={`Quantidade de ${produto.nome}`}>
        <button
          type="button"
          aria-label="Diminuir"
          disabled={pendente || !ativo || item.quantidade <= 1}
          onClick={() => onQuantidade(item.quantidade - 1)}
        >
          –
        </button>
        <span aria-live="polite">{item.quantidade}</span>
        <button
          type="button"
          aria-label="Aumentar"
          disabled={pendente || !ativo || item.quantidade >= item.estoqueDisponivel}
          onClick={() => onQuantidade(item.quantidade + 1)}
        >
          +
        </button>
      </div>

      <p className="carrinho__preco">{formatarPreco(item.subtotal)}</p>
    </li>
  )
}
