import { useEffect, useState } from 'react'
import BannerSlide from './BannerSlide.jsx'
import './HeroCarousel.css'

// Tempo de cada slide quando o carrossel passa sozinho
const INTERVALO = 6000

function prefereMenosMovimento() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}

// Carrossel da Home. Com banners (GET /banners, #89) mostra os banners; sem nenhum no ar, as fotos dos
// destaques (#10) como antes. Passa sozinho a cada 6 s, menos com o mouse em cima, com o foco dentro, depois
// de "Pausar" ou com o sistema pedindo menos movimento. As setas do teclado também trocam o slide.
export default function HeroCarousel({ banners = [], produtos = [] }) {
  const comBanners = banners.length > 0
  const slides = comBanners ? banners : produtos.filter((produto) => produto.imagemPrincipal)
  const [indiceEscolhido, setIndice] = useState(0)
  const [pausado, setPausado] = useState(prefereMenosMovimento)
  const [emUso, setEmUso] = useState(false)
  const total = slides.length
  const indice = total ? indiceEscolhido % total : 0
  const atual = slides[indice]
  const rodando = total > 1 && !pausado && !emUso
  const nome = comBanners ? 'Banner' : 'Destaque'

  useEffect(() => {
    if (!rodando) return
    const timer = setTimeout(() => setIndice((i) => (i + 1) % total), INTERVALO)
    return () => clearTimeout(timer)
  }, [rodando, indiceEscolhido, total])

  const irPara = (novo) => setIndice((novo + total) % total)

  function teclado(evento) {
    if (total < 2) return
    if (evento.key === 'ArrowLeft') irPara(indice - 1)
    else if (evento.key === 'ArrowRight') irPara(indice + 1)
    else return
    evento.preventDefault()
  }

  function saiuOFoco(evento) {
    if (!evento.currentTarget.contains(evento.relatedTarget)) setEmUso(false)
  }

  const controles = total > 1 && (
    <>
      <button
        type="button"
        className="hero__seta hero__seta--anterior"
        aria-label={`${nome} anterior`}
        onClick={() => irPara(indice - 1)}
      >
        ‹
      </button>
      <button
        type="button"
        className="hero__seta hero__seta--proxima"
        aria-label={`Próximo ${nome.toLowerCase()}`}
        onClick={() => irPara(indice + 1)}
      >
        ›
      </button>
      <div className="hero__controles">
        {slides.map((slide, i) => (
          <button
            key={slide.id}
            type="button"
            className="hero__ponto"
            aria-label={`${nome} ${i + 1} de ${total}`}
            aria-current={i === indice}
            onClick={() => irPara(i)}
          />
        ))}
        <button
          type="button"
          className="hero__pausa"
          aria-label={pausado ? 'Continuar o carrossel' : 'Pausar o carrossel'}
          onClick={() => setPausado((p) => !p)}
        >
          <span aria-hidden="true">{pausado ? '▶' : '❚❚'}</span>
        </button>
      </div>
    </>
  )

  return (
    <section
      className={`hero${comBanners ? ' hero--banners' : ''}`}
      aria-roledescription="carrossel"
      aria-label={comBanners ? 'Banners' : 'Destaques'}
      onKeyDown={teclado}
      onMouseEnter={() => setEmUso(true)}
      onMouseLeave={() => setEmUso(false)}
      onFocus={() => setEmUso(true)}
      onBlur={saiuOFoco}
    >
      {/* Leitor de tela anuncia a troca só quando ela vem de quem está usando, não da passagem automática */}
      <div className="hero__vivo" aria-live={rodando ? 'off' : 'polite'}>
        {atual && comBanners && (
          <div role="group" aria-roledescription="slide" aria-label={`${indice + 1} de ${total}`}>
            <BannerSlide banner={atual} controles={controles} />
          </div>
        )}
        {!comBanners && (
          <div className="hero__palco">
            {atual && (
              <img key={atual.id} className="hero__foto" src={atual.imagemPrincipal} alt={atual.nome} />
            )}
            {controles}
          </div>
        )}
      </div>
    </section>
  )
}
