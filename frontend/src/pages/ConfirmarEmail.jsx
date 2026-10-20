import { useEffect, useRef, useState } from 'react'
import { confirmarTrocaEmail, lerSessaoCliente, listarCategorias } from '../api.js'
import Footer from '../components/Footer.jsx'
import Nav from '../components/Nav.jsx'
import './ConfirmarEmail.css'

// Volta do link de troca de e-mail (#61): /minha-conta/confirmar-email?token=... Pública: o link pode ser aberto
// em outro aparelho, sem a cliente logada. O token sai da barra de endereço assim que é lido (não fica no
// histórico nem vai no Referer) e é usado uma vez só: a API apaga o token confirmado.
const ROTA_DADOS = '/minha-conta/dados'

export default function ConfirmarEmail() {
  const [categorias, setCategorias] = useState([])
  const [token] = useState(() => new URLSearchParams(window.location.search).get('token')?.trim() ?? '')
  const [tentativa, setTentativa] = useState(0)
  const [resultado, setResultado] = useState(() => (token ? { estado: 'confirmando' } : { estado: 'invalido' }))
  // Uma confirmação por tentativa: o StrictMode do dev roda o efeito duas vezes, e a segunda chamada daria
  // "link já usado"
  const enviada = useRef(-1)

  useEffect(() => {
    document.title = 'Confirmar e-mail — Rosie'
    if (window.location.search) window.history.replaceState(null, '', window.location.pathname)
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then(setCategorias)
      .catch(() => {})
    return () => controle.abort()
  }, [])

  useEffect(() => {
    if (!token || enviada.current === tentativa) return
    enviada.current = tentativa
    confirmarTrocaEmail(token)
      .then(({ email }) => setResultado({ estado: 'sucesso', email }))
      .catch((erro) => setResultado({ estado: estadoDoErro(erro), erro }))
  }, [token, tentativa])

  function tentarDeNovo() {
    setResultado({ estado: 'confirmando' })
    setTentativa((t) => t + 1)
  }

  return (
    <>
      <Nav categorias={categorias} />
      <main className="confirmar-email">
        <Conteudo resultado={resultado} onTentarDeNovo={tentarDeNovo} />
      </main>
      <Footer />
    </>
  )
}

function estadoDoErro(erro) {
  if (erro.codigo === 'TOKEN_EXPIRADO') return 'expirado'
  if (erro.codigo === 'TOKEN_INVALIDO') return 'invalido'
  if (erro.codigo === 'EMAIL_JA_CADASTRADO') return 'conflito'
  return 'falha'
}

function Conteudo({ resultado, onTentarDeNovo }) {
  const logada = lerSessaoCliente() !== null

  switch (resultado.estado) {
    case 'confirmando':
      return (
        <p className="confirmar-email__texto" aria-busy="true">
          Confirmando o seu novo e-mail…
        </p>
      )
    case 'sucesso':
      return (
        <Situacao titulo="E-mail confirmado">
          <p className="confirmar-email__texto">
            Pronto! Daqui em diante, entre na loja com{' '}
            <span className="confirmar-email__destaque">{resultado.email}</span>. Mandamos um aviso para o e-mail
            antigo.
          </p>
          <div className="confirmar-email__acoes">
            {logada ? (
              <a className="botao botao--primario" href={ROTA_DADOS}>
                Ver meus dados
              </a>
            ) : (
              <a className="botao botao--primario" href={`/entrar?voltar=${encodeURIComponent(ROTA_DADOS)}`}>
                Entrar
              </a>
            )}
            <a className="botao botao--contorno" href="/">
              Voltar para a loja
            </a>
          </div>
        </Situacao>
      )
    case 'expirado':
      return (
        <Situacao titulo="Este link expirou">
          <p className="confirmar-email__texto">
            O prazo para confirmar a troca acabou e o seu e-mail continua o mesmo. Peça a troca de novo em Dados
            Pessoais: mandamos um link novo.
          </p>
          <div className="confirmar-email__acoes">
            <a className="botao botao--primario" href={ROTA_DADOS}>
              Pedir de novo
            </a>
          </div>
        </Situacao>
      )
    case 'conflito':
      return (
        <Situacao titulo="Não foi possível trocar o e-mail">
          <p className="confirmar-email__texto">
            Este e-mail foi cadastrado em outra conta depois do seu pedido, então o seu e-mail continua o mesmo.
            Peça a troca para outro endereço em Dados Pessoais.
          </p>
          <div className="confirmar-email__acoes">
            <a className="botao botao--primario" href={ROTA_DADOS}>
              Ir para Dados Pessoais
            </a>
          </div>
        </Situacao>
      )
    case 'falha':
      return (
        <Situacao titulo="Não foi possível confirmar agora">
          <p className="confirmar-email__texto" role="alert">
            {resultado.erro?.codigo === 'SEM_CONEXAO'
              ? 'Sem conexão com a loja. Confira a internet e tente de novo.'
              : 'Algo deu errado do nosso lado. Tente de novo em instantes.'}
          </p>
          <div className="confirmar-email__acoes">
            <button type="button" className="botao botao--primario" onClick={onTentarDeNovo}>
              Tentar de novo
            </button>
          </div>
        </Situacao>
      )
    default:
      return (
        <Situacao titulo="Link inválido">
          <p className="confirmar-email__texto">
            Este link não é válido ou já foi usado. Se você já confirmou a troca, entre com o e-mail novo. Se pediu
            a troca mais de uma vez, só o link do último e-mail vale.
          </p>
          <div className="confirmar-email__acoes">
            <a className="botao botao--primario" href={ROTA_DADOS}>
              Ir para Dados Pessoais
            </a>
          </div>
        </Situacao>
      )
  }
}

function Situacao({ titulo, children }) {
  return (
    <section className="confirmar-email__situacao" aria-labelledby="titulo-confirmar-email">
      <h1 className="titulo-2" id="titulo-confirmar-email">
        {titulo}
      </h1>
      {children}
    </section>
  )
}
