import { useState } from 'react'
import { ApiError, comVoltar, destinoSeguro, entrarNaLoja, privacidadePendente } from '../api.js'
import AceitePrivacidade from '../components/AceitePrivacidade.jsx'
import CampoSenha from '../components/CampoSenha.jsx'
import LogoBadge from '../components/LogoBadge.jsx'
import { ROTAS_SENHA } from '../senha.js'
import './AcessoCliente.css'

// Mensagem de erro para cada código do contrato; o resto mostra o texto da API
function mensagemDeErro(erro) {
  if (!(erro instanceof ApiError)) return 'Não foi possível entrar. Tente de novo.'
  if (erro.codigo === 'CREDENCIAIS_INVALIDAS') return 'E-mail ou senha incorretos.'
  if (erro.codigo === 'SEM_CONEXAO') return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  return erro.message
}

// Login do cliente (RF08). Depois de entrar, volta para a página de ?voltar= ou para a Home. Quem aceitou
// uma versão antiga da política de privacidade aceita a nova antes de seguir (#31).
export default function Entrar() {
  const params = new URLSearchParams(window.location.search)
  const voltar = destinoSeguro(params.get('voltar'))
  // ?motivo=expirou vem do api.js quando o token venceu no meio do uso
  const sessaoExpirou = params.get('motivo') === 'expirou'

  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState(null)
  const [pedirAceite, setPedirAceite] = useState(false)

  async function entrar(evento) {
    evento.preventDefault()
    setCarregando(true)
    setErro(null)
    try {
      await entrarNaLoja(email.trim(), senha)
      if (privacidadePendente()) {
        setPedirAceite(true)
        return
      }
      window.location.assign(voltar)
    } catch (e) {
      setErro(mensagemDeErro(e))
      setCarregando(false)
    }
  }

  return (
    <main className="acesso">
      <a href="/" aria-label="Rosie — página inicial">
        <LogoBadge tamanho={72} />
      </a>
      <h1 className="titulo-2 acesso__titulo">Entrar</h1>

      {pedirAceite ? (
        <div className="acesso__form">
          <AceitePrivacidade onAceito={() => window.location.assign(voltar)} />
        </div>
      ) : (
        <form className="acesso__form" onSubmit={entrar}>
          <label className="campo">
            <span className="eyebrow">E-mail</span>
            <input
              className="campo__caixa"
              type="email"
              name="email"
              autoComplete="email"
              placeholder="seu@email.com"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={carregando}
            />
          </label>

          <CampoSenha
            rotulo="Senha"
            name="senha"
            autoComplete="current-password"
            placeholder="••••••••"
            required
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            disabled={carregando}
          />
          <a className="acesso__esqueci" href={ROTAS_SENHA.cliente.esqueci}>
            Esqueci minha senha
          </a>

          {(erro || sessaoExpirou) && (
            <p className="acesso__aviso" role="alert">
              {erro ?? 'Sua sessão expirou. Entre de novo.'}
            </p>
          )}

          <button type="submit" className="botao botao--primario acesso__enviar" disabled={carregando}>
            {carregando ? 'Entrando…' : 'Entrar'}
          </button>

          <p className="acesso__alternativa">
            Ainda não tem conta? <a href={comVoltar('/cadastro', voltar)}>Cadastre-se</a>
          </p>
        </form>
      )}
    </main>
  )
}
