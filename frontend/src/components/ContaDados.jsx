import { useState } from 'react'
import { atualizarConta, buscarConta, excluirConta, solicitarTrocaEmail, trocarSenha } from '../api.js'
import { formatarData, formatarDataHora, mascararTelefone, somenteDigitos } from '../formato.js'
import { ROTA_POLITICA_PRIVACIDADE } from '../privacidade.js'
import useCarregar from '../useCarregar.js'
import ContaAviso from './ContaAviso.jsx'

// Aba "Dados Pessoais" da Minha Conta (#26): nome e telefone editáveis, troca do e-mail de acesso com
// confirmação pelo endereço novo (#61) e troca de senha, as duas pedindo a senha atual. No fim, os direitos do titular (LGPD, #31): o aceite da política, onde
// ver cada dado guardado e a exclusão da conta.
export default function ContaDados() {
  const { carregando, dados: conta, erro, tentarDeNovo } = useCarregar(buscarConta)
  const [excluida, setExcluida] = useState(false)

  // A confirmação substitui a aba inteira: volta ao topo para ela aparecer
  function mostrarExcluida() {
    setExcluida(true)
    window.scrollTo(0, 0)
  }

  if (excluida) return <ContaExcluida />

  return (
    <section className="conta__secao" aria-labelledby="titulo-dados">
      <h1 className="titulo-2" id="titulo-dados">
        Dados Pessoais
      </h1>
      {carregando || erro ? (
        <ContaAviso
          carregando={carregando}
          erro={erro}
          mensagemErro="Não foi possível carregar seus dados."
          onTentarDeNovo={tentarDeNovo}
        />
      ) : (
        <>
          <FormularioDados conta={conta} />
          <EmailAcesso conta={conta} />
          <FormularioSenha />
          <Privacidade conta={conta} onExcluida={mostrarExcluida} />
        </>
      )}
    </section>
  )
}

// 400 traz a mensagem pronta da API
function mensagemErro(erro, padrao) {
  if (erro.status === 400) return erro.message
  if (erro.codigo === 'SEM_CONEXAO') return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  return padrao
}

function FormularioDados({ conta }) {
  const [nome, setNome] = useState(conta.nome)
  const [telefone, setTelefone] = useState(mascararTelefone(conta.telefone ?? ''))
  const [envio, setEnvio] = useState({ enviando: false, erro: null, salvo: false })

  async function salvar(evento) {
    evento.preventDefault()
    setEnvio({ enviando: true, erro: null, salvo: false })
    try {
      const salva = await atualizarConta({ nome: nome.trim(), telefone: somenteDigitos(telefone) })
      setNome(salva.nome)
      setTelefone(mascararTelefone(salva.telefone))
      setEnvio({ enviando: false, erro: null, salvo: true })
    } catch (erro) {
      setEnvio({ enviando: false, erro, salvo: false })
    }
  }

  return (
    <form className="conta__formulario" onSubmit={salvar} noValidate>
      <label className="campo">
        <span className="eyebrow">Nome</span>
        <input
          className="campo__caixa"
          name="nome"
          autoComplete="name"
          maxLength={150}
          required
          value={nome}
          onChange={(e) => setNome(e.target.value)}
        />
      </label>
      <label className="campo">
        <span className="eyebrow">Telefone</span>
        <input
          className="campo__caixa"
          name="telefone"
          type="tel"
          inputMode="numeric"
          autoComplete="tel-national"
          placeholder="(11) 98765-4321"
          required
          value={telefone}
          onChange={(e) => setTelefone(mascararTelefone(e.target.value))}
        />
      </label>
      <div className="conta__acoes">
        <button type="submit" className="botao botao--primario" disabled={envio.enviando}>
          {envio.enviando ? 'Salvando…' : 'Salvar dados'}
        </button>
        <p className="conta__retorno" role="status">
          {envio.salvo ? 'Dados salvos.' : ''}
        </p>
      </div>
      {envio.erro && (
        <p className="conta__erro" role="alert">
          {mensagemErro(envio.erro, 'Não foi possível salvar agora. Tente de novo em instantes.')}
        </p>
      )}
    </form>
  )
}

// E-mail de acesso (#61). O novo só vale depois que a cliente abre o link mandado a ele: até lá ela continua
// entrando com o atual, e a aba mostra para onde foi o link e até quando vale. Pedir de novo troca o link.
function EmailAcesso({ conta }) {
  const [pendente, setPendente] = useState(conta.emailPendente)
  const [aberto, setAberto] = useState(false)
  const [novoEmail, setNovoEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [envio, setEnvio] = useState({ enviando: false, erro: null })

  async function enviar(evento) {
    evento.preventDefault()
    setEnvio({ enviando: true, erro: null })
    try {
      setPendente(await solicitarTrocaEmail(novoEmail.trim(), senha))
      fechar()
    } catch (erro) {
      setEnvio({ enviando: false, erro })
    }
  }

  function fechar() {
    setAberto(false)
    setNovoEmail('')
    setSenha('')
    setEnvio({ enviando: false, erro: null })
  }

  return (
    <section className="conta__formulario" aria-labelledby="titulo-email">
      <h2 className="titulo-3" id="titulo-email">
        E-mail de acesso
      </h2>
      <p className="conta__texto">
        Você entra na loja com <span className="conta__destaque">{conta.email}</span>.
      </p>
      {pendente && (
        <p className="conta__pendente" role="status">
          Enviamos um link para <span className="conta__destaque">{pendente.novoEmail}</span>. Abra o e-mail e confirme
          até {formatarDataHora(pendente.expiraEm)}; até lá, você continua entrando com o e-mail atual. Não chegou?
          Confira o spam ou peça de novo.
        </p>
      )}

      {aberto ? (
        <form className="conta__subformulario" onSubmit={enviar} noValidate aria-label="Trocar e-mail">
          <label className="campo">
            <span className="eyebrow">Novo e-mail</span>
            <input
              className="campo__caixa"
              type="email"
              inputMode="email"
              autoComplete="email"
              maxLength={150}
              required
              value={novoEmail}
              onChange={(e) => setNovoEmail(e.target.value)}
              disabled={envio.enviando}
            />
          </label>
          <label className="campo">
            <span className="eyebrow">Senha atual</span>
            <input
              className="campo__caixa"
              type="password"
              autoComplete="current-password"
              required
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              disabled={envio.enviando}
              aria-describedby="nota-troca-email"
            />
            <span className="conta__nota" id="nota-troca-email">
              Mandamos um link de confirmação para o novo e-mail. A troca só vale depois que você abrir o link.
            </span>
          </label>
          {envio.erro && (
            <p className="conta__erro" role="alert">
              {erroTrocaEmail(envio.erro)}
            </p>
          )}
          <div className="conta__acoes">
            <button
              type="submit"
              className="botao botao--contorno"
              disabled={envio.enviando || !novoEmail.trim() || !senha}
            >
              {envio.enviando ? 'Enviando…' : 'Enviar link'}
            </button>
            <button type="button" className="conta__link" onClick={fechar} disabled={envio.enviando}>
              Cancelar
            </button>
          </div>
        </form>
      ) : (
        <div className="conta__acoes">
          <button type="button" className="botao botao--contorno" onClick={() => setAberto(true)}>
            {pendente ? 'Pedir de novo' : 'Trocar e-mail'}
          </button>
        </div>
      )}
    </section>
  )
}

// Senha errada (403), e-mail já cadastrado (409), dado inválido (400) e falha no envio (502) trazem a mensagem
// pronta da API
function erroTrocaEmail(erro) {
  if (erro.codigo === 'SENHA_INCORRETA') return 'Senha atual incorreta.'
  if ([400, 409, 502].includes(erro.status)) return erro.message
  return mensagemErro(erro, 'Não foi possível pedir a troca agora. Tente de novo em instantes.')
}

const SENHAS_VAZIAS = { atual: '', nova: '', confirmacao: '' }

function FormularioSenha() {
  const [senhas, setSenhas] = useState(SENHAS_VAZIAS)
  const [envio, setEnvio] = useState({ enviando: false, erro: null, salvo: false })

  function campo(nome) {
    return { value: senhas[nome], onChange: (e) => setSenhas((atuais) => ({ ...atuais, [nome]: e.target.value })) }
  }

  async function salvar(evento) {
    evento.preventDefault()
    if (senhas.nova !== senhas.confirmacao) {
      setEnvio({ enviando: false, erro: { status: 400, message: 'A confirmação não é igual à nova senha' }, salvo: false })
      return
    }
    setEnvio({ enviando: true, erro: null, salvo: false })
    try {
      await trocarSenha(senhas.atual, senhas.nova)
      setSenhas(SENHAS_VAZIAS)
      setEnvio({ enviando: false, erro: null, salvo: true })
    } catch (erro) {
      setEnvio({ enviando: false, erro, salvo: false })
    }
  }

  return (
    <form className="conta__formulario" onSubmit={salvar} noValidate aria-labelledby="titulo-senha">
      <h2 className="titulo-3" id="titulo-senha">
        Trocar senha
      </h2>
      <label className="campo">
        <span className="eyebrow">Senha atual</span>
        <input className="campo__caixa" type="password" autoComplete="current-password" required {...campo('atual')} />
      </label>
      <label className="campo">
        <span className="eyebrow">Nova senha</span>
        <input
          className="campo__caixa"
          type="password"
          autoComplete="new-password"
          minLength={8}
          maxLength={72}
          required
          aria-describedby="nota-senha"
          {...campo('nova')}
        />
        <span className="conta__nota" id="nota-senha">
          De 8 a 72 caracteres.
        </span>
      </label>
      <label className="campo">
        <span className="eyebrow">Confirme a nova senha</span>
        <input
          className="campo__caixa"
          type="password"
          autoComplete="new-password"
          maxLength={72}
          required
          {...campo('confirmacao')}
        />
      </label>

      <div className="conta__acoes">
        <button
          type="submit"
          className="botao botao--contorno"
          disabled={envio.enviando || !senhas.atual || !senhas.nova || !senhas.confirmacao}
        >
          {envio.enviando ? 'Salvando…' : 'Trocar senha'}
        </button>
        <p className="conta__retorno" role="status">
          {envio.salvo ? 'Senha alterada. As sessões abertas em outros aparelhos foram encerradas.' : ''}
        </p>
      </div>
      {envio.erro && (
        <p className="conta__erro" role="alert">
          {mensagemErro(envio.erro, 'Não foi possível trocar a senha agora. Tente de novo em instantes.')}
        </p>
      )}
    </form>
  )
}

// Onde a cliente vê cada dado guardado (direito de acesso) e quando aceitou a política
function Privacidade({ conta, onExcluida }) {
  return (
    <section className="conta__formulario" aria-labelledby="titulo-privacidade">
      <h2 className="titulo-3" id="titulo-privacidade">
        Privacidade
      </h2>
      <p className="conta__texto">
        {conta.privacidadeVersao
          ? `Você aceitou a Política de privacidade (versão ${conta.privacidadeVersao}) em ${formatarData(conta.privacidadeAceitaEm)}.`
          : 'Não há registro de aceite da Política de privacidade nesta conta.'}{' '}
        <a className="conta__link" href={ROTA_POLITICA_PRIVACIDADE}>
          Ler a política
        </a>
      </p>
      <p className="conta__texto">
        Guardamos o seu nome, e-mail e telefone (acima), os <a className="conta__link" href="/minha-conta/enderecos">endereços</a>, os{' '}
        <a className="conta__link" href="/minha-conta">pedidos</a> e as{' '}
        <a className="conta__link" href="/minha-conta/trocas">trocas</a>. Os dados do cartão ficam só no Mercado Pago.
      </p>
      <ExcluirConta onExcluida={onExcluida} />
    </section>
  )
}

// Exclusão da conta (#31), confirmada com a senha. Começa fechada: o botão abre a explicação e o formulário.
function ExcluirConta({ onExcluida }) {
  const [aberto, setAberto] = useState(false)
  const [senha, setSenha] = useState('')
  const [ciente, setCiente] = useState(false)
  const [envio, setEnvio] = useState({ enviando: false, erro: null })

  if (!aberto) {
    return (
      <div className="conta__acoes">
        <button type="button" className="botao botao--contorno" onClick={() => setAberto(true)}>
          Excluir minha conta
        </button>
      </div>
    )
  }

  async function excluir(evento) {
    evento.preventDefault()
    setEnvio({ enviando: true, erro: null })
    try {
      await excluirConta(senha)
      onExcluida()
    } catch (erro) {
      setEnvio({ enviando: false, erro })
    }
  }

  function cancelar() {
    setAberto(false)
    setSenha('')
    setCiente(false)
    setEnvio({ enviando: false, erro: null })
  }

  return (
    <form className="conta__exclusao" onSubmit={excluir} noValidate aria-labelledby="titulo-exclusao">
      <h3 className="conta__exclusao-titulo" id="titulo-exclusao">
        Excluir minha conta
      </h3>
      <ul className="conta__exclusao-lista">
        <li>Seu nome, e-mail, telefone e endereços são apagados, e a sacola é esvaziada.</li>
        <li>Você não consegue mais entrar com esta conta. O e-mail fica livre para um cadastro novo.</li>
        <li>Pedidos ainda não pagos são cancelados.</li>
        <li>Os pedidos e valores continuam guardados, sem identificar você, porque a lei fiscal exige.</li>
        <li>Com um pedido a caminho ou uma troca em andamento, a exclusão espera a entrega ou a troca terminar.</li>
      </ul>
      <label className="campo">
        <span className="eyebrow">Sua senha</span>
        <input
          className="campo__caixa"
          type="password"
          autoComplete="current-password"
          required
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
          disabled={envio.enviando}
        />
      </label>
      <label className="conta__exclusao-ciente">
        <input
          type="checkbox"
          checked={ciente}
          onChange={(e) => setCiente(e.target.checked)}
          disabled={envio.enviando}
        />
        <span>Entendi que a exclusão não pode ser desfeita.</span>
      </label>
      {envio.erro && (
        <p className="conta__erro" role="alert">
          {envio.erro.status === 409
            ? envio.erro.message
            : mensagemErro(envio.erro, 'Não foi possível excluir a conta agora. Tente de novo em instantes.')}
        </p>
      )}
      <div className="conta__acoes">
        <button type="submit" className="botao botao--primario" disabled={!senha || !ciente || envio.enviando}>
          {envio.enviando ? 'Excluindo…' : 'Excluir conta'}
        </button>
        <button type="button" className="conta__link" onClick={cancelar} disabled={envio.enviando}>
          Cancelar
        </button>
      </div>
    </form>
  )
}

function ContaExcluida() {
  return (
    <section className="conta__secao" aria-labelledby="titulo-excluida">
      <h1 className="titulo-2" id="titulo-excluida">
        Conta excluída
      </h1>
      <p className="conta__texto" role="status">
        Seus dados pessoais foram apagados e você saiu da conta. Os pedidos ficam guardados sem identificar
        você, como a lei fiscal exige.
      </p>
      <a className="botao botao--contorno" href="/">
        Voltar para a loja
      </a>
    </section>
  )
}
