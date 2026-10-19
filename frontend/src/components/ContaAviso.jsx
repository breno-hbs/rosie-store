// Carregando ou erro (com "Tentar de novo") nas abas da Minha Conta (#26)
export default function ContaAviso({ carregando, erro, mensagemErro, onTentarDeNovo }) {
  if (carregando) {
    return (
      <p className="conta__aviso" aria-busy="true">
        Carregando…
      </p>
    )
  }
  return (
    <div className="conta__aviso" role="alert">
      <p>{erro.codigo === 'SEM_CONEXAO' ? 'Sem conexão com a loja. Confira a internet e tente de novo.' : mensagemErro}</p>
      <button type="button" className="botao botao--contorno" onClick={onTentarDeNovo}>
        Tentar de novo
      </button>
    </div>
  )
}
