# Contrato da API

Define os endpoints REST, formato de requisição/resposta e convenções compartilhadas entre o Catálogo e o Painel Admin, baseado no modelo de dados MySQL e nos requisitos funcionais (`docs/requisitos.md`).

Migrado do Notion em 17/10/2026. A partir daqui, este arquivo é a fonte de verdade: **se um endpoint mudar, atualize este arquivo no mesmo PR.**

## Status de implementação

| Endpoint | Status | DTO de resposta |
|---|---|---|
| `GET /produtos` | Implementado | `ProdutoListaResponseDTO` |
| `GET /produtos/destaques` | Implementado (público, só ativos com `destaque = true`) | lista de `ProdutoResumoDTO` |
| `GET /produtos/{id}` | Implementado (404 com `RECURSO_NAO_ENCONTRADO`, também para produto inativo) | `ProdutoDetalheDTO` |
| `GET /categorias` | Implementado (público, ordenado por nome) | lista de `CategoriaResumoDTO` |
| `GET /categorias/{id}/medidas` | Implementado (público; `medidas` vazia se a categoria não tem tabela; 404) | `TabelaMedidasDTO` |
| `POST /auth/registrar` | Implementado (público; 201; 400 `DADOS_INVALIDOS`; 409 com `EMAIL_JA_CADASTRADO`) | `LoginResponseDTO` |
| `POST /auth/login` | Implementado (público; só `CLIENTE`; 401 com `CREDENCIAIS_INVALIDAS`) | `LoginResponseDTO` |
| `GET /auth/me` | Implementado (exige token `CLIENTE`) | `UsuarioResumoDTO` |
| `GET /carrinho` | Implementado (exige token `CLIENTE`; marca itens que ficaram indisponíveis sem apagá-los) | `CarrinhoDTO` |
| `POST /carrinho/itens` | Implementado (exige token `CLIENTE`; 201; 400 `DADOS_INVALIDOS`; 409 com `ESTOQUE_INSUFICIENTE`/`PRODUTO_INDISPONIVEL`) | `CarrinhoDTO` |
| `PUT /carrinho/itens/{itemId}` | Implementado (exige token `CLIENTE`; 400; 404; 409 com `ESTOQUE_INSUFICIENTE`/`PRODUTO_INDISPONIVEL`) | `CarrinhoDTO` |
| `DELETE /carrinho/itens/{itemId}` | Implementado (exige token `CLIENTE`; 200 com o carrinho; 404) | `CarrinhoDTO` |
| `POST /frete/calcular` | Implementado (exige token `CLIENTE`; cota o carrinho no Melhor Envio; 400 `DADOS_INVALIDOS`; 409 `CARRINHO_VAZIO`; 502 `FRETE_FALHOU`; 503 `FRETE_INDISPONIVEL`) | `FreteResponseDTO` |
| `GET /enderecos` | Implementado (exige token `CLIENTE`; sem os arquivados) | lista de `EnderecoDTO` |
| `POST /enderecos` | Implementado (exige token `CLIENTE`; 201; 400 `DADOS_INVALIDOS`) | `EnderecoDTO` |
| `PUT /enderecos/{id}` | Implementado (exige token `CLIENTE`; 400; 404; endereço já usado em pedido volta com id novo) | `EnderecoDTO` |
| `DELETE /enderecos/{id}` | Implementado (exige token `CLIENTE`; 204; 404; endereço já usado em pedido só é arquivado) | — |
| `POST /pedidos` | Implementado (exige token `CLIENTE`; 201; 400 `DADOS_INVALIDOS`; 409 `CARRINHO_VAZIO`/`ESTOQUE_INSUFICIENTE`/`PRODUTO_INDISPONIVEL`/`FRETE_ALTERADO`; 502 `FRETE_FALHOU`/`PAGAMENTO_FALHOU`; 503 `FRETE_INDISPONIVEL`/`PAGAMENTO_INDISPONIVEL`) | `PedidoCriadoDTO` |
| `GET /pedidos` | Implementado (exige token `CLIENTE`; só os pedidos da cliente do token, do mais recente ao mais antigo) | lista de `PedidoResumoDTO` |
| `GET /pedidos/{id}` | Implementado (exige token `CLIENTE`; 404 também para pedido de outra cliente) | `PedidoDTO` |
| `GET /conta` | Implementado (exige token `CLIENTE`) | `ContaDTO` |
| `PUT /conta` | Implementado (exige token `CLIENTE`; nome e telefone; 400 `DADOS_INVALIDOS`) | `ContaDTO` |
| `PUT /conta/senha` | Implementado (exige token `CLIENTE`; 204; 400 `DADOS_INVALIDOS`, também para senha atual errada) | — |
| `POST /pagamentos/webhook` | Implementado (público, assinado pelo Mercado Pago; 200; 401 `ASSINATURA_INVALIDA`; 502/503 para o Mercado Pago tentar de novo) | — |
| `POST /pedidos/{id}/pagamento-simulado` | Só desenvolvimento, com `PAGAMENTO_SIMULADO=true` (404 fora dele) | `PedidoDTO` |
| `POST /admin/auth/login` | Implementado (público; 401 com `CREDENCIAIS_INVALIDAS`) | `LoginResponseDTO` |
| `GET /admin/auth/me` | Implementado (exige token `ADMIN`) | `UsuarioResumoDTO` |
| `GET /admin/produtos` | Implementado (exige token `ADMIN`; inclui inativos) | lista de `ProdutoAdminResumoDTO` |
| `GET /admin/produtos/{id}` | Implementado (exige token `ADMIN`; inclui inativos) | `ProdutoAdminDTO` |
| `POST /admin/produtos` | Implementado (exige token `ADMIN`; 201) | `ProdutoAdminDTO` |
| `PUT /admin/produtos/{id}` | Implementado (exige token `ADMIN`; `ativo: true` reativa; não altera o estoque) | `ProdutoAdminDTO` |
| `PUT /admin/produtos/{id}/estoque` | Implementado (exige token `ADMIN`; só os tamanhos enviados) | `EstoqueProdutoDTO` |
| `DELETE /admin/produtos/{id}` | Implementado (exige token `ADMIN`; 204, só desativa) | — |
| `POST /admin/produtos/{id}/imagens` | Implementado (exige token `ADMIN`; multipart com `enquadramento` opcional; 201; 400 com `FORMATO_NAO_SUPORTADO`/`ARQUIVO_MUITO_GRANDE`/`DADOS_INVALIDOS`; 503 com `ARMAZENAMENTO_INDISPONIVEL`) | `ProdutoImagemDTO` |
| `PUT /admin/produtos/{id}/imagens/ordem` | Implementado (exige token `ADMIN`; 400 se faltar ou repetir foto) | lista de `ProdutoImagemDTO` |
| `DELETE /admin/produtos/{id}/imagens/{imagemId}` | Implementado (exige token `ADMIN`; 204; 404) | — |
| `GET /admin/categorias` | Implementado (exige token `ADMIN`) | lista de `CategoriaAdminDTO` |
| `POST /admin/categorias` | Implementado (exige token `ADMIN`; 201; 409 com `CATEGORIA_DUPLICADA`) | `CategoriaAdminDTO` |
| `PUT /admin/categorias/{id}` | Implementado (exige token `ADMIN`; 404; 409 com `CATEGORIA_DUPLICADA`) | `CategoriaAdminDTO` |
| `DELETE /admin/categorias/{id}` | Implementado (exige token `ADMIN`; 204; 404; 409 com `CATEGORIA_EM_USO`; a tabela de medidas sai junto) | — |
| `GET /admin/categorias/{id}/medidas` | Implementado (exige token `ADMIN`; 404) | `TabelaMedidasDTO` |
| `PUT /admin/categorias/{id}/medidas` | Implementado (exige token `ADMIN`; troca a tabela inteira; 400 com `DADOS_INVALIDOS`; 404) | `TabelaMedidasDTO` |
| `DELETE /admin/categorias/{id}/medidas` | Implementado (exige token `ADMIN`; 204; 404) | — |
| Demais endpoints | Não implementados | — |

---

## Convenções gerais

- **Base URL (dev):** `http://localhost:8080`
- **Formato:** JSON em todas as requisições e respostas (exceção: o upload de foto do produto é `multipart/form-data`)
- **Autenticação:** JWT via header `Authorization: Bearer {token}` — obtido no login
- **Tamanhos:** sempre um dos valores `P`, `M`, `G`, `GG`
- **Datas:** formato ISO 8601 (`2026-08-25T14:30:00`)
- **Erros:** sempre nesse formato (`ErroResponseDTO`), com o HTTP status code correspondente (400, 401, 403, 404, 409, 500, 502, 503)

```json
{
  "erro": "MENSAGEM_LEGIVEL",
  "codigo": "CODIGO_INTERNO"
}
```

### Códigos de erro em uso

| Código | HTTP | Quando |
|---|---|---|
| `RECURSO_NAO_ENCONTRADO` | 404 | Recurso buscado por id não existe (ex.: `GET /produtos/{id}`) |
| `DADOS_INVALIDOS` | 400 | Corpo ausente, JSON malformado ou campo inválido (ex.: e-mail vazio no login, preço zero, categoria inexistente ou tamanho repetido no cadastro de produto); `erro` traz a mensagem do primeiro campo |
| `CREDENCIAIS_INVALIDAS` | 401 | Login com e-mail inexistente, senha errada ou usuário sem permissão (mesma resposta nos três casos) |
| `NAO_AUTENTICADO` | 401 | Rota protegida sem token, ou com token inválido, adulterado ou expirado |
| `ACESSO_NEGADO` | 403 | Token válido, mas sem a role exigida (ex.: token de cliente em `/admin/**`) |
| `FORMATO_NAO_SUPORTADO` | 400 | Upload de foto que não é JPG, PNG nem WEBP (conferido pelo conteúdo do arquivo, não pela extensão) |
| `ARQUIVO_MUITO_GRANDE` | 400 | Upload de foto acima de 5 MB |
| `ARMAZENAMENTO_INDISPONIVEL` | 503 | Upload de foto sem `CLOUDINARY_URL` configurada no servidor, ou com o Cloudinary fora do ar |
| `CATEGORIA_DUPLICADA` | 409 | Criar ou renomear categoria com um nome que já existe (sem diferenciar maiúsculas) |
| `CATEGORIA_EM_USO` | 409 | Remover categoria que ainda tem produtos (ativos ou inativos); `erro` diz quantos |
| `EMAIL_JA_CADASTRADO` | 409 | Cadastro de cliente com um e-mail que já tem conta (sem diferenciar maiúsculas), de cliente ou de admin |
| `ESTOQUE_INSUFICIENTE` | 409 | Carrinho: a quantidade pedida (somada à que já está na sacola, no POST) passa do estoque do tamanho, ou o tamanho está esgotado; `erro` traz a mensagem pronta ("Só temos 3 no tamanho P") |
| `PRODUTO_INDISPONIVEL` | 409 | Carrinho: adicionar ou mudar a quantidade de um produto desativado pela Roseane |
| `CARRINHO_VAZIO` | 409 | Frete: o carrinho não tem nenhuma peça disponível para cotar. Checkout: a sacola está vazia |
| `FRETE_ALTERADO` | 409 | Checkout: a forma de entrega escolhida (`servicoFreteId`) não voltou na cotação refeita no servidor para o CEP do endereço |
| `PAGAMENTO_FALHOU` | 502 | Checkout: o Mercado Pago não respondeu a tempo, caiu ou devolveu algo inesperado. A cliente pode tentar de novo (nenhum pedido fica gravado) |
| `PAGAMENTO_INDISPONIVEL` | 503 | Checkout: servidor sem `MERCADO_PAGO_ACCESS_TOKEN`, `MERCADO_PAGO_WEBHOOK_SECRET` ou `ROSIE_API_URL` (https), ou access token recusado pelo Mercado Pago |
| `ASSINATURA_INVALIDA` | 401 | Webhook do Mercado Pago sem `x-signature`, com assinatura malformada ou que não confere com o segredo |
| `FRETE_FALHOU` | 502 | Frete: o Melhor Envio não respondeu a tempo, caiu ou devolveu algo inesperado. A cliente pode tentar de novo |
| `FRETE_INDISPONIVEL` | 503 | Frete: servidor sem `MELHOR_ENVIO_TOKEN` ou `ROSIE_CEP_ORIGEM`, ou token recusado pelo Melhor Envio |

---

## Autenticação (RF08, RF13)

Cliente e admin são o mesmo `usuario`, com `role` `CLIENTE` ou `ADMIN`, e o mesmo token (ver "Login admin"). **Cada um entra pelo seu login:** `POST /auth/login` só aceita `CLIENTE` e `POST /admin/auth/login` só aceita `ADMIN`; a conta da outra role recebe o mesmo 401 de senha errada. Assim o token de cada área sai do formulário daquela área, e a sessão da loja e a do painel ficam separadas no navegador.

| Rotas | Quem acessa |
|---|---|
| `POST /auth/registrar`, `POST /auth/login`, `POST /admin/auth/login`, `GET /produtos/**`, `GET /categorias` | Público |
| `POST /pagamentos/webhook` | Público, mas só processa aviso com a assinatura `x-signature` do Mercado Pago |
| `/auth/**` (ex.: `GET /auth/me`), `/conta/**`, `/carrinho/**`, `/frete/**`, `/enderecos/**`, `/pedidos/**`, `/trocas/**` | Token `CLIENTE` (401 `NAO_AUTENTICADO` sem token; 403 `ACESSO_NEGADO` com token de admin) |
| `/admin/**` | Token `ADMIN` (403 `ACESSO_NEGADO` com token de cliente) |
| Qualquer outra | Qualquer token válido |

As rotas da compra (#22, #23, #24, #27) já estão no `SecurityConfig` exigindo `CLIENTE`: os controllers novos não precisam mexer nele. Quem criar uma rota de cliente fora desses prefixos acrescenta o prefixo lá (como `/frete/**` na #23, `/enderecos/**` na #24 e `/conta/**` na #26).

### Cadastro de cliente
`POST /auth/registrar`

Público. Cria a conta com role `CLIENTE` e já devolve o token, no mesmo formato do login: quem se cadastra sai logado. O e-mail é guardado sem espaços nas pontas e em minúsculas.

```json
// Request — RegistroRequestDTO
{
  "nome": "Roseane Silva",
  "email": "roseane@email.com",
  "senha": "senha123",
  "telefone": "11987654321",
  "aceitePrivacidade": true
}
// Response 201 — LoginResponseDTO
{ "token": "eyJhbGc...", "usuario": { "id": 7, "nome": "Roseane Silva", "role": "CLIENTE" } }
// Response 400
{ "erro": "Aceite a política de privacidade para criar a conta", "codigo": "DADOS_INVALIDOS" }
// Response 409
{ "erro": "Este e-mail já está cadastrado. Entre com ele ou use outro.", "codigo": "EMAIL_JA_CADASTRADO" }
```

| Campo | Regra |
|---|---|
| `nome` | Obrigatório, até 150 caracteres (espaços nas pontas são removidos). A Nav mostra o primeiro nome |
| `email` | Obrigatório, até 150 caracteres, com domínio que tenha ponto (`maria@email` é recusado). Comparado sem diferenciar maiúsculas: `Maria@Email.com` e `maria@email.com` são o mesmo e-mail (409) |
| `senha` | Obrigatória, de 8 a 72 caracteres (o BCrypt ignora o que passa de 72 bytes; com acentos, o limite em bytes vale primeiro). Guardada só como hash BCrypt (RNF07) |
| `telefone` | Obrigatório, só dígitos, com DDD: 10 ou 11 dígitos (`11987654321`). O front tira a máscara antes de enviar |
| `aceitePrivacidade` | Obrigatório e `true` (LGPD, RNF06). Ausente ou `false` responde 400 |

Mensagens de 400 (`DADOS_INVALIDOS`): "Informe o nome", "Informe o e-mail", "E-mail inválido", "Informe a senha", "A senha precisa ter de 8 a 72 caracteres", "Informe o telefone", "Telefone inválido: informe DDD e número, só com dígitos", "Aceite a política de privacidade para criar a conta".

**Decisão (#21): o cadastro devolve `LoginResponseDTO`.** O rascunho deste contrato tinha `{ id, nome, email, token }`; trocamos pelo formato do login para o front guardar a sessão do mesmo jeito nos dois casos. O e-mail não volta na resposta (quem cadastrou já sabe qual é).

**Consentimento (LGPD):** o cadastro grava em `usuario` a data e hora do aceite (`privacidade_aceita_em`) e a versão da política aceita (`privacidade_versao`, hoje `2026-10`, constante `PoliticaPrivacidade.VERSAO` no back-end). Quando o texto da política mudar (página da #31), suba a versão: os cadastros seguintes gravam a nova, e os antigos continuam mostrando qual versão aceitaram. Admin não tem aceite (nulo).

O 409 diz que o e-mail já tem conta, o que revela que ele está cadastrado. É aceito de propósito: o cadastro precisa avisar a pessoa para entrar em vez de criar outra conta. O login, ao contrário, não diz se o e-mail existe.

### Login de cliente
`POST /auth/login`

Público. Mesmas regras do login admin (e-mail sem diferenciar maiúsculas, mesmo 401 para e-mail inexistente e senha errada, token de 2 horas), mas só entra `CLIENTE`: o e-mail da Roseane recebe 401 aqui.

```json
// Request
{ "email": "roseane@email.com", "senha": "senha123" }
// Response 200 — LoginResponseDTO
{ "token": "eyJhbGc...", "usuario": { "id": 7, "nome": "Roseane Silva", "role": "CLIENTE" } }
// Response 401
{ "erro": "E-mail ou senha incorretos", "codigo": "CREDENCIAIS_INVALIDAS" }
// Response 400 (e-mail ou senha vazios, e-mail mal formado)
{ "erro": "Informe o e-mail", "codigo": "DADOS_INVALIDOS" }
```

### Cliente do token
`GET /auth/me`

Exige token `CLIENTE`. Devolve o dono do token; a loja usa para conferir se a sessão ainda vale.

```json
// Response 200 — UsuarioResumoDTO
{ "id": 7, "nome": "Roseane Silva", "role": "CLIENTE" }
// Response 401 (sem token, token inválido ou expirado) — NAO_AUTENTICADO
// Response 403 (token de admin) — ACESSO_NEGADO
```

### Dados pessoais (Minha Conta, #26)
`GET /conta`, `PUT /conta`, `PUT /conta/senha`

Exigem token `CLIENTE` (401 sem token, 403 com token de admin). Sempre da cliente do token: não há id na URL.

```json
// GET /conta → 200 — ContaDTO
{ "id": 7, "nome": "Maria Silva", "email": "maria@email.com", "telefone": "11987654321" }
// PUT /conta — ContaRequestDTO → 200 com o ContaDTO atualizado
{ "nome": "Maria Souza", "telefone": "1134567890" }
// PUT /conta/senha — SenhaRequestDTO → 204, sem corpo
{ "senhaAtual": "senha123", "novaSenha": "nova-senha-456" }
// Response 400
{ "erro": "Senha atual incorreta", "codigo": "DADOS_INVALIDOS" }
```

| Campo | Regra |
|---|---|
| `nome` | Obrigatório, até 150 caracteres (espaços nas pontas são removidos), como no cadastro |
| `telefone` | Obrigatório, só dígitos com DDD: 10 ou 11 dígitos, como no cadastro. O front tira a máscara |
| `email` | Só leitura: é o login e não muda por aqui (trocar exigiria confirmar o endereço novo) |
| `senhaAtual` | Obrigatória e precisa conferir com a senha da conta |
| `novaSenha` | Obrigatória, de 8 a 72 caracteres (e até 72 bytes, como no cadastro). Guardada só como hash BCrypt |

Mensagens de 400 (`DADOS_INVALIDOS`): "Informe o nome", "O nome pode ter no máximo 150 caracteres", "Informe o telefone", "Telefone inválido: informe DDD e número, só com dígitos", "Informe a senha atual", "Senha atual incorreta", "Informe a nova senha", "A senha precisa ter de 8 a 72 caracteres".

**Senha atual errada é 400, não 401:** a sessão continua valendo, só o formulário está errado (um 401 faria a loja tirar a cliente da conta). Os tokens já emitidos continuam valendo até vencer (JWT sem estado, 2 horas).

**O nome no token não muda:** o `nome` do JWT (e do `GET /auth/me`) é o do momento do login. A loja atualiza o nome da sessão guardada no navegador com a resposta do `PUT /conta`, e o próximo login já sai com o nome novo.

### Login admin
`POST /admin/auth/login`

Público. Só entra usuário com role `ADMIN` (RF13, RNF02). O e-mail é comparado sem diferenciar maiúsculas. E-mail inexistente, senha errada e conta de cliente respondem o mesmo 401, sem dizer qual dos dois errou.

O token é um JWT HS256 com validade de **2 horas**: `sub` = id do usuário, `role`, `nome`, `iss` = `rosie`. Mande-o em `Authorization: Bearer {token}` nas rotas `/admin/**`. Sem logout no servidor: o painel só esquece o token.

```json
// Request
{ "email": "admin@rosie.com", "senha": "senha123" }
// Response 200 — LoginResponseDTO (mesmo formato do login de cliente, com role: "ADMIN")
{ "token": "eyJhbGc...", "usuario": { "id": 1, "nome": "Roseane", "role": "ADMIN" } }
// Response 401
{ "erro": "E-mail ou senha incorretos", "codigo": "CREDENCIAIS_INVALIDAS" }
// Response 400 (e-mail ou senha vazios, e-mail mal formado)
{ "erro": "Informe o e-mail", "codigo": "DADOS_INVALIDOS" }
```

### Usuário do token admin
`GET /admin/auth/me`

Exige token `ADMIN`. Devolve o dono do token; o painel usa ao abrir para conferir se a sessão ainda vale.

```json
// Response 200 — UsuarioResumoDTO
{ "id": 1, "nome": "Roseane", "role": "ADMIN" }
// Response 401 (sem token, token inválido ou expirado)
{ "erro": "Autenticação necessária", "codigo": "NAO_AUTENTICADO" }
// Response 403 (token de cliente)
{ "erro": "Acesso negado", "codigo": "ACESSO_NEGADO" }
```

### Configuração (variáveis de ambiente do back-end)

| Variável | Obrigatória | Uso |
|---|---|---|
| `JWT_SECRET` | Sim | Segredo HS256 dos tokens, pelo menos 32 caracteres. Sem ele a API não sobe. Trocar o segredo invalida todos os tokens |
| `ROSIE_ADMIN_EMAIL` | Na primeira subida | E-mail do admin inicial |
| `ROSIE_ADMIN_SENHA` | Na primeira subida | Senha do admin inicial (mínimo 8 caracteres), guardada só como hash BCrypt |
| `ROSIE_ADMIN_NOME` | Não (padrão `Roseane`) | Nome do admin inicial |
| `CLOUDINARY_URL` | Não (sem ela, o upload de fotos responde 503) | Credencial do Cloudinary, no formato `cloudinary://<api_key>:<api_secret>@<cloud_name>`; ver "Upload de imagem" |
| `MERCADO_PAGO_ACCESS_TOKEN`, `MERCADO_PAGO_WEBHOOK_SECRET`, `ROSIE_API_URL` | Não (faltando qualquer uma, o checkout responde 503) | Pagamento pelo Mercado Pago; ver "Finalizar pedido" |
| `ROSIE_LOJA_URL` | Não (padrão `http://localhost:5173`) | Endereço da loja, para as URLs de retorno do Mercado Pago |

O admin inicial é criado na subida da API só enquanto não existir nenhum usuário `ADMIN`; depois as três variáveis `ROSIE_ADMIN_*` são ignoradas.

---

## Catálogo (RF01–RF06)

### Listar categorias
`GET /categorias`

Pública (sem token). Retorna todas as categorias ordenadas por nome.

```json
// Response 200
[
  { "id": 1, "nome": "Vestido" },
  { "id": 2, "nome": "Calça" }
]
```

### Tabela de medidas da categoria
`GET /categorias/{id}/medidas`

Pública (sem token). Usada pela Página de Produto, com o `categoria.id` do produto: se `medidas` vier vazia, o link "Ver tabela de medidas" não aparece. As linhas vêm na ordem que a Roseane montou no painel; `valores` sempre traz `P`, `M`, `G` e `GG`, nessa ordem, com `""` para tamanho sem medida. Os valores são texto (aceitam faixas como `84–88`) na unidade de `unidade`, por enquanto sempre `cm`.

```json
// Response 200 — TabelaMedidasDTO
{
  "categoriaId": 1,
  "categoria": "Vestido",
  "unidade": "cm",
  "medidas": [
    { "nome": "Busto", "valores": { "P": "84–88", "M": "89–93", "G": "94–98", "GG": "99–104" } },
    { "nome": "Comprimento", "valores": { "P": "", "M": "92", "G": "", "GG": "" } }
  ]
}
// Response 200 — categoria sem tabela
{ "categoriaId": 2, "categoria": "Calça", "unidade": "cm", "medidas": [] }
// Response 404
{ "erro": "Categoria não encontrada", "codigo": "RECURSO_NAO_ENCONTRADO" }
```

### Listar produtos (com filtro opcional por categoria)
`GET /produtos?categoriaId=1&pagina=0&tamanho=12`

Parâmetros (todos opcionais): `categoriaId`, `pagina` (padrão `0`), `tamanho` (padrão `12`).

Lista só produtos ativos (`ativo = true`), com ou sem `categoriaId`. Produto inativo não aparece na listagem.

`disponivel` é `true` quando algum tamanho tem `quantidade > 0` no estoque; produto sem estoque cadastrado ou com todos os tamanhos zerados vem com `false` (continua listado, como esgotado).

```json
// Response 200 — ProdutoListaResponseDTO
{
  "conteudo": [
    {
      "id": 10,
      "nome": "Conjunto Serpente",
      "preco": 189.00,
      "categoria": { "id": 1, "nome": "Vestido" },
      "imagemPrincipal": "https://.../conjunto-serpente-1.jpg",
      "disponivel": true
    }
  ],
  "totalPaginas": 3,
  "totalItens": 34
}
```

### Listar destaques da Home
`GET /produtos/destaques?limite=12`

Pública (sem token). Lista os produtos ativos marcados com `destaque = true`, do mais recente para o mais antigo (RF01). Alimenta o carrossel e o "Best Sellers" da Home.

Parâmetro opcional: `limite` (padrão `12`, máximo `50`). Valores fora de `1..50` são ajustados para o mais próximo, sem erro. Não é paginado: a Roseane marca poucos produtos.

A marcação é manual: o campo `destaque` do produto (padrão `false`) é o checkbox "Destaque" do cadastro no painel admin (`POST`/`PUT /admin/produtos`). Sem nenhum destaque marcado, a resposta é `[]` e a Home cai na listagem comum de `GET /produtos`.

Cada item tem o mesmo formato de um item de `conteudo` em `GET /produtos` (`ProdutoResumoDTO`).

```json
// Response 200 — lista de ProdutoResumoDTO
[
  {
    "id": 10,
    "nome": "Conjunto Serpente",
    "preco": 189.00,
    "categoria": { "id": 1, "nome": "Vestido" },
    "imagemPrincipal": "https://.../conjunto-serpente-1.jpg",
    "disponivel": true
  }
]
```

### Detalhe de um produto
`GET /produtos/{id}`

Pública (sem token). Só produto ativo: inativo responde 404, igual a um id que não existe. `imagens` vem na ordem de exibição (a primeira é a principal), já em 4:5 (ver "Corte 4:5 na entrega"), `composicao` ("Composição e cuidados", com quebras de linha `\n`) vem `null` quando não foi preenchida e `estoque` vem ordenado por tamanho (`P`, `M`, `G`, `GG`), só com os tamanhos cadastrados; `quantidade: 0` significa esgotado naquele tamanho.

```json
// Response 200 — ProdutoDetalheDTO
{
  "id": 10,
  "nome": "Conjunto Serpente",
  "descricao": "Tecido leve... Trocas aceitas em caso de defeito ou produto que não serviu.",
  "composicao": "100% linho\nLavar à mão em água fria\nNão usar secadora",
  "preco": 189.00,
  "categoria": { "id": 1, "nome": "Vestido" },
  "imagens": ["https://.../1.jpg", "https://.../2.jpg"],
  "estoque": [
    { "tamanho": "P", "quantidade": 3 },
    { "tamanho": "M", "quantidade": 5 },
    { "tamanho": "G", "quantidade": 0 }
  ]
}
// Response 404
{ "erro": "...", "codigo": "RECURSO_NAO_ENCONTRADO" }
```

---

## Carrinho (RF09) — requer autenticação CLIENTE

Sempre o carrinho da cliente do token (o id vem do `sub` do JWT; nada de id de usuário na URL ou no corpo). Token de admin responde 403 `ACESSO_NEGADO`. **Todas as rotas respondem com o carrinho atualizado** (`CarrinhoDTO`, o mesmo do GET), para a tela e o número da "Sacola" na Nav se atualizarem com uma chamada só.

**Decisão (#22): o carrinho só confere o estoque, não baixa.** Quem baixa é o checkout (#24), na hora do pedido. Por isso duas clientes podem ter a última peça na sacola ao mesmo tempo; o checkout confere de novo.

### Ver carrinho
`GET /carrinho`

Itens na ordem em que entraram. `preco` e `subtotal` usam o **preço atual** do produto (não o do momento em que o item entrou). `imagemPrincipal` já vem em 4:5 (ver "Corte 4:5 na entrega"), `null` sem foto.

```json
// Response 200 — CarrinhoDTO
{
  "itens": [
    {
      "id": 1,
      "produto": { "id": 10, "nome": "Conjunto Serpente", "preco": 189.00, "imagemPrincipal": "https://.../1.jpg" },
      "tamanho": "M",
      "quantidade": 2,
      "subtotal": 378.00,
      "estoqueDisponivel": 5,
      "disponivel": true,
      "motivoIndisponivel": null
    },
    {
      "id": 2,
      "produto": { "id": 10, "nome": "Conjunto Serpente", "preco": 189.00, "imagemPrincipal": "https://.../1.jpg" },
      "tamanho": "P",
      "quantidade": 4,
      "subtotal": 756.00,
      "estoqueDisponivel": 3,
      "disponivel": false,
      "motivoIndisponivel": "ESTOQUE_INSUFICIENTE"
    }
  ],
  "quantidadeItens": 6,
  "subtotal": 378.00,
  "temIndisponiveis": true
}
```

| Campo | Significado |
|---|---|
| `itens[].subtotal` | `preco × quantidade` do item, mesmo se ele estiver indisponível |
| `itens[].estoqueDisponivel` | Estoque atual do tamanho (tamanho sem estoque cadastrado = 0). O front usa para travar o "+" |
| `itens[].disponivel` / `motivoIndisponivel` | Item que ficou indisponível **depois** de adicionado continua no carrinho (a API não apaga sozinha), com `disponivel: false` e o motivo: `PRODUTO_INATIVO` (a Roseane desativou o produto), `TAMANHO_ESGOTADO` (estoque do tamanho zerou) ou `ESTOQUE_INSUFICIENTE` (estoque menor que a quantidade). A cliente remove ou diminui a quantidade |
| `quantidadeItens` | Soma das quantidades de todos os itens, inclusive indisponíveis: é o número da "Sacola" na Nav |
| `subtotal` | Soma dos `subtotal` **só dos itens disponíveis** (o que dá para comprar agora). Sem frete: ele é calculado no checkout |
| `temIndisponiveis` | `true` se algum item está com `disponivel: false` |

Carrinho vazio: `{ "itens": [], "quantidadeItens": 0, "subtotal": 0, "temIndisponiveis": false }`.

### Adicionar item
`POST /carrinho/itens`

Mesmo produto e tamanho **somam no item que já existe** (uma linha por produto + tamanho). A soma também respeita o estoque.

```json
// Request
{ "produtoId": 10, "tamanho": "M", "quantidade": 1 }
// Response 201 — CarrinhoDTO (o carrinho inteiro, igual ao GET)
// Response 409 — passou do estoque do tamanho
{ "erro": "Só temos 3 no tamanho P, e você já tem 2 na sacola", "codigo": "ESTOQUE_INSUFICIENTE" }
// Response 409 — tamanho esgotado ou sem estoque cadastrado
{ "erro": "O tamanho GG está esgotado", "codigo": "ESTOQUE_INSUFICIENTE" }
// Response 409 — produto desativado
{ "erro": "Este produto não está mais à venda", "codigo": "PRODUTO_INDISPONIVEL" }
// Response 400
{ "erro": "Escolha um tamanho", "codigo": "DADOS_INVALIDOS" }
```

| Campo | Regra |
|---|---|
| `produtoId` | Obrigatório. Produto que não existe responde **400** `DADOS_INVALIDOS` ("Produto não encontrado"), como `categoriaId` no cadastro de produto: o erro está no corpo, não no recurso da URL. Produto inativo responde **409** `PRODUTO_INDISPONIVEL` |
| `tamanho` | Obrigatório, `P`, `M`, `G` ou `GG` (400 se outro). Tamanho sem linha de estoque cadastrada conta como esgotado: **409** `ESTOQUE_INSUFICIENTE` |
| `quantidade` | Obrigatório, de 1 a 99 (400 fora disso). Quantidade + o que já está na sacola acima do estoque do tamanho: **409** `ESTOQUE_INSUFICIENTE`, e nada muda |

### Atualizar quantidade
`PUT /carrinho/itens/{itemId}` — body: `{ "quantidade": 2 }` → **200** com o `CarrinhoDTO`

`quantidade` é a nova quantidade do item (não um acréscimo), de 1 a 99; para tirar o item, use o DELETE. Acima do estoque do tamanho: **409** `ESTOQUE_INSUFICIENTE`. Diminuir até o estoque deixa um item `ESTOQUE_INSUFICIENTE` disponível de novo. Item de produto desativado: **409** `PRODUTO_INDISPONIVEL` (só dá para remover).

### Remover item
`DELETE /carrinho/itens/{itemId}` → **200** com o `CarrinhoDTO` (não 204: a resposta é o carrinho atualizado, como nas outras rotas)

Nos dois, `itemId` que não existe **ou é de outra cliente** responde **404** `RECURSO_NAO_ENCONTRADO` (a mesma resposta, para não revelar itens alheios).

---

## Endereços de entrega (#24) — requer autenticação CLIENTE

Endereços salvos da cliente do token, usados no checkout (`enderecoId` do `POST /pedidos`) e na Minha Conta (#26). Endereço de outra cliente responde **404** `RECURSO_NAO_ENCONTRADO`, como um que não existe.

```json
// GET /enderecos → 200, lista de EnderecoDTO (na ordem em que foram cadastrados)
[
  { "id": 5, "cep": "03925050", "logradouro": "Rua das Flores", "numero": "120", "complemento": "Apto 12",
    "bairro": "Jardim Iva", "cidade": "São Paulo", "estado": "SP" }
]
// POST /enderecos — EnderecoRequestDTO → 201 com o EnderecoDTO
{ "cep": "03925-050", "logradouro": "Rua das Flores", "numero": "120", "complemento": "Apto 12",
  "bairro": "Jardim Iva", "cidade": "São Paulo", "estado": "SP" }
// PUT /enderecos/{id} — mesmo corpo → 200 com o EnderecoDTO
// DELETE /enderecos/{id} → 204
// Response 400
{ "erro": "UF inválida: use a sigla do estado, ex.: SP", "codigo": "DADOS_INVALIDOS" }
```

| Campo | Regra |
|---|---|
| `cep` | Obrigatório, 8 dígitos com ou sem hífen; guardado e devolvido só com os dígitos |
| `logradouro` | Obrigatório (a "Rua" da tela), até 200 caracteres |
| `numero` | Obrigatório, até 20 caracteres (texto: aceita "s/n", "120A") |
| `complemento` | Opcional, até 100 caracteres; em branco vira `null` |
| `bairro` / `cidade` | Obrigatórios, até 100 caracteres |
| `estado` | Obrigatório, sigla de uma das 27 UFs; minúsculas são aceitas e guardadas em maiúsculas |

Espaços nas pontas de todos os campos são removidos. A tela preenche rua, bairro, cidade e UF pelo ViaCEP a partir do CEP, mas a cliente pode corrigir e a API confere tudo.

**Endereço já usado num pedido não muda:** o pedido aponta para o endereço para onde foi entregue (e a etiqueta da #25 sai dele). Então, se ele já tem pedido, o `PUT` arquiva o antigo e cria outro com os dados novos (a resposta vem com **id novo**), e o `DELETE` só arquiva. Arquivado não aparece no `GET`, responde 404 no `PUT`/`DELETE` e não pode ser usado em pedido novo. Sem pedido, `PUT` altera no lugar e `DELETE` apaga.

---

## Pedidos e frete (RF10–RF12) — requer autenticação

### Calcular frete
`POST /frete/calcular`

Exige token `CLIENTE` (401 sem token, 403 com token de admin). Cota **as peças disponíveis do carrinho da cliente do token** (as mesmas que entram no `subtotal` do `CarrinhoDTO`) na API do Melhor Envio (`POST /api/v2/me/shipment/calculate`), do CEP da loja (`ROSIE_CEP_ORIGEM`) até o CEP informado. Não muda nada no carrinho: com o frete fora do ar, a sacola continua funcionando.

```json
// Request — FreteRequestDTO
{ "cep": "03925-050" }
// Response 200 — FreteResponseDTO
{
  "cep": "03925050",
  "opcoes": [
    { "id": 1, "transportadora": "Correios", "servico": "PAC", "preco": 22.90, "prazoDias": 7 },
    { "id": 2, "transportadora": "Correios", "servico": "SEDEX", "preco": 38.50, "prazoDias": 3 }
  ]
}
// Response 200 — nenhuma transportadora atende o CEP
{ "cep": "69980000", "opcoes": [] }
// Response 400 — formato
{ "erro": "CEP inválido: use os 8 números, ex.: 03925-050", "codigo": "DADOS_INVALIDOS" }
// Response 400 — 8 dígitos, mas o Melhor Envio não reconhece o CEP
{ "erro": "CEP não encontrado. Confira os números.", "codigo": "DADOS_INVALIDOS" }
// Response 409
{ "erro": "Sua sacola não tem peças disponíveis para calcular o frete", "codigo": "CARRINHO_VAZIO" }
// Response 502
{ "erro": "Não foi possível calcular o frete agora. Tente de novo em instantes.", "codigo": "FRETE_FALHOU" }
// Response 503
{ "erro": "O cálculo de frete não está configurado no servidor", "codigo": "FRETE_INDISPONIVEL" }
```

| Campo | Regra |
|---|---|
| `cep` (request) | Obrigatório, 8 dígitos com ou sem hífen (`03925-050` ou `03925050`). Outro formato responde 400 sem chamar o Melhor Envio |
| `cep` (response) | O CEP de destino só com os dígitos |
| `opcoes` | Do mais barato para o mais caro (empate: o mais rápido primeiro). Serviços que o Melhor Envio devolve com erro (ex.: "Serviço indisponível para o trecho") ficam de fora; sem nenhum, a lista vem vazia |
| `id` | Id do serviço no Melhor Envio (ex.: 1 = PAC, 2 = SEDEX). **O checkout (#24) recebe esse id e cota de novo no servidor**: o preço que o front mostrou nunca é usado direto |
| `transportadora` / `servico` | Nome da empresa (`company.name`) e do serviço (`name`) no Melhor Envio |
| `preco` | `custom_price` do Melhor Envio (já com as taxas e descontos da conta), em reais |
| `prazoDias` | `custom_delivery_time` do Melhor Envio, em dias úteis depois da postagem |

**Carrinho vazio é 409 (`CARRINHO_VAZIO`), não 400:** o corpo está certo, o que impede a cotação é o estado do carrinho, como em `ESTOQUE_INSUFICIENTE`. Carrinho só com peças indisponíveis conta como vazio.

**Falha do Melhor Envio:** timeout (5 s, `FRETE_TIMEOUT`), queda, erro 5xx ou resposta fora do formato → 502 `FRETE_FALHOU`. Servidor sem `MELHOR_ENVIO_TOKEN`/`ROSIE_CEP_ORIGEM`, ou token recusado (401/403 do Melhor Envio) → 503 `FRETE_INDISPONIVEL`. Um 422 do Melhor Envio apontando o CEP de destino vira 400 `DADOS_INVALIDOS`.

**Pacote cotado (limitação até o produto ter peso e medidas):** cada linha do carrinho vai como um produto do Melhor Envio com a quantidade da linha, valor declarado (seguro) igual ao preço atual da peça e um pacote padrão por peça, configurável em `rosie.frete.peca.*`: **0,3 kg, 20 × 5 × 30 cm** (largura × altura × comprimento). O Melhor Envio junta as peças nos pacotes. Sem aviso de recebimento nem mão própria.

**Modo de desenvolvimento:** com `FRETE_SIMULADO=true` a API não chama o Melhor Envio e devolve opções fixas (PAC, SEDEX e Jadlog .Package, com preço subindo por peça extra); o CEP `99999-999` responde `opcoes: []`. Nunca ligar em produção.

### Finalizar pedido
`POST /pedidos`

Exige token `CLIENTE`. Fecha o pedido com **o carrinho inteiro** da cliente do token e devolve o link de pagamento do Mercado Pago (Checkout Pro), para onde a loja manda a cliente. Os dados do cartão nunca passam pela API.

```json
// Request — PedidoRequestDTO
{ "enderecoId": 5, "servicoFreteId": 1, "formaPagamento": "CARTAO" }
// Response 201 — PedidoCriadoDTO
{
  "id": 100,
  "status": "AGUARDANDO_PAGAMENTO",
  "subtotal": 189.00,
  "valorFrete": 22.90,
  "valorTotal": 211.90,
  "urlPagamento": "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=..."
}
// Response 409
{ "erro": "Só temos 1 de Conjunto Serpente (M). Ajuste a quantidade na sacola.", "codigo": "ESTOQUE_INSUFICIENTE" }
{ "erro": "Essa forma de entrega não está mais disponível para o endereço. Escolha outra.", "codigo": "FRETE_ALTERADO" }
// Response 503
{ "erro": "O pagamento não está configurado no servidor", "codigo": "PAGAMENTO_INDISPONIVEL" }
```

| Campo | Regra |
|---|---|
| `enderecoId` | Obrigatório. Endereço salvo (não arquivado) da cliente do token; de outra cliente ou inexistente → 400 "Endereço de entrega não encontrado" |
| `servicoFreteId` | Obrigatório. O `id` de uma opção do `POST /frete/calcular` |
| `formaPagamento` | Obrigatório: `CARTAO` (cartão de crédito, com parcelas) ou `PIX`. A página do Mercado Pago só mostra os meios dessa forma (boleto e lotérica nunca) |

**O servidor refaz tudo; do front só vêm escolhas.** Itens, quantidades e preços saem do carrinho (o preço é o atual do produto e fica guardado em `pedido_item.preco_unitario`: mudar o preço depois não altera o pedido). Toda peça precisa estar disponível agora: produto desativado → 409 `PRODUTO_INDISPONIVEL`; tamanho esgotado ou estoque menor que a quantidade → 409 `ESTOQUE_INSUFICIENTE` (a cliente ajusta na sacola). O frete é **cotado de novo** no Melhor Envio para o CEP do endereço escolhido, e o preço e o prazo usados são os dessa cotação (nunca os que a tela mostrou); se o `servicoFreteId` não voltar nela → 409 `FRETE_ALTERADO`. Erros do frete são os do `POST /frete/calcular` (502 `FRETE_FALHOU`, 503 `FRETE_INDISPONIVEL`).

**O estoque não baixa aqui** e a sacola fica como está: os dois só mudam quando o Mercado Pago confirmar o pagamento (ver "Webhook do Mercado Pago"). Se o Mercado Pago falhar (502 `PAGAMENTO_FALHOU`, 503 `PAGAMENTO_INDISPONIVEL`), nenhum pedido fica gravado.

**Preferência criada no Mercado Pago** (`POST https://api.mercadopago.com/checkout/preferences`): uma linha por peça ("Conjunto Serpente (tamanho M)", preço unitário, quantidade, foto) e uma do frete ("Frete Correios PAC"), somando o `valorTotal`; `payer` com nome e e-mail da cliente; `external_reference` = id do pedido; `notification_url` = `ROSIE_API_URL` + `/pagamentos/webhook`; `back_urls` = `ROSIE_LOJA_URL` + `/pedido/{id}/sucesso`, `/pendente` e `/falha`, com `auto_return: approved` só quando a loja é https (o Mercado Pago não aceita retorno automático para localhost); `statement_descriptor` `ROSIE`; `payment_methods.excluded_payment_types` conforme a forma. `urlPagamento` é o `init_point` da preferência.

**Modo de desenvolvimento:** com `PAGAMENTO_SIMULADO=true` a API não chama o Mercado Pago e `urlPagamento` aponta para a página da loja `/pedido/{id}/pagamento-simulado`, que chama `POST /pedidos/{id}/pagamento-simulado` com `{ "status": "APROVADO" }` (ou `PENDENTE`, `RECUSADO`, `CANCELADO`) e recebe o `PedidoDTO` atualizado. O pagamento simulado, com o valor do pedido, passa pelo mesmo processamento do webhook. Sem o modo simulado a rota responde 404. Nunca ligar em produção.

### Status do pedido

| Status | Quando |
|---|---|
| `AGUARDANDO_PAGAMENTO` | Pedido criado; a cliente ainda não pagou (PIX gerado e cartão em análise também ficam aqui) |
| `PAGAMENTO_RECUSADO` | O Mercado Pago recusou o pagamento. A cliente pode tentar de novo pelo mesmo `urlPagamento` |
| `PAGO` | Pagamento aprovado: o estoque já baixou e as peças saíram da sacola |
| `PAGO_COM_PENDENCIA` | Pagamento aprovado, mas o pedido não fechou sozinho: faltou estoque (outra cliente pagou antes a última peça) ou o valor pago não bate com o total. **O estoque não baixa** (nunca fica negativo), as peças saem da sacola e o motivo fica gravado em `pedido.pendencia` para a Roseane resolver (enviar quando repor, combinar troca ou devolver o dinheiro pelo Mercado Pago). O painel de pedidos (#25) mostra o motivo no detalhe do pedido; resolver a pendência pelo painel ainda não existe |
| `ENVIADO` | A Roseane gerou a etiqueta no painel (ver "Painel admin — Pedidos e etiqueta"): a etiqueta foi comprada e gerada no Melhor Envio e o pedido tem `codigoRastreio` (assim que o Melhor Envio devolver). A peça pode ainda não ter sido postada |
| `ENTREGUE` | Reservado: nada marca ainda (fica para quando o rastreio for acompanhado) |
| `CANCELADO` | Pagamento cancelado ou expirado no Mercado Pago (ex.: PIX não pago no prazo). Se mesmo assim chegar um pagamento aprovado depois, ele é processado como qualquer aprovação |

Na tabela `pedido` a coluna `status` é `VARCHAR(30)`. **Banco criado antes da #24** (o `ddl-auto=update` não muda o tipo de coluna que já existe, e a coluna era `ENUM` sem os status novos): rode uma vez `ALTER TABLE pedido MODIFY status VARCHAR(30) NOT NULL;`.

### Webhook do Mercado Pago
`POST /pagamentos/webhook?data.id={id do pagamento}&type=payment`

Público (sem token), chamado pelo Mercado Pago a cada mudança de um pagamento. A API:

1. Confere a assinatura do cabeçalho `x-signature` (`ts=...,v1=...`): `v1` deve ser o HMAC-SHA256, em hexadecimal, com `MERCADO_PAGO_WEBHOOK_SECRET` como chave, do manifesto `id:{data.id};request-id:{x-request-id};ts:{ts};` (o par cujo valor não veio sai do manifesto). Não confere → **401** `ASSINATURA_INVALIDA`, sem consultar nada.
2. Consulta o pagamento no Mercado Pago (`GET /v1/payments/{id}`): **o status nunca vem do corpo do aviso nem do retorno do navegador**. O pedido é o `external_reference`.
3. Atualiza o pedido e responde **200** (sem corpo). Mercado Pago fora do ar na consulta → 502 `PAGAMENTO_FALHOU`; servidor sem configuração → 503 `PAGAMENTO_INDISPONIVEL`. Nos dois casos o Mercado Pago tenta de novo mais tarde.

| Pagamento no Mercado Pago | Pedido |
|---|---|
| `approved` | `PAGO`: baixa o estoque de todas as peças e tira da sacola as quantidades compradas (o que a cliente pôs depois do checkout fica), tudo na mesma transação. Falta de estoque ou valor diferente → `PAGO_COM_PENDENCIA`, sem baixar nada |
| `rejected` | `PAGAMENTO_RECUSADO` |
| `cancelled` | `CANCELADO` |
| `pending`, `in_process`, `authorized`, `in_mediation` | Continua como está |
| `refunded`, `charged_back` | Não muda (só registra no log): devolução e contestação ficam com a Roseane no painel do Mercado Pago |

Recusa e cancelamento só mudam pedido que ainda espera pagamento (`AGUARDANDO_PAGAMENTO` ou `PAGAMENTO_RECUSADO`): um aviso atrasado de uma tentativa recusada não desfaz um pedido já pago.

**Idempotente:** o Mercado Pago repete avisos (e pode mandar dois ao mesmo tempo). A linha do pedido fica travada (`SELECT ... FOR UPDATE`) durante o processamento, e um pedido que já está `PAGO` ou além ignora novas aprovações: o mesmo pagamento aprovado baixa o estoque **uma vez só**. As linhas de estoque também são travadas, para dois pedidos diferentes aprovados juntos não venderem a mesma última peça.

Avisos de outros tópicos (`merchant_order`...) e do formato antigo (IPN, `?topic=payment&id=...`, que não vem assinado) só recebem 200, sem processar: o mesmo pagamento chega também como webhook assinado. Pagamento cujo `external_reference` não é um pedido da loja é ignorado (200).

### Consultar pedido
`GET /pedidos/{id}`

Exige token `CLIENTE`. Pedido de outra cliente responde **404** `RECURSO_NAO_ENCONTRADO`, como um que não existe. As páginas de retorno do Mercado Pago (`/pedido/{id}/sucesso`, `/pendente`, `/falha`) mostram o status daqui, não dos parâmetros da URL.

```json
// Response 200 — PedidoDTO
{
  "id": 100,
  "status": "AGUARDANDO_PAGAMENTO",
  "formaPagamento": "PIX",
  "subtotal": 189.00,
  "valorFrete": 22.90,
  "valorTotal": 211.90,
  "transportadora": "Correios",
  "servicoFrete": "PAC",
  "prazoFreteDias": 7,
  "codigoRastreio": null,
  "urlPagamento": "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=...",
  "createdAt": "2026-10-18T14:30:00",
  "endereco": { "id": 5, "cep": "03925050", "logradouro": "Rua das Flores", "numero": "120", "complemento": null,
                "bairro": "Jardim Iva", "cidade": "São Paulo", "estado": "SP" },
  "itens": [
    { "produtoId": 1, "nome": "Conjunto Serpente", "imagemPrincipal": "https://res.cloudinary.com/...",
      "tamanho": "M", "quantidade": 1, "precoUnitario": 189.00, "subtotal": 189.00 }
  ]
}
```

`urlPagamento` só vem enquanto dá para pagar (`AGUARDANDO_PAGAMENTO` ou `PAGAMENTO_RECUSADO`); nos outros status é `null`. `precoUnitario` é o preço do momento da compra. `codigoRastreio` fica `null` até a Roseane gerar a etiqueta (#25); depois traz o código da transportadora ou, enquanto ela não devolve, o do Melhor Envio (os dois rastreáveis no [Melhor Rastreio](https://www.melhorrastreio.com.br)). O link da etiqueta não vem aqui: é só do painel.

O detalhe do pedido na Minha Conta (#26) também usa esta rota: mostra itens, endereço, frete, status e, quando a etiqueta (#25) preencher, o `codigoRastreio` com o link "Acompanhar a entrega" (um `urlRastreio` no pedido, se a #25 acrescentar; senão o Melhor Rastreio com o código). Com `urlPagamento`, a tela oferece "Pagar agora".

### Listar meus pedidos
`GET /pedidos`

Exige token `CLIENTE`. Todos os pedidos da cliente do token (inclusive os que esperam pagamento e os cancelados), **do mais recente ao mais antigo** (`createdAt`; no mesmo segundo, o id maior primeiro). Cliente sem pedidos recebe `[]`. Os pedidos de outras clientes nunca aparecem. Para itens, endereço, frete e rastreio, a tela "Minha Conta" (#26) abre o `GET /pedidos/{id}`.

```json
// Response 200 — lista de PedidoResumoDTO
[
  { "id": 100, "status": "ENTREGUE", "valorTotal": 211.90, "createdAt": "2026-08-20T14:30:00" },
  { "id": 98, "status": "ENVIADO", "valorTotal": 189.00, "createdAt": "2026-08-12T10:15:00" }
]
```

`status` é um dos valores de "Status do pedido"; a loja mostra o nome legível ("Aguardando pagamento", "Pago", "Enviado"...).

---

## Troca e devolução (RF18, RNF08)

`POST /trocas`

```json
// Request
{ "pedidoId": 100, "produtoId": 10, "motivo": "DEFEITO" } // ou "NAO_SERVIU"
// Response 201
{
  "id": 1,
  "status": "SOLICITADA",
  "freteConta": "ROSIE" // ou "CLIENTE", calculado automaticamente a partir do motivo (RNF08)
}
```

---

## Painel admin — Produtos (RF14–RF16) — requer autenticação ADMIN

Todas as rotas exigem `Authorization: Bearer {token}` de `ADMIN` (401 `NAO_AUTENTICADO` sem token, 403 `ACESSO_NEGADO` com token de cliente). Diferente do catálogo, o painel enxerga os produtos inativos. Id de produto que não existe responde 404 `RECURSO_NAO_ENCONTRADO`.

### Listar produtos do painel
`GET /admin/produtos`

Todos os produtos, ativos e inativos, do mais recente para o mais antigo. Sem paginação (o catálogo é pequeno). `estoqueTotal` é a soma das quantidades de todos os tamanhos; `estoque` traz cada tamanho cadastrado, na ordem P, M, G, GG (tamanho que o produto não tem não aparece).

Serve às duas telas do painel: "Lista de Produtos" usa `estoqueTotal` e "Gestão de Estoque" usa `estoque`.

**Decisão (#16): sem `GET /admin/estoque` próprio.** A tela de estoque precisa dos mesmos produtos (inclusive os inativos) e da mesma ordem da lista; o `estoque` por tamanho já é carregado para calcular `estoqueTotal`, então acrescentar o campo aqui não custa consulta a mais e evita um segundo endpoint com o mesmo recorte.

```json
// Response 200 — lista de ProdutoAdminResumoDTO
[
  {
    "id": 10,
    "nome": "Conjunto Serpente",
    "preco": 189.00,
    "categoria": { "id": 1, "nome": "Vestido" },
    "estoqueTotal": 12,
    "estoque": [ { "tamanho": "P", "quantidade": 3 }, { "tamanho": "M", "quantidade": 5 }, { "tamanho": "G", "quantidade": 4 } ],
    "ativo": true,
    "destaque": false
  }
]
```

### Detalhe de um produto no painel
`GET /admin/produtos/{id}`

Mesmo formato do `GET /produtos/{id}`, mais `ativo` e `destaque`. Também responde para produto inativo. Diferente da loja, `imagens` traz cada foto com `id`, `ordem` e `enquadramento` (`ProdutoImagemDTO`), para o painel remover e reordenar; a de `ordem` 0 é a principal. A `url` é a mesma da loja, já em 4:5.

```json
// Response 200 — ProdutoAdminDTO
{
  "id": 10,
  "nome": "Conjunto Serpente",
  "descricao": "...",
  "composicao": "...",
  "preco": 189.00,
  "categoria": { "id": 1, "nome": "Vestido" },
  "imagens": [ { "id": 3, "url": "https://res.cloudinary.com/<cloud>/image/upload/c_fill,ar_4:5,g_auto/c_limit,w_1000/f_auto,q_auto/v1/rosie/produtos/abc123.jpg", "ordem": 0, "enquadramento": "AUTOMATICO" } ],
  "estoque": [ { "tamanho": "P", "quantidade": 3 }, { "tamanho": "M", "quantidade": 5 } ],
  "ativo": true,
  "destaque": false
}
```

### Criar produto
`POST /admin/produtos`

```json
// Request
{
  "nome": "Conjunto Serpente",
  "descricao": "...",
  "composicao": "100% linho\nLavar à mão em água fria",
  "preco": 189.00,
  "categoriaId": 1,
  "destaque": false,
  "estoque": [ { "tamanho": "P", "quantidade": 3 }, { "tamanho": "M", "quantidade": 5 } ]
}
// Response 201 — ProdutoAdminDTO (o produto criado, no formato do GET /admin/produtos/{id})
// Response 400
{ "erro": "O preço deve ser maior que zero", "codigo": "DADOS_INVALIDOS" }
```

Campos:

| Campo | Regra |
|---|---|
| `nome` | Obrigatório, até 150 caracteres (espaços nas pontas são removidos) |
| `descricao` | Opcional; vazio vira `null`. No PUT, vazio limpa o campo e ausente (ou `null`) mantém o valor atual |
| `composicao` | Opcional, texto livre ("Composição e cuidados"; quebras de linha são mantidas); vazio vira `null`, e a Página de Produto esconde o item. No PUT, vazio limpa o campo e ausente (ou `null`) mantém o valor atual (#50: reativar sem mandar o campo apagava a composição) |
| `preco` | Obrigatório, maior que zero, até 2 casas decimais |
| `categoriaId` | Obrigatório. Categoria que não existe responde **400** `DADOS_INVALIDOS` ("Categoria não encontrada"), não 404: o erro está no corpo, não no recurso da URL |
| `destaque` | Opcional (padrão `false`). Marca o produto para o carrossel e o Best Sellers da Home (`GET /produtos/destaques`) |
| `ativo` | Opcional (padrão `true`) |
| `estoque` | Opcional, **só no POST** (estoque inicial). Só os tamanhos que o produto tem: cada `tamanho` é `P`, `M`, `G` ou `GG`, sem repetir, e `quantidade` é inteiro `>= 0` (`0` = esgotado). Tamanho fora da lista não é vendido. Depois do cadastro, o estoque muda só por `PUT /admin/produtos/{id}/estoque` |

Mensagens de 400 em uso: "Informe o nome do produto", "Informe o preço", "O preço deve ser maior que zero", "Selecione a categoria", "Categoria não encontrada", "Tamanho inválido: use P, M, G ou GG", "Tamanho P repetido no estoque", "A quantidade não pode ser negativa".

### Editar produto
`PUT /admin/produtos/{id}`

```json
// Request — ProdutoEdicaoDTO (sem estoque)
{
  "nome": "Conjunto Serpente",
  "descricao": "...",
  "composicao": "100% linho\nLavar à mão em água fria",
  "preco": 189.00,
  "categoriaId": 1,
  "destaque": false,
  "ativo": true
}
// Response 200 — ProdutoAdminDTO atualizado (com o estoque atual, que não muda)
```

Mesmos campos e mesmas validações do POST, **menos o `estoque`**. `destaque`, `ativo`, `descricao` e `composicao` ausentes (ou `null`) mantêm o valor atual; `descricao` e `composicao` vazias limpam o campo.

**Decisão (#47): a edição não mexe no estoque, e um `estoque` no corpo é ignorado** (não dá 400). O estoque muda só por `PUT /admin/produtos/{id}/estoque` (tela "Gestão de Estoque"). Antes, o PUT substituía o estoque inteiro, e um formulário de edição aberto antes de uma mudança de estoque (pela tela de estoque ou, mais adiante, por uma venda) desfazia essa mudança ao salvar. Ignorar em vez de rejeitar é o que o back já faz com qualquer campo que o DTO não tem, e não quebra um cliente antigo que ainda mande o campo; o que importa é que ele não tem efeito.

**Reativar:** um produto removido volta para a loja com `PUT` e `"ativo": true` (o painel busca o produto e reenvia os outros campos). Também dá para desativar com `"ativo": false`, com o mesmo efeito do DELETE.

### Remover produto
`DELETE /admin/produtos/{id}` → **204** sem corpo.

**Decisão (#14): remover desativa, não apaga.** O produto fica com `ativo = false`: some de `GET /produtos`, `GET /produtos/destaques` e `GET /produtos/{id}` (404), mas continua no banco e no painel, porque pedidos e carrinhos antigos apontam para ele (`pedido_item.produto_id`). Remover um produto já inativo também responde 204.

### Upload de imagem
`POST /admin/produtos/{id}/imagens` — multipart/form-data, campo `arquivo` e, opcional, `enquadramento` (`AUTOMATICO`, `CENTRO` ou `TOPO`; vazio = `AUTOMATICO`)

```json
// Response 201 — ProdutoImagemDTO: a foto nova, sempre no fim da galeria, com a url já cortada em 4:5
{
  "id": 3,
  "url": "https://res.cloudinary.com/<cloud>/image/upload/c_fill,ar_4:5,g_north/c_limit,w_1000/f_auto,q_auto/v1/rosie/produtos/abc123.jpg",
  "ordem": 2,
  "enquadramento": "TOPO"
}
// Response 400
{ "erro": "Formato não aceito: envie a foto em JPG, PNG ou WEBP", "codigo": "FORMATO_NAO_SUPORTADO" }
{ "erro": "A foto passa de 5 MB: diminua o arquivo e tente de novo", "codigo": "ARQUIVO_MUITO_GRANDE" }
{ "erro": "Envie a foto no campo arquivo", "codigo": "DADOS_INVALIDOS" }
{ "erro": "Enquadramento inválido: use AUTOMATICO, CENTRO ou TOPO", "codigo": "DADOS_INVALIDOS" }
// Response 503 (sem CLOUDINARY_URL no servidor, ou Cloudinary fora do ar)
{ "erro": "O envio de fotos não está configurado no servidor (falta CLOUDINARY_URL)", "codigo": "ARMAZENAMENTO_INDISPONIVEL" }
```

| Regra | |
|---|---|
| Formatos | JPG, PNG ou WEBP, reconhecidos pelo conteúdo do arquivo (a extensão e o `Content-Type` enviados não contam) |
| Tamanho | Até 5 MB por foto (`rosie.imagens.tamanho-maximo`); uma foto por requisição |
| Proporção | Qualquer uma: a API não recusa. A foto original fica no Cloudinary e sai sempre em 4:5 (RNF09), ver "Corte 4:5 na entrega". Referência: 1000 x 1250 px |
| Enquadramento | Que parte fica no corte: `AUTOMATICO` (o Cloudinary acha o ponto de interesse, `g_auto`), `CENTRO` (`g_center`) ou `TOPO` (`g_north`, corta só embaixo). Guardado por foto; vem de volta em `ProdutoImagemDTO.enquadramento` |
| Ordem | A foto de `ordem` 0 é a principal (`imagemPrincipal` em `GET /produtos` e a primeira de `imagens` em `GET /produtos/{id}`). A ordem é sempre 0, 1, 2... sem buracos |
| Produto | Precisa já existir (404 `RECURSO_NAO_ENCONTRADO`): no cadastro novo, o painel salva o produto antes de liberar as fotos. Funciona também para produto inativo |

O `id` do produto é conferido antes do arquivo: produto inexistente responde 404 mesmo com arquivo inválido. Arquivo acima do limite é recusado pelo servidor antes disso (400 `ARQUIVO_MUITO_GRANDE`).

**Decisão (#15): fotos no Cloudinary.** O disco do Railway é efêmero, então as fotos ficam num storage externo. A API recebe o multipart, envia para o Cloudinary (pasta `rosie/produtos`) e guarda em `produto_imagem` a `url` segura (https) e o `public_id`, usado para apagar a foto de lá quando ela sai do produto. A credencial fica só na variável `CLOUDINARY_URL` (local: `rosie.cloudinary.url` no `application-local.properties`; produção: variável do Railway), nunca no repositório. Sem ela, a API sobe normalmente e só o upload responde 503. As imagens cadastradas antes desta mudança ficam com `public_id` nulo e continuam funcionando. Os testes automatizados usam um armazenamento falso no lugar do Cloudinary, e o CI não precisa das chaves.

#### Corte 4:5 na entrega
**Decisão (#18): o corte vai na URL de entrega, não no upload.** O banco guarda a `url` do original, e toda URL de foto que sai da API (`imagemPrincipal` em `GET /produtos` e `/produtos/destaques`, `imagens` em `GET /produtos/{id}` e `url` em `ProdutoImagemDTO` no painel) passa pela transformação do Cloudinary, inserida logo depois de `/image/upload/`:

```
c_fill,ar_4:5,<gravidade>/c_limit,w_1000/f_auto,q_auto
```

Corta em 4:5 na resolução original com a gravidade do enquadramento, reduz para no máximo 1000 px de largura (1000 x 1250, sem ampliar foto pequena) e deixa o Cloudinary escolher formato (WEBP/AVIF quando o navegador aceita) e qualidade. A URL é montada num lugar só do back-end (`UrlEntregaImagem`). Imagens sem `public_id` (as antigas, ex.: placehold.co) e URLs que não são do Cloudinary saem como foram salvas. Foto enviada antes desta mudança (sem enquadramento guardado) fica no `AUTOMATICO`.

Por que não cortar no upload (`transformation`/`eager`): cortar na chegada joga fora o resto da foto, e mudar o enquadramento depois exigiria reenviar o arquivo. Na entrega, o Cloudinary gera cada versão uma vez e serve do cache da CDN; o original continua lá. O painel mostra antes de enviar uma prévia do corte das fotos fora de 4:5 (calculada no navegador) e deixa escolher o enquadramento.

### Reordenar imagens
`PUT /admin/produtos/{id}/imagens/ordem`

```json
// Request: os ids de TODAS as fotos do produto, na nova ordem; o primeiro vira a principal
{ "imagens": [5, 3, 4] }
// Response 200 — lista de ProdutoImagemDTO na nova ordem
[
  { "id": 5, "url": "https://.../c.jpg", "ordem": 0, "enquadramento": "AUTOMATICO" },
  { "id": 3, "url": "https://.../a.jpg", "ordem": 1, "enquadramento": "TOPO" },
  { "id": 4, "url": "https://.../b.jpg", "ordem": 2, "enquadramento": "AUTOMATICO" }
]
// Response 400
{ "erro": "Envie todas as fotos do produto, cada uma uma vez", "codigo": "DADOS_INVALIDOS" }
```

Lista vazia responde 400 "Informe a ordem das fotos". Foto faltando, repetida ou de outro produto responde 400 "Envie todas as fotos do produto, cada uma uma vez" (assim uma tela desatualizada não bagunça a galeria).

### Remover imagem
`DELETE /admin/produtos/{id}/imagens/{imagemId}` → **204** sem corpo.

Tira a foto do produto, renumera as que ficaram (a seguinte vira a principal, se a removida era a primeira) e apaga o arquivo do Cloudinary. Se o Cloudinary falhar nessa hora, a foto sai do produto mesmo assim (o arquivo órfão fica registrado no log). Foto que não existe ou é de outro produto: 404 `RECURSO_NAO_ENCONTRADO`.

### Atualizar estoque
`PUT /admin/produtos/{id}/estoque`

```json
// Request
{ "estoque": [ { "tamanho": "P", "quantidade": 10 }, { "tamanho": "M", "quantidade": 0 } ] }
// Response 200 — EstoqueProdutoDTO: o estoque completo do produto, na ordem P, M, G, GG
{
  "produtoId": 10,
  "estoque": [
    { "tamanho": "P", "quantidade": 10 },
    { "tamanho": "M", "quantidade": 0 },
    { "tamanho": "G", "quantidade": 4 }
  ]
}
// Response 400
{ "erro": "A quantidade não pode ser negativa", "codigo": "DADOS_INVALIDOS" }
```

**Regra (#16): atualiza só os tamanhos enviados.** Tamanho que o produto já tem recebe a nova quantidade; tamanho que ele ainda não tem é cadastrado; tamanho que ficou de fora do body **continua como está**. É a única rota que muda o estoque de um produto já cadastrado (#47: o `PUT /admin/produtos/{id}` não mexe nele). Para esgotar um tamanho, envie `quantidade: 0`; por enquanto não há como descadastrar um tamanho (ele fica como esgotado). Também funciona para produto inativo.

| Campo | Regra |
|---|---|
| `estoque` | Obrigatório, com ao menos um item. Cada `tamanho` é `P`, `M`, `G` ou `GG`, sem repetir, e `quantidade` é inteiro `>= 0` (`0` = esgotado) |

Mensagens de 400 (`DADOS_INVALIDOS`): "Informe ao menos um tamanho", "Tamanho inválido: use P, M, G ou GG", "Tamanho P repetido no estoque", "A quantidade não pode ser negativa", "Informe a quantidade do tamanho". Produto inexistente: 404 `RECURSO_NAO_ENCONTRADO`.

Com todos os tamanhos zerados, o produto continua na vitrine, mas com `disponivel: false` em `GET /produtos` e todos os tamanhos desabilitados na Página de Produto.

---

## Painel admin — Categorias (RF17) — requer autenticação ADMIN

O nome é obrigatório, tem até 80 caracteres e é salvo sem os espaços das pontas. Dois nomes iguais sem diferenciar maiúsculas (`Bolsas` e `BOLSAS`) contam como duplicados. O `GET /categorias` público continua igual (só `id` e `nome`).

### Listar categorias
`GET /admin/categorias`

Todas as categorias, ordenadas por nome, com a quantidade de produtos ligados a cada uma (ativos e inativos).

```json
// Response 200 — lista de CategoriaAdminDTO
[
  { "id": 2, "nome": "Calça", "quantidadeProdutos": 0 },
  { "id": 1, "nome": "Vestido", "quantidadeProdutos": 4 }
]
```

### Criar categoria
`POST /admin/categorias`

```json
// Request — CategoriaRequestDTO
{ "nome": "Acessórios" }
// Response 201 — CategoriaAdminDTO
{ "id": 5, "nome": "Acessórios", "quantidadeProdutos": 0 }
// Response 400 — nome vazio ou com mais de 80 caracteres
{ "erro": "Informe o nome da categoria", "codigo": "DADOS_INVALIDOS" }
// Response 409 — já existe categoria com esse nome
{ "erro": "Já existe uma categoria com esse nome", "codigo": "CATEGORIA_DUPLICADA" }
```

### Renomear categoria
`PUT /admin/categorias/{id}` — mesmo body e mesmas validações do POST. Mudar só maiúsculas/minúsculas do próprio nome é permitido.

```json
// Response 200 — CategoriaAdminDTO
{ "id": 5, "nome": "Bijuterias", "quantidadeProdutos": 0 }
// Response 404 — RECURSO_NAO_ENCONTRADO; 409 — CATEGORIA_DUPLICADA
```

### Remover categoria
`DELETE /admin/categorias/{id}`

Só remove categoria sem nenhum produto. Produto inativo também bloqueia, porque continua ligado à categoria (o `DELETE /admin/produtos/{id}` só desativa): para liberar a categoria, mude os produtos de categoria. A tabela de medidas não bloqueia: se a categoria vazia tiver uma, ela é apagada junto.

```json
// Response 204 — sem corpo
// Response 404
{ "erro": "Categoria não encontrada", "codigo": "RECURSO_NAO_ENCONTRADO" }
// Response 409
{ "erro": "Não dá para remover \"Vestido\": a categoria tem 4 produtos. Mova os produtos para outra categoria antes.", "codigo": "CATEGORIA_EM_USO" }
```

### Tabela de medidas (#20)

Uma tabela por categoria, que a Roseane preenche no painel (`/admin/categorias/{id}/medidas`). Cada linha é uma medida (ex.: Busto, Cintura, Quadril, Comprimento) com um valor para cada tamanho, em cm. Medidas por produto ficam fora do escopo.

`GET /admin/categorias/{id}/medidas` — mesma resposta do `GET /categorias/{id}/medidas` público (`TabelaMedidasDTO`).

`PUT /admin/categorias/{id}/medidas` — troca a tabela inteira pela lista enviada, na ordem da lista (é assim que se cria, edita, reordena e remove linhas). Lista vazia deixa a categoria sem tabela.

- `nome`: obrigatório, até 40 caracteres, salvo sem os espaços das pontas; dois nomes iguais sem diferenciar maiúsculas na mesma tabela são recusados
- `valores`: opcional, com as chaves `P`, `M`, `G` e `GG` (outra chave é recusada); cada valor tem até 20 caracteres, salvo sem os espaços das pontas. Tamanho ausente, `null` ou só com espaços fica vazio (`""` na resposta)
- No máximo 20 linhas por tabela

```json
// Request — TabelaMedidasRequestDTO
{
  "medidas": [
    { "nome": "Busto", "valores": { "P": "84–88", "M": "89–93", "G": "94–98", "GG": "99–104" } },
    { "nome": "Comprimento", "valores": { "M": "92" } }
  ]
}
// Response 200 — TabelaMedidasDTO (como no GET)
// Response 400 — DADOS_INVALIDOS, com a mensagem do primeiro problema:
{ "erro": "Informe o nome da medida", "codigo": "DADOS_INVALIDOS" }
{ "erro": "A medida \"Busto\" aparece mais de uma vez", "codigo": "DADOS_INVALIDOS" }
{ "erro": "Cada valor da tabela pode ter no máximo 20 caracteres", "codigo": "DADOS_INVALIDOS" }
{ "erro": "Tamanho inválido: use P, M, G ou GG", "codigo": "DADOS_INVALIDOS" }
{ "erro": "A tabela pode ter no máximo 20 medidas", "codigo": "DADOS_INVALIDOS" }
// Response 404
{ "erro": "Categoria não encontrada", "codigo": "RECURSO_NAO_ENCONTRADO" }
```

`DELETE /admin/categorias/{id}/medidas` — apaga a tabela inteira (a categoria continua existindo).

```json
// Response 204 — sem corpo
// Response 404 — RECURSO_NAO_ENCONTRADO
```

---

## Painel admin — Pedidos e etiqueta (RF12) — requer autenticação ADMIN

Todas as rotas exigem token de `ADMIN` (401 `NAO_AUTENTICADO` sem token, 403 `ACESSO_NEGADO` com token de cliente). O painel vê os pedidos de todas as clientes, em qualquer status. Pedido que não existe: 404 `RECURSO_NAO_ENCONTRADO`.

### Listar pedidos
`GET /admin/pedidos`

Todos os pedidos, do mais recente para o mais antigo, sem paginação (o volume é pequeno). `quantidadePecas` soma as quantidades de todas as linhas.

```json
// Response 200 — lista de PedidoAdminResumoDTO
[
  {
    "id": 100,
    "status": "PAGO",
    "cliente": { "id": 7, "nome": "Maria Silva", "email": "maria@exemplo.com", "telefone": "11988887777" },
    "quantidadePecas": 2,
    "valorTotal": 211.90,
    "codigoRastreio": null,
    "createdAt": "2026-10-18T14:30:00"
  }
]
```

### Detalhe do pedido
`GET /admin/pedidos/{id}`

Os campos do `GET /pedidos/{id}` da cliente (menos `urlPagamento`), mais quem comprou, o pagamento e a etiqueta.

```json
// Response 200 — PedidoAdminDTO
{
  "id": 100,
  "status": "ENVIADO",
  "cliente": { "id": 7, "nome": "Maria Silva", "email": "maria@exemplo.com", "telefone": "11988887777" },
  "formaPagamento": "PIX",
  "pagamentoId": "1234567890",
  "pagoEm": "2026-10-18T14:35:00",
  "pendencia": null,
  "subtotal": 189.00,
  "valorFrete": 22.90,
  "valorTotal": 211.90,
  "transportadora": "Correios",
  "servicoFrete": "PAC",
  "prazoFreteDias": 7,
  "envioId": "9b0a3c1e-1d2f-4a5b-8c7d-6e5f4a3b2c1d",
  "urlEtiqueta": "https://melhorenvio.com.br/imprimir/ixQLaqqjmb2E",
  "etiquetaGeradaEm": "2026-10-18T16:02:00",
  "codigoRastreio": "AA123456789BR",
  "createdAt": "2026-10-18T14:30:00",
  "endereco": { "id": 5, "cep": "03925050", "logradouro": "Rua das Flores", "numero": "120", "complemento": null,
                "bairro": "Jardim Iva", "cidade": "São Paulo", "estado": "SP" },
  "itens": [
    { "produtoId": 1, "nome": "Conjunto Serpente", "imagemPrincipal": "https://res.cloudinary.com/...",
      "tamanho": "M", "quantidade": 1, "precoUnitario": 189.00, "subtotal": 189.00 }
  ]
}
```

| Campo | Regra |
|---|---|
| `pendencia` | Por que o pedido ficou `PAGO_COM_PENDENCIA` (ver "Status do pedido"); `null` nos outros |
| `envioId` | Id do envio no Melhor Envio, gravado assim que ele entra no carrinho de lá; `null` antes de gerar |
| `urlEtiqueta` | Link para abrir e imprimir a etiqueta (link **privado** do Melhor Envio: abre com a conta da loja logada lá). `null` até a etiqueta ficar pronta |
| `etiquetaGeradaEm` | Quando a etiqueta ficou pronta |
| `codigoRastreio` | Código da transportadora; enquanto ela não devolve, o do Melhor Envio. Pode ficar `null` logo depois de gerar (ver abaixo) |

### Gerar etiqueta
`POST /admin/pedidos/{id}/etiqueta` (sem corpo)

Compra e gera a etiqueta de envio no Melhor Envio para um pedido `PAGO` e devolve o `PedidoAdminDTO` atualizado: `status` `ENVIADO`, `urlEtiqueta`, `etiquetaGeradaEm` e `codigoRastreio`. A cliente passa a ver o `codigoRastreio` no `GET /pedidos/{id}`.

```json
// Response 200 — PedidoAdminDTO (como no detalhe)
// Response 409
{ "erro": "Só pedido pago tem etiqueta: este está AGUARDANDO_PAGAMENTO.", "codigo": "PEDIDO_NAO_PAGO" }
{ "erro": "Este pedido está com pendência: .... Resolva antes de gerar a etiqueta.", "codigo": "PEDIDO_COM_PENDENCIA" }
{ "erro": "O envio deste pedido foi cancelado no Melhor Envio. Clique em \"Gerar etiqueta\" de novo para comprar outra.", "codigo": "ETIQUETA_CANCELADA" }
{ "erro": "A etiqueta deste pedido já está sendo gerada. Aguarde e atualize a página.", "codigo": "ETIQUETA_EM_ANDAMENTO" }
// Response 502
{ "erro": "O Melhor Envio recusou a etiqueta: Saldo insuficiente para a compra.", "codigo": "ETIQUETA_FALHOU" }
{ "erro": "Não foi possível falar com o Melhor Envio agora. Tente de novo em instantes.", "codigo": "ETIQUETA_FALHOU" }
// Response 503
{ "erro": "A etiqueta de envio não está configurada no servidor", "codigo": "ETIQUETA_INDISPONIVEL" }
```

**Decisão (#25): a etiqueta sai pelo botão da Roseane, não sozinha no pagamento.** Comprar a etiqueta debita o saldo da conta do Melhor Envio, e a Roseane confere o pedido (peças, endereço, alguma conversa com a cliente) antes. Também evita fazer chamadas lentas a um serviço externo dentro do webhook do Mercado Pago.

**Passos no Melhor Envio**, cada um gravado no pedido assim que volta:

1. `POST /api/v2/me/cart`: põe o envio no carrinho, com o serviço escolhido no checkout (`pedido.servicoFreteId`), o remetente (`ROSIE_REMETENTE_*` e `ROSIE_CEP_ORIGEM`), o destinatário (nome, telefone e e-mail da cliente e o endereço do pedido), a declaração de conteúdo (uma linha por peça, "Conjunto Serpente (M)", com quantidade e preço pago; `non_commercial: true`, sem nota fiscal), o valor declarado (soma das peças, sem o frete) e a tag "Pedido {id}" (para achar o envio no painel do Melhor Envio). **Volume:** um pacote só (Correios não aceita mais de um por envio), com o pacote padrão por peça do frete (`rosie.frete.peca.*`: 20 × 5 × 30 cm, 0,3 kg) empilhado: altura e peso multiplicados pelo número de peças. Vale até o produto ter peso e medidas (#56). O `id` devolvido vira `envioId`.
2. `POST /api/v2/me/shipment/checkout`: compra com o saldo da conta.
3. `POST /api/v2/me/shipment/generate`: gera a etiqueta.
4. `POST /api/v2/me/shipment/print` (`mode: private`): link de impressão, que vira `urlEtiqueta`.
5. `POST /api/v2/me/shipment/tracking`: código de rastreio (`tracking`, ou `melhorenvio_tracking` enquanto o da transportadora não sai).

**Idempotente, sem comprar duas vezes.** Com `urlEtiqueta` e `codigoRastreio` já no pedido, responde 200 com o pedido como está, sem chamar o Melhor Envio. Se algo falhou no meio (saldo insuficiente, Melhor Envio fora do ar), o pedido continua `PAGO` com o `envioId` gravado, e o próximo clique **retoma de onde parou**: consulta a situação do envio no Melhor Envio (`pending` → compra e gera; `released` → só gera; `generated`/`posted`/`delivered` → só busca link e rastreio), então uma compra que deu certo lá, mas cuja resposta se perdeu, não é paga de novo. Envio `canceled` ou `expired` lá (cancelado pela Roseane no painel do Melhor Envio) → 409 `ETIQUETA_CANCELADA`, e o pedido esquece o envio: o clique seguinte compra uma etiqueta nova. Etiqueta pronta mas sem rastreio ainda (o Melhor Envio às vezes demora a devolver): o pedido fica `ENVIADO` com `codigoRastreio: null`, e repetir o POST só consulta o rastreio. Dois cliques ao mesmo tempo: o segundo espera o primeiro (até 90 s; passou disso, 409 `ETIQUETA_EM_ANDAMENTO`) e devolve a mesma etiqueta. A trava fica na memória da API (o Railway roda uma instância só).

**Pode gerar:** só `PAGO`. `PAGO_COM_PENDENCIA` → 409 `PEDIDO_COM_PENDENCIA` (o estoque não baixou; a Roseane resolve antes). `AGUARDANDO_PAGAMENTO`, `PAGAMENTO_RECUSADO` e `CANCELADO` → 409 `PEDIDO_NAO_PAGO`. `ENVIADO`/`ENTREGUE` só devolvem o pedido (ou buscam o rastreio que faltava).

**Erros do Melhor Envio:** recusa com mensagem (4xx, ex.: saldo insuficiente, campo inválido) → 502 `ETIQUETA_FALHOU` com "O Melhor Envio recusou a etiqueta: " + a primeira mensagem de lá; queda, 5xx, timeout (`ETIQUETA_TIMEOUT`, 20 s por chamada) ou resposta fora do formato → 502 `ETIQUETA_FALHOU` genérico. Servidor sem `MELHOR_ENVIO_TOKEN`, `ROSIE_CEP_ORIGEM` ou os dados obrigatórios do remetente, ou token recusado (401/403) → 503 `ETIQUETA_INDISPONIVEL`. O token precisa dos escopos `cart-read`, `cart-write`, `shipping-checkout`, `shipping-generate`, `shipping-print` e `shipping-tracking`.

**Modo de desenvolvimento:** com `FRETE_SIMULADO=true` a etiqueta também é de mentira: nada é cobrado, o rastreio sai no formato `SIM00000000BR` e o link abre `/etiqueta-simulada.html` da loja. Nunca ligar em produção.

---

## Decisão: autenticação via JWT (confirmado)

**Por quê:**
- Front-end (Netlify/Cloudflare) e back-end (Railway) ficam em **domínios diferentes** — sessão tradicional usa cookies, que esbarram em bloqueios de CORS/SameSite entre domínios distintos. JWT evita isso: o token vai no header da requisição, não depende de cookie.
- **Sem estado no servidor** = menos RAM ocupada no Railway (que cobra por uso contínuo de memória) — sessão tradicional exige guardar quem está logado na memória do servidor.
- Combinação JWT + Spring Security é extremamente comum e bem documentada.

**Trade-off aceito:** JWT não tem "logout forte" fácil (sem lista negra de tokens). Solução simples adotada: token com validade curta (2 horas), sem controle de logout forçado por enquanto — suficiente pro escopo atual.

**Implementação (#13):** suporte nativo do Spring Security (`spring-boot-starter-security-oauth2-resource-server`, `NimbusJwtEncoder`/`NimbusJwtDecoder`), sem biblioteca extra. A claim `role` vira a authority `ROLE_ADMIN`/`ROLE_CLIENTE`. No front, o token fica no `sessionStorage` e o `api.js` só o envia nas chamadas do painel: as rotas públicas do catálogo não recebem `Authorization`, porque um token vencido faria a API responder 401 mesmo nelas.

**Cliente (#21):** mesmo token e mesma validade, com `role` `CLIENTE`. No front, a sessão do cliente fica no `localStorage` (`rosie.cliente.sessao`), separada da do painel (`rosie.admin.sessao`, no `sessionStorage`): o cliente continua logado entre abas e ao reabrir o navegador, até o token vencer em 2 horas. O `api.js` só manda o token de cliente nas rotas de cliente, e um 401 nelas limpa a sessão e leva a `/entrar?voltar=<página atual>`.

---

## Próximos passos

- [ ] Definir mensagens de erro específicas conforme forem surgindo casos reais
