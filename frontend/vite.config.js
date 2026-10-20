import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Modo de demonstração (#77, `npm run demo`, usado pelo scripts/demo.ps1): o site compilado é servido em
// 127.0.0.1:5173 e chama a API pelo caminho /api do mesmo endereço (VITE_API_URL=/api em .env.demo), que este
// proxy repassa para a API em localhost:8080. Assim o link do túnel (Cloudflare Quick Tunnel) é um só, para a
// loja e para a API, e não há CORS no caminho.
const API_LOCAL = 'http://localhost:8080'

const demo = {
  host: '127.0.0.1',
  port: 5173,
  strictPort: true,
  // O túnel chega com o Host aleatório *.trycloudflare.com, que o Vite recusaria
  allowedHosts: ['.trycloudflare.com'],
  proxy: {
    '/api': {
      target: API_LOCAL,
      changeOrigin: true,
      rewrite: (caminho) => caminho.replace(/^\/api/, ''),
      // Sem o Origin do túnel, a API trata a chamada como do mesmo endereço (o navegador já está na loja)
      configure: (proxy) => proxy.on('proxyReq', (requisicao) => requisicao.removeHeader('origin')),
    },
  },
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  ...(mode === 'demo' && {
    build: { outDir: 'dist-demo' },
    server: demo,
    preview: demo,
  }),
}))
