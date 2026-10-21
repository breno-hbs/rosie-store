import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import {
  ApiError,
  atualizarBanner,
  criarBanner,
  listarBannersAdmin,
  listarCategorias,
  listarProdutosAdmin,
} from '../api.js'
import BannerSlide from './BannerSlide.jsx'
import './AdminProdutos.css'
import './AdminBanners.css'

// Mesmos limites da API (docs/contrato-api.md, "Banners"): conferidos aqui só para avisar antes de enviar
const TIPOS_ACEITOS = ['image/jpeg', 'image/png', 'image/webp']
const TAMANHO_MAXIMO = 5 * 1024 * 1024

// Para onde o banner leva. A API só aceita páginas da própria loja.
const TIPOS_LINK = [
  { valor: 'nenhum', rotulo: 'Nenhum lugar' },
  { valor: 'categoria', rotulo: 'Uma categoria' },
  { valor: 'produto', rotulo: 'Um produto' },
  { valor: 'busca', rotulo: 'Uma busca' },
  { valor: 'pagina', rotulo: 'Outra página da loja' },
]

const VAZIO = {
  titulo: '',
  texto: '',
  textoBotao: '',
  textoAlternativo: '',
  tipoLink: 'nenhum',
  categoria: '',
  produto: '',
  busca: '',
  pagina: '',
  inicio: '',
  fim: '',
  ativo: true,
}

// Link gravado → campos do formulário ("/categoria/vestido" vira tipo categoria com o slug)
function camposDoLink(link) {
  if (!link) return { tipoLink: 'nenhum' }
  const categoria = link.match(/^\/categoria\/([^/?#]+)\/?$/)
  if (categoria) return { tipoLink: 'categoria', categoria: categoria[1] }
  const produto = link.match(/^\/produtos\/(\d+)\/?$/)
  if (produto) return { tipoLink: 'produto', produto: produto[1] }
  if (/^\/busca\?q=[^&#]*$/.test(link)) {
    try {
      return { tipoLink: 'busca', busca: decodeURIComponent(link.slice('/busca?q='.length).replace(/\+/g, ' ')) }
    } catch {
      // busca com % solto: fica como página
    }
  }
  return { tipoLink: 'pagina', pagina: link }
}

// Campos do formulário → link para a API; null = sem link, undefined = falta preencher
function montarLink(form) {
  if (form.tipoLink === 'categoria') return form.categoria ? `/categoria/${form.categoria}` : undefined
  if (form.tipoLink === 'produto') return form.produto ? `/produtos/${form.produto}` : undefined
  if (form.tipoLink === 'busca') return form.busca.trim() ? `/busca?q=${encodeURIComponent(form.busca.trim())}` : undefined
  if (form.tipoLink === 'pagina') return form.pagina.trim() || undefined
  return null
}

function doBanner(banner) {
  return {
    ...VAZIO,
    titulo: banner.titulo ?? '',
    texto: banner.texto ?? '',
    textoBotao: banner.textoBotao ?? '',
    textoAlternativo: banner.textoAlternativo,
    ...camposDoLink(banner.link),
    // "2026-10-19T00:00:00" → "2026-10-19T00:00" (valor do datetime-local)
    inicio: banner.inicio ? banner.inicio.slice(0, 16) : '',
    fim: banner.fim ? banner.fim.slice(0, 16) : '',
    ativo: banner.ativo,
  }
}

function problemaNoArquivo(arquivo) {
  if (!TIPOS_ACEITOS.includes(arquivo.type)) return 'Formato não aceito: envie a imagem em JPG, PNG ou WEBP'
  if (arquivo.size > TAMANHO_MAXIMO) return 'A imagem passa de 5 MB: diminua o arquivo e tente de novo'
  return null
}

function mensagemDeErro(erro, padrao) {
  if (erro instanceof ApiError && erro.codigo === 'SEM_CONEXAO') {
    return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  }
  return erro instanceof ApiError ? erro.message : padrao
}

// Tela de tamanho fixo (375 px do celular, 1280 px do computador) reduzida para caber na coluna: o banner
// aparece com as mesmas proporções e tamanhos de letra que terá na loja. inert: nada clicável na prévia.
function MolduraPrevia({ largura, rotulo, margem, children }) {
  const caixa = useRef(null)
  const tela = useRef(null)
  const [medida, setMedida] = useState({ escala: 1, altura: 0 })

  useLayoutEffect(() => {
    const medir = () => {
      const escala = Math.min(1, caixa.current.clientWidth / largura)
      setMedida({ escala, altura: tela.current.offsetHeight * escala })
    }
    medir()
    const observador = new ResizeObserver(medir)
    observador.observe(caixa.current)
    observador.observe(tela.current)
    return () => observador.disconnect()
  }, [largura])

  return (
    <figure className="banner-previa">
      <figcaption className="rotulo">{rotulo}</figcaption>
      <div ref={caixa} className="banner-previa__caixa" style={{ height: medida.altura }}>
        <div
          ref={tela}
          className="banner-previa__tela"
          inert
          style={{ width: largura, transform: `scale(${medida.escala})`, '--margem': margem }}
        >
          {children}
        </div>
      </div>
    </figure>
  )
}

// Cadastro e edição de banner da Home (#89). id null = banner novo. As imagens vão junto com os dados ao
// salvar; na edição, imagem não escolhida continua a mesma.
export default function AdminBannerForm({ id }) {
  const editando = id !== null
  const [form, setForm] = useState(VAZIO)
  const [original, setOriginal] = useState(null)
  const [carregando, setCarregando] = useState(editando)
  const [erroCarga, setErroCarga] = useState(null)
  const [categorias, setCategorias] = useState([])
  const [produtos, setProdutos] = useState([])
  // Imagens escolhidas agora: { arquivo, url (prévia local) }
  const [imagens, setImagens] = useState({ desktop: null, celular: null })
  const [removerCelular, setRemoverCelular] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState(null)
  const previas = useRef(new Set())

  useEffect(() => {
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then(setCategorias)
      .catch(() => {})
    listarProdutosAdmin({ signal: controle.signal })
      .then((lista) => setProdutos(lista.filter((produto) => produto.ativo)))
      .catch(() => {})
    if (editando) {
      listarBannersAdmin({ signal: controle.signal })
        .then((lista) => {
          const banner = lista.find((b) => b.id === id)
          if (!banner) {
            setErroCarga('Banner não encontrado. Ele pode ter sido removido.')
          } else {
            setOriginal(banner)
            setForm(doBanner(banner))
          }
          setCarregando(false)
        })
        .catch((e) => {
          if (e.name === 'AbortError') return
          setErroCarga(mensagemDeErro(e, 'Não foi possível carregar o banner.'))
          setCarregando(false)
        })
    }
    return () => controle.abort()
  }, [editando, id])

  // Libera as prévias locais ao sair da tela
  useEffect(() => {
    const abertas = previas.current
    return () => abertas.forEach((url) => URL.revokeObjectURL(url))
  }, [])

  function alterar(campo, valor) {
    setForm((atual) => ({ ...atual, [campo]: valor }))
  }

  function escolher(tela, evento) {
    const arquivo = evento.target.files?.[0]
    evento.target.value = ''
    if (!arquivo) return
    const problema = problemaNoArquivo(arquivo)
    if (problema) {
      setErro(problema)
      return
    }
    setErro(null)
    const url = URL.createObjectURL(arquivo)
    previas.current.add(url)
    setImagens((atuais) => {
      if (atuais[tela]) {
        URL.revokeObjectURL(atuais[tela].url)
        previas.current.delete(atuais[tela].url)
      }
      return { ...atuais, [tela]: { arquivo, url } }
    })
    if (tela === 'celular') setRemoverCelular(false)
  }

  function tirarCelular() {
    setImagens((atuais) => {
      if (atuais.celular) {
        URL.revokeObjectURL(atuais.celular.url)
        previas.current.delete(atuais.celular.url)
      }
      return { ...atuais, celular: null }
    })
    if (original?.previaCelular) setRemoverCelular(true)
  }

  const link = montarLink(form)
  const imagemDesktop = imagens.desktop?.url ?? original?.previaDesktop ?? null
  const celularPropria = imagens.celular?.url ?? (removerCelular ? null : original?.previaCelular) ?? null
  const imagemCelular = celularPropria ?? imagemDesktop
  // Categorias ganham slug na #88; sem ele, a opção de categoria fica de fora
  const comSlug = categorias.filter((categoria) => categoria.slug)
  const tiposLink = TIPOS_LINK.filter((tipo) => tipo.valor !== 'categoria' || comSlug.length > 0 || form.categoria)
  const bannerPrevia = {
    titulo: form.titulo.trim(),
    texto: form.texto.trim(),
    textoBotao: form.textoBotao.trim(),
    link: link ?? null,
    textoAlternativo: form.textoAlternativo.trim(),
  }

  async function salvar(evento) {
    evento.preventDefault()
    if (!imagemDesktop) return setErro('Escolha a imagem para computador.')
    if (!form.textoAlternativo.trim()) return setErro('Descreva a imagem do banner (texto alternativo).')
    if (link === undefined) return setErro('Complete para onde o banner leva, ou escolha “Nenhum lugar”.')
    if (form.inicio && form.fim && form.fim <= form.inicio) return setErro('O fim da exibição precisa ser depois do início.')

    const dados = {
      titulo: form.titulo,
      texto: form.texto,
      // O campo do botão só aparece com link; sem link, o texto que ficou nele não vale
      textoBotao: link ? form.textoBotao : '',
      link,
      textoAlternativo: form.textoAlternativo,
      ativo: form.ativo,
      inicio: form.inicio || null,
      fim: form.fim || null,
      removerImagemCelular: removerCelular,
    }
    const arquivos = { imagemDesktop: imagens.desktop?.arquivo, imagemCelular: imagens.celular?.arquivo }

    setSalvando(true)
    setErro(null)
    try {
      if (editando) await atualizarBanner(id, dados, arquivos)
      else await criarBanner(dados, arquivos)
      window.location.assign('/admin/banners?salvo=1')
    } catch (e) {
      setErro(mensagemDeErro(e, 'Não foi possível salvar o banner. Tente de novo.'))
      setSalvando(false)
    }
  }

  if (carregando) return <p className="admin-banners__estado">Carregando banner…</p>
  if (erroCarga) {
    return (
      <div className="admin-banners__estado" role="alert">
        <p>{erroCarga}</p>
        <a href="/admin/banners" className="botao botao--contorno">
          Voltar aos banners
        </a>
      </div>
    )
  }

  return (
    <section className="banner-form">
      <a href="/admin/banners" className="admin-banners__acao produto-form__voltar">
        ← Banners
      </a>
      <h1 className="titulo-2 admin__titulo">{editando ? 'Editar banner' : 'Novo banner'}</h1>

      <form className="banner-form__corpo" onSubmit={salvar} noValidate>
        <div className="produto-form__campos">
          <fieldset className="produto-form__grupo">
            <legend className="eyebrow">Imagens</legend>

            <div className="banner-form__imagem">
              <span className="produto-form__opcao-texto">
                Para computador (obrigatória)
                <span className="produto-form__dica">
                  Faixa larga, na proporção 3:1 — ex: 1920 x 640 px. Fora dela, é cortada pelo centro.
                </span>
              </span>
              <label className="botao botao--contorno banner-form__escolher">
                {imagemDesktop ? 'Trocar imagem' : 'Escolher imagem'}
                <input
                  type="file"
                  accept={TIPOS_ACEITOS.join(',')}
                  onChange={(e) => escolher('desktop', e)}
                  disabled={salvando}
                  className="produto-form__oculto"
                />
              </label>
              {imagens.desktop && <span className="produto-form__dica">{imagens.desktop.arquivo.name}</span>}
            </div>

            <div className="banner-form__imagem">
              <span className="produto-form__opcao-texto">
                Para celular (opcional)
                <span className="produto-form__dica">
                  Retrato 4:5, como no Instagram — ex: 1080 x 1350 px. Sem ela, o celular mostra o centro da imagem
                  de computador.
                </span>
              </span>
              <span className="banner-form__botoes">
                <label className="botao botao--contorno banner-form__escolher">
                  {celularPropria ? 'Trocar imagem' : 'Escolher imagem'}
                  <input
                    type="file"
                    accept={TIPOS_ACEITOS.join(',')}
                    onChange={(e) => escolher('celular', e)}
                    disabled={salvando}
                    className="produto-form__oculto"
                  />
                </label>
                {celularPropria && (
                  <button type="button" className="admin-banners__acao" onClick={tirarCelular} disabled={salvando}>
                    Usar a de computador
                  </button>
                )}
              </span>
              {imagens.celular && <span className="produto-form__dica">{imagens.celular.arquivo.name}</span>}
            </div>
            <p className="produto-form__dica">JPG, PNG ou WEBP até 5 MB cada.</p>
          </fieldset>

          <label className="campo">
            <span className="eyebrow">Texto alternativo (obrigatório)</span>
            <input
              className="campo__caixa"
              name="textoAlternativo"
              maxLength={200}
              placeholder="Ex: Modelo com vestido florido de linho na praia"
              required
              value={form.textoAlternativo}
              onChange={(e) => alterar('textoAlternativo', e.target.value)}
              disabled={salvando}
            />
            <span className="produto-form__dica">
              O que a imagem mostra, para quem usa leitor de tela. Não precisa repetir o título.
            </span>
          </label>

          <label className="campo">
            <span className="eyebrow">Título (opcional)</span>
            <input
              className="campo__caixa"
              name="titulo"
              maxLength={80}
              placeholder="Ex: Coleção de verão"
              value={form.titulo}
              onChange={(e) => alterar('titulo', e.target.value)}
              disabled={salvando}
            />
          </label>

          <label className="campo">
            <span className="eyebrow">Texto (opcional)</span>
            <textarea
              className="campo__caixa produto-form__descricao"
              name="texto"
              maxLength={200}
              rows={2}
              placeholder="Ex: Frete grátis acima de R$ 200"
              value={form.texto}
              onChange={(e) => alterar('texto', e.target.value)}
              disabled={salvando}
            />
          </label>

          <div className="produto-form__linha">
            <label className="campo produto-form__select">
              <span className="eyebrow">Leva para</span>
              <select
                className="campo__caixa"
                name="tipoLink"
                value={form.tipoLink}
                onChange={(e) => alterar('tipoLink', e.target.value)}
                disabled={salvando}
              >
                {tiposLink.map((tipo) => (
                  <option key={tipo.valor} value={tipo.valor}>
                    {tipo.rotulo}
                  </option>
                ))}
              </select>
            </label>

            {form.tipoLink === 'categoria' && (
              <label className="campo produto-form__select">
                <span className="eyebrow">Categoria</span>
                <select
                  className="campo__caixa"
                  name="categoria"
                  value={form.categoria}
                  onChange={(e) => alterar('categoria', e.target.value)}
                  disabled={salvando}
                >
                  <option value="" disabled>
                    Selecionar categoria...
                  </option>
                  {/* Categoria do link que não está mais na lista (removida ou renomeada) continua visível */}
                  {form.categoria && !comSlug.some((c) => c.slug === form.categoria) && (
                    <option value={form.categoria}>{form.categoria}</option>
                  )}
                  {comSlug.map((categoria) => (
                    <option key={categoria.id} value={categoria.slug}>
                      {categoria.nome}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {form.tipoLink === 'produto' && (
              <label className="campo produto-form__select">
                <span className="eyebrow">Produto</span>
                <select
                  className="campo__caixa"
                  name="produto"
                  value={form.produto}
                  onChange={(e) => alterar('produto', e.target.value)}
                  disabled={salvando}
                >
                  <option value="" disabled>
                    Selecionar produto...
                  </option>
                  {form.produto && !produtos.some((p) => String(p.id) === form.produto) && (
                    <option value={form.produto}>Produto {form.produto}</option>
                  )}
                  {produtos.map((produto) => (
                    <option key={produto.id} value={produto.id}>
                      {produto.nome}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {form.tipoLink === 'busca' && (
              <label className="campo">
                <span className="eyebrow">Buscar por</span>
                <input
                  className="campo__caixa"
                  name="busca"
                  placeholder="Ex: linho"
                  value={form.busca}
                  onChange={(e) => alterar('busca', e.target.value)}
                  disabled={salvando}
                />
              </label>
            )}

            {form.tipoLink === 'pagina' && (
              <label className="campo">
                <span className="eyebrow">Endereço da página</span>
                <input
                  className="campo__caixa"
                  name="pagina"
                  placeholder="Ex: /politica-de-privacidade"
                  value={form.pagina}
                  onChange={(e) => alterar('pagina', e.target.value)}
                  disabled={salvando}
                />
                <span className="produto-form__dica">
                  Só páginas da loja: comece com / ou cole o endereço completo do rosiestore.com.br.
                </span>
              </label>
            )}
          </div>

          {link && (
            <label className="campo">
              <span className="eyebrow">Texto do botão (opcional)</span>
              <input
                className="campo__caixa"
                name="textoBotao"
                maxLength={30}
                placeholder="Ex: Ver coleção"
                value={form.textoBotao}
                onChange={(e) => alterar('textoBotao', e.target.value)}
                disabled={salvando}
              />
              <span className="produto-form__dica">Sem botão, a imagem inteira leva ao link.</span>
            </label>
          )}

          <fieldset className="produto-form__grupo">
            <legend className="eyebrow">Período (opcional, horário de Brasília)</legend>
            <div className="produto-form__linha">
              <label className="campo">
                <span className="produto-form__dica">Entra no ar em</span>
                <input
                  className="campo__caixa"
                  type="datetime-local"
                  name="inicio"
                  value={form.inicio}
                  onChange={(e) => alterar('inicio', e.target.value)}
                  disabled={salvando}
                />
              </label>
              <label className="campo">
                <span className="produto-form__dica">Sai do ar em</span>
                <input
                  className="campo__caixa"
                  type="datetime-local"
                  name="fim"
                  value={form.fim}
                  onChange={(e) => alterar('fim', e.target.value)}
                  disabled={salvando}
                />
              </label>
            </div>
            <p className="produto-form__dica">Em branco: no ar desde já e sem data para sair.</p>
          </fieldset>

          <label className="produto-form__opcao">
            <input
              type="checkbox"
              name="ativo"
              checked={form.ativo}
              onChange={(e) => alterar('ativo', e.target.checked)}
              disabled={salvando}
            />
            <span className="produto-form__opcao-texto">
              Ativo
              <span className="produto-form__dica">Desmarcado, o banner sai da página inicial, mas continua aqui.</span>
            </span>
          </label>

          {erro && (
            <p className="produto-form__erro" role="alert">
              {erro}
            </p>
          )}

          <button type="submit" className="botao botao--primario produto-form__salvar" disabled={salvando}>
            {salvando ? 'Salvando…' : 'Salvar banner'}
          </button>
        </div>

        <div className="banner-form__previas">
          <p className="eyebrow">Prévia</p>
          {imagemDesktop ? (
            <>
              <MolduraPrevia largura={375} rotulo="Celular" margem="16px">
                <BannerSlide banner={bannerPrevia} modo="celular" previa={imagemCelular} />
              </MolduraPrevia>
              <MolduraPrevia largura={1280} rotulo="Computador" margem="32px">
                <BannerSlide banner={bannerPrevia} modo="desktop" previa={imagemDesktop} />
              </MolduraPrevia>
              <p className="produto-form__dica">
                A prévia corta as imagens pelo centro. Na loja, o corte procura a parte principal da foto (sem imagem de
                celular, o celular usa o centro).
              </p>
            </>
          ) : (
            <p className="produto-form__dica">Escolha a imagem para computador para ver como o banner fica.</p>
          )}
        </div>
      </form>
    </section>
  )
}
