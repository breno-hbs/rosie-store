import { useEffect, useState } from 'react'
import {
  ApiError,
  atualizarProduto,
  buscarProdutoAdmin,
  desativarProduto,
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

// Lista do painel (Figma: "Admin — Lista de Produtos"). Mostra também os inativos,
// que podem ser reativados; "Remover" só desativa, com confirmação na própria linha.
export default function AdminProdutos() {
  const [produtos, setProdutos] = useState(null)
  const [erro, setErro] = useState(null)
  const [tentativa, setTentativa] = useState(0)
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

  async function remover(produto) {
    setOcupado(produto.id)
    setAviso(null)
    try {
      await desativarProduto(produto.id)
      trocarAtivo(produto.id, false)
      setAviso(`${produto.nome} saiu da loja. Dá para reativar quando quiser.`)
    } catch (e) {
      setAviso(mensagemDeErro(e, 'Não foi possível remover o produto.'))
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
            {produtos.map((produto) => (
              <tr key={produto.id} className={produto.ativo ? undefined : 'admin-produtos__inativo'}>
                <td className="admin-produtos__nome">
                  {produto.nome}
                  {!produto.ativo && <span className="rotulo admin-produtos__marca">Inativo</span>}
                  {produto.destaque && <span className="rotulo admin-produtos__marca">Destaque</span>}
                </td>
                <td data-rotulo="Categoria">{produto.categoria.nome}</td>
                <td data-rotulo="Preço">{formatarPreco(produto.preco)}</td>
                <td data-rotulo="Estoque">{produto.estoqueTotal} un.</td>
                <td className="admin-produtos__acoes">
                  {confirmando === produto.id ? (
                    <span role="group" aria-label={`Confirmar remoção de ${produto.nome}`}>
                      Tirar da loja?{' '}
                      <button
                        type="button"
                        className="admin-produtos__acao"
                        onClick={() => remover(produto)}
                        disabled={ocupado === produto.id}
                      >
                        Sim
                      </button>
                      {' · '}
                      <button
                        type="button"
                        className="admin-produtos__acao"
                        onClick={() => setConfirmando(null)}
                        disabled={ocupado === produto.id}
                      >
                        Não
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
                          onClick={() => setConfirmando(produto.id)}
                        >
                          Remover
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
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
