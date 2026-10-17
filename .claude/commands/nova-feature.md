---
description: "Implementa uma issue do GitHub ponta a ponta"
argument-hint: "<número da issue>"
---

Implemente a issue #$ARGUMENTS ponta a ponta:

1. Leia a issue com `gh issue view $ARGUMENTS` (contexto, requisitos, critérios de aceite, dependências). Se ela depende de outra issue ainda aberta, pare e avise.
2. Atribua a issue a você (`gh issue edit $ARGUMENTS --add-assignee @me`) e crie a branch `<tipo>/$ARGUMENTS-<nome>` a partir da `main` atualizada (tipo pelo label: `feature`, `fix`, `chore` ou `docs`).
3. Acione o agente `backend` e/ou `frontend`, conforme os labels `area:*`.
4. Acione o agente `revisor` e corrija os pontos críticos.
5. Confira cada critério de aceite da issue; o que não couber vira comentário na issue ou uma issue nova.
6. Faça commit, push e abra o PR pelo template (`.github/pull_request_template.md`) com `Closes #$ARGUMENTS`.
