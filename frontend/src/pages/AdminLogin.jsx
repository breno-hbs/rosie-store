import { useState } from 'react'
import { ApiError, entrarNoPainel } from '../api.js'
import LogoBadge from '../components/LogoBadge.jsx'
import './AdminLogin.css'

// Mensagem de erro para cada código do contrato; o resto mostra o texto da API
function mensagemDeErro(erro) {
  if (!(erro instanceof ApiError)) return 'Não foi possível entrar. Tente de novo.'
  if (erro.codigo === 'CREDENCIAIS_INVALIDAS') return 'E-mail ou senha incorretos.'
  if (erro.codigo === 'SEM_CONEXAO') return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  return erro.message
}

export default function AdminLogin() {
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState(null)

  // ?motivo=expirou vem do api.js quando o token venceu no meio do uso
  const sessaoExpirou = new URLSearchParams(window.location.search).get('motivo') === 'expirou'

  async function entrar(evento) {
    evento.preventDefault()
    setCarregando(true)
    setErro(null)
    try {
      await entrarNoPainel(email.trim(), senha)
      window.location.assign('/admin')
    } catch (e) {
      setErro(mensagemDeErro(e))
      setCarregando(false)
    }
  }

  return (
    <main className="admin-login">
      <LogoBadge tamanho={72} />
      <h1 className="rotulo admin-login__titulo">Painel administrativo</h1>

      <form className="admin-login__form" onSubmit={entrar}>
        <label className="campo">
          <span className="eyebrow">E-mail</span>
          <input
            className="campo__caixa"
            type="email"
            name="email"
            autoComplete="username"
            placeholder="admin@rosie.com"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={carregando}
          />
        </label>

        <label className="campo">
          <span className="eyebrow">Senha</span>
          <input
            className="campo__caixa"
            type="password"
            name="senha"
            autoComplete="current-password"
            placeholder="••••••••"
            required
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            disabled={carregando}
          />
        </label>

        {(erro || sessaoExpirou) && (
          <p className="admin-login__aviso" role="alert">
            {erro ?? 'Sua sessão expirou. Entre de novo.'}
          </p>
        )}

        <button type="submit" className="botao botao--primario admin-login__entrar" disabled={carregando}>
          {carregando ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </main>
  )
}
