import { useEffect, useState } from 'react'

// Carrega dados da API com "Tentar de novo" (Minha Conta, #26). `carregar` recebe { signal } e precisa
// ser estável (função de fora do componente ou guardada em estado). Enquanto o resultado não for da
// tentativa atual, está carregando (mesmo padrão do Carrinho).
export default function useCarregar(carregar) {
  const [tentativa, setTentativa] = useState(0)
  const [resultado, setResultado] = useState({ chave: null, dados: null, erro: null })

  useEffect(() => {
    const controle = new AbortController()
    carregar({ signal: controle.signal })
      .then((dados) => setResultado({ chave: tentativa, dados, erro: null }))
      .catch((erro) => {
        if (erro.name !== 'AbortError') setResultado({ chave: tentativa, dados: null, erro })
      })
    return () => controle.abort()
  }, [carregar, tentativa])

  return {
    carregando: resultado.chave !== tentativa,
    dados: resultado.dados,
    erro: resultado.erro,
    tentarDeNovo: () => setTentativa((t) => t + 1),
  }
}
