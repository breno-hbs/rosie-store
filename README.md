# Rosie — E-commerce

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
- **Banco de dados:** (definir — Postgres ou MySQL)
- **Hospedagem:** (definir)

## Equipe

- Breno — [Feature: Catálogo]
- Matheus — [Feature: Painel Admin]

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

Cada funcionalidade é desenvolvida em uma branch própria a partir da `main`:

```bash
git checkout -b feature/nome-da-funcionalidade
```

Ao finalizar, abrir um Pull Request para revisão antes de mesclar na `main`.

## Documentação do projeto

Ver pasta `docs/` para moodboard, wireframe, cronograma e requisitos funcionais/não funcionais.
