import { useEffect, useState } from 'react'
import { ApiError, pedirLinkSenha } from '../api.js'
import MolduraAcesso from '../components/MolduraAcesso.jsx'
import { ROTAS_SENHA } from '../senha.js'

// Mensagem de erro para cada código do contrato; o resto (validação, limite de pedidos) mostra o texto da API
function mensagemDeErro(erro) {
  if (!(erro instanceof ApiError)) return 'Não foi possível enviar o link. Tente de novo.'
  if (erro.codigo === 'SEM_CONEXAO') return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  return erro.message
}

// Pedido do link de nova senha (#93), na loja (/esqueci-senha) e no painel (/admin/esqueci-senha). A resposta é
// a mesma exista ou não a conta, então a tela nunca diz se o e-mail está cadastrado.
export default function EsqueciSenha({ area }) {
  const rotas = ROTAS_SENHA[area]
  const [email, setEmail] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState(null)
  const [enviado, setEnviado] = useState(null)

  useEffect(() => {
    document.title = 'Esqueci minha senha — Rosie'
  }, [])

  async function enviar(evento) {
    evento.preventDefault()
    setCarregando(true)
    setErro(null)
    try {
      const { mensagem } = await pedirLinkSenha(email.trim())
      setEnviado({ email: email.trim(), mensagem })
    } catch (e) {
      setErro(mensagemDeErro(e))
    } finally {
      setCarregando(false)
    }
  }

  if (enviado) {
    return (
      <MolduraAcesso area={area} titulo="Confira o seu e-mail">
        <div className="acesso__form">
          <p className="acesso__texto" role="status">
            {enviado.mensagem} Procure na caixa de entrada (e no spam) de{' '}
            <span className="acesso__destaque">{enviado.email}</span>. O link vale por pouco tempo e só o do último
            pedido funciona.
          </p>
          <a className="botao botao--primario acesso__enviar" href={rotas.login}>
            Voltar para o login
          </a>
          <p className="acesso__alternativa">
            Não chegou?{' '}
            <button type="button" className="acesso__link" onClick={() => setEnviado(null)}>
              Pedir de novo
            </button>
          </p>
        </div>
      </MolduraAcesso>
    )
  }

  return (
    <MolduraAcesso area={area} titulo="Esqueci minha senha">
      <form className="acesso__form" onSubmit={enviar}>
        <p className="acesso__texto">
          Informe o e-mail da sua conta. Mandamos para ele um link para você criar uma senha nova.
        </p>

        <label className="campo">
          <span className="eyebrow">E-mail</span>
          <input
            className="campo__caixa"
            type="email"
            name="email"
            autoComplete={area === 'admin' ? 'username' : 'email'}
            placeholder={area === 'admin' ? 'admin@rosie.com' : 'seu@email.com'}
            required
            maxLength={150}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={carregando}
          />
        </label>

        {erro && (
          <p className="acesso__aviso" role="alert">
            {erro}
          </p>
        )}

        <button type="submit" className="botao botao--primario acesso__enviar" disabled={carregando}>
          {carregando ? 'Enviando…' : 'Enviar link'}
        </button>

        <p className="acesso__alternativa">
          Lembrou a senha? <a href={rotas.login}>Entrar</a>
        </p>
      </form>
    </MolduraAcesso>
  )
}
