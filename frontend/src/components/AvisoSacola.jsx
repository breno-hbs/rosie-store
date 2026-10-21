import { useEffect, useId, useRef } from 'react'
import { formatarPreco } from '../formato.js'
import './AvisoSacola.css'

const FOCAVEIS = 'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'

function pecas(quantidade) {
  return `${quantidade} ${quantidade === 1 ? 'peça' : 'peças'}`
}

// Confirmação de "Adicionar à sacola" (#79): painel que sobe de baixo no celular e entra pela direita
// no desktop, como nas lojas de referência (COS, Arket, & Other Stories). Usa o <dialog> nativo com
// showModal(), como a tabela de medidas: a página fica inerte, o Esc e o fundo fecham e o Tab fica
// preso aqui dentro. Não fecha sozinho: a cliente decide entre ver a sacola e continuar comprando.
// `carrinho` é o CarrinhoDTO que o POST /carrinho/itens devolveu; `produto` e `tamanho`, a peça adicionada.
export default function AvisoSacola({ aberto, produto, tamanho, carrinho, onFechar }) {
  const dialogo = useRef(null)
  const verSacola = useRef(null)
  const idTitulo = useId()

  useEffect(() => {
    const el = dialogo.current
    if (!el) return
    if (aberto) {
      // Sempre que aberto: o painel já monta aberto, e no StrictMode a limpeza do efeito abaixo roda no meio
      document.documentElement.classList.add('sem-rolagem')
      if (el.open) return
      el.showModal()
      // O leitor de tela anuncia o título do painel e já cai na ação principal
      verSacola.current?.focus()
    } else if (el.open) {
      el.close()
    }
    // carrinho: cada adição traz um novo, e o painel reabre mesmo se o "close" da anterior ainda não chegou
  }, [aberto, carrinho])

  // Garante a limpeza se a página sair com o painel aberto
  useEffect(() => () => document.documentElement.classList.remove('sem-rolagem'), [])

  function aoFechar() {
    // O evento "close" chega depois do close(): se o painel já reabriu com outra adição, é o de antes
    if (dialogo.current.open) return
    document.documentElement.classList.remove('sem-rolagem')
    onFechar()
  }

  function fechar() {
    dialogo.current.close()
  }

  function prenderTab(evento) {
    if (evento.key !== 'Tab') return
    const focaveis = [...dialogo.current.querySelectorAll(FOCAVEIS)]
    if (focaveis.length === 0) return
    const primeiro = focaveis[0]
    const ultimo = focaveis[focaveis.length - 1]
    if (evento.shiftKey && document.activeElement === primeiro) {
      evento.preventDefault()
      ultimo.focus()
    } else if (!evento.shiftKey && document.activeElement === ultimo) {
      evento.preventDefault()
      primeiro.focus()
    }
  }

  // O <dialog> ocupa a tela toda e o painel é o filho: clique fora dele é no fundo
  function cliqueNoFundo(evento) {
    if (evento.target === dialogo.current) fechar()
  }

  // A linha da sacola traz a quantidade já somada (o mesmo tamanho adicionado de novo soma no item)
  const item = carrinho.itens.find((linha) => linha.produto.id === produto.id && linha.tamanho === tamanho)
  const foto = item?.produto.imagemPrincipal ?? produto.imagens[0]
  const naSacola = item?.quantidade ?? 1

  return (
    <dialog
      ref={dialogo}
      className="aviso-sacola"
      aria-labelledby={idTitulo}
      aria-modal="true"
      onClose={aoFechar}
      onKeyDown={prenderTab}
      onClick={cliqueNoFundo}
    >
      <div className="aviso-sacola__painel">
        <div className="aviso-sacola__topo">
          <h2 id={idTitulo} className="aviso-sacola__titulo">
            <span className="aviso-sacola__marca" aria-hidden="true">
              ✓
            </span>
            Adicionado à sacola
          </h2>
          <button type="button" className="aviso-sacola__fechar" onClick={fechar} aria-label="Fechar">
            ×
          </button>
        </div>

        <div className="aviso-sacola__peca">
          <div className="aviso-sacola__foto">{foto && <img src={foto} alt="" />}</div>
          <div className="aviso-sacola__info">
            <p className="aviso-sacola__nome">{produto.nome}</p>
            <p className="aviso-sacola__detalhe">Tamanho {tamanho}</p>
            {naSacola > 1 && <p className="aviso-sacola__detalhe">{pecas(naSacola)} deste tamanho na sacola</p>}
            <p className="aviso-sacola__preco">{formatarPreco(produto.preco)}</p>
          </div>
        </div>

        <dl className="aviso-sacola__resumo">
          <div>
            <dt>Sua sacola</dt>
            <dd>{pecas(carrinho.quantidadeItens)}</dd>
          </div>
          <div>
            <dt>Subtotal</dt>
            <dd>{formatarPreco(carrinho.subtotal)}</dd>
          </div>
        </dl>

        <div className="aviso-sacola__acoes">
          <a ref={verSacola} className="botao botao--primario" href="/carrinho">
            Ver sacola
          </a>
          <button type="button" className="botao botao--contorno" onClick={fechar}>
            Continuar comprando
          </button>
        </div>
      </div>
    </dialog>
  )
}
