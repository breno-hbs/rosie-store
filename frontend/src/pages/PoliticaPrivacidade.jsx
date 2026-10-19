import { useEffect, useState } from 'react'
import { buscarPoliticaPrivacidade, listarCategorias } from '../api.js'
import Footer from '../components/Footer.jsx'
import Nav from '../components/Nav.jsx'
import { formatarData, linkWhatsApp } from '../formato.js'
import './PoliticaPrivacidade.css'

// Página "Política de privacidade" (LGPD, RNF06, #31), em /politica-de-privacidade: rodapé, cadastro e
// aceite da versão nova levam para cá. Pública.
// MUDOU O TEXTO? Troque PoliticaPrivacidade.VIGENCIA no back-end para o dia em que o texto novo entra no
// ar: a versão mostrada aqui vem de lá (GET /privacidade), e quem aceitou a anterior aceita a nova no
// próximo login ou checkout.

// Itens que só a Roseane pode informar (dados do controlador, prazos a confirmar com a orientação
// jurídica). Aparecem destacados na página até serem preenchidos.
function Preencher({ children }) {
  return <span className="politica__preencher">[preencher: {children}]</span>
}

const SECOES = [
  { id: 'controlador', titulo: 'Quem cuida dos seus dados' },
  { id: 'dados', titulo: 'Quais dados guardamos e para quê' },
  { id: 'base-legal', titulo: 'Por que podemos usar esses dados' },
  { id: 'compartilhamento', titulo: 'Com quem compartilhamos' },
  { id: 'prazo', titulo: 'Por quanto tempo guardamos' },
  { id: 'direitos', titulo: 'Seus direitos e como usá-los' },
  { id: 'exclusao', titulo: 'Excluir a sua conta' },
  { id: 'navegador', titulo: 'Cookies e dados no navegador' },
  { id: 'seguranca', titulo: 'Segurança' },
  { id: 'mudancas', titulo: 'Mudanças nesta política' },
  { id: 'contato', titulo: 'Contato' },
]

export default function PoliticaPrivacidade() {
  const [categorias, setCategorias] = useState([])
  const [politica, setPolitica] = useState({ carregando: true, dados: null })
  const whatsapp = linkWhatsApp('Olá! Tenho uma dúvida sobre os meus dados na Rosie.')

  useEffect(() => {
    document.title = 'Política de privacidade — Rosie'
    const controle = new AbortController()
    listarCategorias({ signal: controle.signal })
      .then(setCategorias)
      .catch(() => {}) // Sem categorias a Nav fica só com logo e ações
    buscarPoliticaPrivacidade({ signal: controle.signal })
      .then((dados) => setPolitica({ carregando: false, dados }))
      .catch((erro) => {
        if (erro.name !== 'AbortError') setPolitica({ carregando: false, dados: null })
      })
    return () => controle.abort()
  }, [])

  return (
    <>
      <Nav categorias={categorias} />

      <main className="politica">
        <header className="politica__topo">
          <p className="eyebrow">Institucional</p>
          <h1 className="titulo-2">Política de privacidade</h1>
          <p className="politica__versao" aria-live="polite">
            {politica.carregando && 'Carregando a versão…'}
            {!politica.carregando &&
              (politica.dados
                ? `Versão ${politica.dados.versao} · em vigor desde ${formatarData(politica.dados.vigencia)}`
                : 'Não foi possível carregar a versão agora. Recarregue a página para ver.')}
          </p>
        </header>

        {/* Tire este aviso depois que a Roseane revisar o texto (e, se possível, um advogado) */}
        <aside className="politica__modelo" role="note">
          <p className="rotulo">Texto-modelo</p>
          <p>
            Este texto é um modelo e ainda precisa ser revisado pela Roseane, de preferência com orientação
            jurídica, antes de valer. Os trechos entre colchetes ainda serão preenchidos.
          </p>
        </aside>

        <p className="politica__intro">
          A Rosie é uma loja de roupas on-line. Para vender, entregar e trocar as peças, precisamos de alguns
          dados seus. Aqui explicamos, sem juridiquês, quais são, para que servem, com quem são compartilhados e
          como você controla tudo isso, conforme a Lei Geral de Proteção de Dados (LGPD, Lei nº 13.709/2018).
        </p>

        <nav className="politica__indice" aria-labelledby="politica-indice">
          <p className="eyebrow" id="politica-indice">
            Nesta página
          </p>
          <ol>
            {SECOES.map((secao) => (
              <li key={secao.id}>
                <a href={`#${secao.id}`}>{secao.titulo}</a>
              </li>
            ))}
          </ol>
        </nav>

        <div className="politica__texto">
          <Secao id="controlador">
            <p>
              A responsável pelos seus dados (a &ldquo;controladora&rdquo;, na linguagem da LGPD) é a Rosie
              (@rosie.sttore), loja da Roseane:
            </p>
            <dl className="politica__ficha">
              <div>
                <dt>Nome ou razão social</dt>
                <dd>
                  <Preencher>nome completo da Roseane ou razão social da empresa</Preencher>
                </dd>
              </div>
              <div>
                <dt>CPF ou CNPJ</dt>
                <dd>
                  <Preencher>CPF ou CNPJ</Preencher>
                </dd>
              </div>
              <div>
                <dt>E-mail para assuntos de privacidade</dt>
                <dd>
                  <Preencher>e-mail de contato</Preencher>
                </dd>
              </div>
              <div>
                <dt>Encarregado (pessoa que atende os pedidos sobre dados)</dt>
                <dd>
                  <Preencher>nome de quem responde, ou a própria Roseane</Preencher>
                </dd>
              </div>
            </dl>
          </Secao>

          <Secao id="dados">
            <p>Guardamos só o que a loja usa:</p>
            <ul>
              <li>
                <strong>Cadastro:</strong> nome, e-mail, telefone e senha. A senha fica guardada de um jeito que
                ninguém consegue ler (nem nós): só dá para conferir se a que você digitou está certa. Serve para
                criar a sua conta, você entrar nela e a gente falar com você sobre os seus pedidos.
              </li>
              <li>
                <strong>Endereços de entrega:</strong> CEP, rua, número, complemento, bairro, cidade e estado. Servem
                para calcular o frete e entregar as peças.
              </li>
              <li>
                <strong>Pedidos:</strong> peças, tamanhos, valores, frete escolhido, forma de pagamento (cartão ou
                PIX), situação do pedido, código de rastreio e data da entrega. Servem para vender, entregar,
                atender trocas e manter os registros que a lei exige.
              </li>
              <li>
                <strong>Pagamento:</strong> você paga no site do Mercado Pago. A Rosie não recebe nem guarda os dados
                do cartão ou da sua conta bancária; guardamos só o número de identificação do pagamento e a data em
                que ele foi aprovado.
              </li>
              <li>
                <strong>Trocas e devoluções:</strong> a peça, o motivo e o que você escreveu ao pedir a troca.
              </li>
              <li>
                <strong>Sacola:</strong> as peças que você colocou na sacola, guardadas na sua conta.
              </li>
              <li>
                <strong>Aceite desta política:</strong> a data e a versão que você aceitou, como registro do seu
                consentimento.
              </li>
            </ul>
            <p>
              Não usamos ferramentas de análise de visitas nem anúncios que sigam você por outros sites. As empresas
              que hospedam o site podem registrar dados técnicos de acesso (como o endereço IP) para manter o
              sistema funcionando e seguro.
            </p>
          </Secao>

          <Secao id="base-legal">
            <p>A LGPD só permite usar dados pessoais com uma justificativa prevista na lei. As nossas são:</p>
            <ul>
              <li>
                <strong>Execução do contrato</strong> (art. 7º, V): cadastro, pedidos, pagamento, entrega e trocas.
                Sem esses dados não há como vender e entregar.
              </li>
              <li>
                <strong>Cumprimento de obrigação legal</strong> (art. 7º, II): guardar os registros das vendas para
                fins fiscais e atender o direito de troca e arrependimento do Código de Defesa do Consumidor.
              </li>
              <li>
                <strong>Consentimento</strong> (art. 7º, I): quando couber, por exemplo se um dia mandarmos novidades
                por e-mail ou WhatsApp. Só com o seu &ldquo;sim&rdquo;, e você pode desistir quando quiser.
              </li>
              <li>
                <strong>Legítimo interesse</strong> (art. 7º, IX): os registros técnicos de acesso, para a
                segurança do site e para evitar fraudes.
              </li>
            </ul>
          </Secao>

          <Secao id="compartilhamento">
            <p>
              Não vendemos os seus dados. Passamos a outras empresas só o necessário para a loja funcionar:
            </p>
            <ul>
              <li>
                <strong>Mercado Pago</strong> (pagamento): o seu nome, e-mail, as peças e o valor do pedido. O
                cartão ou o PIX você informa direto no Mercado Pago, que segue a política de privacidade dele.
              </li>
              <li>
                <strong>Melhor Envio e transportadoras</strong> (Correios e outras): o CEP para calcular o frete e,
                para gerar a etiqueta e entregar, o seu nome, telefone, e-mail, endereço e as peças do pedido.
              </li>
              <li>
                <strong>ViaCEP</strong>: quando você digita o CEP, o site consulta o ViaCEP para preencher a rua e
                o bairro. Só o CEP vai para lá.
              </li>
              <li>
                <strong>Cloudinary</strong>: guarda as fotos dos produtos. Nenhum dado seu vai para lá.
              </li>
              <li>
                <strong>Railway</strong> (servidor e banco de dados) e <strong>Netlify/Cloudflare</strong> (site):
                hospedam o sistema, então os dados ficam guardados nos servidores deles, que podem estar fora do
                Brasil.{' '}
                <Preencher>confirmar a região dos servidores e as garantias de cada empresa (art. 33 da LGPD)</Preencher>
              </li>
              <li>
                <strong>Google Fonts</strong>: a fonte do site vem do Google, então o seu navegador se conecta aos
                servidores dele (que recebem o seu endereço IP).
              </li>
              <li>
                <strong>Autoridades</strong>: quando a lei ou uma ordem judicial exigir.
              </li>
            </ul>
          </Secao>

          <Secao id="prazo">
            <ul>
              <li>
                <strong>Conta, endereços e sacola:</strong> enquanto a sua conta existir. Se você excluir a conta,
                esses dados são apagados ou anonimizados na hora (veja &ldquo;Excluir a sua conta&rdquo;).
              </li>
              <li>
                <strong>Pedidos e valores:</strong> pelo prazo que a lei fiscal exige, mesmo depois de a conta ser
                excluída, mas sem o seu nome, e-mail, telefone ou endereço.{' '}
                <Preencher>confirmar o prazo com a contabilidade (em geral, 5 anos)</Preencher>
              </li>
              <li>
                <strong>Registros técnicos de acesso:</strong> pelo tempo que a hospedagem guarda.{' '}
                <Preencher>confirmar o prazo com a orientação jurídica (o Marco Civil da Internet pede 6 meses)</Preencher>
              </li>
              <li>
                <strong>Registro do aceite desta política:</strong> enquanto for preciso comprovar o consentimento.
              </li>
            </ul>
          </Secao>

          <Secao id="direitos">
            <p>Pela LGPD (art. 18), você pode, a qualquer momento:</p>
            <ul>
              <li>saber se temos dados seus e ver quais são;</li>
              <li>corrigir dados errados, incompletos ou desatualizados;</li>
              <li>pedir a anonimização, o bloqueio ou a exclusão de dados desnecessários ou tratados fora da lei;</li>
              <li>receber os seus dados num formato que dê para levar a outra loja (portabilidade);</li>
              <li>saber com quem compartilhamos os seus dados;</li>
              <li>revogar um consentimento que tenha dado e pedir a exclusão dos dados usados com ele;</li>
              <li>reclamar na Autoridade Nacional de Proteção de Dados (ANPD).</li>
            </ul>
            <p>Como fazer:</p>
            <ul>
              <li>
                <strong>Ver e corrigir:</strong> em <a href="/minha-conta/dados">Minha Conta &gt; Dados Pessoais</a>{' '}
                (nome, e-mail, telefone e o aceite desta política),{' '}
                <a href="/minha-conta/enderecos">Endereços</a> e <a href="/minha-conta">Meus Pedidos</a>.
              </li>
              <li>
                <strong>Excluir a conta:</strong> em Minha Conta &gt; Dados Pessoais &gt; &ldquo;Excluir minha
                conta&rdquo;.
              </li>
              <li>
                <strong>Os outros pedidos</strong> (portabilidade, cópia completa dos dados, trocar o e-mail,
                revogar consentimento): pelo e-mail <Preencher>e-mail de contato</Preencher>. Respondemos em até 15
                dias.
              </li>
            </ul>
          </Secao>

          <Secao id="exclusao">
            <p>
              Em Minha Conta &gt; Dados Pessoais, use &ldquo;Excluir minha conta&rdquo; e confirme com a sua senha.
              O que acontece:
            </p>
            <ul>
              <li>
                o seu nome, e-mail, telefone e endereços são apagados ou trocados por um texto genérico, e a sacola
                é esvaziada;
              </li>
              <li>você não consegue mais entrar com aquela conta, e o e-mail fica livre para um cadastro novo;</li>
              <li>pedidos ainda não pagos são cancelados;</li>
              <li>
                os pedidos e os valores continuam guardados, sem identificar você, porque a lei fiscal obriga a
                loja a mantê-los;
              </li>
              <li>
                se houver um pedido a caminho ou uma troca em andamento, a exclusão espera a entrega ou a troca
                terminar: até lá, os seus dados ainda são necessários para entregar.
              </li>
            </ul>
            <p>
              O Mercado Pago e o Melhor Envio guardam as próprias cópias dos dados dos pedidos, conforme as
              políticas deles.
            </p>
          </Secao>

          <Secao id="navegador">
            <p>O site não usa cookies de propaganda nem de rastreamento. Ele guarda no seu navegador apenas:</p>
            <ul>
              <li>
                <strong>A sua sessão</strong> (no armazenamento local do navegador): um código de acesso que vale
                por 2 horas, o seu número de cliente e o seu nome, para você não precisar entrar de novo a cada
                página. Some quando você sai da conta ou o código vence.
              </li>
              <li>
                <strong>O frete escolhido na sacola</strong> (só enquanto a aba estiver aberta): o CEP e a opção de
                entrega, para o checkout já abrir com eles.
              </li>
            </ul>
            <p>
              A sacola fica na sua conta, no servidor, e não no navegador. Você pode apagar esses dados nas
              configurações do navegador a qualquer momento; a única consequência é ter que entrar de novo.
            </p>
          </Secao>

          <Secao id="seguranca">
            <ul>
              <li>a conexão com o site é criptografada (https);</li>
              <li>as senhas ficam guardadas só de forma irreversível (hash), nunca em texto puro;</li>
              <li>o acesso à sua conta expira em 2 horas, e o painel da loja só abre para a Roseane;</li>
              <li>os dados do cartão nunca passam pelo site da Rosie.</li>
            </ul>
            <p>
              Se acontecer um incidente de segurança que possa trazer risco a você, avisamos você e a ANPD, como a
              LGPD manda.
            </p>
          </Secao>

          <Secao id="mudancas">
            <p>
              Quando esta política mudar, a versão e a data no topo da página mudam junto. Se você já tiver conta,
              pedimos que leia e aceite a versão nova no próximo login ou antes de finalizar um pedido.
            </p>
          </Secao>

          <Secao id="contato">
            <p>
              Dúvidas ou pedidos sobre os seus dados: <Preencher>e-mail de contato</Preencher>
              {whatsapp && (
                <>
                  {', '}
                  <a href={whatsapp} target="_blank" rel="noopener noreferrer">
                    WhatsApp
                  </a>
                </>
              )}{' '}
              ou o Instagram{' '}
              <a href="https://www.instagram.com/rosie.sttore/" target="_blank" rel="noreferrer">
                @rosie.sttore
              </a>
              .
            </p>
          </Secao>
        </div>
      </main>

      <Footer />
    </>
  )
}

function Secao({ id, children }) {
  const { titulo } = SECOES.find((secao) => secao.id === id)
  return (
    <section className="politica__secao" id={id} aria-labelledby={`${id}-titulo`}>
      <h2 className="titulo-3" id={`${id}-titulo`}>
        {titulo}
      </h2>
      {children}
    </section>
  )
}
