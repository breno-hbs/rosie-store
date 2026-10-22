import { useEffect, useState } from 'react'
import {
  ApiError,
  arquivarProduto,
  atualizarProduto,
  buscarProdutoAdmin,
  excluirProduto,
  listarProdutosAdmin,
} from '../api.js'
import { formatarPreco } from '../formato.js'
import './AdminProdutos.css'

function mensagemDeErro(erro, padrao) {
  if (erro instanceof ApiError && erro.codigo === 'SEM_CONEXAO') {
    return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  }
  return erro instanceof ApiError ? erro.message : padrao
}

// Texto e botões de cada confirmação, feita na própria linha. Arquivar se desfaz (Reativar);
// excluir não, por isso a pergunta é outra e o botão diz o que faz (#87).
const CONFIRMACOES = {
  arquivar: { pergunta: 'Tirar da loja? Dá para reativar depois.', sim: 'Arquivar', nao: 'Não' },
  excluir: { pergunta: 'Excluir de vez? Não dá para desfazer.', sim: 'Excluir', nao: 'Cancelar' },
}

// Lista do painel (Figma: "Admin — Lista de Produtos"). "Arquivar" tira da loja sem apagar (o produto
// pode ser reativado); "Excluir" apaga de vez, só para quem nunca entrou em pedido nem troca. Os
// arquivados ficam escondidos até marcar "Mostrar arquivados".
export default function AdminProdutos() {
  const [produtos, setProdutos] = useState(null)
  const [erro, setErro] = useState(null)
  const [tentativa, setTentativa] = useState(0)
  const [mostrarArquivados, setMostrarArquivados] = useState(false)
  // { id, acao: 'arquivar' | 'excluir' }
  const [confirmando, setConfirmando] = useState(null)
  const [ocupado, setOcupado] = useState(null)
  const [aviso, setAviso] = useState(() =>
    new URLSearchParams(window.location.search).get('salvo') ? 'Produto salvo.' : null,
  )

  useEffect(() => {
    const controle = new AbortController()
    listarProdutosAdmin({ signal: controle.signal })
      .then((lista) => {
        setProdutos(lista)
        setErro(null)
      })
      .catch((e) => {
        if (e.name !== 'AbortError') setErro(mensagemDeErro(e, 'Não foi possível carregar os produtos.'))
      })
    return () => controle.abort()
  }, [tentativa])

  function tentarDeNovo() {
    setErro(null)
    setProdutos(null)
    setTentativa((t) => t + 1)
  }

  function trocarAtivo(id, ativo) {
    setProdutos((lista) => lista.map((p) => (p.id === id ? { ...p, ativo } : p)))
  }

  async function arquivar(produto) {
    setOcupado(produto.id)
    setAviso(null)
    try {
      await arquivarProduto(produto.id)
      trocarAtivo(produto.id, false)
      setAviso(
        mostrarArquivados
          ? `${produto.nome} foi arquivado e saiu da loja. Dá para reativar quando quiser.`
          : `${produto.nome} foi arquivado e saiu da loja. Para reativar, marque “Mostrar arquivados”.`,
      )
    } catch (e) {
      setAviso(mensagemDeErro(e, 'Não foi possível arquivar o produto.'))
    } finally {
      setConfirmando(null)
      setOcupado(null)
    }
  }

  // Produto com pedido ou troca volta 409 PRODUTO_COM_HISTORICO, com a mensagem que sugere arquivar
  async function excluir(produto) {
    setOcupado(produto.id)
    setAviso(null)
    try {
      await excluirProduto(produto.id)
      setProdutos((lista) => lista.filter((p) => p.id !== produto.id))
      setAviso(`${produto.nome} foi excluído.`)
    } catch (e) {
      setAviso(mensagemDeErro(e, 'Não foi possível excluir o produto.'))
    } finally {
      setConfirmando(null)
      setOcupado(null)
    }
  }

  // O PUT pede os dados do produto: busca o detalhe e manda de volta com ativo = true (sem o
  // estoque, que o PUT não altera)
  async function reativar(produto) {
    setOcupado(produto.id)
    setAviso(null)
    try {
      const atual = await buscarProdutoAdmin(produto.id)
      await atualizarProduto(produto.id, {
        nome: atual.nome,
        descricao: atual.descricao,
        composicao: atual.composicao,
        preco: atual.preco,
        categoriaId: atual.categoria.id,
        destaque: atual.destaque,
        ativo: true,
      })
      trocarAtivo(produto.id, true)
      setAviso(`${produto.nome} voltou para a loja.`)
    } catch (e) {
      setAviso(mensagemDeErro(e, 'Não foi possível reativar o produto.'))
    } finally {
      setOcupado(null)
    }
  }

  function confirmar(produto) {
    if (confirmando.acao === 'excluir') excluir(produto)
    else arquivar(produto)
  }

  const arquivados = produtos ? produtos.filter((p) => !p.ativo).length : 0
  const visiveis = produtos ? produtos.filter((p) => p.ativo || mostrarArquivados) : []

  return (
    <section className="admin-produtos">
      <header className="admin-produtos__topo">
        <h1 className="titulo-2">Produtos</h1>
        <a href="/admin/produtos/novo" className="botao botao--primario admin-produtos__novo">
          + Novo produto
        </a>
      </header>

      {aviso && (
        <p className="admin-produtos__aviso" role="status">
          {aviso}
        </p>
      )}

      {erro ? (
        <div className="admin-produtos__estado" role="alert">
          <p>{erro}</p>
          <button type="button" className="botao botao--contorno" onClick={tentarDeNovo}>
            Tentar de novo
          </button>
        </div>
      ) : produtos === null ? (
        <p className="admin-produtos__estado">Carregando produtos…</p>
      ) : produtos.length === 0 ? (
        <p className="admin-produtos__estado">
          Nenhum produto cadastrado ainda. Use “Novo produto” para cadastrar o primeiro.
        </p>
      ) : (
        <>
          <label className="admin-produtos__filtro">
            <input
              type="checkbox"
              checked={mostrarArquivados}
              onChange={(e) => setMostrarArquivados(e.target.checked)}
            />
            Mostrar arquivados ({arquivados})
          </label>

          {visiveis.length === 0 ? (
            <p className="admin-produtos__estado">
              Nenhum produto à venda. Marque “Mostrar arquivados” para ver os arquivados.
            </p>
          ) : (
            <table className="admin-produtos__tabela">
              <thead>
                <tr className="rotulo">
                  <th scope="col">Produto</th>
                  <th scope="col">Categoria</th>
                  <th scope="col">Preço</th>
                  <th scope="col">Estoque</th>
                  <th scope="col">Ações</th>
                </tr>
              </thead>
              <tbody>
                {visiveis.map((produto) => (
                  <tr key={produto.id} className={produto.ativo ? undefined : 'admin-produtos__inativo'}>
                    <td className="admin-produtos__nome">
                      {produto.nome}
                      {!produto.ativo && <span className="rotulo admin-produtos__marca">Arquivado</span>}
                      {produto.destaque && <span className="rotulo admin-produtos__marca">Destaque</span>}
                    </td>
                    <td data-rotulo="Categoria">{produto.categoria.nome}</td>
                    <td data-rotulo="Preço">{formatarPreco(produto.preco)}</td>
                    <td data-rotulo="Estoque">{produto.estoqueTotal} un.</td>
                    <td className="admin-produtos__acoes">
                      {confirmando?.id === produto.id ? (
                        <span
                          role="group"
                          aria-label={
                            confirmando.acao === 'excluir'
                              ? `Confirmar exclusão de ${produto.nome}`
                              : `Confirmar arquivamento de ${produto.nome}`
                          }
                        >
                          {CONFIRMACOES[confirmando.acao].pergunta}{' '}
                          <button
                            type="button"
                            className="admin-produtos__acao"
                            onClick={() => confirmar(produto)}
                            disabled={ocupado === produto.id}
                          >
                            {CONFIRMACOES[confirmando.acao].sim}
                          </button>
                          {' · '}
                          <button
                            type="button"
                            className="admin-produtos__acao"
                            onClick={() => setConfirmando(null)}
                            disabled={ocupado === produto.id}
                          >
                            {CONFIRMACOES[confirmando.acao].nao}
                          </button>
                        </span>
                      ) : (
                        <>
                          <a href={`/admin/produtos/${produto.id}`} className="admin-produtos__acao">
                            Editar
                          </a>
                          {' · '}
                          {produto.ativo ? (
                            <button
                              type="button"
                              className="admin-produtos__acao"
                              onClick={() => setConfirmando({ id: produto.id, acao: 'arquivar' })}
                            >
                              Arquivar
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="admin-produtos__acao"
                              onClick={() => reativar(produto)}
                              disabled={ocupado === produto.id}
                            >
                              Reativar
                            </button>
                          )}
                          {' · '}
                          <button
                            type="button"
                            className="admin-produtos__acao"
                            onClick={() => setConfirmando({ id: produto.id, acao: 'excluir' })}
                            disabled={ocupado === produto.id}
                          >
                            Excluir
                          </button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </section>
  )
}
