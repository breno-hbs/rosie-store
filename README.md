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
- **Hospedagem:** (definir — back-end e front-end)
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

- **Board de tarefas:** [Kanban — Notion](https://app.notion.com/p/65daf739da254a479b5facc5604010fd?v=3f27cec9256646e18d46729e89261b8e)
- **Branches:** cada funcionalidade é desenvolvida em uma branch própria a partir da `main`

```bash
git checkout -b feature/nome-da-funcionalidade
```

Ao finalizar, abrir um Pull Request para revisão antes de mesclar na `main`.

## Cronograma

Prazo estimado: **8 semanas**, da descoberta ao lançamento. Ver `docs/cronograma-rosie.html` para o detalhamento por fase.

## Documentação do projeto

Ver pasta `docs/` para moodboard, wireframe, cronograma e requisitos funcionais/não funcionais.
