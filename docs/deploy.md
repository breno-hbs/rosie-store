# Deploy da Rosie

Passo a passo para pôr a loja no ar (issue #28) e a lista de **todas** as variáveis de ambiente. O código já está pronto para produção desde a #74; o que falta são as contas, as variáveis e o DNS.

## Visão geral

| Peça | Onde | Endereço |
|---|---|---|
| Front (React + Vite) | Netlify, pasta `frontend/` | `https://rosiestore.com.br` (e `www`) |
| API (Spring Boot) | Railway, serviço com Root Directory `backend` | `https://api.rosiestore.com.br` |
| Banco (MySQL 8) | Railway, no mesmo projeto da API | só pela rede privada do Railway |
| Domínio e DNS | Registro.br | `rosiestore.com.br` |
| Fotos | Cloudinary | já em uso |
| Frete e etiquetas | Melhor Envio (conta real) | `https://melhorenvio.com.br` |
| Pagamento | Mercado Pago (conta da Roseane) | Checkout Pro + webhook na API |
| E-mail | Resend | domínio `rosiestore.com.br` verificado |

O que já está no repositório:

- **Flyway** (`backend/src/main/resources/db/migration`): o esquema do banco é criado e atualizado pelas migrações na subida da API. O Hibernate só valida (`ddl-auto=validate`).
- **Perfil `prod`** (`application-prod.properties`): porta por `PORT`, `forward-headers-strategy=framework` (o Railway fica atrás de um proxy https), sem `show-sql`, sem log de SQL nem do corpo dos e-mails, CORS e URL da loja sem padrão `localhost`.
- **Health check**: `GET /actuator/health` responde `{"status":"UP"}` (ou 503 com `DOWN`, por exemplo sem banco). Nenhum outro endpoint do Actuator existe.
- **`backend/Dockerfile`** e **`backend/railway.json`**: o Railway compila a imagem com o JDK 17, roda com o JRE 17 e um usuário sem root, sobe com o perfil `prod` e só põe a versão nova no ar depois que o health check responde.
- **`frontend/netlify.toml`**: comando de build, pasta publicada, Node 22 e o redirect da SPA.

## Esquema do banco (Flyway)

A V1 (`V1__esquema_inicial.sql`) é exatamente o esquema que o Hibernate gerava até a v1.0 (mesmas tabelas, colunas, tipos e nomes de constraint). Em cada subida, o Flyway aplica as migrações que faltam e anota na tabela `flyway_schema_history`; depois o Hibernate confere se as entidades batem com as tabelas e, se não baterem, a API não sobe.

**Banco novo (produção, CI, um banco local apagado):** a V1 cria todas as tabelas.

**Seu `rosie_db` de desenvolvimento** já tem as tabelas, criadas pelo Hibernate, e nenhum histórico do Flyway. No desenvolvimento (sem perfil) vale `spring.flyway.baseline-on-migrate=true` com `baseline-version=1`: na primeira subida depois da #74, o Flyway cria só a `flyway_schema_history` e marca o banco como já estando na V1 (linha `BASELINE`), **sem recriar, alterar nem apagar nada**. A partir daí as migrações novas (V2, V3...) rodam nele normalmente. Diferenças conhecidas do seu banco em relação à V1, que não atrapalham: a ordem das colunas (o `update` acrescentava no fim) e a falta dos `CHECK` de `pedido.status` e `troca.status` (as colunas viraram `VARCHAR` com `ALTER TABLE`, e o `update` não cria `CHECK` em coluna que já existe).

Se a API local não subir com `Schema-validation: missing column/table`, o seu banco está atrás da `main` (por exemplo, não rodou a API depois de alguma feature). Suba **uma vez** com o Hibernate completando o que falta e depois volte ao normal:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.jpa.hibernate.ddl-auto=update
```

Em produção, o baseline automático fica **desligado**: um banco com tabelas e sem histórico do Flyway faz a API parar com `Found non-empty schema(s) ... but no schema history table`, em vez de ser aceito sem conferência. Por isso, não restaure um dump do banco de desenvolvimento no MySQL de produção.

**Mudou uma entidade?** Crie a migração seguinte em `backend/src/main/resources/db/migration`, por exemplo `V2__pedido_cupom.sql`, com o `ALTER TABLE`/`CREATE TABLE` correspondente, e rode `./mvnw test`: a suíte sobe contra um banco vazio aplicando todas as migrações e falha se o Hibernate não bater com elas. Regras:

- Nunca edite uma migração que já foi para a `main` (o Flyway confere o checksum e a API para de subir). Corrigir = nova migração.
- Valor novo num enum gravado como `ENUM` do MySQL (`tamanho`, `role`, `motivo`, `enquadramento`) precisa de `ALTER TABLE ... MODIFY`. Nos gravados como `VARCHAR` (`pedido.status`, `forma_pagamento`, `origem_entrega`, `troca.status`, `troca.tamanho`), troque o `CHECK` (`ALTER TABLE pedido DROP CHECK pedido_chk_3, ADD CONSTRAINT pedido_chk_3 CHECK (...)`). No seu `rosie_db` antigo esse `CHECK` não existe: use `DROP CHECK` só se ele existir, ou recrie o banco local.
- O seed de categorias (#29) pode entrar como uma migração (`V2__categorias_iniciais.sql`).

## 1. Railway: banco e API

1. Assine o plano **Hobby** (o trial expirou) e crie um projeto novo, ex.: `rosie`.
2. **MySQL:** no projeto, *Create* → *Database* → *MySQL*. Deixe o nome do serviço `MySQL` (as referências abaixo usam esse nome). Não é preciso criar tabelas: a API cria na primeira subida.
3. **API:** *Create* → *GitHub Repo* → `breno-hbs/rosie-store`. Nas *Settings* do serviço:
   - **Root Directory:** `backend`
   - **Config file path:** `/backend/railway.json` (o arquivo de configuração não segue o Root Directory; precisa do caminho a partir da raiz do repositório)
   - **Branch:** `main`
   - Confira que o *Builder* ficou **Dockerfile**, o *Start command* é o do `railway.json` e o *Healthcheck path* é `/actuator/health`.
4. **Variáveis da API** (*Variables*, ver a [tabela completa](#variáveis-de-ambiente)): no mínimo as obrigatórias. Para o banco, use as referências ao serviço MySQL, que o Railway resolve sozinho:

   ```
   DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true
   DB_USERNAME=${{MySQL.MYSQLUSER}}
   DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
   ```

   O `MYSQLHOST` é o endereço da rede privada (`mysql.railway.internal`), que não sai para a internet; por isso `useSSL=false`. Não defina `PORT` nem `SPRING_PROFILES_ACTIVE`: o Railway põe a porta e o `railway.json` já sobe com `prod`.
5. **Domínio da API:** em *Settings* → *Networking*, *Custom Domain* `api.rosiestore.com.br`. O Railway mostra um `CNAME` (para `xxxx.up.railway.app`) e um `TXT` de verificação: crie os dois no Registro.br ([seção 3](#3-registrobr-dns)). Enquanto o DNS não propaga, dá para testar pelo domínio `*.up.railway.app` (*Generate Domain*).
6. Faça o deploy. No log, procure `Successfully applied 1 migration ... now at version v1`, `Admin inicial criado` e `Started BackendApplication`. O deploy só troca de versão depois que `/actuator/health` responde 200 (até 3 minutos).

Cada push na `main` que mexe em `backend/` gera um deploy novo (o `watchPatterns` do `railway.json` ignora mudanças só no front). As migrações novas rodam nessa subida.

## 2. Netlify: front

1. *Add new site* → *Import an existing project* → GitHub → `breno-hbs/rosie-store`.
2. **Base directory:** `frontend`. O resto (comando `npm run build`, pasta `dist`, Node 22, redirect da SPA e cabeçalhos) vem do `frontend/netlify.toml`; deixe os outros campos como o Netlify preencher a partir dele.
3. **Variáveis** (*Site configuration* → *Environment variables*): `VITE_API_URL=https://api.rosiestore.com.br` e `VITE_WHATSAPP_NUMERO`. Elas entram no build: mudou alguma, faça um novo deploy (*Deploys* → *Trigger deploy*).
4. **Domínio:** *Domain management* → *Add a domain* → `rosiestore.com.br` (o Netlify oferece incluir o `www`). Com o DNS no Registro.br, o Netlify mostra os registros a criar ([seção 3](#3-registrobr-dns)). Depois da verificação, ele emite o certificado https sozinho (Let's Encrypt).

O redirect `/* → /index.html (200)` faz as rotas do React (`/produtos/12`, `/minha-conta`, `/admin`) abrirem direto pelo link ou no F5; arquivos que existem no build (`/assets/...`, `/etiqueta-simulada.html`) continuam servidos antes.

## 3. Registro.br: DNS

Mantenha o DNS no Registro.br (servidores `a.dns.br`/`b.dns.br`): em *Domínios* → `rosiestore.com.br` → *DNS* → *Editar zona*, crie os registros abaixo. Use sempre os valores que cada painel mostrar; os daqui são o formato esperado.

| Para | Tipo | Nome | Valor |
|---|---|---|---|
| Site (Netlify) | `A` | `rosiestore.com.br` (raiz, `@`) | `75.2.60.5` (balanceador do Netlify; confira no painel) |
| Site (Netlify) | `CNAME` | `www` | `<seu-site>.netlify.app` |
| API (Railway) | `CNAME` | `api` | `xxxx.up.railway.app` (o que o Railway mostrar) |
| API (Railway) | `TXT` | `_railway-verify.api` | o valor de verificação do Railway |
| E-mail (Resend) | `MX` | `send` | `feedback-smtp.<região>.amazonses.com`, prioridade 10 |
| E-mail (Resend) | `TXT` | `send` | `v=spf1 include:amazonses.com ~all` (SPF) |
| E-mail (Resend) | `TXT` | `resend._domainkey` | a chave DKIM que o Resend mostrar |
| E-mail (opcional) | `TXT` | `_dmarc` | `v=DMARC1; p=none;` |

Os registros do Resend ficam no subdomínio `send` e no `resend._domainkey`: não mexem no e-mail que a Roseane já recebe (o `MX` da raiz, se houver). A propagação leva de minutos a algumas horas; o Netlify, o Railway e o Resend mostram quando verificaram.

## 4. Integrações

**Mercado Pago (conta da Roseane).** No [painel de desenvolvedor](https://www.mercadopago.com.br/developers/panel/app), na aplicação Checkout Pro:

1. *Credenciais de produção* → copie o access token para `MERCADO_PAGO_ACCESS_TOKEN`.
2. *Webhooks* → *Configurar notificações* → **modo produção**: URL `https://api.rosiestore.com.br/pagamentos/webhook`, evento **Pagamentos**. Salve e copie a *assinatura secreta* para `MERCADO_PAGO_WEBHOOK_SECRET`.
3. `ROSIE_API_URL=https://api.rosiestore.com.br` (a API manda essa URL como `notification_url` em cada preferência) e `ROSIE_LOJA_URL=https://rosiestore.com.br` (volta da cliente depois de pagar).

**Melhor Envio (conta real da Rosie).** Painel → *Integrações* → *Permissões de acesso* → gerar token com os escopos `shipping-calculate`, `cart-read`, `cart-write`, `shipping-checkout`, `shipping-generate`, `shipping-print` e `shipping-tracking`. Defina `MELHOR_ENVIO_TOKEN`, `MELHOR_ENVIO_URL=https://melhorenvio.com.br` (o padrão é a sandbox), `MELHOR_ENVIO_USER_AGENT`, `ROSIE_CEP_ORIGEM` e os `ROSIE_REMETENTE_*`. A conta precisa de **saldo**: cada "Gerar etiqueta" compra a etiqueta com ele.

**Resend.** Em *Domains*, adicione `rosiestore.com.br` e crie os registros da [seção 3](#3-registrobr-dns). Em *API Keys*, crie uma chave com *Sending access* para `RESEND_API_KEY`. O `ROSIE_EMAIL_REMETENTE` precisa ser um endereço desse domínio.

**Cloudinary.** O mesmo `CLOUDINARY_URL` de hoje (Settings → API Keys → *API environment variable*).

## 5. Primeira subida e admin inicial

1. Com `ROSIE_ADMIN_EMAIL` e `ROSIE_ADMIN_SENHA` (mínimo 8 caracteres) definidas, a primeira subida sem nenhum `ADMIN` no banco cria o admin. Procure no log `Admin inicial criado`.
2. Entre em `https://rosiestore.com.br/admin/login` com esse e-mail e senha.
3. **Apague `ROSIE_ADMIN_SENHA` e `ROSIE_ADMIN_EMAIL` do Railway** (depois do primeiro admin elas são ignoradas, e a senha não deve ficar guardada lá). Por enquanto o admin é o e-mail do Breno; a Roseane recebe o acesso depois.
4. Cadastre as categorias pelo painel (ou pela migração da #29) e os produtos.

## 6. Conferências depois do deploy

- [ ] `https://api.rosiestore.com.br/actuator/health` responde `{"status":"UP"}`; `/actuator/env` responde 401.
- [ ] `https://api.rosiestore.com.br/produtos` responde JSON.
- [ ] O site abre em `https://rosiestore.com.br` e `https://www.rosiestore.com.br`, com cadeado, e o F5 numa página interna (ex.: `/produtos/1`) não dá 404.
- [ ] O console do navegador não mostra erro de CORS (se mostrar, confira `CORS_ORIGENS`: sem barra no fim, com `https://`).
- [ ] No log da API **não** aparecem os avisos `MELHOR_ENVIO_TOKEN não definido`, `MERCADO_PAGO_ACCESS_TOKEN não definido`, `... o checkout vai responder 503`, `RESEND_API_KEY não definida` nem `Nenhum admin cadastrado`.
- [ ] Login no painel, cadastro de um produto com foto (Cloudinary).
- [ ] Cadastro de uma cliente, carrinho e cálculo de frete com um CEP real.
- [ ] Um pedido de valor baixo pago com PIX na conta real: ele vira `PAGO` sozinho (webhook chegando), e o estoque baixa. No painel do Mercado Pago, em *Webhooks*, as notificações aparecem com resposta 200.
- [ ] Gerar a etiqueta desse pedido (debita o saldo do Melhor Envio) e conferir o link e o rastreio.
- [ ] Aprovar uma troca de teste e conferir no log `E-mail enviado pelo Resend` e o e-mail na caixa de entrada (não no spam).
- [ ] A data/hora do pedido no painel bate com o horário de Brasília.
- [ ] Backup do banco configurado (#30).

## Variáveis de ambiente

Conferidas no código: todos os `${...}` de `application.properties` e `application-prod.properties` e os `import.meta.env` do front. "Obrigatória" quer dizer: **sobe** = sem ela a API não sobe no perfil `prod`; **loja** = a API sobe, mas a parte indicada responde 503 até ela ser definida; **não** = tem padrão que serve em produção.

### API (serviço no Railway)

| Variável | Para que serve | Exemplo (sem segredo) | Obrigatória |
|---|---|---|---|
| `DB_URL` | JDBC do MySQL do Railway | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true` | sobe |
| `DB_USERNAME` | Usuário do MySQL | `${{MySQL.MYSQLUSER}}` | sobe |
| `DB_PASSWORD` | Senha do MySQL | `${{MySQL.MYSQLPASSWORD}}` | sobe |
| `JWT_SECRET` | Assina os tokens do painel e da cliente (HS256), com 32+ caracteres. Gere um novo (`openssl rand -base64 48`), nunca o do desenvolvimento. Trocar derruba todas as sessões | (gerado) | sobe |
| `CORS_ORIGENS` | Origens do front liberadas no CORS, separadas por vírgula, sem barra no fim | `https://rosiestore.com.br,https://www.rosiestore.com.br` | sobe |
| `ROSIE_LOJA_URL` | Endereço da loja: volta do Mercado Pago (`/pedido/{id}/sucesso`...) e links dos e-mails (`/minha-conta/...`) | `https://rosiestore.com.br` | sobe |
| `ROSIE_ADMIN_EMAIL` | E-mail do admin criado na primeira subida sem admin. Apagar depois | `voce@exemplo.com` | só na 1ª subida |
| `ROSIE_ADMIN_SENHA` | Senha desse admin (8+ caracteres, guardada só como hash). Apagar depois | (sua) | só na 1ª subida |
| `ROSIE_ADMIN_NOME` | Nome desse admin | `Roseane` (padrão) | não |
| `CLOUDINARY_URL` | Fotos dos produtos | `cloudinary://<api_key>:<api_secret>@<cloud_name>` | loja (upload de fotos) |
| `MELHOR_ENVIO_TOKEN` | Token da conta real do Melhor Envio (escopos na [seção 4](#4-integrações)) | (do painel) | loja (frete, etiqueta, rastreio) |
| `MELHOR_ENVIO_URL` | API do Melhor Envio; o padrão é a sandbox | `https://melhorenvio.com.br` | loja (em produção, sempre definir) |
| `MELHOR_ENVIO_USER_AGENT` | Nome da aplicação e e-mail de contato técnico, exigido pelo Melhor Envio | `Rosie (contato@rosiestore.com.br)` | não (padrão `Rosie (rosiestore.com.br)`; melhor pôr um e-mail) |
| `ROSIE_CEP_ORIGEM` | CEP de onde a Rosie despacha | `01310-100` | loja (frete, etiqueta) |
| `FRETE_TIMEOUT` | Espera máxima pelo Melhor Envio no frete | `5s` (padrão) | não |
| `ROSIE_REMETENTE_NOME` | Remetente na etiqueta | `Rosie Store` | loja (etiqueta) |
| `ROSIE_REMETENTE_DOCUMENTO` | CPF (11 dígitos) ou CNPJ (14) da loja | `00000000000` | loja (etiqueta) |
| `ROSIE_REMETENTE_INSCRICAO_ESTADUAL` | Só para CNPJ com inscrição estadual | (vazio) | não |
| `ROSIE_REMETENTE_TELEFONE` | Telefone de contato | `11999999999` | loja (etiqueta) |
| `ROSIE_REMETENTE_EMAIL` | E-mail de contato na etiqueta | `contato@rosiestore.com.br` | não |
| `ROSIE_REMETENTE_LOGRADOURO` | Rua de onde a Rosie despacha (também destino da etiqueta de volta e endereço de devolução da troca) | `Rua Exemplo` | loja (etiqueta) |
| `ROSIE_REMETENTE_NUMERO` | Número | `100` | loja (etiqueta) |
| `ROSIE_REMETENTE_COMPLEMENTO` | Complemento | `Sala 2` | não |
| `ROSIE_REMETENTE_BAIRRO` | Bairro | `Centro` | loja (etiqueta) |
| `ROSIE_REMETENTE_CIDADE` | Cidade | `São Paulo` | loja (etiqueta) |
| `ROSIE_REMETENTE_UF` | Sigla do estado | `SP` | loja (etiqueta) |
| `ETIQUETA_TIMEOUT` | Espera máxima por chamada da etiqueta | `20s` (padrão) | não |
| `ROSIE_RASTREIO_ATIVO` | Rotina que marca sozinha os pedidos entregues pelo rastreio | `true` (padrão) | não |
| `ROSIE_RASTREIO_INTERVALO` | Intervalo entre as rodadas do rastreio | `6h` (padrão) | não |
| `ROSIE_RASTREIO_LOTE` | Pedidos consultados por rodada | `50` (padrão) | não |
| `ROSIE_RASTREIO_PAUSA` | Pausa entre consultas ao Melhor Envio | `1s` (padrão) | não |
| `MERCADO_PAGO_ACCESS_TOKEN` | Access token **de produção** da aplicação Checkout Pro da conta da Roseane | `APP_USR-...` | loja (checkout) |
| `MERCADO_PAGO_WEBHOOK_SECRET` | Assinatura secreta dos webhooks (modo produção) | (do painel) | loja (checkout) |
| `ROSIE_API_URL` | URL pública da API, com https; o webhook vai para `<url>/pagamentos/webhook` | `https://api.rosiestore.com.br` | loja (checkout) |
| `PAGAMENTO_TIMEOUT` | Espera máxima pelo Mercado Pago | `10s` (padrão) | não |
| `TROCA_PRAZO_DIAS` | Dias depois da entrega para pedir troca (mínimo do CDC: 7) | `7` (padrão) | não |
| `RESEND_API_KEY` | Chave do Resend (*Sending access*); sem ela os e-mails só vão para o log | `re_...` | loja (e-mails, link da troca de e-mail) |
| `ROSIE_EMAIL_REMETENTE` | Remetente dos e-mails, num domínio verificado no Resend | `Rosie <contato@rosiestore.com.br>` (padrão) | não |
| `ROSIE_EMAIL_RESPONDER_PARA` | Para onde vão as respostas da cliente | `roseane@exemplo.com` | não |
| `EMAIL_TIMEOUT` | Espera máxima pelo Resend | `10s` (padrão) | não |
| `ROSIE_WHATSAPP_NUMERO` | WhatsApp da loja nos e-mails (DDI + DDD, só dígitos; o mesmo de `VITE_WHATSAPP_NUMERO`) | `5511999999999` | não |
| `ROSIE_TROCA_EMAIL_VALIDADE` | Prazo do link de confirmação da troca de e-mail | `24h` (padrão) | não |
| `PORT` | Porta HTTP; **o Railway define sozinho** | (não definir) | — |
| `SPRING_PROFILES_ACTIVE` | Perfil do Spring; o `railway.json` e o Dockerfile já sobem com `prod`. O perfil `demo` ([`docs/demonstracao.md`](demonstracao.md)) é só para a demonstração local, e a API não sobe com `prod` e `demo` juntos | (não definir) | — |
| `FRETE_SIMULADO` | Só desenvolvimento: frete, etiqueta e rastreio de mentira | (não definir) | **nunca em produção** |
| `PAGAMENTO_SIMULADO` | Só desenvolvimento: pagamento de mentira, aprovado pela tela | (não definir) | **nunca em produção** |
| `JPA_SHOW_SQL` | Só desenvolvimento: SQL no console. Ignorada no perfil `prod` | (não definir) | — |

Também nunca defina `LOGGING_LEVEL_COM_ROSIE_BACKEND_SERVICE_EMAILSONOLOG=DEBUG` em produção: põe o texto dos e-mails, com os links de confirmação, no log.

### Front (site no Netlify)

| Variável | Para que serve | Exemplo | Obrigatória |
|---|---|---|---|
| `VITE_API_URL` | URL base da API, sem barra no fim. Sem ela o build aponta para `http://localhost:8080` | `https://api.rosiestore.com.br` | sim |
| `VITE_WHATSAPP_NUMERO` | WhatsApp da Rosie (DDI 55 + DDD + número, só dígitos) nos botões do WhatsApp do site | `5511999999999` | não (vazio = os botões do WhatsApp somem e fica o Instagram) |

As `VITE_*` são públicas: vão no JavaScript do site. Nunca ponha segredo nelas.

## Problemas comuns

| Sintoma | Causa provável |
|---|---|
| Deploy da API falha no health check | Variável obrigatória faltando (o log mostra `Could not resolve placeholder 'X'`) ou banco inacessível (confira `DB_URL` e se o MySQL está no mesmo projeto) |
| `Found non-empty schema(s) ... but no schema history table` | O banco de produção tem tabelas criadas fora do Flyway (ex.: um dump restaurado). Use um banco vazio |
| `Validate failed: Migrations have failed validation` / checksum | Uma migração já aplicada foi editada. Volte o arquivo e crie uma migração nova |
| `Schema-validation: missing column` | Entidade mudou sem migração. Crie a `V<n>__...sql` |
| Erro de CORS no navegador | `CORS_ORIGENS` sem o domínio exato do site (com `https://`, sem barra no fim, `www` incluído se usado) |
| Mercado Pago não confirma o pedido | Webhook não configurado em modo produção, `MERCADO_PAGO_WEBHOOK_SECRET` de outra aplicação (401 `ASSINATURA_INVALIDA` no log) ou `ROSIE_API_URL` sem https |
| E-mails não chegam | Domínio ainda não verificado no Resend (403 no log) ou `ROSIE_EMAIL_REMETENTE` fora do domínio |
| Página interna dá 404 no F5 | O site não está usando o `frontend/netlify.toml` (Base directory diferente de `frontend`) |
