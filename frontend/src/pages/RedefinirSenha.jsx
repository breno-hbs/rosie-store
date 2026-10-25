import { useEffect, useState } from 'react'
import { ApiError, redefinirSenha } from '../api.js'
import MolduraAcesso from '../components/MolduraAcesso.jsx'
import { ROTAS_SENHA } from '../senha.js'

// Mensagem de erro para cada código do contrato; o resto (validação da senha) mostra o texto da API
function mensagemDeErro(erro) {
  if (!(erro instanceof ApiError)) return 'Não foi possível salvar a senha. Tente de novo.'
  if (erro.codigo === 'SEM_CONEXAO') return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  return erro.message
}

// Nova senha pelo link do e-mail (#93): /redefinir-senha?token=... (loja) ou /admin/redefinir-senha?token=...
// (painel). Pública: o token é a prova. Ele sai da barra de endereço assim que é lido (não fica no histórico nem
// vai no Referer) e só serve uma vez. Depois de salvar, a pessoa entra pelo login da sua área com a senha nova.
export default function RedefinirSenha({ area }) {
  const rotas = ROTAS_SENHA[area]
  const [token] = useState(() => new URLSearchParams(window.location.search).get('token')?.trim() ?? '')
  const [estado, setEstado] = useState(() => (token ? 'formulario' : 'invalido'))
  const [senhas, setSenhas] = useState({ nova: '', confirmacao: '' })
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    document.title = 'Nova senha — Rosie'
    if (window.location.search) window.history.replaceState(null, '', window.location.pathname)
  }, [])

  function alterar(evento) {
    const { name, value } = evento.target
    setSenhas((atuais) => ({ ...atuais, [name]: value }))
  }

  async function salvar(evento) {
    evento.preventDefault()
    if (senhas.nova !== senhas.confirmacao) {
      setErro('As senhas não conferem.')
      return
    }
    setCarregando(true)
    setErro(null)
    try {
      await redefinirSenha(token, senhas.nova, area)
      setEstado('sucesso')
    } catch (e) {
      if (e.codigo === 'TOKEN_EXPIRADO') setEstado('expirado')
      else if (e.codigo === 'TOKEN_INVALIDO') setEstado('invalido')
      else setErro(mensagemDeErro(e))
    } finally {
      setCarregando(false)
    }
  }

  if (estado === 'sucesso') {
    return (
      <MolduraAcesso area={area} titulo="Senha redefinida">
        <div className="acesso__form">
          <p className="acesso__texto" role="status">
            Pronto! Entre com a senha nova. Por segurança, as sessões que estavam abertas com a senha antiga foram
            encerradas, e mandamos um aviso para o seu e-mail.
          </p>
          <a className="botao botao--primario acesso__enviar" href={rotas.login}>
            Entrar
          </a>
        </div>
      </MolduraAcesso>
    )
  }

  if (estado === 'expirado' || estado === 'invalido') {
    return (
      <MolduraAcesso area={area} titulo={estado === 'expirado' ? 'Este link expirou' : 'Link inválido'}>
        <div className="acesso__form">
          <p className="acesso__texto">
            {estado === 'expirado'
              ? 'O prazo do link acabou e a sua senha continua a mesma. Peça um link novo: ele chega em instantes.'
              : 'Este link não é válido ou já foi usado. Se você pediu mais de um, só o do último e-mail vale. ' +
                'Peça um link novo se ainda precisar trocar a senha.'}
          </p>
          <a className="botao botao--primario acesso__enviar" href={rotas.esqueci}>
            Pedir um link novo
          </a>
          <p className="acesso__alternativa">
            Já trocou a senha? <a href={rotas.login}>Entrar</a>
          </p>
        </div>
      </MolduraAcesso>
    )
  }

  return (
    <MolduraAcesso area={area} titulo="Crie uma nova senha">
      <form className="acesso__form" onSubmit={salvar}>
        <label className="campo">
          <span className="eyebrow">Nova senha</span>
          <input
            className="campo__caixa"
            type="password"
            name="nova"
            autoComplete="new-password"
            placeholder="••••••••"
            required
            minLength={8}
            maxLength={72}
            aria-describedby="nota-nova-senha"
            value={senhas.nova}
            onChange={alterar}
            disabled={carregando}
          />
          <span className="acesso__nota" id="nota-nova-senha">
            De 8 a 72 caracteres.
          </span>
        </label>

        <label className="campo">
          <span className="eyebrow">Confirmar nova senha</span>
          <input
            className="campo__caixa"
            type="password"
            name="confirmacao"
            autoComplete="new-password"
            placeholder="••••••••"
            required
            maxLength={72}
            value={senhas.confirmacao}
            onChange={alterar}
            disabled={carregando}
          />
        </label>

        {erro && (
          <p className="acesso__aviso" role="alert">
            {erro}
          </p>
        )}

        <button type="submit" className="botao botao--primario acesso__enviar" disabled={carregando}>
          {carregando ? 'Salvando…' : 'Salvar nova senha'}
        </button>
      </form>
    </MolduraAcesso>
  )
}
