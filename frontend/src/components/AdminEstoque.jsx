import { useEffect, useState } from 'react'
import { ApiError, atualizarEstoque, listarProdutosAdmin } from '../api.js'
import './AdminEstoque.css'

const TAMANHOS = ['P', 'M', 'G', 'GG']

function mensagemDeErro(erro, padrao) {
  if (erro instanceof ApiError && erro.codigo === 'SEM_CONEXAO') {
    return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  }
  return erro instanceof ApiError ? erro.message : padrao
}

// [{ tamanho, quantidade }] → { P: 3, M: 5 } (só os tamanhos cadastrados)
function porTamanho(estoque) {
  return Object.fromEntries(estoque.map((item) => [item.tamanho, item.quantidade]))
}

// Valor dos campos: tamanho não cadastrado fica vazio e mostra "—"
function paraCampos(quantidades) {
  return Object.fromEntries(TAMANHOS.map((t) => [t, t in quantidades ? String(quantidades[t]) : '']))
}

// Confere os tamanhos alterados antes de mandar; devolve a mensagem do primeiro problema
function validar(valores, alterados) {
  for (const tamanho of alterados) {
    const valor = valores[tamanho].trim()
    if (valor === '') return `Informe a quantidade de ${tamanho} (0 = esgotado). Para tirar o tamanho, edite o produto.`
    if (valor.startsWith('-')) return 'A quantidade não pode ser negativa.'
    if (!/^\d+$/.test(valor)) return `Use só números inteiros em ${tamanho}.`
  }
  return null
}

// Uma linha por produto: edita P, M, G e GG e salva só os tamanhos que mudaram
function LinhaEstoque({ produto }) {
  const [salvo, setSalvo] = useState(() => porTamanho(produto.estoque))
  const [valores, setValores] = useState(() => paraCampos(porTamanho(produto.estoque)))
  const [salvando, setSalvando] = useState(false)
  const [aviso, setAviso] = useState({ tipo: null, mensagem: null })

  const original = paraCampos(salvo)
  const alterados = TAMANHOS.filter((t) => valores[t].trim() !== original[t])
  const idAviso = `estoque-aviso-${produto.id}`

  function alterar(tamanho, valor) {
    setValores((atual) => ({ ...atual, [tamanho]: valor }))
    if (aviso.tipo === 'sucesso') setAviso({ tipo: null, mensagem: null })
  }

  async function salvar() {
    if (alterados.length === 0 || salvando) return
    const problema = validar(valores, alterados)
    if (problema) {
      setAviso({ tipo: 'erro', mensagem: problema })
      return
    }

    setSalvando(true)
    setAviso({ tipo: null, mensagem: null })
    try {
      const resposta = await atualizarEstoque(
        produto.id,
        alterados.map((tamanho) => ({ tamanho, quantidade: Number(valores[tamanho].trim()) })),
      )
      const novo = porTamanho(resposta.estoque)
      const esgotado = resposta.estoque.every((item) => item.quantidade === 0)
      setSalvo(novo)
      setValores(paraCampos(novo))
      setAviso({
        tipo: 'sucesso',
        mensagem: esgotado ? 'Estoque salvo. Sem nenhum tamanho disponível, aparece como esgotado na loja.' : 'Estoque salvo.',
      })
    } catch (erro) {
      setAviso({ tipo: 'erro', mensagem: mensagemDeErro(erro, 'Não foi possível salvar o estoque. Tente de novo.') })
    } finally {
      setSalvando(false)
    }
  }

  return (
    <tbody className={produto.ativo ? 'admin-estoque__produto' : 'admin-estoque__produto admin-estoque__produto--inativo'}>
      <tr>
        <th scope="row" className="admin-estoque__nome">
          {produto.nome}
          {!produto.ativo && <span className="rotulo admin-estoque__marca">Arquivado</span>}
        </th>
        {TAMANHOS.map((tamanho) => (
          <td key={tamanho} className="admin-estoque__tamanho" data-rotulo={tamanho}>
            <input
              className={
                valores[tamanho].trim() !== original[tamanho]
                  ? 'admin-estoque__campo admin-estoque__campo--alterado'
                  : 'admin-estoque__campo'
              }
              type="text"
              inputMode="numeric"
              maxLength={5}
              placeholder="—"
              value={valores[tamanho]}
              onChange={(e) => alterar(tamanho, e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && salvar()}
              disabled={salvando}
              aria-label={`${produto.nome}, tamanho ${tamanho}${tamanho in salvo ? '' : ' (não cadastrado)'}`}
              aria-invalid={aviso.tipo === 'erro' && alterados.includes(tamanho) ? true : undefined}
              aria-describedby={aviso.mensagem ? idAviso : undefined}
            />
          </td>
        ))}
        <td className="admin-estoque__acoes">
          <button
            type="button"
            className="admin-estoque__salvar"
            onClick={salvar}
            disabled={salvando || alterados.length === 0}
            aria-label={`Salvar estoque de ${produto.nome}`}
          >
            {salvando ? 'Salvando…' : 'Salvar'}
          </button>
        </td>
      </tr>
      {aviso.mensagem && (
        <tr className="admin-estoque__linha-aviso">
          <td colSpan={TAMANHOS.length + 2}>
            <p
              id={idAviso}
              className={`admin-estoque__aviso admin-estoque__aviso--${aviso.tipo}`}
              role={aviso.tipo === 'erro' ? 'alert' : 'status'}
            >
              {aviso.mensagem}
            </p>
          </td>
        </tr>
      )}
    </tbody>
  )
}

// Gestão de estoque (Figma: frame "Admin — Gestão de Estoque", RF16). Usa o estoque por tamanho
// que já vem em GET /admin/produtos e salva cada produto com PUT /admin/produtos/{id}/estoque.
export default function AdminEstoque() {
  const [tentativa, setTentativa] = useState(0)
  // Enquanto `lista.tentativa` não for a atual, está carregando
  const [lista, setLista] = useState({ tentativa: null, produtos: [], erro: null })

  const carregando = lista.tentativa !== tentativa

  useEffect(() => {
    const controle = new AbortController()
    listarProdutosAdmin({ signal: controle.signal })
      .then((produtos) => setLista({ tentativa, produtos, erro: null }))
      .catch((erro) => {
        if (erro.name === 'AbortError') return
        setLista({ tentativa, produtos: [], erro: mensagemDeErro(erro, 'Não foi possível carregar o estoque.') })
      })
    return () => controle.abort()
  }, [tentativa])

  return (
    <section className="admin-estoque">
      <h1 className="titulo-2">Gestão de Estoque</h1>

      {carregando && <p className="admin-estoque__estado">Carregando estoque…</p>}

      {!carregando && lista.erro && (
        <div className="admin-estoque__estado" role="alert">
          <p>{lista.erro}</p>
          <button type="button" className="botao botao--contorno" onClick={() => setTentativa((t) => t + 1)}>
            Tentar de novo
          </button>
        </div>
      )}

      {!carregando && !lista.erro && lista.produtos.length === 0 && (
        <p className="admin-estoque__estado">
          Nenhum produto cadastrado ainda. Cadastre em <a href="/admin/produtos/novo">Produtos</a> para controlar o
          estoque.
        </p>
      )}

      {!carregando && !lista.erro && lista.produtos.length > 0 && (
        <>
          <p className="admin-estoque__dica">
            “—” é tamanho que o produto não tem: digite uma quantidade para cadastrar. Use 0 para esgotado.
          </p>
          <table className="admin-estoque__tabela">
            <thead>
              <tr className="rotulo">
                <th scope="col">Produto</th>
                {TAMANHOS.map((tamanho) => (
                  <th key={tamanho} scope="col">
                    {tamanho}
                  </th>
                ))}
                <th scope="col">
                  <span className="admin-estoque__oculto">Ações</span>
                </th>
              </tr>
            </thead>
            {lista.produtos.map((produto) => (
              <LinhaEstoque key={produto.id} produto={produto} />
            ))}
          </table>
        </>
      )}
    </section>
  )
}
