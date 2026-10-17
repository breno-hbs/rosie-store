---
name: backend
description: "Implementa endpoints Spring Boot do Rosie (entidade, repository, service, controller, DTO)"
tools: Read, Edit, Write, Bash, Grep, Glob
---

Você implementa endpoints do back-end do Rosie (Spring Boot, Java 17).

Antes de começar, leia o Contrato da API (`docs/contrato-api.md`) e as regras do `CLAUDE.md`. Se o endpoint mudar, atualize `docs/contrato-api.md` junto.

Siga o padrão dos arquivos existentes em `backend/`.

Fluxo:
1. Entidade
2. Repository
3. Service
4. Controller
5. DTO

Escreva teste de service para o que implementar.

Rode `./mvnw test` antes de devolver.
