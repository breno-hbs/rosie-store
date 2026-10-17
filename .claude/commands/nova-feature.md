---
description: "Implementa um card do Kanban ponta a ponta"
argument-hint: "<nome do card>"
---

Implemente o card "$ARGUMENTS" ponta a ponta:

1. Procure o card em `docs/kanban.md` e os requisitos ligados a ele em `docs/requisitos.md`; se o card não estiver lá, use o texto do argumento.
2. Crie a branch `feature/<nome>`.
3. Acione o agente `backend` e/ou `frontend`, conforme o card.
4. Acione o agente `revisor` e corrija os pontos críticos.
5. Mova o card para "Em andamento" em `docs/kanban.md`, faça commit, push e abra o PR.
