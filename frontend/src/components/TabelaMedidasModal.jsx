import { useEffect, useId, useRef } from 'react'
import './TabelaMedidasModal.css'

const TAMANHOS = ['P', 'M', 'G', 'GG']
const FOCAVEIS = 'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'

// Tabela de medidas da categoria na Página de Produto (#20). Usa o <dialog> nativo com showModal():
// o resto da página fica inerte e o Esc fecha. O Tab também fica preso aqui dentro (sem passar
// pela barra do navegador) e, ao fechar, o foco volta para o botão que abriu.
export default function TabelaMedidasModal({ tabela, aberto, onFechar }) {
  const dialogo = useRef(null)
  const idTitulo = useId()

  useEffect(() => {
    const el = dialogo.current
    if (!el) return
    if (aberto && !el.open) {
      el.showModal()
      // Rola a página por trás só depois de fechar
      document.documentElement.classList.add('sem-rolagem')
    } else if (!aberto && el.open) {
      el.close()
    }
  }, [aberto])

  // Garante a limpeza se a página sair com o modal aberto
  useEffect(() => () => document.documentElement.classList.remove('sem-rolagem'), [])

  function aoFechar() {
    document.documentElement.classList.remove('sem-rolagem')
    onFechar()
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

  // Clique no fundo (fora da caixa) fecha; o <dialog> ocupa a tela toda e a caixa é o filho
  function cliqueNoFundo(evento) {
    if (evento.target === dialogo.current) dialogo.current.close()
  }

  return (
    <dialog
      ref={dialogo}
      className="medidas-modal"
      aria-labelledby={idTitulo}
      onClose={aoFechar}
      onKeyDown={prenderTab}
      onClick={cliqueNoFundo}
    >
      <div className="medidas-modal__caixa">
        <div className="medidas-modal__topo">
          <div>
            <p className="eyebrow">{tabela.categoria}</p>
            <h2 id={idTitulo} className="titulo-3 medidas-modal__titulo">
              Tabela de medidas
            </h2>
          </div>
          <button
            type="button"
            className="medidas-modal__fechar"
            onClick={() => dialogo.current.close()}
            aria-label="Fechar tabela de medidas"
          >
            ×
          </button>
        </div>

        {/* tabIndex: no celular a tabela pode rolar de lado, e quem usa teclado também precisa rolar */}
        <div className="medidas-modal__rolagem" tabIndex={0} role="group" aria-labelledby={idTitulo}>
          <table className="medidas-modal__tabela">
            <caption className="medidas-modal__legenda">Medidas em {tabela.unidade}</caption>
            <thead>
              <tr>
                <th scope="col" className="rotulo">
                  Medida
                </th>
                {TAMANHOS.map((tamanho) => (
                  <th key={tamanho} scope="col" className="rotulo">
                    {tamanho}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {tabela.medidas.map((medida) => (
                <tr key={medida.nome}>
                  <th scope="row">{medida.nome}</th>
                  {TAMANHOS.map((tamanho) => (
                    <td key={tamanho}>
                      {medida.valores[tamanho] || (
                        <>
                          <span aria-hidden="true">—</span>
                          <span className="medidas-modal__oculto">sem medida</span>
                        </>
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </dialog>
  )
}
