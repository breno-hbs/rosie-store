import { useEffect, useId, useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import './CampoSenha.css'

// Campo de senha com o botão de olho para mostrar e esconder o que foi digitado (#95). Usado em todos os
// formulários com senha. O rótulo vai num <label for> à parte, e não envolvendo o campo, para o nome do
// botão não entrar no nome do campo. As outras props (name, autoComplete, required, minLength,
// aria-describedby...) vão direto para o <input>; `children` é a nota abaixo do campo, se houver.
// O botão é type="button" (Enter no campo continua enviando o formulário), não tira o foco do campo no
// clique e a senha volta a ficar escondida quando o formulário é enviado.
export default function CampoSenha({ rotulo, id, disabled, children, ...props }) {
  const idGerado = useId()
  const idCampo = id ?? idGerado
  const campo = useRef(null)
  const [visivel, setVisivel] = useState(false)

  // O Chrome leva o cursor para o início do campo quando o type muda, no próximo layout: troca o type na
  // hora (flushSync), força o layout e devolve a seleção, para continuar digitando de onde parou
  function alternar() {
    const caixa = campo.current
    const { selectionStart, selectionEnd } = caixa
    flushSync(() => setVisivel((atual) => !atual))
    if (document.activeElement !== caixa) return
    caixa.getBoundingClientRect()
    caixa.setSelectionRange(selectionStart, selectionEnd)
  }

  useEffect(() => {
    const formulario = campo.current?.form
    if (!formulario) return
    const esconder = () => setVisivel(false)
    formulario.addEventListener('submit', esconder)
    return () => formulario.removeEventListener('submit', esconder)
  }, [])

  return (
    <div className="campo">
      <label className="eyebrow" htmlFor={idCampo}>
        {rotulo}
      </label>
      <div className="campo-senha">
        <input
          ref={campo}
          id={idCampo}
          className="campo__caixa campo-senha__caixa"
          type={visivel ? 'text' : 'password'}
          disabled={disabled}
          autoCapitalize="off"
          autoCorrect="off"
          spellCheck={false}
          {...props}
        />
        <button
          type="button"
          className="campo-senha__botao"
          aria-label={visivel ? 'Ocultar senha' : 'Mostrar senha'}
          aria-pressed={visivel}
          aria-controls={idCampo}
          disabled={disabled}
          onMouseDown={(e) => e.preventDefault()}
          onClick={alternar}
        >
          <svg className="campo-senha__icone" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true" focusable="false">
            <path d="M2.5 12c2.3-4 5.5-6 9.5-6s7.2 2 9.5 6c-2.3 4-5.5 6-9.5 6s-7.2-2-9.5-6z" />
            <circle cx="12" cy="12" r="3" />
            {visivel && <path d="m4 4 16 16" />}
          </svg>
        </button>
      </div>
      {children}
    </div>
  )
}
