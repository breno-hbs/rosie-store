import { useEffect } from 'react'
import { listarCategorias } from '../api.js'
import { rotaCategoria } from '../categoria.js'

// Link antigo da Home filtrada (/?categoria=1, de antes da #88), que pode já ter sido divulgado: vai para a
// página da categoria, sem deixar o link antigo no histórico. Id que não existe (ou a API fora do ar) cai na Home.
export default function CategoriaAntiga({ id }) {
  useEffect(() => {
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then((categorias) => {
        const categoria = categorias.find((outra) => outra.id === id)
        window.location.replace(categoria ? rotaCategoria(categoria) : '/')
      })
      .catch((erro) => {
        if (erro.name !== 'AbortError') window.location.replace('/')
      })
    return () => controle.abort()
  }, [id])

  return null
}
