import { useEffect, useState } from 'react'
import { ApiError, criarCategoria, listarCategoriasAdmin, removerCategoria } from '../api.js'
import './AdminCategorias.css'

// Erros do contrato com mensagem própria; o resto mostra o texto da API (ex.: CATEGORIA_EM_USO)
function mensagemDeErro(erro, padrao) {
  if (!(erro instanceof ApiError)) return padrao
  if (erro.codigo === 'CATEGORIA_DUPLICADA') return 'Já existe uma categoria com esse nome.'
  if (erro.codigo === 'SEM_CONEXAO') return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  return erro.message
}

function ordenarPorNome(categorias) {
  return [...categorias].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))
}

// Gestão de categorias (Figma: frame "Admin — Gestão de Categorias", RF17)
export default function AdminCategorias() {
  const [tentativa, setTentativa] = useState(0)
  // Enquanto `lista.tentativa` não for a atual, está carregando
  const [lista, setLista] = useState({ tentativa: null, categorias: [], erro: null })
  const [nome, setNome] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [erroForm, setErroForm] = useState(null)
  // Remoção em duas etapas: "Remover" pede confirmação na própria linha
  const [confirmando, setConfirmando] = useState(null)
  const [removendo, setRemovendo] = useState(null)
  const [erroLinha, setErroLinha] = useState({ id: null, mensagem: null })

  const carregando = lista.tentativa !== tentativa

  useEffect(() => {
    const controle = new AbortController()
    listarCategoriasAdmin({ signal: controle.signal })
      .then((categorias) => setLista({ tentativa, categorias, erro: null }))
      .catch((erro) => {
        if (erro.name === 'AbortError') return
        setLista({ tentativa, categorias: [], erro: mensagemDeErro(erro, 'Não foi possível carregar as categorias.') })
      })
    return () => controle.abort()
  }, [tentativa])

  async function adicionar(evento) {
    evento.preventDefault()
    const nomeLimpo = nome.trim()
    if (!nomeLimpo) {
      setErroForm('Informe o nome da categoria.')
      return
    }
    setSalvando(true)
    setErroForm(null)
    try {
      const nova = await criarCategoria(nomeLimpo)
      setLista((atual) => ({ ...atual, categorias: ordenarPorNome([...atual.categorias, nova]) }))
      setNome('')
    } catch (erro) {
      setErroForm(mensagemDeErro(erro, 'Não foi possível adicionar a categoria. Tente de novo.'))
    } finally {
      setSalvando(false)
    }
  }

  function pedirConfirmacao(id) {
    setConfirmando(id)
    setErroLinha({ id: null, mensagem: null })
  }

  async function remover(categoria) {
    setRemovendo(categoria.id)
    try {
      await removerCategoria(categoria.id)
      setLista((atual) => ({ ...atual, categorias: atual.categorias.filter((c) => c.id !== categoria.id) }))
    } catch (erro) {
      setErroLinha({
        id: categoria.id,
        mensagem: mensagemDeErro(erro, 'Não foi possível remover a categoria. Tente de novo.'),
      })
    } finally {
      setRemovendo(null)
      setConfirmando(null)
    }
  }

  return (
    <section className="admin-categorias">
      <form className="admin-categorias__form" onSubmit={adicionar} noValidate>
        <label className="campo admin-categorias__campo">
          <span className="eyebrow">Nova categoria</span>
          <input
            className="campo__caixa"
            type="text"
            name="nome"
            placeholder="Ex: Acessórios"
            maxLength={80}
            autoComplete="off"
            value={nome}
            onChange={(e) => setNome(e.target.value)}
            disabled={salvando}
            aria-invalid={erroForm ? true : undefined}
            aria-describedby={erroForm ? 'erro-nova-categoria' : undefined}
          />
        </label>
        <button type="submit" className="botao botao--primario admin-categorias__adicionar" disabled={salvando}>
          {salvando ? 'Adicionando…' : '+ Adicionar'}
        </button>
      </form>

      {erroForm && (
        <p id="erro-nova-categoria" className="admin-categorias__aviso" role="alert">
          {erroForm}
        </p>
      )}

      {carregando && <p className="admin-categorias__estado">Carregando categorias…</p>}

      {!carregando && lista.erro && (
        <div className="admin-categorias__estado" role="alert">
          <p>{lista.erro}</p>
          <button type="button" className="botao botao--contorno" onClick={() => setTentativa((t) => t + 1)}>
            Tentar de novo
          </button>
        </div>
      )}

      {!carregando && !lista.erro && lista.categorias.length === 0 && (
        <p className="admin-categorias__estado">Nenhuma categoria cadastrada ainda. Adicione a primeira acima.</p>
      )}

      {!carregando && !lista.erro && lista.categorias.length > 0 && (
        <ul className="admin-categorias__lista">
          {lista.categorias.map((categoria) => (
            <li key={categoria.id} className="admin-categorias__item">
              <div className="admin-categorias__linha">
                <span className="admin-categorias__nome">{categoria.nome}</span>

                {confirmando === categoria.id ? (
                  <span className="admin-categorias__acoes">
                    <span className="admin-categorias__pergunta">Remover?</span>
                    <button
                      type="button"
                      className="admin-categorias__acao admin-categorias__acao--forte"
                      onClick={() => remover(categoria)}
                      disabled={removendo === categoria.id}
                    >
                      {removendo === categoria.id ? 'Removendo…' : 'Sim, remover'}
                    </button>
                    <button
                      type="button"
                      className="admin-categorias__acao"
                      onClick={() => setConfirmando(null)}
                      disabled={removendo === categoria.id}
                    >
                      Cancelar
                    </button>
                  </span>
                ) : (
                  <span className="admin-categorias__acoes">
                    <a
                      className="admin-categorias__acao"
                      href={`/admin/categorias/${categoria.id}/medidas`}
                      aria-label={`Tabela de medidas de ${categoria.nome}`}
                    >
                      Medidas
                    </a>
                    <button
                      type="button"
                      className="admin-categorias__acao"
                      onClick={() => pedirConfirmacao(categoria.id)}
                      aria-label={`Remover ${categoria.nome}`}
                    >
                      Remover
                    </button>
                  </span>
                )}
              </div>

              {erroLinha.id === categoria.id && (
                <p className="admin-categorias__aviso" role="alert">
                  {erroLinha.mensagem}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
