import { useState } from 'react'
import { aceitarPrivacidade } from '../api.js'
import { ROTA_POLITICA_PRIVACIDADE } from '../privacidade.js'
import './AceitePrivacidade.css'

// Aceite da versão nova da política de privacidade (#31), para quem aceitou uma antiga: aparece depois do
// login (Entrar) e no checkout. Não é um <form> porque no checkout fica dentro do formulário do pedido.
export default function AceitePrivacidade({ onAceito, textoBotao = 'Aceitar e continuar' }) {
  const [marcado, setMarcado] = useState(false)
  const [envio, setEnvio] = useState({ enviando: false, erro: null })

  async function aceitar() {
    setEnvio({ enviando: true, erro: null })
    try {
      await aceitarPrivacidade()
      onAceito()
    } catch (erro) {
      setEnvio({ enviando: false, erro })
    }
  }

  return (
    <div className="aceite" role="group" aria-labelledby="aceite-titulo">
      <p className="aceite__titulo" id="aceite-titulo">
        Atualizamos a nossa política de privacidade
      </p>
      <p className="aceite__texto">
        Leia a versão nova e confirme que está de acordo para continuar comprando.
      </p>
      <label className="aceite__caixa">
        <input
          type="checkbox"
          checked={marcado}
          onChange={(e) => setMarcado(e.target.checked)}
          disabled={envio.enviando}
        />
        <span>
          Li e aceito a{' '}
          <a href={ROTA_POLITICA_PRIVACIDADE} target="_blank" rel="noreferrer">
            Política de privacidade
          </a>
        </span>
      </label>
      {envio.erro && (
        <p className="aceite__erro" role="alert">
          {envio.erro.codigo === 'SEM_CONEXAO'
            ? 'Sem conexão com a loja. Confira a internet e tente de novo.'
            : 'Não foi possível registrar o aceite. Tente de novo.'}
        </p>
      )}
      <button
        type="button"
        className="botao botao--primario aceite__botao"
        onClick={aceitar}
        disabled={!marcado || envio.enviando}
      >
        {envio.enviando ? 'Registrando…' : textoBotao}
      </button>
    </div>
  )
}
