import { useEffect, useRef, useState } from 'react'
import { ApiError, buscarTabelaMedidasAdmin, removerTabelaMedidas, salvarTabelaMedidas } from '../api.js'
import './AdminMedidas.css'

const TAMANHOS = ['P', 'M', 'G', 'GG']
const MAXIMO_LINHAS = 20
const SUGESTAO = ['Busto', 'Cintura', 'Quadril', 'Comprimento']

function mensagemDeErro(erro, padrao) {
  if (erro instanceof ApiError && erro.codigo === 'SEM_CONEXAO') {
    return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  }
  return erro instanceof ApiError ? erro.message : padrao
}

// Cada linha leva uma chave local, para o React não trocar os campos de lugar ao reordenar
let ultimaChave = 0
function novaLinha(nome = '', valores = {}) {
  ultimaChave += 1
  return { chave: ultimaChave, nome, valores: Object.fromEntries(TAMANHOS.map((t) => [t, valores[t] ?? ''])) }
}

// Body do PUT, sem espaços nas pontas: também serve para saber se há mudança não salva
function paraEnvio(linhas) {
  return linhas.map((linha) => ({
    nome: linha.nome.trim(),
    valores: Object.fromEntries(TAMANHOS.map((t) => [t, linha.valores[t].trim()])),
  }))
}

// Mesmas regras da API, para a Roseane ver o problema sem esperar o servidor
function validar(linhas) {
  const vistos = new Set()
  for (const [i, linha] of linhas.entries()) {
    const nome = linha.nome.trim()
    if (!nome) return { chave: linha.chave, mensagem: `Informe o nome da medida na linha ${i + 1}.` }
    if (vistos.has(nome.toLowerCase())) return { chave: linha.chave, mensagem: `A medida "${nome}" aparece mais de uma vez.` }
    vistos.add(nome.toLowerCase())
  }
  return null
}

// Tabela de medidas de uma categoria (#20). Não há frame no Figma: segue a Gestão de Estoque
// (linhas com P, M, G e GG) e salva a tabela inteira de uma vez com PUT /admin/categorias/{id}/medidas.
export default function AdminMedidas({ categoriaId }) {
  const [tentativa, setTentativa] = useState(0)
  // Enquanto `carga.tentativa` não for a atual, está carregando
  const [carga, setCarga] = useState({ tentativa: null, categoria: null, erro: null, naoEncontrada: false })
  const [linhas, setLinhas] = useState([])
  const [salvo, setSalvo] = useState('[]')
  const [salvando, setSalvando] = useState(false)
  const [aviso, setAviso] = useState({ tipo: null, mensagem: null, chave: null })
  const [confirmandoApagar, setConfirmandoApagar] = useState(false)
  const [apagando, setApagando] = useState(false)
  // Depois de adicionar, mover ou remover linha, o foco vai para o campo ou botão certo (valor de data-foco)
  const focoPendente = useRef(null)
  const raiz = useRef(null)

  const carregando = carga.tentativa !== tentativa
  const atual = JSON.stringify(paraEnvio(linhas))
  const alterado = atual !== salvo
  const temTabelaSalva = salvo !== '[]'
  const ocupado = salvando || apagando

  useEffect(() => {
    const controle = new AbortController()
    buscarTabelaMedidasAdmin(categoriaId, { signal: controle.signal })
      .then((tabela) => {
        const carregadas = tabela.medidas.map((m) => novaLinha(m.nome, m.valores))
        setLinhas(carregadas)
        setSalvo(JSON.stringify(paraEnvio(carregadas)))
        setCarga({ tentativa, categoria: tabela.categoria, erro: null, naoEncontrada: false })
      })
      .catch((erro) => {
        if (erro.name === 'AbortError') return
        setCarga({
          tentativa,
          categoria: null,
          erro: mensagemDeErro(erro, 'Não foi possível carregar a tabela de medidas.'),
          naoEncontrada: erro instanceof ApiError && erro.status === 404,
        })
      })
    return () => controle.abort()
  }, [categoriaId, tentativa])

  useEffect(() => {
    if (carga.categoria) document.title = `Medidas de ${carga.categoria} — Painel Rosie`
  }, [carga.categoria])

  // Sem dependências: roda depois de cada render e aplica o foco pedido pelo último clique
  useEffect(() => {
    if (!focoPendente.current) return
    raiz.current?.querySelector(`[data-foco="${focoPendente.current}"]`)?.focus()
    focoPendente.current = null
  })

  // Avisa antes de sair da página com mudança não salva
  useEffect(() => {
    if (!alterado) return
    const segurar = (evento) => evento.preventDefault()
    window.addEventListener('beforeunload', segurar)
    return () => window.removeEventListener('beforeunload', segurar)
  }, [alterado])

  function mudar(novas) {
    setLinhas(novas)
    setConfirmandoApagar(false)
    if (aviso.tipo === 'sucesso') setAviso({ tipo: null, mensagem: null, chave: null })
  }

  function alterarLinha(chave, campo, valor, tamanho) {
    mudar(
      linhas.map((linha) => {
        if (linha.chave !== chave) return linha
        if (campo === 'nome') return { ...linha, nome: valor }
        return { ...linha, valores: { ...linha.valores, [tamanho]: valor } }
      }),
    )
  }

  function adicionar() {
    const linha = novaLinha()
    mudar([...linhas, linha])
    focoPendente.current = `${linha.chave}-nome`
  }

  function comecarComSugestao() {
    const sugeridas = SUGESTAO.map((nome) => novaLinha(nome))
    mudar(sugeridas)
    focoPendente.current = `${sugeridas[0].chave}-P`
  }

  function mover(indice, direcao) {
    const destino = indice + direcao
    const novas = [...linhas]
    ;[novas[indice], novas[destino]] = [novas[destino], novas[indice]]
    mudar(novas)
    // Chegando na ponta, o botão usado fica desabilitado: o foco passa para o outro
    const naPonta = destino === 0 || destino === linhas.length - 1
    const botao = direcao < 0 ? (naPonta ? 'descer' : 'subir') : naPonta ? 'subir' : 'descer'
    focoPendente.current = linhas.length > 1 ? `${linhas[indice].chave}-${botao}` : null
  }

  function removerLinha(indice) {
    const vizinha = linhas[indice + 1] ?? linhas[indice - 1]
    mudar(linhas.filter((_, i) => i !== indice))
    focoPendente.current = vizinha ? `${vizinha.chave}-nome` : 'adicionar'
  }

  async function salvar() {
    if (!alterado || ocupado) return
    const problema = validar(linhas)
    if (problema) {
      setAviso({ tipo: 'erro', mensagem: problema.mensagem, chave: problema.chave })
      focoPendente.current = `${problema.chave}-nome`
      return
    }

    setSalvando(true)
    setAviso({ tipo: null, mensagem: null, chave: null })
    try {
      const tabela = await salvarTabelaMedidas(categoriaId, paraEnvio(linhas))
      // A resposta vem na mesma ordem: mantém as chaves para os campos não serem recriados
      const salvas = tabela.medidas.map((m, i) => ({ ...linhas[i], nome: m.nome, valores: m.valores }))
      setLinhas(salvas)
      setSalvo(JSON.stringify(paraEnvio(salvas)))
      setAviso({
        tipo: 'sucesso',
        mensagem:
          salvas.length === 0
            ? 'Tabela salva sem medidas: o link “Ver tabela de medidas” não aparece na loja.'
            : 'Tabela salva. Já aparece na página dos produtos desta categoria.',
        chave: null,
      })
    } catch (erro) {
      setAviso({
        tipo: 'erro',
        mensagem: mensagemDeErro(erro, 'Não foi possível salvar a tabela. Tente de novo.'),
        chave: null,
      })
    } finally {
      setSalvando(false)
    }
  }

  async function apagarTabela() {
    setApagando(true)
    setAviso({ tipo: null, mensagem: null, chave: null })
    try {
      await removerTabelaMedidas(categoriaId)
      setLinhas([])
      setSalvo('[]')
      setAviso({ tipo: 'sucesso', mensagem: 'Tabela apagada. O link “Ver tabela de medidas” saiu da loja.', chave: null })
      focoPendente.current = 'adicionar'
    } catch (erro) {
      setAviso({
        tipo: 'erro',
        mensagem: mensagemDeErro(erro, 'Não foi possível apagar a tabela. Tente de novo.'),
        chave: null,
      })
    } finally {
      setApagando(false)
      setConfirmandoApagar(false)
    }
  }

  const idAviso = 'medidas-aviso'
  const nomeLinha = (linha, i) => linha.nome.trim() || `medida ${i + 1}`

  return (
    <section className="admin-medidas" ref={raiz}>
      <a className="admin-medidas__voltar" href="/admin/categorias">
        ← Categorias
      </a>
      <h1 className="titulo-2">Tabela de medidas</h1>

      {carregando && <p className="admin-medidas__estado">Carregando tabela…</p>}

      {!carregando && carga.erro && (
        <div className="admin-medidas__estado" role="alert">
          <p>{carga.naoEncontrada ? 'Categoria não encontrada.' : carga.erro}</p>
          {carga.naoEncontrada ? (
            <a className="botao botao--contorno" href="/admin/categorias">
              Voltar para Categorias
            </a>
          ) : (
            <button type="button" className="botao botao--contorno" onClick={() => setTentativa((t) => t + 1)}>
              Tentar de novo
            </button>
          )}
        </div>
      )}

      {!carregando && !carga.erro && (
        <>
          <div className="admin-medidas__cabecalho">
            <p className="eyebrow">{carga.categoria}</p>
            <p className="admin-medidas__dica">
              Valores em cm. Aceita faixas, como 84–88. Tamanho sem medida fica em branco e aparece como “—” na loja.
            </p>
          </div>

          {linhas.length === 0 ? (
            <div className="admin-medidas__estado">
              <p>
                {carga.categoria} ainda não tem tabela de medidas: o link “Ver tabela de medidas” não aparece na
                página dos produtos.
              </p>
              <button type="button" className="botao botao--contorno" onClick={comecarComSugestao} disabled={ocupado}>
                Começar com Busto, Cintura, Quadril e Comprimento
              </button>
            </div>
          ) : (
            <table className="admin-medidas__tabela">
              <caption className="admin-medidas__oculto">Medidas de {carga.categoria}, em cm</caption>
              <thead>
                <tr className="rotulo">
                  <th scope="col">Medida</th>
                  {TAMANHOS.map((tamanho) => (
                    <th key={tamanho} scope="col">
                      {tamanho}
                    </th>
                  ))}
                  <th scope="col">
                    <span className="admin-medidas__oculto">Ações</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {linhas.map((linha, i) => (
                  <tr key={linha.chave} className="admin-medidas__linha">
                    <td className="admin-medidas__nome">
                      <input
                        className="admin-medidas__campo admin-medidas__campo--nome"
                        type="text"
                        maxLength={40}
                        placeholder="Ex: Busto"
                        autoComplete="off"
                        value={linha.nome}
                        onChange={(e) => alterarLinha(linha.chave, 'nome', e.target.value)}
                        disabled={ocupado}
                        data-foco={`${linha.chave}-nome`}
                        aria-label={`Nome da medida ${i + 1}`}
                        aria-invalid={aviso.chave === linha.chave ? true : undefined}
                        aria-describedby={aviso.chave === linha.chave ? idAviso : undefined}
                      />
                    </td>
                    {TAMANHOS.map((tamanho) => (
                      <td key={tamanho} className="admin-medidas__valor" data-rotulo={tamanho}>
                        <input
                          className="admin-medidas__campo"
                          type="text"
                          maxLength={20}
                          placeholder="—"
                          autoComplete="off"
                          value={linha.valores[tamanho]}
                          onChange={(e) => alterarLinha(linha.chave, 'valor', e.target.value, tamanho)}
                          disabled={ocupado}
                          data-foco={`${linha.chave}-${tamanho}`}
                          aria-label={`${nomeLinha(linha, i)}, tamanho ${tamanho}, em cm`}
                        />
                      </td>
                    ))}
                    <td className="admin-medidas__acoes">
                      <button
                        type="button"
                        className="admin-medidas__acao"
                        onClick={() => mover(i, -1)}
                        disabled={ocupado || i === 0}
                        data-foco={`${linha.chave}-subir`}
                        aria-label={`Subir ${nomeLinha(linha, i)}`}
                      >
                        ↑
                      </button>
                      <button
                        type="button"
                        className="admin-medidas__acao"
                        onClick={() => mover(i, 1)}
                        disabled={ocupado || i === linhas.length - 1}
                        data-foco={`${linha.chave}-descer`}
                        aria-label={`Descer ${nomeLinha(linha, i)}`}
                      >
                        ↓
                      </button>
                      <button
                        type="button"
                        className="admin-medidas__acao admin-medidas__acao--texto"
                        onClick={() => removerLinha(i)}
                        disabled={ocupado}
                        aria-label={`Remover ${nomeLinha(linha, i)}`}
                      >
                        Remover
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <button
            type="button"
            className="admin-medidas__adicionar"
            onClick={adicionar}
            disabled={ocupado || linhas.length >= MAXIMO_LINHAS}
            data-foco="adicionar"
          >
            + Adicionar medida
          </button>
          {linhas.length >= MAXIMO_LINHAS && (
            <p className="admin-medidas__dica">A tabela pode ter no máximo {MAXIMO_LINHAS} medidas.</p>
          )}

          {aviso.mensagem && (
            <p
              id={idAviso}
              className={`admin-medidas__aviso admin-medidas__aviso--${aviso.tipo}`}
              role={aviso.tipo === 'erro' ? 'alert' : 'status'}
            >
              {aviso.mensagem}
            </p>
          )}

          <div className="admin-medidas__rodape">
            <button type="button" className="botao botao--primario" onClick={salvar} disabled={!alterado || ocupado}>
              {salvando ? 'Salvando…' : 'Salvar tabela'}
            </button>

            {temTabelaSalva &&
              (confirmandoApagar ? (
                <span className="admin-medidas__confirmar">
                  <span className="admin-medidas__pergunta">Apagar a tabela inteira?</span>
                  <button
                    type="button"
                    className="admin-medidas__acao admin-medidas__acao--forte"
                    onClick={apagarTabela}
                    disabled={apagando}
                  >
                    {apagando ? 'Apagando…' : 'Sim, apagar'}
                  </button>
                  <button
                    type="button"
                    className="admin-medidas__acao admin-medidas__acao--texto"
                    onClick={() => setConfirmandoApagar(false)}
                    disabled={apagando}
                  >
                    Cancelar
                  </button>
                </span>
              ) : (
                <button
                  type="button"
                  className="admin-medidas__acao admin-medidas__acao--texto"
                  onClick={() => setConfirmandoApagar(true)}
                  disabled={ocupado}
                >
                  Apagar tabela
                </button>
              ))}
          </div>
        </>
      )}
    </section>
  )
}
