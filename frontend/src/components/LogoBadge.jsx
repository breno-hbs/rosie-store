import logo from '../assets/logo-rosie.jpg'
import './LogoBadge.css'

export default function LogoBadge({ tamanho = 44 }) {
  return (
    <span className="logo-badge" style={{ '--tamanho': `${tamanho}px` }}>
      <img src={logo} alt="Rosie" width={tamanho} height={tamanho} />
    </span>
  )
}
