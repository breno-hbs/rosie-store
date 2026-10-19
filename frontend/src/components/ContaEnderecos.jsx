import { useState } from 'react'
import { atualizarEndereco, criarEndereco, listarEnderecos, removerEndereco } from '../api.js'
import { ENDERECO_VAZIO, enderecoCompleto, enderecoParaApi } from '../endereco.js'
import { mascararCep } from '../formato.js'
import useCarregar from '../useCarregar.js'
import CamposEndereco from './CamposEndereco.jsx'
import ContaAviso from './ContaAviso.jsx'

// Aba "Endereços" da Minha Conta (#26): os endereços salvos (os mesmos do checkout), com adicionar,
// editar e remover. Usa o formulário do checkout (CamposEndereco).
export default function ContaEnderecos() {
  const { carregando, dados, erro, tentarDeNovo } = useCarregar(listarEnderecos)

  return (
    <section className="conta__secao" aria-labelledby="titulo-enderecos">
      <h1 className="titulo-2" id="titulo-enderecos">
        Endereços
      </h1>
      {carregando || erro ? (
        <ContaAviso
          carregando={carregando}
          erro={erro}
          mensagemErro="Não foi possível carregar seus endereços."
          onTentarDeNovo={tentarDeNovo}
        />
      ) : (
        <ListaEnderecos iniciais={dados} />
      )}
    </section>
  )
}

function mensagemErro(erro, padrao) {
  if (erro.status === 400) return erro.message
  if (erro.status === 404) return 'Este endereço não existe mais. Recarregue a página.'
  if (erro.codigo === 'SEM_CONEXAO') return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  return padrao
}

// `editando`: null, 'novo' ou o id do endereço aberto no formulário (um de cada vez)
function ListaEnderecos({ iniciais }) {
  const [enderecos, setEnderecos] = useState(iniciais)
  const [editando, setEditando] = useState(null)

  function salvo(idAnterior, endereco) {
    // Endereço já usado em pedido volta com id novo: troca o antigo pelo novo no mesmo lugar
    setEnderecos((atuais) =>
      idAnterior === 'novo' ? [...atuais, endereco] : atuais.map((e) => (e.id === idAnterior ? endereco : e)),
    )
    setEditando(null)
  }

  return (
    <div className="conta__enderecos">
      {enderecos.length === 0 && editando !== 'novo' && (
        <p className="conta__texto">Você ainda não tem endereços salvos.</p>
      )}

      {enderecos.length > 0 && (
        <ul className="conta__lista-enderecos">
          {enderecos.map((endereco) => (
            <li key={endereco.id}>
              {editando === endereco.id ? (
                <FormularioEndereco
                  titulo="Editar endereço"
                  inicial={{ ...endereco, cep: mascararCep(endereco.cep), complemento: endereco.complemento ?? '' }}
                  salvar={(dados) => atualizarEndereco(endereco.id, dados)}
                  onSalvo={(novo) => salvo(endereco.id, novo)}
                  onCancelar={() => setEditando(null)}
                />
              ) : (
                <CartaoEndereco
                  endereco={endereco}
                  bloqueado={editando !== null}
                  onEditar={() => setEditando(endereco.id)}
                  onRemovido={() => setEnderecos((atuais) => atuais.filter((e) => e.id !== endereco.id))}
                />
              )}
            </li>
          ))}
        </ul>
      )}

      {editando === 'novo' ? (
        <FormularioEndereco
          titulo="Novo endereço"
          inicial={ENDERECO_VAZIO}
          salvar={criarEndereco}
          onSalvo={(novo) => salvo('novo', novo)}
          onCancelar={() => setEditando(null)}
        />
      ) : (
        <button
          type="button"
          className="botao botao--contorno conta__adicionar"
          disabled={editando !== null}
          onClick={() => setEditando('novo')}
        >
          Adicionar endereço
        </button>
      )}
    </div>
  )
}

function CartaoEndereco({ endereco, bloqueado, onEditar, onRemovido }) {
  const [confirmando, setConfirmando] = useState(false)
  const [remocao, setRemocao] = useState({ removendo: false, erro: null })

  async function remover() {
    setRemocao({ removendo: true, erro: null })
    try {
      await removerEndereco(endereco.id)
      onRemovido()
    } catch (erro) {
      setRemocao({ removendo: false, erro })
    }
  }

  return (
    <div className="conta__endereco">
      <p className="conta__endereco-texto">
        <span className="conta__endereco-rua">
          {endereco.logradouro}, {endereco.numero}
          {endereco.complemento ? ` — ${endereco.complemento}` : ''}
        </span>
        <span className="conta__nota">
          {endereco.bairro}, {endereco.cidade}/{endereco.estado} · {mascararCep(endereco.cep)}
        </span>
      </p>

      {/* As keys separam os dois grupos: sem elas o React reaproveitaria o botão "Remover" como
          "Sim, remover", e um segundo clique ou Enter apagaria sem a cliente ler a pergunta */}
      {confirmando ? (
        <div key="confirmar" className="conta__endereco-acoes" role="group" aria-label="Confirmar remoção">
          <span className="conta__nota">Remover este endereço?</span>
          <button type="button" className="conta__link" onClick={remover} disabled={remocao.removendo}>
            {remocao.removendo ? 'Removendo…' : 'Sim, remover'}
          </button>
          {/* O foco vai para o Cancelar, a opção que não apaga nada */}
          <button
            type="button"
            className="conta__link"
            onClick={() => setConfirmando(false)}
            disabled={remocao.removendo}
            autoFocus
          >
            Cancelar
          </button>
        </div>
      ) : (
        <div key="acoes" className="conta__endereco-acoes">
          <button type="button" className="conta__link" onClick={onEditar} disabled={bloqueado}>
            Editar<span className="conta__oculto"> {endereco.logradouro}, {endereco.numero}</span>
          </button>
          <button type="button" className="conta__link" onClick={() => setConfirmando(true)} disabled={bloqueado}>
            Remover<span className="conta__oculto"> {endereco.logradouro}, {endereco.numero}</span>
          </button>
        </div>
      )}
      {remocao.erro && (
        <p className="conta__erro" role="alert">
          {mensagemErro(remocao.erro, 'Não foi possível remover agora. Tente de novo em instantes.')}
        </p>
      )}
    </div>
  )
}

function FormularioEndereco({ titulo, inicial, salvar, onSalvo, onCancelar }) {
  const [valor, setValor] = useState(inicial)
  const [envio, setEnvio] = useState({ enviando: false, erro: null })
  const completo = enderecoCompleto(valor)

  async function enviar(evento) {
    evento.preventDefault()
    if (!completo) return
    setEnvio({ enviando: true, erro: null })
    try {
      onSalvo(await salvar(enderecoParaApi(valor)))
    } catch (erro) {
      setEnvio({ enviando: false, erro })
    }
  }

  return (
    <form className="conta__formulario conta__formulario--endereco" onSubmit={enviar} noValidate aria-label={titulo}>
      <h2 className="titulo-3">{titulo}</h2>
      <CamposEndereco valor={valor} onMudar={setValor} />
      <div className="conta__acoes">
        <button type="submit" className="botao botao--primario" disabled={!completo || envio.enviando}>
          {envio.enviando ? 'Salvando…' : 'Salvar endereço'}
        </button>
        <button type="button" className="botao botao--contorno" onClick={onCancelar} disabled={envio.enviando}>
          Cancelar
        </button>
      </div>
      {envio.erro && (
        <p className="conta__erro" role="alert">
          {mensagemErro(envio.erro, 'Não foi possível salvar agora. Tente de novo em instantes.')}
        </p>
      )}
    </form>
  )
}
