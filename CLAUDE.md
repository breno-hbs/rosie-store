# Rosie — e-commerce

Projeto freelance para a Roseane (@rosie.sttore). Desenvolvedor: Breno (solo, desde que o Matheus saiu do projeto em outubro de 2026), cuidando de todas as features.

## Stack
- Back-end: Java 17, Spring Boot, MySQL 8 (banco `rosie_db`), porta 8080
- Front-end: React + Vite (JavaScript), porta 5173
- Deploy: Railway (API + MySQL), Netlify/Cloudflare (front), domínio rosiestore.com.br

## Estrutura
- `backend/` organizado por camada: model, repository, dto, service, controller, config, exception
- `frontend/src/` componentes + páginas, chamadas à API centralizadas em `api.js`
- `docs/` materiais de projeto e fontes de verdade (requisitos, contrato da API)
- `.github/` templates de issue e de PR

## Fontes de verdade
- Tarefas: Issues do GitHub (`breno-hbs/rosie-store`), no Project "Rosie", com milestones v0.1 — Vitrine, v0.2 — Painel Admin, v0.3 — Compra e v1.0 — Lançamento. `docs/kanban.md` é só histórico.
- Requisitos: `docs/requisitos.md`
- Contrato da API: `docs/contrato-api.md`
- Design: Figma, file key HZyo0IXpIUNSmoOpCNeqgg

## Regras
- O Contrato da API manda. Se um endpoint mudar, atualize `docs/contrato-api.md` no mesmo PR.
- Entidade JPA nunca sai na API: sempre DTO.
- Erros no formato {"erro": "...", "codigo": "..."}.
- Toda mudança parte de uma issue. Trabalho novo sem issue: crie a issue antes, com o template.
- Uma sessão (ou subtarefa) do Claude por issue: o título da sessão é `#<número> — <título da issue>`, e a sessão termina no PR que fecha essa issue. Nada de sessão com várias issues misturadas.
- Nada direto na main: branch `<tipo>/<número da issue>-<nome>` (tipo: `feature`, `fix`, `chore`, `docs`; ex.: `feature/15-carrinho`), commits em português (conventional commits), PR pelo template com `Closes #<número>`.
- Labels: um `tipo:*`, as `area:*` tocadas, a `feature:*` e uma `prioridade:*`.
- Nunca commitar credenciais (.env, application-local.properties).
- Tamanhos: P, M, G, GG. Categorias: Vestido, Calça, Camiseta, Cropped/Body.
- Design: cores só via variáveis CSS (tinta, papel, pedra, fumaça, linha, acento), fonte Jost, mobile-first, sem sombra nem gradiente.

## Comandos
- Back: `cd backend && ./mvnw spring-boot:run`
- Front: `cd frontend && npm run dev`
- Testes back: `./mvnw test`
- Lint e build front: `cd frontend && npm run lint && npm run build`
- Issues: `gh issue list --milestone "v0.1 — Vitrine"`, `gh issue view <número>`
