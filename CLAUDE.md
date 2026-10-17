# Rosie — e-commerce

Projeto freelance para a Roseane (@rosie.sttore). Desenvolvedor: Breno (solo).

## Stack
- Back-end: Java 17, Spring Boot, MySQL 8 (banco `rosie_db`), porta 8080
- Front-end: React + Vite (JavaScript), porta 5173
- Deploy: Railway (API + MySQL), Netlify/Cloudflare (front), domínio rosiestore.com.br

## Estrutura
- `backend/` organizado por camada: model, repository, dto, service, controller, config, exception
- `frontend/src/` componentes + páginas, chamadas à API centralizadas em `api.js`
- `docs/` materiais de projeto

## Fontes de verdade
- Kanban, Requisitos e Contrato da API: Notion ("Site - Rosie")
- Design: Figma, file key HZyo0IXpIUNSmoOpCNeqgg

## Regras
- O Contrato da API manda. Se um endpoint mudar, atualize o Notion no mesmo PR.
- Entidade JPA nunca sai na API: sempre DTO.
- Erros no formato {"erro": "...", "codigo": "..."}.
- Nada direto na main: branch `feature/<nome>`, PR, commits em português (conventional commits).
- Nunca commitar credenciais (.env, application-local.properties).
- Tamanhos: P, M, G, GG. Categorias: Vestido, Calça, Camiseta, Cropped/Body.
- Design: cores só via variáveis CSS (tinta, papel, pedra, fumaça, linha, acento), fonte Jost, mobile-first, sem sombra nem gradiente.

## Comandos
- Back: `cd backend && ./mvnw spring-boot:run`
- Front: `cd frontend && npm run dev`
- Testes back: `./mvnw test`
