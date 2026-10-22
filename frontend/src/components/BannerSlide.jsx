import './BannerSlide.css'

// Só caminhos do próprio site ("/categoria/vestido"): a API já recusa outros, isto é a segunda trava
function linkInterno(link) {
  return typeof link === 'string' && /^\/(?![/\\])/.test(link) ? link : null
}

// Um banner da Home (#89): imagem 4:5 com o texto embaixo no celular; faixa 3:1 com o texto num quadro à
// esquerda a partir de 768px. modo 'auto' segue a tela (Home); 'celular' e 'desktop' fixam o layout para a
// prévia do painel, que passa a imagem inteira em `previa` (o corte sai do CSS, pelo centro).
// `controles` (setas, pontos) ficam por cima da imagem.
export default function BannerSlide({ banner, modo = 'auto', previa, controles }) {
  const link = linkInterno(banner.link)
  const botao = link && banner.textoBotao ? banner.textoBotao : null
  const temLegenda = Boolean(banner.titulo || banner.texto || botao)

  const imagem =
    modo === 'auto' ? (
      <picture>
        <source
          media="(min-width: 768px)"
          srcSet={banner.imagemDesktop.srcset ?? banner.imagemDesktop.url}
          sizes="100vw"
        />
        <img
          className="banner__imagem"
          src={banner.imagemCelular.url}
          srcSet={banner.imagemCelular.srcset ?? undefined}
          sizes="100vw"
          alt={banner.textoAlternativo}
        />
      </picture>
    ) : (
      <img className="banner__imagem" src={previa} alt={banner.textoAlternativo} />
    )

  return (
    <div className={`banner banner--${modo}`}>
      <div className="banner__palco">
        {/* Sem botão, a imagem toda leva ao link; com botão, só ele (um link por banner) */}
        {link && !botao ? (
          <a className="banner__link" href={link}>
            {imagem}
          </a>
        ) : (
          imagem
        )}
        {controles}
      </div>

      {temLegenda && (
        <div className="banner__legenda">
          {banner.titulo && <h2 className="banner__titulo">{banner.titulo}</h2>}
          {banner.texto && <p className="banner__texto">{banner.texto}</p>}
          {botao && (
            <a className="botao botao--primario banner__botao" href={link}>
              {botao}
            </a>
          )}
        </div>
      )}
    </div>
  )
}
