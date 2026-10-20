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

- **Breno** — desenvolvimento completo: Catálogo, Painel Admin e Compra, back-end e front-end.

O Matheus participou da descoberta e do planejamento até outubro de 2026.

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

### Back-end: banco, JWT e admin inicial

Escolha **uma** das duas formas.

**1. Arquivo `application-local.properties`** (recomendado). Copie o exemplo e troque `sua_senha_aqui` pela senha do seu MySQL (e o usuário, se não for `root`). O exemplo já traz um `rosie.jwt.segredo` só de desenvolvimento; para criar o admin do painel, descomente e preencha `rosie.admin.email` e `rosie.admin.senha`:

Windows (PowerShell):

```powershell
Copy-Item backend\src\main\resources\application-example.properties backend\src\main\resources\application-local.properties
```

macOS/Linux:

```bash
cp backend/src/main/resources/application-example.properties backend/src/main/resources/application-local.properties
```

O `application.properties` importa esse arquivo automaticamente quando ele existe; não é preciso ativar perfil. Quem já tinha o arquivo antes do login admin precisa acrescentar a linha `rosie.jwt.segredo=...` (copie do exemplo), senão a API não sobe.

**2. Variáveis de ambiente.** O `application.properties` lê:

| Variável | Padrão | Uso |
|---|---|---|
| `DB_PASSWORD` | (nenhum, obrigatória) | Senha do MySQL |
| `DB_USERNAME` | `root` | Usuário do MySQL |
| `DB_URL` | `jdbc:mysql://localhost:3306/rosie_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true` | URL JDBC |
| `CORS_ORIGENS` | `http://localhost:5173` | Origens liberadas no CORS, separadas por vírgula |
| `JWT_SECRET` | (nenhum, obrigatória) | Segredo que assina os tokens do painel (HS256), com pelo menos 32 caracteres |
| `ROSIE_ADMIN_EMAIL` | (vazio) | E-mail do admin criado na subida, se ainda não houver nenhum admin |
| `ROSIE_ADMIN_SENHA` | (vazio) | Senha desse admin, com pelo menos 8 caracteres (guardada só como hash BCrypt) |
| `ROSIE_ADMIN_NOME` | `Roseane` | Nome desse admin |
| `CLOUDINARY_URL` | (vazio) | Credencial do Cloudinary para as fotos dos produtos, no formato `cloudinary://<api_key>:<api_secret>@<cloud_name>`. Opcional: sem ela a API sobe e só o upload de fotos responde 503 |
| `MELHOR_ENVIO_TOKEN` | (vazio) | Token do Melhor Envio para o cálculo de frete (escopo `shipping-calculate`) e a etiqueta (escopos `cart-read`, `cart-write`, `shipping-checkout`, `shipping-generate`, `shipping-print` e `shipping-tracking`). Opcional: sem ele a API sobe e só o frete e a etiqueta respondem 503 |
| `MELHOR_ENVIO_URL` | `https://sandbox.melhorenvio.com.br` | API do Melhor Envio. Em produção: `https://melhorenvio.com.br` (o token da sandbox só vale na sandbox, e o de produção só em produção) |
| `MELHOR_ENVIO_USER_AGENT` | `Rosie (rosiestore.com.br)` | `User-Agent` exigido pelo Melhor Envio: nome da aplicação e e-mail de contato técnico, ex.: `Rosie (contato@exemplo.com)` |
| `ROSIE_CEP_ORIGEM` | (vazio) | CEP de onde a Rosie despacha (8 dígitos, com ou sem hífen). Sem ele o frete responde 503 |
| `FRETE_TIMEOUT` | `5s` | Tempo máximo de espera pelo Melhor Envio (passou disso, 502 `FRETE_FALHOU`) |
| `FRETE_SIMULADO` | `false` | Só desenvolvimento: `true` devolve opções de frete fixas sem chamar o Melhor Envio. Nunca em produção |
| `MERCADO_PAGO_ACCESS_TOKEN` | (vazio) | Access token do Mercado Pago (de teste na sandbox, de produção na conta da Roseane). Sem ele o checkout responde 503 |
| `MERCADO_PAGO_WEBHOOK_SECRET` | (vazio) | "Assinatura secreta" da aba Webhooks da aplicação no Mercado Pago, para validar o `x-signature` do webhook. Sem ela o checkout responde 503 |
| `ROSIE_API_URL` | (vazio) | URL pública da API, com `https` (ex.: `https://api.rosiestore.com.br`): o Mercado Pago manda o webhook para `<url>/pagamentos/webhook`. Sem ela (ou sem https) o checkout responde 503 |
| `ROSIE_LOJA_URL` | `http://localhost:5173` | Endereço da loja, para onde o Mercado Pago devolve a cliente depois de pagar (`/pedido/{id}/sucesso`, `/pendente`, `/falha`) e para onde aponta o link de confirmação da troca de e-mail (`/minha-conta/confirmar-email`). Em produção: `https://rosiestore.com.br` |
| `PAGAMENTO_TIMEOUT` | `10s` | Tempo máximo de espera pelo Mercado Pago (passou disso, 502 `PAGAMENTO_FALHOU`) |
| `PAGAMENTO_SIMULADO` | `false` | Só desenvolvimento: `true` troca o Mercado Pago por uma página da loja que aprova, recusa ou cancela o pagamento. Nunca em produção |
| `ROSIE_REMETENTE_NOME` | (vazio) | Remetente da etiqueta de envio (#25): nome da loja ou da Roseane. Obrigatória para a etiqueta |
| `ROSIE_REMETENTE_DOCUMENTO` | (vazio) | CPF (11 dígitos) ou CNPJ (14) do remetente, com ou sem pontuação. Obrigatória para a etiqueta |
| `ROSIE_REMETENTE_INSCRICAO_ESTADUAL` | (vazio) | Inscrição estadual, só para CNPJ que tenha |
| `ROSIE_REMETENTE_TELEFONE` | (vazio) | Telefone do remetente. Obrigatória para a etiqueta |
| `ROSIE_REMETENTE_EMAIL` | (vazio) | E-mail do remetente (opcional) |
| `ROSIE_REMETENTE_LOGRADOURO`, `ROSIE_REMETENTE_NUMERO`, `ROSIE_REMETENTE_COMPLEMENTO`, `ROSIE_REMETENTE_BAIRRO`, `ROSIE_REMETENTE_CIDADE`, `ROSIE_REMETENTE_UF` | (vazio) | Endereço de onde a Rosie despacha (o CEP é o `ROSIE_CEP_ORIGEM`), que também é o destino da etiqueta de volta e o endereço de devolução que a cliente vê na troca "não serviu" (#63). Só o complemento é opcional; `UF` é a sigla (SP) |
| `ETIQUETA_TIMEOUT` | `20s` | Tempo máximo de espera por cada chamada da etiqueta ao Melhor Envio (passou disso, 502 `ETIQUETA_FALHOU`) |
| `TROCA_PRAZO_DIAS` | `7` | Dias corridos depois da entrega em que a cliente pode pedir troca (#27). O padrão é o direito de arrependimento do CDC; pode aumentar, não diminuir |
| `ROSIE_RASTREIO_ATIVO` | `true` | Liga a entrega pelo rastreio (#64): a API consulta o Melhor Envio e marca sozinha os pedidos entregues. `false` desliga (fica só o botão "Marcar como entregue"). Sem `MELHOR_ENVIO_TOKEN` não roda |
| `ROSIE_RASTREIO_INTERVALO` | `6h` | Intervalo entre as rodadas do rastreio (ex.: `30m`, `6h`); a primeira sai 1 min depois de a API subir |
| `ROSIE_RASTREIO_LOTE` | `50` | Máximo de pedidos consultados por rodada; os outros ficam para a seguinte |
| `ROSIE_RASTREIO_PAUSA` | `1s` | Pausa entre uma consulta e outra ao Melhor Envio, para respeitar os limites da API |
| `RESEND_API_KEY` | (vazio) | Chave da API do [Resend](https://resend.com) para os e-mails à cliente (#65: troca aprovada, recusada ou concluída; #61: link de confirmação e aviso da troca de e-mail). Opcional: sem ela a API sobe e os e-mails só vão para o log |
| `ROSIE_EMAIL_REMETENTE` | `Rosie <contato@rosiestore.com.br>` | Remetente dos e-mails. O domínio precisa estar verificado no Resend (registros SPF e DKIM no DNS) |
| `ROSIE_EMAIL_RESPONDER_PARA` | (vazio) | Para onde vão as respostas da cliente (opcional; vazio = para o remetente) |
| `EMAIL_TIMEOUT` | `10s` | Tempo máximo de espera pelo Resend em cada e-mail (o envio roda fora da requisição) |
| `ROSIE_TROCA_EMAIL_VALIDADE` | `24h` | Prazo do link de confirmação da troca de e-mail na Minha Conta (#61), ex.: `30m`, `24h`, `2d`. Passou disso, o link responde "expirado" e a cliente pede de novo |
| `ROSIE_WHATSAPP_NUMERO` | (vazio) | WhatsApp da loja com DDI e DDD, só dígitos (o mesmo de `VITE_WHATSAPP_NUMERO`), mostrado como contato nos e-mails. Vazio = só o Instagram |

Windows (PowerShell, vale só para o terminal aberto):

```powershell
$env:DB_PASSWORD = "sua_senha"
```

macOS/Linux:

```bash
export DB_PASSWORD=sua_senha
```

`CORS_ORIGENS` só precisa ser definida se o front rodar em outro endereço que não `http://localhost:5173` (em produção, inclui `https://rosiestore.com.br`).

O admin inicial é criado uma vez só: na primeira subida sem nenhum usuário `ADMIN` no banco. Depois disso `ROSIE_ADMIN_EMAIL` e `ROSIE_ADMIN_SENHA` são ignoradas (trocar a senha não altera o admin já criado). Em produção, gere um `JWT_SECRET` próprio (ex.: `openssl rand -base64 48`); trocar o segredo derruba todas as sessões abertas do painel.

**Fotos dos produtos (Cloudinary).** O upload de fotos do painel guarda os arquivos no Cloudinary (o disco do Railway é apagado a cada deploy). Para testar o upload localmente, crie uma conta gratuita, copie a "API environment variable" em Settings > API Keys e coloque em `rosie.cloudinary.url` no `application-local.properties` (ou na variável `CLOUDINARY_URL`). Sem isso, todo o resto funciona e o envio de foto mostra o erro `ARMAZENAMENTO_INDISPONIVEL`. Em produção, defina `CLOUDINARY_URL` nas variáveis do serviço da API no Railway. Os testes (`./mvnw test`) não usam o Cloudinary.

**Frete (Melhor Envio).** O carrinho cota o frete na API do Melhor Envio. Para testar com a API de verdade, crie uma conta na [sandbox](https://sandbox.melhorenvio.com.br), gere um token em Integrações > Permissões de acesso (escopo `shipping-calculate`) e preencha `rosie.frete.melhor-envio.*` e `rosie.frete.cep-origem` no `application-local.properties` (ou as variáveis `MELHOR_ENVIO_TOKEN`, `MELHOR_ENVIO_USER_AGENT` e `ROSIE_CEP_ORIGEM`). Sem token, dá para testar a tela com opções fixas ligando o modo simulado: `rosie.frete.simulado=true` no `application-local.properties` (ou `FRETE_SIMULADO=true`); nele, o CEP `99999-999` responde sem opções. Em produção, defina `MELHOR_ENVIO_TOKEN` (da conta real), `MELHOR_ENVIO_URL=https://melhorenvio.com.br`, `MELHOR_ENVIO_USER_AGENT` e `ROSIE_CEP_ORIGEM` no Railway. Os testes (`./mvnw test`) não chamam o Melhor Envio.

**Pagamento (Mercado Pago).** O checkout cria uma preferência no Checkout Pro do Mercado Pago e manda a cliente pagar lá (cartão de crédito ou PIX); o pedido só vira `PAGO`, e o estoque só baixa, quando o webhook do Mercado Pago avisa que o pagamento foi aprovado. Para testar com a sandbox: no [painel de desenvolvedor](https://www.mercadopago.com.br/developers/panel/app) crie uma aplicação (Checkout Pro), copie o access token de teste em Credenciais de teste e o segredo em Webhooks > Assinatura secreta, e preencha `rosie.pagamento.mercado-pago.*` e `rosie.api.url-publica` no `application-local.properties` (ou as variáveis `MERCADO_PAGO_ACCESS_TOKEN`, `MERCADO_PAGO_WEBHOOK_SECRET` e `ROSIE_API_URL`). Como o Mercado Pago não alcança o `localhost`, a API local precisa de um túnel https (ex.: `ngrok http 8080`) e `ROSIE_API_URL` aponta para ele; pague com as contas e os [cartões de teste](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/integration-test/test-cards) do Mercado Pago. Sem credencial, dá para testar o fluxo inteiro ligando o modo simulado: `rosie.pagamento.simulado=true` (ou `PAGAMENTO_SIMULADO=true`); o "Finalizar pedido" leva a uma página da loja com os botões "Aprovar", "Recusar" e "Cancelar", que passam pelo mesmo processamento do webhook. Em produção, defina as três variáveis com as credenciais da conta da Roseane e `ROSIE_LOJA_URL=https://rosiestore.com.br` no Railway. Os testes (`./mvnw test`) não chamam o Mercado Pago.

**Etiqueta de envio (Melhor Envio).** No painel, em Pedidos, cada pedido pago tem o botão "Gerar etiqueta": a API põe o envio no carrinho do Melhor Envio, compra (com o **saldo da conta do Melhor Envio**), gera a etiqueta e guarda no pedido o link para imprimir e o código de rastreio, que a cliente vê no pedido. Não é automático no pagamento, para a Roseane conferir o pedido antes de gastar o saldo. Usa o mesmo token, URL e CEP de origem do frete; o token precisa também dos escopos `cart-read`, `cart-write`, `shipping-checkout`, `shipping-generate`, `shipping-print` e `shipping-tracking`, e os dados do remetente vêm de `ROSIE_REMETENTE_*` (ou `rosie.etiqueta.remetente.*` no `application-local.properties`). Na sandbox, adicione saldo de teste pelo painel da sandbox antes de gerar. Sem token ou sem os dados do remetente, o botão responde 503 `ETIQUETA_INDISPONIVEL`; com `FRETE_SIMULADO=true`, a etiqueta também é de mentira (o link abre `/etiqueta-simulada.html` da loja). Em produção, defina as variáveis `ROSIE_REMETENTE_*` no Railway. Os testes (`./mvnw test`) não chamam o Melhor Envio.

**Etiqueta de volta da troca (logística reversa).** Em Trocas, uma troca aprovada por defeito (frete da Rosie) tem o botão "Gerar etiqueta de volta" (#63): a API compra no Melhor Envio, com o mesmo saldo e os mesmos passos da etiqueta de ida, um envio reverso da cliente (endereço do pedido) para a loja e guarda na troca o link público para imprimir e o rastreio. A cliente vê a etiqueta e como postar em Minha Conta > Trocas e recebe um e-mail. Na troca "não serviu", a loja não compra etiqueta: a cliente vê o endereço da loja (`ROSIE_REMETENTE_*`) e manda por conta própria. A conclusão da troca continua manual.

**Entrega pelo rastreio (Melhor Envio).** A cada `ROSIE_RASTREIO_INTERVALO` (padrão 6 h) a API consulta o rastreio dos pedidos `ENVIADO` e, quando o Melhor Envio informa a entrega, marca o pedido `ENTREGUE` com a data da transportadora: o prazo de troca da cliente abre sem a Roseane precisar clicar. O painel mostra se a entrega foi confirmada pelo rastreio ou marcada à mão; o botão "Marcar como entregue" continua para transportadora sem rastreio ou atraso na atualização. Usa o mesmo token e a mesma configuração da etiqueta: sem `MELHOR_ENVIO_TOKEN`, a rotina não roda (avisa uma vez no log). Com `FRETE_SIMULADO=true`, a etiqueta simulada conta como entregue no dia, e a rodada seguinte marca o pedido; para ver a rodada logo, suba a API com `ROSIE_RASTREIO_INTERVALO=30s`, e para testar o botão em vez da rotina use `ROSIE_RASTREIO_ATIVO=false`. Em produção, os padrões servem; ajuste `ROSIE_RASTREIO_LOTE` e `ROSIE_RASTREIO_PAUSA` só se o Melhor Envio reclamar de limite. Os testes (`./mvnw test`) rodam com a rotina desligada (`pom.xml`) e chamam a rodada direto, com o Melhor Envio falso.

**E-mail à cliente (Resend).** Quando a Roseane aprova, recusa ou conclui uma troca no painel, a cliente recebe um e-mail (#65). A infraestrutura (`EnviadorEmail` e `FilaEmail`) serve para os próximos e-mails, como a troca de e-mail da conta (#61). O e-mail sai depois do commit, numa fila própria: falha ou demora do Resend nunca desfaz nem segura a ação que o disparou, só vai para o log da API, e não há nova tentativa. A exceção é o link de confirmação da troca de e-mail (#61), enviado na hora: se o Resend falhar, a troca não fica pendente e a cliente vê o erro (502 `EMAIL_FALHOU`). Sem `RESEND_API_KEY` (o normal no dev e no CI), nada sai e o log registra o destinatário mascarado e o assunto, sem o corpo. Para testar a troca de e-mail no dev, ligue o log do corpo com `logging.level.com.rosie.backend.service.EmailSoNoLog=DEBUG` (no `application-local.properties` ou `--logging.level...=DEBUG` no `spring-boot:run`) e copie o link de confirmação do console; nunca ligue isso em produção. Para testar com o Resend de verdade, crie uma conta, gere uma chave em API Keys (permissão "Sending access") e preencha `rosie.email.resend.api-key` e `rosie.email.remetente` no `application-local.properties`; antes de verificar o domínio, o remetente `onboarding@resend.dev` só entrega no e-mail da própria conta do Resend. Em produção: em Domains, adicione `rosiestore.com.br` e crie no DNS os registros SPF e DKIM que o Resend mostrar; depois defina `RESEND_API_KEY`, `ROSIE_EMAIL_REMETENTE` e, se quiser, `ROSIE_EMAIL_RESPONDER_PARA` e `ROSIE_WHATSAPP_NUMERO` no Railway. Os testes (`./mvnw test`) não chamam o Resend.

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
