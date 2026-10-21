import { useEffect, useState } from 'react'
import { listarProdutos } from '../api.js'
import { rotaCategoria } from '../categoria.js'
import './CategoryTiles.css'

// Produtos olhados por categoria até achar um com foto para a capa
const AMOSTRA = 4
// Até 6 categorias cabem numa linha só em tela larga; mais que isso, quebram em linhas de 4
const MAX_LINHA_LARGA = 6

function primeiraFoto(produtos) {
  return produtos.find((produto) => produto.imagemPrincipal)?.imagemPrincipal ?? null
}

// Capa da categoria: a foto do destaque dela (já carregado na Home); sem destaque com foto, a do primeiro
// produto ativo com foto, buscado aqui. undefined enquanto busca, null quando a categoria não tem foto.
// Cada card leva para a página da categoria (#88).
export default function CategoryTiles({ categorias, destaques }) {
  const [fotosBuscadas, setFotosBuscadas] = useState({})

  const fotosDestaques = {}
  for (const produto of destaques ?? []) {
    const id = produto.categoria?.id
    if (id && produto.imagemPrincipal && !fotosDestaques[id]) fotosDestaques[id] = produto.imagemPrincipal
  }

  // Espera os destaques (null enquanto carregam) para não buscar o que eles já trazem
  const semFotoDestaque =
    destaques === null
      ? ''
      : categorias
          .filter((categoria) => !fotosDestaques[categoria.id])
          .map((categoria) => categoria.id)
          .join(',')

  useEffect(() => {
    if (!semFotoDestaque) return
    const controle = new AbortController()
    for (const id of semFotoDestaque.split(',').map(Number)) {
      listarProdutos({ categoriaId: id, pagina: 0, tamanho: AMOSTRA }, { signal: controle.signal })
        .then((resposta) => setFotosBuscadas((atual) => ({ ...atual, [id]: primeiraFoto(resposta.conteudo) })))
        .catch((erro) => {
          if (erro.name !== 'AbortError') setFotosBuscadas((atual) => ({ ...atual, [id]: null }))
        })
    }
    return () => controle.abort()
  }, [semFotoDestaque])

  if (categorias.length === 0) return null

  return (
    <section className="categorias" aria-labelledby="categorias-titulo">
      <h2 id="categorias-titulo" className="categorias__titulo eyebrow">
        Compre por categoria
      </h2>

      <ul
        className="categorias__grade"
        style={{
          '--colunas': Math.min(categorias.length, 4),
          '--colunas-largo': categorias.length <= MAX_LINHA_LARGA ? categorias.length : 4,
        }}
      >
        {categorias.map((categoria) => {
          const foto = fotosDestaques[categoria.id] ?? fotosBuscadas[categoria.id]
          return (
            <li key={categoria.id}>
              <a className="categorias__item" href={rotaCategoria(categoria)}>
                <span className="categorias__foto">
                  {foto ? (
                    <img src={foto} alt="" loading="lazy" />
                  ) : (
                    foto === null && (
                      <span className="categorias__sem-foto" aria-hidden="true">
                        Rosie
                      </span>
                    )
                  )}
                </span>
                {/* Espaço invisível depois da barra: "Cropped/Body" quebra ali, não no meio da palavra */}
                <span className="categorias__nome">{categoria.nome.replaceAll('/', '/\u200b')}</span>
                <span className="categorias__acao" aria-hidden="true">
                  Ver tudo →
                </span>
              </a>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
