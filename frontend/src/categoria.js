// Página da categoria (#88): /categoria/{slug}, com o slug que a API gera do nome ("Cropped/Body" → cropped-body)
export const ROTA_CATEGORIA = '/categoria'

export function rotaCategoria(categoria) {
  return `${ROTA_CATEGORIA}/${categoria.slug}`
}
