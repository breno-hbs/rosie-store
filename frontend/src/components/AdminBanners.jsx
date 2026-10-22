import { useEffect, useState } from 'react'
import { ApiError, alterarAtivoBanner, listarBannersAdmin, removerBanner, reordenarBanners } from '../api.js'
import { formatarDataHora } from '../formato.js'
import './AdminBanners.css'

function mensagemDeErro(erro, padrao) {
  if (erro instanceof ApiError && erro.codigo === 'SEM_CONEXAO') {
    return 'Sem conexão com a loja. Confira a internet e tente de novo.'
  }
  return erro instanceof ApiError ? erro.message : padrao
}

// Situação do banner agora (calculada pela API, no horário de São Paulo), como a Roseane lê
function situacao(banner) {
  if (banner.situacao === 'DESATIVADO') return 'Desativado'
  if (banner.situacao === 'AGENDADO') return `Agendado: entra em ${formatarDataHora(banner.inicio)}`
  if (banner.situacao === 'ENCERRADO') return `Encerrado em ${formatarDataHora(banner.fim)}`
  return banner.fim ? `No ar até ${formatarDataHora(banner.fim)}` : 'No ar'
}

// Banners da Home (#89): lista na ordem do carrossel, com setas para reordenar, ativar/desativar e remover
// (confirmação na própria linha). Criar e editar abrem o formulário em /admin/banners/novo e /admin/banners/{id}.
export default function AdminBanners() {
  const [banners, setBanners] = useState(null)
  const [erro, setErro] = useState(null)
  const [tentativa, setTentativa] = useState(0)
  const [confirmando, setConfirmando] = useState(null)
  const [ocupado, setOcupado] = useState(null)
  const [aviso, setAviso] = useState(() =>
    new URLSearchParams(window.location.search).get('salvo') ? 'Banner salvo.' : null,
  )
  const [erroAcao, setErroAcao] = useState(null)

  useEffect(() => {
    const controle = new AbortController()
    listarBannersAdmin({ signal: controle.signal })
      .then((lista) => {
        setBanners(lista)
        setErro(null)
      })
      .catch((e) => {
        if (e.name !== 'AbortError') setErro(mensagemDeErro(e, 'Não foi possível carregar os banners.'))
      })
    return () => controle.abort()
  }, [tentativa])

  function tentarDeNovo() {
    setErro(null)
    setBanners(null)
    setTentativa((t) => t + 1)
  }

  // Ação numa linha: trava as outras enquanto a API responde e mostra o erro embaixo da lista
  async function agir(id, acao, mensagemPadrao) {
    setOcupado(id)
    setAviso(null)
    setErroAcao(null)
    try {
      await acao()
    } catch (e) {
      setErroAcao(mensagemDeErro(e, mensagemPadrao))
    } finally {
      setOcupado(null)
    }
  }

  function mover(indice, direcao) {
    const nova = [...banners]
    const [movido] = nova.splice(indice, 1)
    nova.splice(indice + direcao, 0, movido)
    return agir(movido.id, async () => {
      setBanners(nova)
      try {
        setBanners(await reordenarBanners(nova.map((b) => b.id)))
      } catch (e) {
        setBanners(banners)
        throw e
      }
    }, 'Não foi possível mudar a ordem dos banners.')
  }

  function alternarAtivo(banner) {
    return agir(banner.id, async () => {
      const atualizado = await alterarAtivoBanner(banner.id, !banner.ativo)
      setBanners((lista) => lista.map((b) => (b.id === banner.id ? atualizado : b)))
    }, 'Não foi possível mudar o banner.')
  }

  function remover(banner) {
    return agir(banner.id, async () => {
      await removerBanner(banner.id)
      setBanners((lista) => lista.filter((b) => b.id !== banner.id).map((b, i) => ({ ...b, ordem: i })))
      setConfirmando(null)
      setAviso('Banner removido.')
    }, 'Não foi possível remover o banner.')
  }

  return (
    <section className="admin-banners">
      <div className="admin-banners__topo">
        <p className="admin-banners__explicacao">
          A página inicial mostra os banners no ar, nesta ordem. Sem nenhum no ar, ela volta a mostrar os produtos em
          destaque.
        </p>
        <a href="/admin/banners/novo" className="botao botao--primario admin-banners__novo">
          + Novo banner
        </a>
      </div>

      {aviso && (
        <p className="admin-banners__aviso" role="status">
          {aviso}
        </p>
      )}

      {erro ? (
        <div className="admin-banners__estado" role="alert">
          <p>{erro}</p>
          <button type="button" className="botao botao--contorno" onClick={tentarDeNovo}>
            Tentar de novo
          </button>
        </div>
      ) : banners === null ? (
        <p className="admin-banners__estado">Carregando banners…</p>
      ) : banners.length === 0 ? (
        <p className="admin-banners__estado">Nenhum banner ainda. Crie o primeiro com “+ Novo banner”.</p>
      ) : (
        <ol className="admin-banners__lista">
          {banners.map((banner, indice) => (
            <li key={banner.id} className="admin-banners__item">
              <img
                className="admin-banners__miniatura"
                src={banner.imagemDesktop.url}
                alt=""
                loading="lazy"
              />

              <div className="admin-banners__info">
                <p className="admin-banners__nome">{banner.titulo ?? banner.textoAlternativo}</p>
                <p className={`admin-banners__situacao admin-banners__situacao--${banner.situacao.toLowerCase()}`}>
                  {situacao(banner)}
                </p>
                {banner.link && <p className="admin-banners__link">Leva para {banner.link}</p>}
              </div>

              {confirmando === banner.id ? (
                <div className="admin-banners__acoes" role="group" aria-label="Confirmar remoção">
                  <span className="admin-banners__pergunta">Remover este banner?</span>
                  <button
                    type="button"
                    className="admin-banners__acao admin-banners__acao--forte"
                    onClick={() => remover(banner)}
                    disabled={ocupado !== null}
                  >
                    {ocupado === banner.id ? 'Removendo…' : 'Sim, remover'}
                  </button>
                  <button
                    type="button"
                    className="admin-banners__acao"
                    onClick={() => setConfirmando(null)}
                    disabled={ocupado === banner.id}
                  >
                    Cancelar
                  </button>
                </div>
              ) : (
                <div className="admin-banners__acoes">
                  <button
                    type="button"
                    className="admin-banners__acao admin-banners__seta"
                    aria-label={`Subir o banner ${indice + 1}`}
                    onClick={() => mover(indice, -1)}
                    disabled={indice === 0 || ocupado !== null}
                  >
                    ↑
                  </button>
                  <button
                    type="button"
                    className="admin-banners__acao admin-banners__seta"
                    aria-label={`Descer o banner ${indice + 1}`}
                    onClick={() => mover(indice, 1)}
                    disabled={indice === banners.length - 1 || ocupado !== null}
                  >
                    ↓
                  </button>
                  <a className="admin-banners__acao" href={`/admin/banners/${banner.id}`}>
                    Editar
                  </a>
                  <button
                    type="button"
                    className="admin-banners__acao"
                    onClick={() => alternarAtivo(banner)}
                    disabled={ocupado !== null}
                  >
                    {banner.ativo ? 'Desativar' : 'Ativar'}
                  </button>
                  <button
                    type="button"
                    className="admin-banners__acao"
                    onClick={() => setConfirmando(banner.id)}
                    disabled={ocupado !== null}
                  >
                    Remover
                  </button>
                </div>
              )}
            </li>
          ))}
        </ol>
      )}

      {erroAcao && (
        <p className="admin-banners__aviso" role="alert">
          {erroAcao}
        </p>
      )}
    </section>
  )
}
