import { useState } from 'react'
import { ApiError, cadastrarCliente, comVoltar, destinoSeguro } from '../api.js'
import LogoBadge from '../components/LogoBadge.jsx'
import { mascararTelefone, somenteDigitos } from '../formato.js'
import { ROTA_POLITICA_PRIVACIDADE } from '../privacidade.js'
import './AcessoCliente.css'

const CAMPOS_VAZIOS = { nome: '', email: '', telefone: '', senha: '', confirmacao: '' }

// Mensagem de erro para cada código do contrato; o resto (validações) mostra o texto da API
function mensagemDeErro(erro) {
  if (!(erro instanceof ApiError)) return 'Não foi possível criar a conta. Tente de novo.'
  if (erro.codigo === 'SEM_CONEXAO') return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  return erro.message
}

// Cadastro do cliente (RF08), com o aceite da política de privacidade (LGPD).
// Quem se cadastra sai logado e volta para a página de ?voltar= ou para a Home.
export default function Cadastro() {
  const voltar = destinoSeguro(new URLSearchParams(window.location.search).get('voltar'))

  const [campos, setCampos] = useState(CAMPOS_VAZIOS)
  const [aceite, setAceite] = useState(false)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState(null)

  function alterar(evento) {
    const { name, value } = evento.target
    setCampos((atuais) => ({ ...atuais, [name]: name === 'telefone' ? mascararTelefone(value) : value }))
  }

  async function cadastrar(evento) {
    evento.preventDefault()
    if (campos.senha !== campos.confirmacao) {
      setErro('As senhas não conferem.')
      return
    }

    setCarregando(true)
    setErro(null)
    try {
      await cadastrarCliente({
        nome: campos.nome.trim(),
        email: campos.email.trim(),
        senha: campos.senha,
        telefone: somenteDigitos(campos.telefone),
        aceitePrivacidade: aceite,
      })
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
      <h1 className="titulo-2 acesso__titulo">Criar conta</h1>

      <form className="acesso__form" onSubmit={cadastrar}>
        <label className="campo">
          <span className="eyebrow">Nome completo</span>
          <input
            className="campo__caixa"
            name="nome"
            autoComplete="name"
            placeholder="Seu nome"
            required
            maxLength={150}
            value={campos.nome}
            onChange={alterar}
            disabled={carregando}
          />
        </label>

        <label className="campo">
          <span className="eyebrow">E-mail</span>
          <input
            className="campo__caixa"
            type="email"
            name="email"
            autoComplete="email"
            placeholder="seu@email.com"
            required
            maxLength={150}
            value={campos.email}
            onChange={alterar}
            disabled={carregando}
          />
        </label>

        <label className="campo">
          <span className="eyebrow">Telefone</span>
          <input
            className="campo__caixa"
            type="tel"
            name="telefone"
            inputMode="numeric"
            autoComplete="tel-national"
            placeholder="(00) 00000-0000"
            required
            value={campos.telefone}
            onChange={alterar}
            disabled={carregando}
          />
        </label>

        <label className="campo">
          <span className="eyebrow">Senha</span>
          <input
            className="campo__caixa"
            type="password"
            name="senha"
            autoComplete="new-password"
            placeholder="••••••••"
            required
            minLength={8}
            maxLength={72}
            value={campos.senha}
            onChange={alterar}
            disabled={carregando}
          />
        </label>

        <label className="campo">
          <span className="eyebrow">Confirmar senha</span>
          <input
            className="campo__caixa"
            type="password"
            name="confirmacao"
            autoComplete="new-password"
            placeholder="••••••••"
            required
            value={campos.confirmacao}
            onChange={alterar}
            disabled={carregando}
          />
        </label>

        <label className="acesso__aceite">
          <input
            type="checkbox"
            name="aceitePrivacidade"
            required
            checked={aceite}
            onChange={(e) => setAceite(e.target.checked)}
            disabled={carregando}
          />
          <span>
            Li e aceito a{' '}
            {/* Em outra aba, para não perder o que já foi digitado */}
            <a href={ROTA_POLITICA_PRIVACIDADE} target="_blank" rel="noreferrer">
              Política de privacidade
            </a>
          </span>
        </label>

        {erro && (
          <p className="acesso__aviso" role="alert">
            {erro}
          </p>
        )}

        <button type="submit" className="botao botao--primario acesso__enviar" disabled={carregando}>
          {carregando ? 'Criando conta…' : 'Criar conta'}
        </button>

        <p className="acesso__alternativa">
          Já tem conta? <a href={comVoltar('/entrar', voltar)}>Entrar</a>
        </p>
      </form>
    </main>
  )
}
