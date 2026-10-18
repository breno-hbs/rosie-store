# Rosie — E-commerce

**Em desenvolvimento** — o trabalho está organizado nos milestones das [Issues](https://github.com/breno-hbs/rosie-store/milestones): v0.1 — Vitrine, v0.2 — Painel Admin, v0.3 — Compra e v1.0 — Lançamento.

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

| Ferramenta | Versão | Observação |
|---|---|---|
| JDK | **17** | O `pom.xml` e o CI usam Java 17. O Maven não precisa ser instalado: o projeto traz o wrapper (`mvnw`). |
| Node.js | 20.19+ ou 22.12+ | Exigência do Vite 8. O CI usa Node 22. |
| MySQL | 8 | Rodando em `localhost:3306`. O banco `rosie_db` é criado sozinho na primeira execução. |

Os comandos abaixo aparecem em duas versões quando o sistema faz diferença: **Windows (PowerShell)** e **macOS/Linux** (vale também para o Git Bash no Windows).

### JDK 17

Windows (PowerShell):

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

macOS:

```bash
brew install --cask temurin@17
```

Linux (Debian/Ubuntu):

```bash
sudo apt install openjdk-17-jdk
```

### `JAVA_HOME`

Sem `JAVA_HOME` apontando para a JDK 17, o wrapper do Maven falha com `The JAVA_HOME environment variable is not defined correctly`. No Windows o instalador da Temurin não define essa variável por padrão.

Windows (PowerShell), de forma persistente para o seu usuário:

```powershell
$jdk = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1).FullName
[Environment]::SetEnvironmentVariable("JAVA_HOME", $jdk, "User")
```

Feche e abra o terminal (e a IDE) para a variável valer, e confira:

```powershell
echo $env:JAVA_HOME
java -version
```

`java -version` deve mostrar `17`. Se mostrar outra versão, há outra JDK antes no `Path`: em "Editar as variáveis de ambiente do sistema", mova `%JAVA_HOME%\bin` (ou a pasta `bin` da JDK 17) para o topo do `Path` do usuário.

macOS (adicione ao `~/.zshrc`):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

Linux (adicione ao `~/.bashrc`; o caminho pode variar com a distribuição):

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

### Node.js

Instale a versão LTS em [nodejs.org](https://nodejs.org) (ou `winget install OpenJS.NodeJS.LTS` no Windows, `brew install node` no macOS). Confira com `node --version`.

### MySQL 8

Instale o MySQL Server 8 ([dev.mysql.com/downloads](https://dev.mysql.com/downloads/mysql/), `brew install mysql` no macOS ou `sudo apt install mysql-server` no Linux), deixe-o rodando na porta 3306 e anote a senha do usuário `root` (ou de outro usuário que você criar). Não é preciso criar o banco: a URL de conexão usa `createDatabaseIfNotExist=true`.

## Configuração

### Back-end: credenciais do banco

Escolha **uma** das duas formas.

**1. Arquivo `application-local.properties`** (recomendado). Copie o exemplo e troque `sua_senha_aqui` pela senha do seu MySQL (e o usuário, se não for `root`):

Windows (PowerShell):

```powershell
Copy-Item backend\src\main\resources\application-example.properties backend\src\main\resources\application-local.properties
```

macOS/Linux:

```bash
cp backend/src/main/resources/application-example.properties backend/src/main/resources/application-local.properties
```

O `application.properties` importa esse arquivo automaticamente quando ele existe; não é preciso ativar perfil.

**2. Variáveis de ambiente.** O `application.properties` lê:

| Variável | Padrão | Uso |
|---|---|---|
| `DB_PASSWORD` | (nenhum, obrigatória) | Senha do MySQL |
| `DB_USERNAME` | `root` | Usuário do MySQL |
| `DB_URL` | `jdbc:mysql://localhost:3306/rosie_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true` | URL JDBC |
| `CORS_ORIGENS` | `http://localhost:5173` | Origens liberadas no CORS, separadas por vírgula |

Windows (PowerShell, vale só para o terminal aberto):

```powershell
$env:DB_PASSWORD = "sua_senha"
```

macOS/Linux:

```bash
export DB_PASSWORD=sua_senha
```

`CORS_ORIGENS` só precisa ser definida se o front rodar em outro endereço que não `http://localhost:5173` (em produção, inclui `https://rosiestore.com.br`).

> Nunca commitar `application-local.properties`, `.env.local` nem qualquer arquivo com credenciais reais. Os dois já estão no `.gitignore`.

### Front-end: `.env.local`

Copie o exemplo:

Windows (PowerShell):

```powershell
Copy-Item frontend\.env.example frontend\.env.local
```

macOS/Linux:

```bash
cp frontend/.env.example frontend/.env.local
```

| Variável | Padrão | Uso |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080` | URL base da API |
| `VITE_WHATSAPP_NUMERO` | vazio | Número do WhatsApp da Rosie com DDI e DDD, só dígitos (ex.: `5511999999999`). Vazio deixa o botão "Falar no WhatsApp" sem link. |

Sem `.env.local`, valem os padrões acima. Depois de mudar o arquivo, reinicie o `npm run dev`.

## Como rodar o projeto

Rode o back-end e o front-end em dois terminais, com o MySQL já ligado.

### Back-end

Windows (PowerShell):

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

macOS/Linux e Git Bash no Windows:

```bash
cd backend
./mvnw spring-boot:run
```

A primeira execução baixa o Maven e as dependências, então demora mais. A API sobe em **http://localhost:8080** (ex.: http://localhost:8080/produtos).

### Front-end

```bash
cd frontend
npm install
npm run dev
```

O site abre em **http://localhost:5173**. A Home fica na raiz e a Página de Produto em `/produtos/{id}` (ex.: http://localhost:5173/produtos/1).

### Testes e verificações

O CI do GitHub Actions roda estes mesmos comandos em todo Pull Request para a `main` (checks `testes-backend` e `lint-build-frontend`), e o merge só libera com os dois verdes. Rode antes de abrir o PR.

Testes do back-end (precisam do MySQL local rodando e das credenciais configuradas, como em "Configuração"):

Windows (PowerShell):

```powershell
cd backend
.\mvnw.cmd test
```

macOS/Linux e Git Bash no Windows:

```bash
cd backend
./mvnw test
```

Lint e build do front-end:

```bash
cd frontend
npm run lint
npm run build
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
