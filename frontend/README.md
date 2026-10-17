# Rosie — front-end

React + Vite (JavaScript). Chamadas à API centralizadas em `src/api.js`.

## Rodar

```bash
npm install
npm run dev
```

Abre em http://localhost:5173 e consome a API em http://localhost:8080 (o back já libera CORS para essa origem).

## Variáveis de ambiente

Copie `.env.example` para `.env.local` (não versionado) para mudar os padrões:

- `VITE_API_URL`: URL base da API (padrão `http://localhost:8080`)
- `VITE_WHATSAPP_NUMERO`: número do WhatsApp do botão "Falar no WhatsApp" da Home (só dígitos, com DDI e DDD)

## Estrutura

- `src/pages/`: telas (hoje só a Home)
- `src/components/`: Nav, Footer, ProductCard, HeroCarousel, CategoryTiles, LogoBadge
- `src/index.css`: tokens do Figma (cores tinta, papel, pedra, fumaça, linha, acento), fonte Jost e botões
