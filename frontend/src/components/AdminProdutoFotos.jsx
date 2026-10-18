import { useEffect, useRef, useState } from 'react'
import { ApiError, enviarImagemProduto, reordenarImagensProduto, removerImagemProduto } from '../api.js'
import './AdminProdutoFotos.css'

// Mesmos limites da API (docs/contrato-api.md, "Upload de imagem"): conferidos aqui só para avisar
// antes de enviar; quem decide é o servidor
const TIPOS_ACEITOS = ['image/jpeg', 'image/png', 'image/webp']
const TAMANHO_MAXIMO = 5 * 1024 * 1024

// A loja mostra as fotos em 4:5 (RNF09): o Cloudinary guarda a original e entrega cortada (#18).
// Até 1% de diferença já conta como 4:5 e vai direto, sem prévia.
const PROPORCAO = 4 / 5
const TOLERANCIA = 0.01
// Altura máxima da prévia do corte, para foto muito alta não empurrar o formulário
const ALTURA_PREVIA = 420

const ENQUADRAMENTOS = [
  {
    valor: 'AUTOMATICO',
    rotulo: 'Automático',
    dica: 'O Cloudinary procura a parte principal da foto. A moldura mostra o corte pelo centro; o resultado pode variar um pouco.',
  },
  { valor: 'CENTRO', rotulo: 'Centro', dica: 'Corta por igual dos dois lados.' },
  { valor: 'TOPO', rotulo: 'Topo', dica: 'Guarda a parte de cima e corta só embaixo: bom para foto de corpo inteiro.' },
]

const SELOS = { medindo: 'Preparando…', fila: 'Na fila', enviando: 'Enviando…', erro: 'Não enviada' }

let proximaChave = 0
const novaChave = () => `foto-${++proximaChave}`

function daApi(imagem) {
  return { chave: novaChave(), id: imagem.id, url: imagem.url, estado: 'pronta' }
}

function problemaNoArquivo(arquivo) {
  if (!TIPOS_ACEITOS.includes(arquivo.type)) return 'Formato não aceito: envie a foto em JPG, PNG ou WEBP'
  if (arquivo.size > TAMANHO_MAXIMO) return 'A foto passa de 5 MB: diminua o arquivo e tente de novo'
  return null
}

// Dimensões reais da foto escolhida; null se o navegador não conseguir abrir (o servidor decide)
function medir(url) {
  return new Promise((resolver) => {
    const imagem = new Image()
    imagem.onload = () => resolver({ largura: imagem.naturalWidth, altura: imagem.naturalHeight })
    imagem.onerror = () => resolver(null)
    imagem.src = url
  })
}

function foraDe4por5(medida) {
  if (!medida?.largura || !medida?.altura) return false
  return Math.abs(medida.largura / medida.altura / PROPORCAO - 1) > TOLERANCIA
}

function maisLarga(foto) {
  return foto.largura / foto.altura > PROPORCAO
}

// Parte da foto que fica no corte 4:5, em % da foto inteira, como o c_fill do Cloudinary:
// foto mais larga perde os lados (sempre pelo centro); mais alta perde em cima e embaixo, ou só
// embaixo no TOPO. O AUTOMATICO não dá para prever aqui e aparece como o centro.
function areaDoCorte(foto) {
  const proporcao = foto.largura / foto.altura
  if (proporcao > PROPORCAO) {
    const largura = (PROPORCAO / proporcao) * 100
    return { top: 0, left: `${(100 - largura) / 2}%`, width: `${largura}%`, height: '100%' }
  }
  const altura = (proporcao / PROPORCAO) * 100
  const topo = foto.enquadramento === 'TOPO' ? 0 : (100 - altura) / 2
  return { top: `${topo}%`, left: 0, width: '100%', height: `${altura}%` }
}

function mensagemDeErro(erro, padrao) {
  if (erro instanceof ApiError && erro.codigo === 'SEM_CONEXAO') {
    return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  }
  return erro instanceof ApiError ? erro.message : padrao
}

// Galeria de fotos do formulário de produto (Figma: "Admin — Cadastro de Produto", coluna da esquerda).
// As fotos são salvas na hora, uma de cada vez e na ordem escolhida; a primeira é a principal.
// Foto fora de 4:5 espera: mostra a moldura do corte e o enquadramento antes de enviar (#18).
// produtoId null = produto ainda não salvo: o upload só é liberado depois do primeiro "Salvar".
export default function AdminProdutoFotos({ produtoId, imagensIniciais = [] }) {
  const [fotos, setFotos] = useState(() => imagensIniciais.map(daApi))
  const [confirmando, setConfirmando] = useState(null)
  const [removendo, setRemovendo] = useState(null)
  const [reordenando, setReordenando] = useState(false)
  const [erro, setErro] = useState(null)
  const seletor = useRef(null)
  const fila = useRef(Promise.resolve())
  const previas = useRef(new Set())

  // Libera as prévias locais que ainda estiverem abertas ao sair da tela
  useEffect(() => {
    const abertas = previas.current
    return () => abertas.forEach((url) => URL.revokeObjectURL(url))
  }, [])

  const prontas = fotos.filter((foto) => foto.estado === 'pronta')
  const pendentes = fotos.filter((foto) => foto.estado !== 'pronta')
  const enviando = pendentes.some((foto) => ['medindo', 'fila', 'enviando'].includes(foto.estado))
  // Reordenar com upload em andamento mandaria uma lista sem a foto nova (a API recusa)
  const ordemBloqueada = enviando || reordenando || removendo !== null

  function alterarFoto(chave, mudancas) {
    setFotos((atuais) => atuais.map((foto) => (foto.chave === chave ? { ...foto, ...mudancas } : foto)))
  }

  function soltarPrevia(foto) {
    if (!foto?.previa) return
    URL.revokeObjectURL(foto.previa)
    previas.current.delete(foto.previa)
  }

  function enfileirar(chave, arquivo, enquadramento) {
    fila.current = fila.current.then(async () => {
      alterarFoto(chave, { estado: 'enviando', erro: null })
      try {
        const imagem = await enviarImagemProduto(produtoId, arquivo, enquadramento)
        setFotos((atuais) =>
          atuais.map((foto) => {
            if (foto.chave !== chave) return foto
            soltarPrevia(foto)
            return { chave, id: imagem.id, url: imagem.url, estado: 'pronta' }
          }),
        )
      } catch (e) {
        alterarFoto(chave, { estado: 'erro', erro: mensagemDeErro(e, 'Não foi possível enviar a foto.'), podeTentar: true })
      }
    })
  }

  async function adicionar(evento) {
    const arquivos = Array.from(evento.target.files ?? [])
    evento.target.value = ''
    setErro(null)

    const novas = arquivos.map((arquivo) => {
      const problema = problemaNoArquivo(arquivo)
      if (problema) return { chave: novaChave(), nome: arquivo.name, estado: 'erro', erro: problema, podeTentar: false }

      const previa = URL.createObjectURL(arquivo)
      previas.current.add(previa)
      return { chave: novaChave(), previa, nome: arquivo.name, arquivo, estado: 'medindo' }
    })
    setFotos((atuais) => [...atuais, ...novas])

    // Mede todas antes de enfileirar, para as que já estão em 4:5 subirem na ordem escolhida
    const medidas = await Promise.all(novas.map((foto) => (foto.estado === 'medindo' ? medir(foto.previa) : null)))
    novas.forEach((foto, indice) => {
      if (foto.estado !== 'medindo') return
      if (foraDe4por5(medidas[indice])) {
        alterarFoto(foto.chave, { estado: 'recorte', ...medidas[indice], enquadramento: 'AUTOMATICO' })
      } else {
        alterarFoto(foto.chave, { estado: 'fila' })
        enfileirar(foto.chave, foto.arquivo, null)
      }
    })
  }

  function enviarComCorte(foto) {
    alterarFoto(foto.chave, { estado: 'fila' })
    enfileirar(foto.chave, foto.arquivo, foto.enquadramento)
  }

  function tentarDeNovo(foto) {
    alterarFoto(foto.chave, { estado: 'fila', erro: null })
    enfileirar(foto.chave, foto.arquivo, foto.enquadramento)
  }

  function descartar(foto) {
    soltarPrevia(foto)
    setFotos((atuais) => atuais.filter((f) => f.chave !== foto.chave))
  }

  async function remover(foto) {
    setRemovendo(foto.chave)
    setErro(null)
    try {
      await removerImagemProduto(produtoId, foto.id)
      setFotos((atuais) => atuais.filter((f) => f.chave !== foto.chave))
      setConfirmando(null)
    } catch (e) {
      setErro(mensagemDeErro(e, 'Não foi possível remover a foto.'))
    } finally {
      setRemovendo(null)
    }
  }

  async function mover(indice, deslocamento) {
    const destino = indice + deslocamento
    const novaOrdem = [...prontas]
    ;[novaOrdem[indice], novaOrdem[destino]] = [novaOrdem[destino], novaOrdem[indice]]

    const anterior = fotos
    setFotos([...novaOrdem, ...pendentes])
    setReordenando(true)
    setErro(null)
    try {
      await reordenarImagensProduto(
        produtoId,
        novaOrdem.map((foto) => foto.id),
      )
    } catch (e) {
      setFotos(anterior)
      setErro(mensagemDeErro(e, 'Não foi possível mudar a ordem das fotos.'))
    } finally {
      setReordenando(false)
    }
  }

  const botaoAdicionar = (classe) => (
    <button type="button" className={classe} onClick={() => seletor.current?.click()}>
      + Adicionar fotos
    </button>
  )

  return (
    <div className="produto-form__fotos">
      <p className="eyebrow">Fotos do produto</p>

      {produtoId === null ? (
        <div className="produto-fotos__vazia" aria-disabled="true">
          <span>+ Adicionar fotos</span>
          <span className="produto-form__dica">Salve o produto para liberar as fotos.</span>
        </div>
      ) : fotos.length === 0 ? (
        botaoAdicionar('produto-fotos__vazia')
      ) : (
        <ul className="produto-fotos__grade" aria-label="Fotos do produto">
          {prontas.map((foto, indice) => (
            <li key={foto.chave} className="produto-fotos__item">
              <div className="produto-fotos__moldura">
                <img src={foto.url} alt={indice === 0 ? 'Foto principal' : `Foto ${indice + 1}`} />
                {indice === 0 && <span className="rotulo produto-fotos__selo">Principal</span>}
              </div>

              {confirmando === foto.chave ? (
                <div className="produto-fotos__acoes" role="group" aria-label="Confirmar remoção">
                  <span className="produto-fotos__pergunta">Remover esta foto?</span>
                  <button
                    type="button"
                    className="produto-fotos__acao"
                    onClick={() => remover(foto)}
                    disabled={removendo !== null}
                  >
                    {removendo === foto.chave ? 'Removendo…' : 'Sim, remover'}
                  </button>
                  <button
                    type="button"
                    className="produto-fotos__acao"
                    onClick={() => setConfirmando(null)}
                    disabled={removendo === foto.chave}
                  >
                    Cancelar
                  </button>
                </div>
              ) : (
                <div className="produto-fotos__acoes">
                  <button
                    type="button"
                    className="produto-fotos__acao produto-fotos__seta"
                    aria-label={`Mover foto ${indice + 1} para antes`}
                    onClick={() => mover(indice, -1)}
                    disabled={indice === 0 || ordemBloqueada}
                  >
                    ←
                  </button>
                  <button
                    type="button"
                    className="produto-fotos__acao produto-fotos__seta"
                    aria-label={`Mover foto ${indice + 1} para depois`}
                    onClick={() => mover(indice, 1)}
                    disabled={indice === prontas.length - 1 || ordemBloqueada}
                  >
                    →
                  </button>
                  <button
                    type="button"
                    className="produto-fotos__acao"
                    onClick={() => setConfirmando(foto.chave)}
                    disabled={removendo !== null}
                  >
                    Remover
                  </button>
                </div>
              )}
            </li>
          ))}

          {pendentes.map((foto) =>
            foto.estado === 'recorte' ? (
              <li key={foto.chave} className="produto-fotos__item produto-fotos__item--recorte">
                <div
                  className="produto-fotos__recorte"
                  style={{
                    aspectRatio: `${foto.largura} / ${foto.altura}`,
                    width: `min(100%, ${Math.round((ALTURA_PREVIA * foto.largura) / foto.altura)}px)`,
                  }}
                >
                  <img src={foto.previa} alt={`Prévia do corte 4:5 de ${foto.nome}`} />
                  <span className="produto-fotos__corte" style={areaDoCorte(foto)} aria-hidden="true" />
                </div>
                <p className="produto-fotos__aviso">
                  Esta foto tem {foto.largura} x {foto.altura} px, fora da proporção 4:5, e vai ser cortada: na loja
                  aparece só a parte dentro da moldura.
                </p>
                <fieldset className="produto-fotos__enquadramento">
                  <legend className="rotulo">Enquadramento</legend>
                  {/* Em foto mais larga que 4:5 o corte é sempre nos lados: topo daria o mesmo que centro */}
                  {ENQUADRAMENTOS.filter((opcao) => opcao.valor !== 'TOPO' || !maisLarga(foto)).map((opcao) => (
                    <label key={opcao.valor} className="produto-fotos__opcao">
                      <input
                        type="radio"
                        name={`enquadramento-${foto.chave}`}
                        value={opcao.valor}
                        checked={foto.enquadramento === opcao.valor}
                        onChange={() => alterarFoto(foto.chave, { enquadramento: opcao.valor })}
                      />
                      {opcao.rotulo}
                    </label>
                  ))}
                </fieldset>
                <p className="produto-form__dica">
                  {ENQUADRAMENTOS.find((opcao) => opcao.valor === foto.enquadramento)?.dica}
                </p>
                <div className="produto-fotos__acoes">
                  <button type="button" className="produto-fotos__acao" onClick={() => enviarComCorte(foto)}>
                    Enviar com este corte
                  </button>
                  <button type="button" className="produto-fotos__acao" onClick={() => descartar(foto)}>
                    Descartar
                  </button>
                </div>
              </li>
            ) : (
              <li key={foto.chave} className="produto-fotos__item">
                <div className="produto-fotos__moldura produto-fotos__moldura--pendente">
                  {/* Arquivo recusado aqui (ex.: GIF, PDF) pode nem ser imagem: mostra só o nome */}
                  {foto.arquivo ? (
                    <img
                      src={foto.previa}
                      alt=""
                      style={foto.enquadramento === 'TOPO' ? { objectPosition: 'top' } : undefined}
                      onError={(e) => (e.currentTarget.hidden = true)}
                    />
                  ) : (
                    <span className="produto-fotos__nome">{foto.nome}</span>
                  )}
                  <span className="rotulo produto-fotos__selo">{SELOS[foto.estado]}</span>
                </div>
                {foto.estado === 'erro' && (
                  <>
                    <p className="produto-form__erro" role="alert">
                      {foto.erro}
                    </p>
                    <div className="produto-fotos__acoes">
                      {foto.podeTentar && (
                        <button type="button" className="produto-fotos__acao" onClick={() => tentarDeNovo(foto)}>
                          Tentar de novo
                        </button>
                      )}
                      <button type="button" className="produto-fotos__acao" onClick={() => descartar(foto)}>
                        Descartar
                      </button>
                    </div>
                  </>
                )}
              </li>
            ),
          )}

          <li className="produto-fotos__item">{botaoAdicionar('produto-fotos__adicionar')}</li>
        </ul>
      )}

      {produtoId !== null && (
        <input
          ref={seletor}
          type="file"
          accept={TIPOS_ACEITOS.join(',')}
          multiple
          hidden
          onChange={adicionar}
        />
      )}

      {erro && (
        <p className="produto-form__erro" role="alert">
          {erro}
        </p>
      )}

      <p className="produto-form__dica">
        Recomendado: fotos na proporção 4:5 (retrato) — ex: 1000 x 1250px. Mesmo formato do Instagram em retrato.
        Fotos em outra proporção são cortadas em 4:5, e você vê o corte antes de enviar.
      </p>
      {produtoId !== null && (
        <p className="produto-form__dica">JPG, PNG ou WEBP até 5 MB. As fotos são salvas na hora; a primeira é a principal.</p>
      )}
    </div>
  )
}
