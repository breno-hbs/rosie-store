import { useEffect, useState } from 'react'
import { buscarEnderecoPorCep } from '../api.js'
import { mascararCep, somenteDigitos } from '../formato.js'
import './CamposEndereco.css'

// Campos do Figma (Checkout, #24; também na Minha Conta, #26): CEP e Rua; Número e Complemento;
// Bairro, Cidade e UF. Com os 8 números do CEP, rua, bairro, cidade e UF vêm do ViaCEP (a cliente
// pode corrigir). Endereço que já chega preenchido (edição) só é buscado de novo se o CEP mudar.
export default function CamposEndereco({ valor, onMudar }) {
  const cep = somenteDigitos(valor.cep)
  const [cepPreenchido] = useState(() => (valor.logradouro.trim() !== '' ? somenteDigitos(valor.cep) : null))
  const [busca, setBusca] = useState({ cep: null, resultado: null })
  const buscar = cep.length === 8 && cep !== cepPreenchido
  const buscando = buscar && busca.cep !== cep

  useEffect(() => {
    if (!buscar) return
    const controle = new AbortController()
    buscarEnderecoPorCep(cep, { signal: controle.signal })
      .then((encontrado) => {
        setBusca({ cep, resultado: encontrado ? 'ok' : 'nao-encontrado' })
        if (!encontrado) return
        onMudar((atual) => ({
          ...atual,
          logradouro: encontrado.logradouro || atual.logradouro,
          bairro: encontrado.bairro || atual.bairro,
          cidade: encontrado.cidade || atual.cidade,
          estado: encontrado.estado || atual.estado,
        }))
      })
      .catch((erro) => {
        // Sem ViaCEP a cliente preenche à mão
        if (erro.name !== 'AbortError') setBusca({ cep, resultado: 'erro' })
      })
    return () => controle.abort()
  }, [cep, buscar, onMudar])

  function campo(nome) {
    return {
      name: nome,
      value: valor[nome],
      onChange: (e) => onMudar((atual) => ({ ...atual, [nome]: e.target.value })),
    }
  }

  return (
    <div className="endereco-campos">
      <div className="endereco-campos__fileira">
        <label className="campo endereco-campos__cep">
          <span className="eyebrow">CEP</span>
          <input
            className="campo__caixa"
            name="cep"
            inputMode="numeric"
            autoComplete="postal-code"
            placeholder="00000-000"
            maxLength={9}
            required
            value={valor.cep}
            onChange={(e) => onMudar((atual) => ({ ...atual, cep: mascararCep(e.target.value) }))}
          />
        </label>
        <label className="campo endereco-campos__rua">
          <span className="eyebrow">Rua</span>
          <input
            className="campo__caixa"
            autoComplete="address-line1"
            placeholder="Nome da rua"
            maxLength={200}
            required
            {...campo('logradouro')}
          />
        </label>
      </div>
      <div className="endereco-campos__fileira">
        <label className="campo endereco-campos__numero">
          <span className="eyebrow">Número</span>
          <input className="campo__caixa" placeholder="000" maxLength={20} required {...campo('numero')} />
        </label>
        <label className="campo endereco-campos__complemento">
          <span className="eyebrow">Complemento</span>
          <input
            className="campo__caixa"
            autoComplete="address-line2"
            placeholder="Apto, bloco (opcional)"
            maxLength={100}
            {...campo('complemento')}
          />
        </label>
      </div>
      <div className="endereco-campos__fileira">
        <label className="campo endereco-campos__bairro">
          <span className="eyebrow">Bairro</span>
          <input className="campo__caixa" placeholder="Bairro" maxLength={100} required {...campo('bairro')} />
        </label>
        <label className="campo endereco-campos__cidade">
          <span className="eyebrow">Cidade</span>
          <input
            className="campo__caixa"
            autoComplete="address-level2"
            placeholder="Cidade"
            maxLength={100}
            required
            {...campo('cidade')}
          />
        </label>
        <label className="campo endereco-campos__uf">
          <span className="eyebrow">UF</span>
          <input
            className="campo__caixa"
            autoComplete="address-level1"
            placeholder="SP"
            maxLength={2}
            required
            {...campo('estado')}
          />
        </label>
      </div>
      <p className="endereco-campos__aviso" aria-live="polite">
        {buscando
          ? 'Buscando o endereço do CEP…'
          : busca.cep === cep && busca.resultado === 'nao-encontrado'
            ? 'CEP não encontrado. Confira os números ou preencha o endereço.'
            : ''}
      </p>
    </div>
  )
}
