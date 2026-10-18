import { useState } from 'react'
import './HeroCarousel.css'

// Slides vêm de GET /produtos/destaques; sem destaques, a Home passa os primeiros da listagem.
export default function HeroCarousel({ produtos }) {
  const [indiceEscolhido, setIndice] = useState(0)
  const slides = produtos.filter((produto) => produto.imagemPrincipal)
  const total = slides.length
  const indice = total ? indiceEscolhido % total : 0
  const atual = slides[indice]

  const irPara = (novo) => setIndice((novo + total) % total)

  return (
    <section className="hero" aria-roledescription="carrossel" aria-label="Destaques">
      {atual && (
        <img
          key={atual.id}
          className="hero__foto"
          src={atual.imagemPrincipal}
          alt={atual.nome}
        />
      )}

      {total > 1 && (
        <>
          <button
            type="button"
            className="hero__seta hero__seta--anterior"
            aria-label="Destaque anterior"
            onClick={() => irPara(indice - 1)}
          >
            ‹
          </button>
          <button
            type="button"
            className="hero__seta hero__seta--proxima"
            aria-label="Próximo destaque"
            onClick={() => irPara(indice + 1)}
          >
            ›
          </button>
          <div className="hero__pontos">
            {slides.map((slide, i) => (
              <button
                key={slide.id}
                type="button"
                className="hero__ponto"
                aria-label={`Destaque ${i + 1} de ${total}`}
                aria-current={i === indice}
                onClick={() => irPara(i)}
              />
            ))}
          </div>
        </>
      )}
    </section>
  )
}
