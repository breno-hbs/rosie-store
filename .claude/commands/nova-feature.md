---
description: "Implementa um card do Kanban ponta a ponta"
argument-hint: "<nome do card>"
---

Implemente o card "$ARGUMENTS" ponta a ponta:

1. Leia o card no Kanban do Notion, se o conector estiver disponível; senão, use o texto do argumento.
2. Crie a branch `feature/<nome>`.
3. Acione o agente `backend` e/ou `frontend`, conforme o card.
4. Acione o agente `revisor` e corrija os pontos críticos.
5. Faça commit, push, abra o PR e marque o card como In progress.
