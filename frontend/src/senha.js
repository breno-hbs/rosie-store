// Esqueci minha senha (#93): as páginas de cada área. A chave é a área da sessão no api.js ('cliente' ou 'admin').
// O e-mail do back-end leva o admin a /admin/redefinir-senha e a cliente a /redefinir-senha, e cada um volta
// para o seu login.
export const ROTAS_SENHA = {
  cliente: { esqueci: '/esqueci-senha', redefinir: '/redefinir-senha', login: '/entrar' },
  admin: { esqueci: '/admin/esqueci-senha', redefinir: '/admin/redefinir-senha', login: '/admin/login' },
}
