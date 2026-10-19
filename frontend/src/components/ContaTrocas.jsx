import { listarTrocas } from '../api.js'
import useCarregar from '../useCarregar.js'
import ContaAviso from './ContaAviso.jsx'
import { formatarData } from '../formato.js'
import { nomeMotivo, rotuloStatusTroca, textoFrete, textoStatusTroca } from '../troca.js'

// Aba "Trocas" da Minha Conta (#27): as trocas pedidas, com o status, quem paga o frete de volta e o
// recado da Rosie. Pedir uma nova é na tela "Solicitação de Troca" (/trocas).
export default function ContaTrocas() {
  const { carregando, dados: trocas, erro, tentarDeNovo } = useCarregar(listarTrocas)

  return (
    <section className="conta__secao" aria-labelledby="titulo-trocas">
      <div className="conta__titulo-linha">
        <h1 className="titulo-2" id="titulo-trocas">
          Minhas Trocas
        </h1>
        {trocas?.length > 0 && (
          <a className="botao botao--contorno" href="/trocas">
            Solicitar troca
          </a>
        )}
      </div>

      {carregando || erro ? (
        <ContaAviso
          carregando={carregando}
          erro={erro}
          mensagemErro="Não foi possível carregar suas trocas."
          onTentarDeNovo={tentarDeNovo}
        />
      ) : trocas.length === 0 ? (
        <div className="conta__aviso">
          <p>Você ainda não pediu trocas.</p>
          <p className="conta__nota">
            Peças com defeito ou que não serviram podem ser trocadas até 7 dias depois da entrega.
          </p>
          <a className="botao botao--contorno" href="/trocas">
            Solicitar troca
          </a>
        </div>
      ) : (
        <ul className="conta__trocas">
          {trocas.map((troca) => (
            <li key={troca.id} className="conta__troca">
              <a className="conta__item-foto" href={`/produtos/${troca.produtoId}`} tabIndex={-1} aria-hidden="true">
                {troca.imagemPrincipal && <img src={troca.imagemPrincipal} alt="" loading="lazy" />}
              </a>
              <div className="conta__troca-info">
                <div className="conta__troca-topo">
                  <a className="conta__item-nome" href={`/produtos/${troca.produtoId}`}>
                    {troca.nome}
                  </a>
                  <span className={`conta__selo${troca.status === 'RECUSADA' ? ' conta__selo--encerrado' : ''}`}>
                    {rotuloStatusTroca(troca.status)}
                  </span>
                </div>
                <span className="conta__nota">
                  Tamanho {troca.tamanho} · {troca.quantidade} {troca.quantidade === 1 ? 'peça' : 'peças'} ·{' '}
                  <a className="conta__link conta__link--nota" href={`/minha-conta/pedidos/${troca.pedidoId}`}>
                    Pedido #{troca.pedidoId}
                  </a>{' '}
                  · pedida em {formatarData(troca.createdAt)}
                </span>
                <span className="conta__texto">
                  {nomeMotivo(troca.motivo)} · {textoFrete(troca.freteConta)}
                </span>
                <p className="conta__texto">{textoStatusTroca(troca)}</p>
                {troca.resposta && (
                  <p className="conta__troca-resposta">
                    <span className="eyebrow">Resposta da Rosie</span>
                    {troca.resposta}
                  </p>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
