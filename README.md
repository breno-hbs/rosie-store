# Rosie — E-commerce

**Em desenvolvimento** — Fase atual: Planejamento Técnico

Site oficial da Rosie (@rosie.sttore), desenvolvido como projeto acadêmico/portfólio.

## Estrutura do repositório

```
rosie-store/
├── backend/          # API REST (Spring Boot)
├── frontend/          # Interface web (React)
├── docs/               # Moodboard, wireframe, cronograma, requisitos
└── README.md
```

## Stack

- **Back-end:** Java + Spring Boot
- **Front-end:** React
- **Banco de dados:** MySQL
- **Painel admin:** CRUD custom (Spring Boot + React, sem CMS externo)
- **Hospedagem:** Railway (back-end + MySQL) · Netlify ou Cloudflare Pages (front-end)
- **Domínio:** Registro.br
- **Prototipagem:** Figma, antes de iniciar o desenvolvimento

## Equipe

- **Breno** — Feature: Catálogo (Home, Página de Produto)
- **Matheus** — Feature: Painel Admin (CRUD de produtos, autenticação)

Divisão por feature: cada pessoa desenvolve back-end e front-end da própria funcionalidade, ponta a ponta.

## Pré-requisitos
 
- Java 17+
- Node.js 18+
- MySQL 8+
- Maven (ou usar o `mvnw` incluso no projeto)

## Configuração
 
Antes de rodar o back-end, copie o arquivo de exemplo de configuração e preencha suas credenciais locais de banco:
 
```bash
cp backend/src/main/resources/application-example.properties backend/src/main/resources/application-local.properties
```

Alternativa: definir as variáveis de ambiente `DB_PASSWORD` (obrigatória), `DB_USERNAME` (padrão `root`) e `DB_URL` (padrão `rosie_db` no `localhost:3306`).
 
> Nunca commitar `application-local.properties` nem qualquer arquivo com credenciais reais — já está no `.gitignore`.

## Como rodar o projeto

### Back-end
```bash
cd backend
./mvnw spring-boot:run
```

### Front-end
```bash
cd frontend
npm install
npm run dev
```

## Fluxo de trabalho

- **Tarefas:** [Issues](https://github.com/breno-hbs/rosie-store/issues) no Project "Rosie", agrupadas nos milestones **v0.1 — Vitrine**, **v0.2 — Painel Admin**, **v0.3 — Compra** e **v1.0 — Lançamento**. O histórico anterior está em [`docs/kanban.md`](docs/kanban.md).
- **Requisitos e Contrato da API:** [`docs/requisitos.md`](docs/requisitos.md) e [`docs/contrato-api.md`](docs/contrato-api.md)

1. Escolha uma issue do milestone atual (ou crie uma pelo template) e atribua a você.
2. Crie a branch a partir da `main` com o tipo e o número da issue:

   ```bash
   git checkout -b feature/15-carrinho
   ```

   Tipos: `feature`, `fix`, `chore`, `docs`, os mesmos dos commits (conventional commits, em português).
3. Abra o Pull Request pelo template, com `Closes #15` na descrição, e só mescle na `main` depois da revisão. A issue fecha sozinha no merge.

## Cronograma

Prazo estimado: **8 semanas**, da descoberta ao lançamento. Ver `docs/cronograma-rosie.html` para o detalhamento por fase.

## Documentação do projeto

Ver pasta `docs/` para moodboard, wireframe, cronograma e requisitos funcionais/não funcionais.
