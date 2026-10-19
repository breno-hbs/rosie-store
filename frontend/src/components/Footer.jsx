import { ROTA_POLITICA_PRIVACIDADE } from '../privacidade.js'
import LogoBadge from './LogoBadge.jsx'
import './Footer.css'

// Instagram e Trocas e devoluções (#27) têm destino; os demais itens ganham link quando as páginas existirem.
const colunas = [
  { titulo: 'Loja', itens: [{ texto: 'Todos os produtos' }, { texto: 'Novidades' }, { texto: 'Mais vendidos' }] },
  {
    titulo: 'Institucional',
    itens: [{ texto: 'Sobre' }, { texto: 'Trocas e devoluções', href: '/trocas', interno: true }, { texto: 'Envio' }],
  },
  {
    titulo: 'Contato',
    itens: [
      { texto: 'WhatsApp' },
      { texto: 'Instagram', href: 'https://www.instagram.com/rosie.sttore/' },
      { texto: 'E-mail' },
    ],
  },
]

export default function Footer() {
  return (
    <footer className="footer">
      <div className="footer__links">
        <div className="footer__marca">
          <LogoBadge tamanho={56} />
          <p>Espaço curto sobre a marca, reforçando o posicionamento e convidando a seguir nas redes.</p>
        </div>

        {colunas.map((coluna) => (
          <div key={coluna.titulo} className="footer__coluna">
            <p className="eyebrow">{coluna.titulo}</p>
            <ul>
              {coluna.itens.map((item) => (
                <li key={item.texto}>
                  {item.href ? (
                    <a href={item.href} {...(item.interno ? {} : { target: '_blank', rel: 'noreferrer' })}>
                      {item.texto}
                    </a>
                  ) : (
                    item.texto
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      <div className="footer__base">
        <p>© Rosie — todos os direitos reservados</p>
        <a className="footer__politica" href={ROTA_POLITICA_PRIVACIDADE}>
          Política de privacidade
        </a>
      </div>
    </footer>
  )
}
