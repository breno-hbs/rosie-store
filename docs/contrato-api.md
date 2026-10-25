# Contrato da API

Define os endpoints REST, formato de requisição/resposta e convenções compartilhadas entre o Catálogo e o Painel Admin, baseado no modelo de dados MySQL e nos requisitos funcionais (`docs/requisitos.md`).

Migrado do Notion em 17/10/2026. A partir daqui, este arquivo é a fonte de verdade: **se um endpoint mudar, atualize este arquivo no mesmo PR.**

## Status de implementação

| Endpoint | Status | DTO de resposta |
|---|---|---|
| `GET /produtos` | Implementado (filtros opcionais `categoriaId` e `busca`) | `ProdutoListaResponseDTO` |
| `GET /produtos/destaques` | Implementado (público, só ativos com `destaque = true`) | lista de `ProdutoResumoDTO` |
| `GET /produtos/{id}` | Implementado (404 com `RECURSO_NAO_ENCONTRADO`, também para produto inativo) | `ProdutoDetalheDTO` |
| `GET /banners` | Implementado (público; só os ativos dentro do período, na ordem; #89) | lista de `BannerDTO` |
| `GET /categorias` | Implementado (público, ordenado por nome) | lista de `CategoriaResumoDTO` |
| `GET /categorias/{slug}` | Implementado (público; aceita o slug de antes de renomear; 404 com `RECURSO_NAO_ENCONTRADO`) | `CategoriaResumoDTO` |
| `GET /categorias/{id}/medidas` | Implementado (público; `medidas` vazia se a categoria não tem tabela; 404) | `TabelaMedidasDTO` |
| `POST /auth/registrar` | Implementado (público; 201; 400 `DADOS_INVALIDOS`; 409 com `EMAIL_JA_CADASTRADO`) | `LoginResponseDTO` |
| `POST /auth/login` | Implementado (público; só `CLIENTE`; 401 com `CREDENCIAIS_INVALIDAS`, também para conta excluída) | `LoginResponseDTO` |
| `GET /auth/me` | Implementado (exige token `CLIENTE`) | `UsuarioResumoDTO` |
| `POST /auth/esqueci-senha` | Implementado (público, para cliente e admin; sempre 202 com a mesma mensagem, exista ou não a conta; 400 `DADOS_INVALIDOS`; 429 `MUITAS_TENTATIVAS`) | `MensagemDTO` |
| `POST /auth/redefinir-senha` | Implementado (público, pelo token do link; 204; encerra as sessões abertas; 400 `DADOS_INVALIDOS`/`TOKEN_INVALIDO`/`TOKEN_EXPIRADO`) | — |
| `GET /privacidade` | Implementado (público; versão e vigência da política de privacidade em vigor) | `PoliticaPrivacidadeDTO` |
| `GET /carrinho` | Implementado (exige token `CLIENTE`; marca itens que ficaram indisponíveis sem apagá-los) | `CarrinhoDTO` |
| `POST /carrinho/itens` | Implementado (exige token `CLIENTE`; 201; 400 `DADOS_INVALIDOS`; 409 com `ESTOQUE_INSUFICIENTE`/`PRODUTO_INDISPONIVEL`) | `CarrinhoDTO` |
| `PUT /carrinho/itens/{itemId}` | Implementado (exige token `CLIENTE`; 400; 404; 409 com `ESTOQUE_INSUFICIENTE`/`PRODUTO_INDISPONIVEL`) | `CarrinhoDTO` |
| `DELETE /carrinho/itens/{itemId}` | Implementado (exige token `CLIENTE`; 200 com o carrinho; 404) | `CarrinhoDTO` |
| `POST /frete/calcular` | Implementado (exige token `CLIENTE`; cota o carrinho no Melhor Envio; 400 `DADOS_INVALIDOS`; 409 `CARRINHO_VAZIO`; 502 `FRETE_FALHOU`; 503 `FRETE_INDISPONIVEL`) | `FreteResponseDTO` |
| `GET /enderecos` | Implementado (exige token `CLIENTE`; sem os arquivados) | lista de `EnderecoDTO` |
| `POST /enderecos` | Implementado (exige token `CLIENTE`; 201; 400 `DADOS_INVALIDOS`) | `EnderecoDTO` |
| `PUT /enderecos/{id}` | Implementado (exige token `CLIENTE`; 400; 404; endereço já usado em pedido volta com id novo) | `EnderecoDTO` |
| `DELETE /enderecos/{id}` | Implementado (exige token `CLIENTE`; 204; 404; endereço já usado em pedido só é arquivado) | — |
| `POST /pedidos` | Implementado (exige token `CLIENTE`; 201; 400 `DADOS_INVALIDOS`; 409 `CARRINHO_VAZIO`/`ESTOQUE_INSUFICIENTE`/`PRODUTO_INDISPONIVEL`/`FRETE_ALTERADO`/`PRIVACIDADE_PENDENTE`; 502 `FRETE_FALHOU`/`PAGAMENTO_FALHOU`; 503 `FRETE_INDISPONIVEL`/`PAGAMENTO_INDISPONIVEL`) | `PedidoCriadoDTO` |
| `GET /pedidos` | Implementado (exige token `CLIENTE`; só os pedidos da cliente do token, do mais recente ao mais antigo) | lista de `PedidoResumoDTO` |
| `GET /pedidos/{id}` | Implementado (exige token `CLIENTE`; 404 também para pedido de outra cliente) | `PedidoDTO` |
| `GET /conta` | Implementado (exige token `CLIENTE`; com a troca de e-mail pendente em `emailPendente`) | `ContaDTO` |
| `POST /trocas` | Implementado (exige token `CLIENTE`; 201; 400 `DADOS_INVALIDOS`; 404 também para pedido de outra cliente; 409 `PEDIDO_NAO_ENTREGUE`/`TROCA_FORA_DO_PRAZO`/`TROCA_JA_SOLICITADA`) | `TrocaDTO` |
| `GET /trocas` | Implementado (exige token `CLIENTE`; só as trocas da cliente do token, da mais recente à mais antiga; com a etiqueta de volta ou o endereço de devolução, #63) | lista de `TrocaDTO` |
| `PUT /conta` | Implementado (exige token `CLIENTE`; nome e telefone; 400 `DADOS_INVALIDOS`) | `ContaDTO` |
| `PUT /conta/senha` | Implementado (exige token `CLIENTE`; encerra as outras sessões e devolve o token novo; 400 `DADOS_INVALIDOS`, também para senha atual errada) | `LoginResponseDTO` |
| `POST /conta/privacidade` | Implementado (exige token `CLIENTE`; aceite da versão em vigor da política; 400 `DADOS_INVALIDOS`) | `ContaDTO` |
| `POST /conta/exclusao` | Implementado (exige token `CLIENTE`; 204; 400 `DADOS_INVALIDOS`, também para senha errada; 409 `CONTA_COM_PEDIDO_EM_ANDAMENTO`) | — |
| `PUT /conta/email` | Implementado (exige token `CLIENTE`; 202, a troca fica pendente até a confirmação; 400 `DADOS_INVALIDOS`; 403 `SENHA_INCORRETA`; 409 `EMAIL_JA_CADASTRADO`; 502 `EMAIL_FALHOU`) | `EmailPendenteDTO` |
| `POST /conta/email/confirmar` | Implementado (público, pelo token do link; 400 `TOKEN_INVALIDO`/`TOKEN_EXPIRADO`; 409 `EMAIL_JA_CADASTRADO`) | `EmailConfirmadoDTO` |
| `POST /pagamentos/webhook` | Implementado (público, assinado pelo Mercado Pago; 200; 401 `ASSINATURA_INVALIDA`; 502/503 para o Mercado Pago tentar de novo) | — |
| `POST /pedidos/{id}/pagamento-simulado` | Só desenvolvimento, com `PAGAMENTO_SIMULADO=true` (404 fora dele) | `PedidoDTO` |
| `POST /admin/auth/login` | Implementado (público; 401 com `CREDENCIAIS_INVALIDAS`) | `LoginResponseDTO` |
| `GET /admin/auth/me` | Implementado (exige token `ADMIN`) | `UsuarioResumoDTO` |
| `GET /admin/produtos` | Implementado (exige token `ADMIN`; inclui inativos) | lista de `ProdutoAdminResumoDTO` |
| `GET /admin/produtos/{id}` | Implementado (exige token `ADMIN`; inclui inativos) | `ProdutoAdminDTO` |
| `POST /admin/produtos` | Implementado (exige token `ADMIN`; 201) | `ProdutoAdminDTO` |
| `PUT /admin/produtos/{id}` | Implementado (exige token `ADMIN`; `ativo: true` reativa; não altera o estoque) | `ProdutoAdminDTO` |
| `PUT /admin/produtos/{id}/estoque` | Implementado (exige token `ADMIN`; só os tamanhos enviados) | `EstoqueProdutoDTO` |
| `DELETE /admin/produtos/{id}` | Implementado (exige token `ADMIN`; 204; sem parâmetro arquiva; `?definitivo=true` exclui de vez, 409 `PRODUTO_COM_HISTORICO` se já entrou em pedido ou troca) | — |
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
| `GET /admin/banners` | Implementado (exige token `ADMIN`; todos, na ordem, com a situação de agora) | lista de `BannerAdminDTO` |
| `POST /admin/banners` | Implementado (exige token `ADMIN`; multipart; 201; 400 `DADOS_INVALIDOS`/`FORMATO_NAO_SUPORTADO`/`ARQUIVO_MUITO_GRANDE`; 503 `ARMAZENAMENTO_INDISPONIVEL`) | `BannerAdminDTO` |
| `PUT /admin/banners/{id}` | Implementado (exige token `ADMIN`; multipart, imagens opcionais; mesmos erros do POST; 404) | `BannerAdminDTO` |
| `PUT /admin/banners/{id}/ativo` | Implementado (exige token `ADMIN`; 400; 404) | `BannerAdminDTO` |
| `PUT /admin/banners/ordem` | Implementado (exige token `ADMIN`; 400 se faltar ou repetir banner) | lista de `BannerAdminDTO` |
| `DELETE /admin/banners/{id}` | Implementado (exige token `ADMIN`; 204; 404; as imagens saem do Cloudinary depois) | — |
| `GET /admin/pedidos` | Implementado (exige token `ADMIN`; todos os pedidos) | lista de `PedidoAdminResumoDTO` |
| `GET /admin/pedidos/{id}` | Implementado (exige token `ADMIN`; 404) | `PedidoAdminDTO` |
| `POST /admin/pedidos/{id}/etiqueta` | Implementado (exige token `ADMIN`; ver "Gerar etiqueta") | `PedidoAdminDTO` |
| `POST /admin/pedidos/{id}/entregue` | Implementado (exige token `ADMIN`; corpo opcional com `dataEntrega`; 400 `DADOS_INVALIDOS`; 404; 409 `PEDIDO_NAO_ENVIADO`) | `PedidoAdminDTO` |
| `GET /admin/trocas` | Implementado (exige token `ADMIN`; todas as trocas) | lista de `TrocaAdminDTO` |
| `PUT /admin/trocas/{id}/status` | Implementado (exige token `ADMIN`; 400 `DADOS_INVALIDOS`; 404; 409 `STATUS_TROCA_INVALIDO`) | `TrocaAdminDTO` |
| `POST /admin/trocas/{id}/etiqueta-volta` | Implementado (exige token `ADMIN`; ver "Gerar etiqueta de volta", #63) | `TrocaAdminDTO` |
| Demais endpoints | Não implementados | — |

---

## Convenções gerais

- **Base URL (dev):** `http://localhost:8080`
- **Formato:** JSON em todas as requisições e respostas (exceções: o upload de foto do produto e o cadastro de banner são `multipart/form-data`)
- **Autenticação:** JWT via header `Authorization: Bearer {token}` — obtido no login
- **Tamanhos:** sempre um dos valores `P`, `M`, `G`, `GG`
- **Datas:** formato ISO 8601 (`2026-08-25T14:30:00`); campos só de dia, como `dataEntrega` e `prazoTroca`, vêm sem hora (`2026-08-25`)
- **Erros:** sempre nesse formato (`ErroResponseDTO`), com o HTTP status code correspondente (400, 401, 403, 404, 409, 429, 500, 502, 503)

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
| `DADOS_INVALIDOS` | 400 | Corpo ausente, JSON malformado ou campo inválido (ex.: e-mail vazio no login, preço zero, categoria inexistente ou tamanho repetido no cadastro de produto); `erro` traz a mensagem de um campo só: o primeiro inválido na ordem do DTO e, se ele quebra mais de uma regra, a de obrigatório (ex.: "Informe o telefone") antes da de formato |
| `CREDENCIAIS_INVALIDAS` | 401 | Login com e-mail inexistente, senha errada, usuário sem permissão ou conta excluída (mesma resposta em todos os casos) |
| `NAO_AUTENTICADO` | 401 | Rota protegida sem token, ou com token inválido, adulterado, expirado, de uma conta excluída ou emitido antes da última troca de senha (#93) |
| `ACESSO_NEGADO` | 403 | Token válido, mas sem a role exigida (ex.: token de cliente em `/admin/**`) |
| `FORMATO_NAO_SUPORTADO` | 400 | Upload de foto que não é JPG, PNG nem WEBP (conferido pelo conteúdo do arquivo, não pela extensão) |
| `ARQUIVO_MUITO_GRANDE` | 400 | Upload de foto acima de 5 MB |
| `ARMAZENAMENTO_INDISPONIVEL` | 503 | Upload de foto sem `CLOUDINARY_URL` configurada no servidor, ou com o Cloudinary fora do ar |
| `CATEGORIA_DUPLICADA` | 409 | Criar ou renomear categoria com um nome que já existe (sem diferenciar maiúsculas) |
| `CATEGORIA_EM_USO` | 409 | Remover categoria que ainda tem produtos (ativos ou inativos); `erro` diz quantos |
| `PRODUTO_COM_HISTORICO` | 409 | Painel: excluir definitivamente (`DELETE /admin/produtos/{id}?definitivo=true`) um produto que já entrou em pedido (de qualquer status) ou em troca; `erro` sugere arquivar (#87) |
| `EMAIL_JA_CADASTRADO` | 409 | Cadastro de cliente, ou troca de e-mail na Minha Conta (no pedido e de novo na confirmação), com um e-mail que já tem conta (sem diferenciar maiúsculas), de cliente ou de admin |
| `SENHA_INCORRETA` | 403 | Troca de e-mail com a senha atual errada. Não é 401: a sessão continua valendo |
| `TOKEN_INVALIDO` | 400 | Confirmação da troca de e-mail ou redefinição de senha com um token que não existe, já foi usado ou foi substituído por um pedido mais novo (ou sem token) |
| `TOKEN_EXPIRADO` | 400 | Confirmação da troca de e-mail ou redefinição de senha depois do prazo do link (`ROSIE_TROCA_EMAIL_VALIDADE`, padrão 24 horas; `ROSIE_REDEFINICAO_SENHA_VALIDADE`, padrão 1 hora); a pessoa pede de novo |
| `MUITAS_TENTATIVAS` | 429 | "Esqueci minha senha" acima do limite de pedidos por e-mail ou por IP (padrão 3 e 10 por hora); a pessoa espera a janela acabar |
| `EMAIL_FALHOU` | 502 | Troca de e-mail: o Resend não aceitou o link de confirmação (fora do ar, recusa, tempo esgotado). Nada fica pendente; a cliente pode tentar de novo |
| `ESTOQUE_INSUFICIENTE` | 409 | Carrinho: a quantidade pedida (somada à que já está na sacola, no POST) passa do estoque do tamanho, ou o tamanho está esgotado; `erro` traz a mensagem pronta ("Só temos 3 no tamanho P") |
| `PRODUTO_INDISPONIVEL` | 409 | Carrinho: adicionar ou mudar a quantidade de um produto desativado pela Roseane |
| `CARRINHO_VAZIO` | 409 | Frete: o carrinho não tem nenhuma peça disponível para cotar. Checkout: a sacola está vazia |
| `FRETE_ALTERADO` | 409 | Checkout: a forma de entrega escolhida (`servicoFreteId`) não voltou na cotação refeita no servidor para o CEP do endereço |
| `PAGAMENTO_FALHOU` | 502 | Checkout: o Mercado Pago não respondeu a tempo, caiu ou devolveu algo inesperado. A cliente pode tentar de novo (nenhum pedido fica gravado) |
| `PAGAMENTO_INDISPONIVEL` | 503 | Checkout: servidor sem `MERCADO_PAGO_ACCESS_TOKEN`, `MERCADO_PAGO_WEBHOOK_SECRET` ou `ROSIE_API_URL` (https), ou access token recusado pelo Mercado Pago |
| `ASSINATURA_INVALIDA` | 401 | Webhook do Mercado Pago sem `x-signature`, com assinatura malformada ou que não confere com o segredo |
| `FRETE_FALHOU` | 502 | Frete: o Melhor Envio não respondeu a tempo, caiu ou devolveu algo inesperado. A cliente pode tentar de novo |
| `FRETE_INDISPONIVEL` | 503 | Frete: servidor sem `MELHOR_ENVIO_TOKEN` ou `ROSIE_CEP_ORIGEM`, ou token recusado pelo Melhor Envio |
| `PEDIDO_NAO_ENVIADO` | 409 | Painel: marcar como entregue um pedido que não está `ENVIADO` (ainda sem etiqueta, não pago ou cancelado) |
| `PEDIDO_NAO_ENTREGUE` | 409 | Troca: o pedido ainda não foi marcado como entregue |
| `TROCA_FORA_DO_PRAZO` | 409 | Troca: passou o prazo (7 dias corridos depois da entrega); `erro` traz o último dia |
| `TROCA_JA_SOLICITADA` | 409 | Troca: já existe troca dessa peça do pedido que não foi recusada |
| `PRIVACIDADE_PENDENTE` | 409 | Checkout: a cliente aceitou uma versão antiga da política de privacidade (ou nenhuma); aceita a nova em `POST /conta/privacidade` e tenta de novo |
| `CONTA_COM_PEDIDO_EM_ANDAMENTO` | 409 | Exclusão da conta com pedido pago ainda não entregue (`PAGO`, `PAGO_COM_PENDENCIA`, `ENVIADO`) ou troca em aberto (`SOLICITADA`, `APROVADA`); `erro` diz qual |
| `STATUS_TROCA_INVALIDO` | 409 | Painel: mudança de status da troca fora do caminho (ex.: concluir sem aprovar, mexer numa recusada) |
| `TROCA_NAO_APROVADA` | 409 | Painel: gerar a etiqueta de volta de uma troca que não está `APROVADA` (#63) |
| `TROCA_FRETE_DA_CLIENTE` | 409 | Painel: gerar a etiqueta de volta de uma troca "não serviu" (`freteConta = CLIENTE`): a loja não compra essa etiqueta (#63) |

---

## Autenticação (RF08, RF13)

Cliente e admin são o mesmo `usuario`, com `role` `CLIENTE` ou `ADMIN`, e o mesmo token (ver "Login admin"). **Cada um entra pelo seu login:** `POST /auth/login` só aceita `CLIENTE` e `POST /admin/auth/login` só aceita `ADMIN`; a conta da outra role recebe o mesmo 401 de senha errada. Assim o token de cada área sai do formulário daquela área, e a sessão da loja e a do painel ficam separadas no navegador.

| Rotas | Quem acessa |
|---|---|
| `POST /auth/registrar`, `POST /auth/login`, `POST /auth/esqueci-senha`, `POST /auth/redefinir-senha`, `POST /admin/auth/login`, `GET /produtos/**`, `GET /categorias`, `GET /categorias/{slug}`, `GET /categorias/{id}/medidas`, `GET /privacidade`, `POST /conta/email/confirmar` | Público |
| `POST /pagamentos/webhook` | Público, mas só processa aviso com a assinatura `x-signature` do Mercado Pago |
| `/auth/**` (ex.: `GET /auth/me`; menos o cadastro, o login e o "esqueci minha senha"), `/conta/**` (menos a confirmação da troca de e-mail), `/carrinho/**`, `/frete/**`, `/enderecos/**`, `/pedidos/**`, `/trocas/**` | Token `CLIENTE` (401 `NAO_AUTENTICADO` sem token; 403 `ACESSO_NEGADO` com token de admin) |
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
{ "token": "eyJhbGc...", "usuario": { "id": 7, "nome": "Roseane Silva", "role": "CLIENTE" }, "privacidadePendente": false }
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

**Consentimento (LGPD):** o cadastro grava em `usuario` a data e hora do aceite (`privacidade_aceita_em`) e a versão da política aceita (`privacidade_versao`). Admin não tem aceite (nulo). A versão vem de um lugar só: `PoliticaPrivacidade.VIGENCIA` no back-end (a versão é essa data, ex.: `2026-10-19`), que a página `/politica-de-privacidade` da loja lê em `GET /privacidade`. Quando o texto da política mudar, troque a vigência: os cadastros seguintes gravam a versão nova, e quem aceitou a antiga aceita a nova no próximo login (`privacidadePendente`) ou no checkout (409 `PRIVACIDADE_PENDENTE`); ver "Política de privacidade (LGPD, #31)".

O 409 diz que o e-mail já tem conta, o que revela que ele está cadastrado. É aceito de propósito: o cadastro precisa avisar a pessoa para entrar em vez de criar outra conta. O login, ao contrário, não diz se o e-mail existe.

### Login de cliente
`POST /auth/login`

Público. Mesmas regras do login admin (e-mail sem diferenciar maiúsculas, mesmo 401 para e-mail inexistente e senha errada, token de 2 horas), mas só entra `CLIENTE`: o e-mail da Roseane recebe 401 aqui.

```json
// Request
{ "email": "roseane@email.com", "senha": "senha123" }
// Response 200 — LoginResponseDTO
{ "token": "eyJhbGc...", "usuario": { "id": 7, "nome": "Roseane Silva", "role": "CLIENTE" }, "privacidadePendente": false }
// Response 401
{ "erro": "E-mail ou senha incorretos", "codigo": "CREDENCIAIS_INVALIDAS" }
// Response 400 (e-mail ou senha vazios, e-mail mal formado)
{ "erro": "Informe o e-mail", "codigo": "DADOS_INVALIDOS" }
```

`privacidadePendente` (#31) é `true` quando a cliente aceitou uma versão antiga da política de privacidade (ou nenhuma): ela entra normalmente, e a loja pede o aceite da versão em vigor (`POST /conta/privacidade`) antes de seguir. O cadastro e o login admin também devolvem o campo (sempre `false` nos dois). Conta excluída recebe o mesmo 401 de senha errada.

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
{
  "id": 7, "nome": "Maria Silva", "email": "maria@email.com", "telefone": "11987654321",
  "privacidadeVersao": "2026-10-19", "privacidadeAceitaEm": "2026-10-19T14:30:00",
  "emailPendente": { "novoEmail": "maria.nova@email.com", "expiraEm": "2026-10-20T14:30:00" }
}
// PUT /conta — ContaRequestDTO → 200 com o ContaDTO atualizado
{ "nome": "Maria Souza", "telefone": "1134567890" }
// PUT /conta/senha — SenhaRequestDTO → 200 — LoginResponseDTO (o token novo deste aparelho, #93)
{ "senhaAtual": "senha123", "novaSenha": "nova-senha-456" }
{ "token": "eyJhbGc...", "usuario": { "id": 7, "nome": "Maria Silva", "role": "CLIENTE" }, "privacidadePendente": false }
// Response 400
{ "erro": "Senha atual incorreta", "codigo": "DADOS_INVALIDOS" }
```

| Campo | Regra |
|---|---|
| `nome` | Obrigatório, até 150 caracteres (espaços nas pontas são removidos), como no cadastro |
| `telefone` | Obrigatório, só dígitos com DDD: 10 ou 11 dígitos, como no cadastro. O front tira a máscara |
| `email` | Só leitura: é o login e não muda pelo `PUT /conta`. A troca é pelo `PUT /conta/email`, com confirmação pelo endereço novo (ver "Troca de e-mail") |
| `senhaAtual` | Obrigatória e precisa conferir com a senha da conta |
| `novaSenha` | Obrigatória, de 8 a 72 caracteres (e até 72 bytes, como no cadastro). Guardada só como hash BCrypt |

Mensagens de 400 (`DADOS_INVALIDOS`): "Informe o nome", "O nome pode ter no máximo 150 caracteres", "Informe o telefone", "Telefone inválido: informe DDD e número, só com dígitos", "Informe a senha atual", "Senha atual incorreta", "Informe a nova senha", "A senha precisa ter de 8 a 72 caracteres".

**Senha atual errada é 400, não 401:** a sessão continua valendo, só o formulário está errado (um 401 faria a loja tirar a cliente da conta).

**A troca encerra as outras sessões (#93):** como no "esqueci minha senha", a versão da credencial sobe e todo token emitido antes deixa de valer na hora (401 `NAO_AUTENTICADO`; ex.: a conta aberta num celular perdido). A resposta traz o token novo, que a loja guarda no lugar do antigo: o aparelho de onde a cliente trocou a senha continua logado. Um link de "esqueci minha senha" pendente deixa de valer, e o e-mail da conta recebe o aviso de senha trocada. Antes da #93 a resposta era 204 e os tokens seguiam valendo até vencer.

**O nome no token não muda:** o `nome` do JWT (e do `GET /auth/me`) é o do momento do login. A loja atualiza o nome da sessão guardada no navegador com a resposta do `PUT /conta`, e o próximo login já sai com o nome novo.

`privacidadeVersao` e `privacidadeAceitaEm` (#31) dizem qual versão da política de privacidade a cliente aceitou e quando (nulos se não há registro). Só leitura: mudam pelo `POST /conta/privacidade`.

`emailPendente` (#61) é a troca de e-mail esperando a confirmação pelo link: o endereço novo e até quando o link vale. `null` sem troca pendente ou com o link já vencido.

### Troca de e-mail (Minha Conta, #61)
`PUT /conta/email`, `POST /conta/email/confirmar`

O e-mail é o login, então o novo **não vale na hora**: a cliente pede a troca com a senha atual, a API guarda a troca pendente e manda ao endereço novo um link de confirmação; só a confirmação pelo link troca o login. Até lá a cliente continua entrando com o e-mail antigo (e o novo não entra). Assim uma digitação errada não deixa ninguém sem acesso à conta.

```json
// PUT /conta/email (token CLIENTE) — EmailRequestDTO → 202 — EmailPendenteDTO
{ "novoEmail": "maria.nova@email.com", "senhaAtual": "senha123" }
{ "novoEmail": "maria.nova@email.com", "expiraEm": "2026-10-20T14:30:00" }
// Response 403
{ "erro": "Senha atual incorreta", "codigo": "SENHA_INCORRETA" }
// Response 409
{ "erro": "Este e-mail já está cadastrado. Entre com ele ou use outro.", "codigo": "EMAIL_JA_CADASTRADO" }

// POST /conta/email/confirmar (público) — ConfirmacaoEmailRequestDTO → 200 — EmailConfirmadoDTO
{ "token": "q3Vx...43 caracteres" }
{ "email": "maria.nova@email.com" }
// Response 400
{ "erro": "Este link não é válido ou já foi usado", "codigo": "TOKEN_INVALIDO" }
{ "erro": "Este link expirou. Peça a troca de e-mail de novo", "codigo": "TOKEN_EXPIRADO" }
```

| Campo | Regra |
|---|---|
| `novoEmail` | Obrigatório, como no cadastro: e-mail com ponto no domínio, até 150 caracteres, guardado sem espaços nas pontas e em minúsculas. Igual ao atual (sem diferenciar maiúsculas) → 400 "O novo e-mail é igual ao atual" |
| `senhaAtual` | Obrigatória e precisa conferir com a senha da conta. Errada → **403 `SENHA_INCORRETA`** (não 401: a sessão continua valendo, e um 401 faria a loja tirar a cliente da conta), sem pedir a troca nem mandar e-mail |
| `token` | O do link. Ausente, vazio, inexistente, já usado ou substituído por um pedido mais novo → 400 `TOKEN_INVALIDO`; vencido → 400 `TOKEN_EXPIRADO` |

Mensagens de 400 (`DADOS_INVALIDOS`) do pedido: "Informe o novo e-mail", "E-mail inválido", "O e-mail pode ter no máximo 150 caracteres", "Informe a senha atual", "O novo e-mail é igual ao atual".

**Ordem das conferências no pedido:** formato dos campos (400), senha (403), e-mail igual ao atual (400) e e-mail já cadastrado (409). A senha vem antes do 409, então só a dona da conta descobre se um e-mail está cadastrado, e o 409 não revela mais do que o cadastro já revela (mesma mensagem). Nenhuma outra resposta diz se o endereço novo pertence a alguém.

**Troca pendente:** uma por cliente (tabela `email_pendente`: `usuario_id` único, `novo_email`, `token_hash`, `expira_em`, `solicitado_em`). Um pedido novo substitui o anterior, e o link anterior deixa de valer (`TOKEN_INVALIDO`). O token tem 256 bits aleatórios (Base64 para URL, 43 caracteres) e **só o hash SHA-256 fica no banco**, nunca o token. Prazo: `ROSIE_TROCA_EMAIL_VALIDADE` (padrão 24 horas). Datas no horário de Brasília. A exclusão da conta (#31) apaga a troca pendente.

**O link:** `ROSIE_LOJA_URL` + `/minha-conta/confirmar-email?token=...`, uma página pública da loja (o link pode ser aberto em outro aparelho, sem a cliente logada). A página tira o token da barra de endereço assim que o lê e chama o `POST /conta/email/confirmar` com ele no corpo (o token nunca vai na URL de uma chamada à API).

**O e-mail com o link sai na hora**, dentro da requisição (não pela fila dos avisos): a resposta 202 só volta depois que o Resend aceitou o e-mail, para a loja não dizer "enviamos um link" quando ele não saiu. Se o Resend falhar, a troca pendente é desfeita e a resposta é 502 `EMAIL_FALHOU`. Sem `RESEND_API_KEY` (dev, CI), o link não sai; o log registra só o destinatário mascarado e o assunto, e o texto do e-mail (com o link) só com `logging.level.com.rosie.backend.service.EmailSoNoLog=DEBUG`, para testar no dev.

**Confirmação:** numa transação, com a linha da troca pendente travada (o mesmo token não confirma duas vezes ao mesmo tempo): confere o token e o prazo, **confere de novo se o e-mail novo já tem conta** (alguém pode ter se cadastrado com ele nesse meio-tempo: 409 `EMAIL_JA_CADASTRADO`, e o login continua o antigo), troca o e-mail e apaga a troca pendente (o token não serve mais). Depois do commit, o **endereço antigo recebe um aviso** de que o login foi trocado, com o novo e-mail mascarado (`ma***@email.com`), a data e hora e os contatos da loja para o caso de não ter sido a dona da conta. O aviso sai pela fila dos e-mails: uma falha no envio não desfaz a troca.

**Sessão depois da troca:** o JWT não leva o e-mail (`sub` é o id do usuário; as claims são `role`, `nome` e `vc`), então os tokens já emitidos continuam valendo e a cliente segue logada (diferente da troca de senha, que encerra as sessões, #93). O próximo login já é com o e-mail novo. A proteção contra uma troca feita por outra pessoa é a senha exigida no pedido e o aviso ao endereço antigo.

### Esqueci minha senha (#93)
`POST /auth/esqueci-senha`, `POST /auth/redefinir-senha`

Os dois são públicos e servem à cliente e ao admin (o e-mail é único entre os dois). O pedido manda ao e-mail da conta um link de uso único; a página do link pede a senha nova; depois a pessoa entra pelo login da sua área. A loja abre o fluxo em "Esqueci minha senha" no `/entrar` (página `/esqueci-senha`) e o painel no `/admin/login` (página `/admin/esqueci-senha`).

```json
// POST /auth/esqueci-senha — EsqueciSenhaRequestDTO → 202 — MensagemDTO (sempre a mesma)
{ "email": "maria@email.com" }
{ "mensagem": "Se houver uma conta com esse e-mail, enviamos um link para criar uma nova senha." }
// Response 429
{ "erro": "Muitos pedidos seguidos. Espere um pouco e tente de novo.", "codigo": "MUITAS_TENTATIVAS" }

// POST /auth/redefinir-senha — RedefinicaoSenhaRequestDTO → 204, sem corpo
{ "token": "q3Vx...43 caracteres", "novaSenha": "nova-senha-456" }
// Response 400
{ "erro": "Este link não é válido ou já foi usado", "codigo": "TOKEN_INVALIDO" }
{ "erro": "Este link expirou. Peça um link novo", "codigo": "TOKEN_EXPIRADO" }
{ "erro": "A senha precisa ter de 8 a 72 caracteres", "codigo": "DADOS_INVALIDOS" }
```

| Campo | Regra |
|---|---|
| `email` | Obrigatório, com ponto no domínio, até 150 caracteres; comparado sem espaços nas pontas e sem diferenciar maiúsculas, como no login |
| `token` | O do link. Ausente, vazio, inexistente, já usado ou substituído por um pedido mais novo → 400 `TOKEN_INVALIDO`; vencido → 400 `TOKEN_EXPIRADO` |
| `novaSenha` | As regras do cadastro: obrigatória, de 8 a 72 caracteres (e até 72 bytes). Guardada só como hash BCrypt. A confirmação ("digite de novo") fica só na página |

Mensagens de 400 (`DADOS_INVALIDOS`): "Informe o e-mail", "E-mail inválido", "O e-mail pode ter no máximo 150 caracteres", "Informe a nova senha", "A senha precisa ter de 8 a 72 caracteres". A senha é conferida antes do token: uma senha recusada não gasta o link.

**O pedido sempre responde igual:** e-mail sem conta, conta excluída pela LGPD (#31, que não recebe link) e conta ativa recebem o mesmo 202 com a mesma mensagem, e o e-mail sai pela fila **depois do commit**, sem a resposta esperar o Resend. Assim nem o corpo, nem o status, nem o tempo de resposta (a não ser por alguns milissegundos de gravação no banco) dizem se o e-mail tem conta, e uma falha do Resend vai só para o log. É diferente da troca de e-mail (#61), que manda o link dentro da requisição e responde 502 se ele não sair: lá a cliente está logada e já sabe que a conta é dela.

**Limite de tentativas:** no máximo `ROSIE_ESQUECI_SENHA_LIMITE_EMAIL` pedidos por e-mail (padrão 3) e `ROSIE_ESQUECI_SENHA_LIMITE_IP` por IP (padrão 10) em cada janela fixa de `ROSIE_ESQUECI_SENHA_JANELA` (padrão 1 hora); acima disso, 429 `MUITAS_TENTATIVAS` até a janela acabar. Conta todo pedido, exista ou não a conta, então o 429 não diz nada sobre o e-mail. O pedido barrado pelo IP não gasta a cota do e-mail. A contagem fica na memória da API (`LimitePedidosSenha`): basta para uma instância só (Railway Hobby) e zera quando ela reinicia; com mais de uma instância o limite efetivo se multiplica, e aí a contagem teria de ir para o banco. O IP é o `remoteAddr` da requisição: no perfil `prod`, o `server.forward-headers-strategy=framework` (#74) o troca pelo primeiro endereço do `X-Forwarded-For` que chega do proxy do Railway. Esse cabeçalho pode ser preenchido pelo próprio cliente, então o limite por IP só freia quem não se dá a esse trabalho; o que protege a caixa de entrada de alguém contra spam é o limite por e-mail.

**O link:** `ROSIE_LOJA_URL` + `/redefinir-senha?token=...` para a cliente ou `/admin/redefinir-senha?token=...` para o admin (cada página volta para o seu login). Token de 256 bits aleatórios (Base64 para URL, 43 caracteres) com **só o hash SHA-256 no banco** (tabela `redefinicao_senha`: `usuario_id` único, `token_hash`, `expira_em`, `solicitado_em`), prazo de `ROSIE_REDEFINICAO_SENHA_VALIDADE` (padrão 1 hora, horário de Brasília). Um pedido novo substitui o anterior, cujo link deixa de valer. A página tira o token da barra de endereço assim que o lê e o manda no corpo do `POST`. Sem `RESEND_API_KEY` (dev, CI), o link só aparece no log com `logging.level.com.rosie.backend.service.EmailSoNoLog=DEBUG`, como na troca de e-mail.

**Redefinição:** numa transação, com a linha do pedido travada (o mesmo token não serve duas vezes ao mesmo tempo): confere o token e o prazo, grava a senha nova (BCrypt), **sobe a versão da credencial** (encerra as sessões, ver abaixo) e apaga o pedido (um segundo uso responde `TOKEN_INVALIDO`). Não entra na conta: a resposta é 204 e a página leva ao login da área. Depois do commit, o e-mail da conta recebe o **aviso de senha trocada**, com a data e hora e o que fazer se não foi a dona da conta; uma falha no envio não desfaz a troca. A troca de senha da Minha Conta e a exclusão da conta (#31) também apagam o pedido pendente.

**Sessões encerradas (versão da credencial):** `usuario.versao_credencial` começa em 0 e sobe a cada troca de senha (por aqui ou no `PUT /conta/senha`). O JWT leva a versão do momento do login na claim `vc`, e o validador do JWT (o mesmo que derruba a conta excluída: uma consulta por id em cada requisição com token) recusa o token de outra versão com 401 `NAO_AUTENTICADO`, e a loja ou o painel levam ao login. Token sem `vc` (emitido antes da #93) conta como versão 0, então ninguém perdeu a sessão no deploy. Foi escolhida uma versão, e não a data da troca, porque o `iat` do JWT só tem segundos: um login feito no mesmo segundo da troca seria recusado (ou um token de antes, aceito). A troca de e-mail (#61) não mexe na versão.

### Política de privacidade (LGPD, #31)
`GET /privacidade`, `POST /conta/privacidade`

`GET /privacidade` é público e devolve a versão em vigor, a mesma que o cadastro e o aceite gravam. A página `/politica-de-privacidade` da loja mostra essa versão e a vigência; o texto da política fica no front (`pages/PoliticaPrivacidade.jsx`).

```json
// GET /privacidade → 200 — PoliticaPrivacidadeDTO
{ "versao": "2026-10-19", "vigencia": "2026-10-19" }
// POST /conta/privacidade — AceitePrivacidadeRequestDTO → 200 com o ContaDTO (privacidadeVersao = versão em vigor)
{ "aceitePrivacidade": true }
// Response 400
{ "erro": "Aceite a política de privacidade para continuar", "codigo": "DADOS_INVALIDOS" }
```

`POST /conta/privacidade` exige token `CLIENTE` e `aceitePrivacidade: true` (ausente ou `false` → 400). Grava a data e hora de agora e a versão em vigor. A loja chama depois do login com `privacidadePendente: true` e no checkout quando o `POST /pedidos` responde 409 `PRIVACIDADE_PENDENTE`.

### Exclusão da conta (LGPD, #31)
`POST /conta/exclusao`

Exige token `CLIENTE`. A senha atual confirma que é a dona da conta. É `POST` com corpo (e não `DELETE`) porque `DELETE` com corpo nem sempre passa por proxies.

```json
// Request — ExclusaoContaRequestDTO → 204, sem corpo
{ "senha": "senha123" }
// Response 400
{ "erro": "Senha incorreta", "codigo": "DADOS_INVALIDOS" }
// Response 409
{ "erro": "Você tem um pedido a caminho. A conta pode ser excluída depois que ele for entregue.", "codigo": "CONTA_COM_PEDIDO_EM_ANDAMENTO" }
```

Mensagens de 400 (`DADOS_INVALIDOS`): "Informe a sua senha", "Senha incorreta".

**Regra: anonimiza, não apaga a linha.** Os pedidos e os valores ficam (obrigação fiscal), sem dono identificável:

| Dado | O que acontece |
|---|---|
| `usuario.nome` | Vira `Conta excluída` |
| `usuario.email` | Vira `excluida-{id}@conta-excluida.invalid`: o e-mail original fica livre para um cadastro novo |
| `usuario.telefone` | Apagado (nulo) |
| `usuario.senha_hash` | Trocado pelo hash de uma senha aleatória que ninguém sabe |
| `usuario.excluida_em` | Data e hora da exclusão. Preenchida, o login responde 401 `CREDENCIAIS_INVALIDAS` e todo token já emitido para a conta passa a responder 401 `NAO_AUTENTICADO` na hora (sem esperar as 2 horas) |
| `usuario.privacidade_*` | Ficam, como registro do consentimento dado |
| Endereços | Os usados em pedido ficam só com cidade e UF (CEP `00000000`, logradouro `Endereço removido`, número e bairro `-`, sem complemento) e arquivados; os outros são apagados |
| Sacola | Apagada |
| Trocas | Ficam, sem a `observacao` escrita pela cliente |
| Pedidos | Ficam, com peças, valores, frete, rastreio e pagamento. Os ainda não pagos (`AGUARDANDO_PAGAMENTO`, `PAGAMENTO_RECUSADO`) viram `CANCELADO` |

**Quando não exclui (409 `CONTA_COM_PEDIDO_EM_ANDAMENTO`):** pedido pago ainda não entregue (`PAGO`, `PAGO_COM_PENDENCIA`, `ENVIADO`), porque a etiqueta e a transportadora ainda usam o nome, o telefone e o endereço; ou troca em aberto (`SOLICITADA`, `APROVADA`). A cliente exclui depois da entrega ou da troca.

**Pagamento aprovado depois da exclusão** (um link de pagamento que ficou aberto): o pedido vai para `PAGO_COM_PENDENCIA`, sem baixar estoque, com a pendência dizendo para a Roseane devolver o pagamento pelo Mercado Pago.

O Mercado Pago e o Melhor Envio guardam as próprias cópias dos dados dos pedidos, conforme as políticas deles.

### Login admin
`POST /admin/auth/login`

Público. Só entra usuário com role `ADMIN` (RF13, RNF02). O e-mail é comparado sem diferenciar maiúsculas. E-mail inexistente, senha errada e conta de cliente respondem o mesmo 401, sem dizer qual dos dois errou.

O token é um JWT HS256 com validade de **2 horas**: `sub` = id do usuário, `role`, `nome`, `vc` (versão da credencial, #93), `iss` = `rosie`. Mande-o em `Authorization: Bearer {token}` nas rotas `/admin/**`. Sem logout no servidor: o painel só esquece o token.

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
| `ROSIE_LOJA_URL` | Não (padrão `http://localhost:5173`) | Endereço da loja, para as URLs de retorno do Mercado Pago e os links da troca de e-mail e do "esqueci minha senha"; link de banner com o endereço completo da loja vira caminho (#89) |
| `ROSIE_TROCA_EMAIL_VALIDADE` | Não (padrão `24h`) | Prazo do link de confirmação da troca de e-mail (ex.: `30m`, `24h`, `2d`); ver "Troca de e-mail" |
| `ROSIE_REDEFINICAO_SENHA_VALIDADE` | Não (padrão `1h`) | Prazo do link de "esqueci minha senha"; ver "Esqueci minha senha" |
| `ROSIE_ESQUECI_SENHA_LIMITE_EMAIL`, `ROSIE_ESQUECI_SENHA_LIMITE_IP`, `ROSIE_ESQUECI_SENHA_JANELA` | Não (padrões `3`, `10`, `1h`) | Limite de pedidos de "esqueci minha senha" por e-mail e por IP em cada janela; ver "Esqueci minha senha" |
| `TROCA_PRAZO_DIAS` | Não (padrão `7`) | Prazo para pedir troca, em dias corridos depois da entrega; ver "Troca e devolução" |
| `ROSIE_REMETENTE_NOME`, `_LOGRADOURO`, `_NUMERO`, `_COMPLEMENTO`, `_BAIRRO`, `_CIDADE`, `_UF` e `ROSIE_CEP_ORIGEM` | Não (faltando um obrigatório, a cliente vê "a Rosie vai te passar o endereço") | Além de remetente da etiqueta de ida e destino da de volta, é o endereço de devolução que a cliente vê na troca "não serviu" aprovada (`enderecoDevolucao`, #63); ver "Devolução da peça" |
| `ROSIE_RASTREIO_ATIVO`, `ROSIE_RASTREIO_INTERVALO`, `ROSIE_RASTREIO_LOTE`, `ROSIE_RASTREIO_PAUSA` | Não (padrões `true`, `6h`, `50`, `1s`) | Entrega pelo rastreio: liga/desliga, intervalo entre as rodadas, pedidos por rodada e pausa entre as consultas ao Melhor Envio; ver "Entrega pelo rastreio". Só roda com `MELHOR_ENVIO_TOKEN` |
| `JPA_SHOW_SQL` | Não (padrão `false`) | `true` mostra no log os comandos SQL (com `?` no lugar dos valores), para depurar. Desligado para não encher o log da hospedagem |

O admin inicial é criado na subida da API só enquanto não existir nenhum usuário `ADMIN`; depois as três variáveis `ROSIE_ADMIN_*` são ignoradas.

---

## Catálogo (RF01–RF06)

### Listar categorias
`GET /categorias`

Pública (sem token). Retorna todas as categorias ordenadas por nome.

`slug` (#88) é o endereço da página da categoria na loja, `/categoria/{slug}`, gerado do nome: sem acentos, em minúsculas, e tudo o que não é letra nem número (espaço, `/`, pontuação) vira um hífen só, sem hífen nas pontas ("Cropped/Body" → `cropped-body`, "Calça" → `calca`). Tem até 80 caracteres; nome sem nenhuma letra ou número vira `categoria`. É único: se outra categoria já usa o slug, a nova recebe o primeiro sufixo livre (`vestido-2`, `vestido-3`...). O slug sai também no `categoria` dos produtos (`ProdutoResumoDTO`, `ProdutoDetalheDTO` e os DTOs do painel).

```json
// Response 200 — lista de CategoriaResumoDTO
[
  { "id": 2, "nome": "Calça", "slug": "calca" },
  { "id": 4, "nome": "Cropped/Body", "slug": "cropped-body" },
  { "id": 1, "nome": "Vestido", "slug": "vestido" }
]
```

### Buscar categoria pelo slug
`GET /categorias/{slug}`

Pública (sem token). Usada pela página da categoria (`/categoria/{slug}`, #88); os produtos da página vêm do `GET /produtos?categoriaId={id}`, com a paginação de sempre. Não diferencia maiúsculas (`Vestido` acha `vestido`).

**Renomear muda o slug, mas o link antigo continua funcionando:** o slug de antes de renomear fica guardado e também acha a categoria, que vem com o `slug` atual. Quando o `slug` da resposta é diferente do pedido, a loja troca o endereço pelo atual (sem recarregar). Se outra categoria passar a ter aquele slug (ex.: "Vestido" virou "Vestidos" e depois a Roseane criou uma "Vestido" nova), o slug é dela, e o link leva para a categoria nova. Remover a categoria apaga os slugs antigos dela.

```json
// Response 200 — CategoriaResumoDTO (GET /categorias/cropped-body)
{ "id": 4, "nome": "Cropped/Body", "slug": "cropped-body" }
// Response 200 — slug antigo: "Vestido" foi renomeada para "Vestidos" (GET /categorias/vestido)
{ "id": 1, "nome": "Vestidos", "slug": "vestidos" }
// Response 404 — nenhuma categoria tem (nem teve) esse slug
{ "erro": "Categoria não encontrada", "codigo": "RECURSO_NAO_ENCONTRADO" }
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

### Listar produtos (com filtro opcional por categoria e busca)
`GET /produtos?categoriaId=1&busca=vestido%20midi&pagina=0&tamanho=12`

Parâmetros (todos opcionais): `categoriaId`, `busca`, `pagina` (padrão `0`), `tamanho` (padrão `12`).

Lista só produtos ativos (`ativo = true`), com ou sem `categoriaId` e `busca`. Produto inativo não aparece na listagem.

`busca` (busca do header, #82): texto separado em termos pelos espaços; cada termo precisa aparecer no nome, na descrição, na composição ou no nome da categoria do produto (ex.: `vestido linho` acha um vestido de linho). Não diferencia maiúsculas nem acentos (`calca` acha "Calça"); `%` e `_` são texto, não curinga. Vazia ou só com espaços, lista como se não houvesse busca. Sem erro para busca longa: só os primeiros 100 caracteres e os 5 primeiros termos (sem repetir) contam. Combina com `categoriaId` (os dois filtros valem juntos). Sem resultado: `conteudo` vazio, `totalItens` `0`.

`disponivel` é `true` quando algum tamanho tem `quantidade > 0` no estoque; produto sem estoque cadastrado ou com todos os tamanhos zerados vem com `false` (continua listado, como esgotado).

```json
// Response 200 — ProdutoListaResponseDTO
{
  "conteudo": [
    {
      "id": 10,
      "nome": "Conjunto Serpente",
      "preco": 189.00,
      "categoria": { "id": 1, "nome": "Vestido", "slug": "vestido" },
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
    "categoria": { "id": 1, "nome": "Vestido", "slug": "vestido" },
    "imagemPrincipal": "https://.../conjunto-serpente-1.jpg",
    "disponivel": true
  }
]
```

### Banners da Home
`GET /banners`

Pública (sem token). Os banners do carrossel da Home (#89), cadastrados no painel: só os **ativos e dentro do período** (`inicio <= agora < fim`, no horário de São Paulo; início ou fim vazio = sem limite daquele lado), na ordem do painel. Lista vazia: a Home volta a mostrar os destaques (`GET /produtos/destaques`) no carrossel.

As imagens já vêm cortadas para cada tela pelo Cloudinary (mesma ideia do "Corte 4:5 na entrega"), com `url` (versão padrão) e `srcset` (larguras para o navegador escolher):

| Campo | Proporção | Larguras do `srcset` | Uso |
|---|---|---|---|
| `imagemDesktop` | 3:1 (`c_fill,ar_3:1,g_auto`) | 768, 1280 (a `url`), 1920 | Telas a partir de 768 px (`<source media="(min-width: 768px)">`) |
| `imagemCelular` | 4:5 (`c_fill,ar_4:5,g_auto`) | 480, 750 (a `url`), 1080 | Celular. Sem imagem de celular cadastrada, é a de desktop com corte central (`g_center`) |

Imagem fora do Cloudinary (sem `public_id`) sai como foi salva, com `srcset: null`. `titulo`, `texto`, `textoBotao` e `link` vêm `null` quando não foram preenchidos; `link` é sempre um caminho da própria loja (ver "Painel admin — Banners da Home"). `textoAlternativo` é o `alt` da imagem.

```json
// Response 200 — lista de BannerDTO
[
  {
    "id": 3,
    "titulo": "Coleção de verão",
    "texto": "Peças leves para os dias quentes",
    "textoBotao": "Ver coleção",
    "link": "/categoria/vestido",
    "textoAlternativo": "Modelo com vestido florido de linho",
    "imagemDesktop": {
      "url": "https://res.cloudinary.com/<cloud>/image/upload/c_fill,ar_3:1,g_auto/c_limit,w_1280/f_auto,q_auto/v1/rosie/banners/abc.jpg",
      "srcset": "https://.../c_limit,w_768/... 768w, https://.../c_limit,w_1280/... 1280w, https://.../c_limit,w_1920/... 1920w"
    },
    "imagemCelular": {
      "url": "https://res.cloudinary.com/<cloud>/image/upload/c_fill,ar_4:5,g_auto/c_limit,w_750/f_auto,q_auto/v1/rosie/banners/def.jpg",
      "srcset": "https://.../c_limit,w_480/... 480w, https://.../c_limit,w_750/... 750w, https://.../c_limit,w_1080/... 1080w"
    }
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
  "categoria": { "id": 1, "nome": "Vestido", "slug": "vestido" },
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
| `itens[].disponivel` / `motivoIndisponivel` | Item que ficou indisponível **depois** de adicionado continua no carrinho (a API não apaga sozinha), com `disponivel: false` e o motivo: `PRODUTO_INATIVO` (a Roseane desativou o produto), `TAMANHO_ESGOTADO` (estoque do tamanho zerou) ou `ESTOQUE_INSUFICIENTE` (estoque menor que a quantidade). A cliente remove ou diminui a quantidade. Produto excluído de vez no painel (#87) sai da sacola, sem aviso |
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

**Pacote cotado (#56):** cada linha do carrinho vai como um produto do Melhor Envio com a quantidade da linha, valor declarado (seguro) igual ao preço atual da peça e o pacote de uma peça embalada: o peso e as medidas cadastrados no produto (`pacote` no painel, ver "Criar produto"). **Campo vazio usa o pacote padrão só nele**, configurável em `rosie.frete.peca.*`: **0,3 kg, 20 × 5 × 30 cm** (largura × altura × comprimento); produto sem nada preenchido vai inteiro com o padrão. Ex.: peso 1,2 kg e o resto vazio → 1,2 kg, 20 × 5 × 30 cm. O Melhor Envio junta as peças nos pacotes. A etiqueta usa a mesma montagem (ver "Gerar etiqueta"). Sem aviso de recebimento nem mão própria.

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

**Política de privacidade em dia (#31):** cliente que aceitou uma versão antiga da política (ou nenhuma) recebe 409 `PRIVACIDADE_PENDENTE` antes de qualquer outra conferência; a loja mostra o aceite da versão nova (`POST /conta/privacidade`) e a cliente finaliza de novo.

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
| `ENTREGUE` | A peça chegou, com a data da entrega (`dataEntrega`): confirmada sozinha pelo rastreio do Melhor Envio (ver "Entrega pelo rastreio") ou marcada pela Roseane no painel (ver "Marcar como entregue"). Daí conta o prazo de troca |
| `CANCELADO` | Pagamento cancelado ou expirado no Mercado Pago (ex.: PIX não pago no prazo). Se mesmo assim chegar um pagamento aprovado depois, ele é processado como qualquer aprovação |

Na tabela `pedido` a coluna `status` é `VARCHAR(30)`. **Banco criado antes da #24** (o `ddl-auto=update` não muda o tipo de coluna que já existe, e a coluna era `ENUM` sem os status novos): rode uma vez `ALTER TABLE pedido MODIFY status VARCHAR(30) NOT NULL;`. Banco novo já nasce com `VARCHAR(30)` pela migração V1 do Flyway (#74).

### Webhook do Mercado Pago
`POST /pagamentos/webhook?data.id={id do pagamento}&type=payment`

Público (sem token), chamado pelo Mercado Pago a cada mudança de um pagamento. A API:

1. Confere a assinatura do cabeçalho `x-signature` (`ts=...,v1=...`): `v1` deve ser o HMAC-SHA256, em hexadecimal, com `MERCADO_PAGO_WEBHOOK_SECRET` como chave, do manifesto `id:{data.id};request-id:{x-request-id};ts:{ts};` (o par cujo valor não veio sai do manifesto). Não confere → **401** `ASSINATURA_INVALIDA`, sem consultar nada.
2. Consulta o pagamento no Mercado Pago (`GET /v1/payments/{id}`): **o status nunca vem do corpo do aviso nem do retorno do navegador**. O pedido é o `external_reference`.
3. Atualiza o pedido e responde **200** (sem corpo). Mercado Pago fora do ar na consulta → 502 `PAGAMENTO_FALHOU`; servidor sem configuração → 503 `PAGAMENTO_INDISPONIVEL`. Nos dois casos o Mercado Pago tenta de novo mais tarde.

| Pagamento no Mercado Pago | Pedido |
|---|---|
| `approved` | `PAGO`: baixa o estoque de todas as peças e tira da sacola as quantidades compradas (o que a cliente pôs depois do checkout fica), tudo na mesma transação. Falta de estoque, valor diferente ou conta excluída pela cliente (#31) → `PAGO_COM_PENDENCIA`, sem baixar nada |
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
  "dataEntrega": null,
  "prazoTroca": null,
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

`urlPagamento` só vem enquanto dá para pagar (`AGUARDANDO_PAGAMENTO` ou `PAGAMENTO_RECUSADO`); nos outros status é `null`. `precoUnitario` é o preço do momento da compra. `codigoRastreio` fica `null` até a Roseane gerar a etiqueta (#25); depois traz o código da transportadora ou, enquanto ela não devolve, o do Melhor Envio (os dois rastreáveis no [Melhor Rastreio](https://www.melhorrastreio.com.br)). O link da etiqueta não vem aqui: é só do painel. `dataEntrega` (o dia em que a peça chegou) e `prazoTroca` (o último dia para pedir troca, ver "Troca e devolução") ficam `null` até o pedido ser entregue (pelo rastreio ou pela Roseane no painel).

O detalhe do pedido na Minha Conta (#26) também usa esta rota: mostra itens, endereço, frete, status e, quando a etiqueta (#25) preencher, o `codigoRastreio` com o link "Acompanhar a entrega" (um `urlRastreio` no pedido, se a #25 acrescentar; senão o Melhor Rastreio com o código). Com `urlPagamento`, a tela oferece "Pagar agora".

### Listar meus pedidos
`GET /pedidos`

Exige token `CLIENTE`. Todos os pedidos da cliente do token (inclusive os que esperam pagamento e os cancelados), **do mais recente ao mais antigo** (`createdAt`; no mesmo segundo, o id maior primeiro). Cliente sem pedidos recebe `[]`. Os pedidos de outras clientes nunca aparecem. Para itens, endereço, frete e rastreio, a tela "Minha Conta" (#26) abre o `GET /pedidos/{id}`.

```json
// Response 200 — lista de PedidoResumoDTO
[
  { "id": 100, "status": "ENTREGUE", "valorTotal": 211.90, "createdAt": "2026-08-20T14:30:00", "prazoTroca": "2026-08-29" },
  { "id": 98, "status": "ENVIADO", "valorTotal": 189.00, "createdAt": "2026-08-12T10:15:00", "prazoTroca": null }
]
```

`prazoTroca` (#27) é o mesmo do `GET /pedidos/{id}`: a tela de troca lista os pedidos entregues que ainda estão no prazo.

`status` é um dos valores de "Status do pedido"; a loja mostra o nome legível ("Aguardando pagamento", "Pago", "Enviado"...).

---

## Troca e devolução (RF18, RNF08) — requer autenticação CLIENTE

A cliente pede a troca (ou devolução) de **uma peça** de um pedido dela: o produto no tamanho comprado. Quem paga o frete de volta (`freteConta`) é calculado no servidor a partir do motivo e nunca vem da requisição (RNF08): `DEFEITO` → `ROSIE`; `NAO_SERVIU` → `CLIENTE` (a política do RF07). O motivo vale para troca e para devolução do dinheiro: qual das duas a cliente quer, ela conta na `observacao` e combina com a Roseane.

**Prazo (decisão #27): 7 dias corridos depois da entrega**, contando o dia da entrega como dia 0 e valendo o sétimo dia inteiro (entregue em 20/08, pode pedir até 27/08). É o direito de arrependimento do CDC (art. 49) para compra fora da loja física. O dia da entrega é o que a transportadora informa no rastreio (ver "Entrega pelo rastreio") ou o que a Roseane marca no painel (ver "Marcar como entregue"), e "hoje" é sempre o dia em São Paulo. O prazo é configurável por `TROCA_PRAZO_DIAS` (padrão 7; maior se a Roseane quiser dar mais tempo, nunca menor que 7). Para defeito, o CDC dá mais tempo para reclamar (art. 26: 30 ou 90 dias, conforme o produto): fora do prazo da loja, a cliente fala com a Roseane pelo WhatsApp.

### Pedir troca
`POST /trocas`

```json
// Request — TrocaRequestDTO
{
  "pedidoId": 100,
  "produtoId": 10,
  "tamanho": "M",            // opcional se o produto veio num tamanho só no pedido
  "quantidade": 1,           // opcional: sem ela, todas as unidades compradas da peça
  "motivo": "DEFEITO",       // ou "NAO_SERVIU"
  "observacao": "A costura da manga abriu na primeira lavagem" // opcional, até 1000 caracteres
}
// Response 201 — TrocaDTO
{
  "id": 1,
  "pedidoId": 100,
  "produtoId": 10,
  "nome": "Conjunto Serpente",
  "imagemPrincipal": "https://res.cloudinary.com/...",
  "tamanho": "M",
  "quantidade": 1,
  "motivo": "DEFEITO",
  "freteConta": "ROSIE",
  "status": "SOLICITADA",
  "observacao": "A costura da manga abriu na primeira lavagem",
  "resposta": null,
  "etiquetaVolta": { "situacao": "SEM_ETIQUETA", "urlEtiqueta": null, "codigoRastreio": null, "geradaEm": null },
  "enderecoDevolucao": null,
  "createdAt": "2026-08-22T10:00:00",
  "updatedAt": "2026-08-22T10:00:00"
}
```

| Situação | Resposta |
|---|---|
| Pedido que não existe ou de outra cliente | 404 `RECURSO_NAO_ENCONTRADO` |
| Pedido que não está `ENTREGUE` | 409 `PEDIDO_NAO_ENTREGUE` |
| Passou o prazo | 409 `TROCA_FORA_DO_PRAZO` ("O prazo para pedir troca deste pedido terminou em 27/08/2026") |
| Produto/tamanho que não está no pedido, produto em dois tamanhos sem `tamanho`, `quantidade` acima da comprada, motivo ausente ou fora de `DEFEITO`/`NAO_SERVIU` | 400 `DADOS_INVALIDOS` |
| Já existe troca dessa peça (produto + tamanho) no pedido que não foi recusada | 409 `TROCA_JA_SOLICITADA` |

**Uma troca por peça:** cada produto + tamanho do pedido tem no máximo uma troca `SOLICITADA`, `APROVADA` ou `CONCLUIDA`; só uma `RECUSADA` libera pedir de novo (dentro do prazo). Peças diferentes do mesmo pedido têm cada uma a sua. A linha do pedido fica travada durante o POST, então dois cliques seguidos criam uma troca só (o segundo recebe 409 `TROCA_JA_SOLICITADA`).

**Estoque e dinheiro ficam com a Roseane:** a troca não mexe no estoque nem estorna o pagamento. Quando a peça volta, a Roseane ajusta o estoque na Gestão de Estoque e, se for devolução, devolve o dinheiro pelo Mercado Pago. Como a peça volta está em "Devolução da peça".

### Minhas trocas
`GET /trocas`

Lista de `TrocaDTO` da cliente do token, **da mais recente à mais antiga**. Sem trocas: `[]`.

### Status da troca

| Status | Quando |
|---|---|
| `SOLICITADA` | A cliente pediu; a Roseane ainda não respondeu |
| `APROVADA` | A Roseane aceitou: a cliente manda a peça de volta (frete de quem diz `freteConta`) |
| `RECUSADA` | A Roseane não aceitou (o motivo vai em `resposta`). Final; a cliente pode pedir de novo dentro do prazo |
| `CONCLUIDA` | A peça voltou e a troca (ou a devolução do dinheiro) foi resolvida. Final |

Caminhos possíveis: `SOLICITADA` → `APROVADA` ou `RECUSADA`; `APROVADA` → `CONCLUIDA`. `resposta` é o recado da Roseane que a cliente lê na Minha Conta.

### Devolução da peça (logística reversa, #63)

Quem paga a volta segue o RNF08 (`freteConta`), e cada caso tem um jeito de devolver:

- **`ROSIE` (defeito): etiqueta de volta paga pela loja.** Depois de aprovar, a Roseane gera a etiqueta no painel ("Gerar etiqueta de volta"), comprada com o saldo do Melhor Envio. A cliente só imprime, cola e posta, sem pagar nada. `etiquetaVolta` mostra o andamento.
- **`CLIENTE` (não serviu): a cliente manda por conta própria.** **Decisão (#63): a loja não compra etiqueta nem cobra a cliente pelo site.** Na troca aprovada, `enderecoDevolucao` traz o endereço da loja (o mesmo remetente da etiqueta de ida, `ROSIE_REMETENTE_*` e `ROSIE_CEP_ORIGEM`), e a cliente manda a peça pelos Correios ou pela transportadora que preferir, pagando lá, e passa o rastreio para a Roseane. Cobrar a etiqueta pelo site (link de pagamento, desconto no estorno) fica para uma issue própria, se a Roseane quiser.

```json
// etiquetaVolta — EtiquetaVoltaDTO (sempre presente)
{
  "situacao": "GERADA",        // SEM_ETIQUETA | GERADA | POR_CONTA_DA_CLIENTE
  "urlEtiqueta": "https://melhorenvio.com.br/...", // link público para imprimir (null até gerar)
  "codigoRastreio": "AA123456789BR",               // pode vir null logo depois de gerar
  "geradaEm": "2026-08-23T15:00:00"
}
// enderecoDevolucao — EnderecoDevolucaoDTO, só com status APROVADA e freteConta CLIENTE (senão null);
// também null se o endereço da loja não estiver configurado
{
  "nome": "Rosie Store", "logradouro": "Avenida Paulista", "numero": "1000", "complemento": null,
  "bairro": "Bela Vista", "cidade": "São Paulo", "uf": "SP", "cep": "01310-100"
}
```

Uma falha ao gerar a etiqueta é assunto do painel: para a cliente, a troca continua `SEM_ETIQUETA` até a Roseane tentar de novo. O rastreio da volta e a conclusão da troca continuam com a Roseane: quando a peça chega, ela muda a troca para `CONCLUIDA` no painel.

---

## Painel admin — Produtos (RF14–RF16) — requer autenticação ADMIN

Todas as rotas exigem `Authorization: Bearer {token}` de `ADMIN` (401 `NAO_AUTENTICADO` sem token, 403 `ACESSO_NEGADO` com token de cliente). Diferente do catálogo, o painel enxerga os produtos inativos. Id de produto que não existe responde 404 `RECURSO_NAO_ENCONTRADO`.

### Listar produtos do painel
`GET /admin/produtos`

Todos os produtos, ativos e inativos (arquivados), do mais recente para o mais antigo. A tela "Lista de Produtos" esconde os arquivados até a Roseane marcar "Mostrar arquivados" (#87): o filtro é só no painel, a resposta continua trazendo todos. Sem paginação (o catálogo é pequeno). `estoqueTotal` é a soma das quantidades de todos os tamanhos; `estoque` traz cada tamanho cadastrado, na ordem P, M, G, GG (tamanho que o produto não tem não aparece).

Serve às duas telas do painel: "Lista de Produtos" usa `estoqueTotal` e "Gestão de Estoque" usa `estoque`.

**Decisão (#16): sem `GET /admin/estoque` próprio.** A tela de estoque precisa dos mesmos produtos (inclusive os inativos) e da mesma ordem da lista; o `estoque` por tamanho já é carregado para calcular `estoqueTotal`, então acrescentar o campo aqui não custa consulta a mais e evita um segundo endpoint com o mesmo recorte.

```json
// Response 200 — lista de ProdutoAdminResumoDTO
[
  {
    "id": 10,
    "nome": "Conjunto Serpente",
    "preco": 189.00,
    "categoria": { "id": 1, "nome": "Vestido", "slug": "vestido" },
    "estoqueTotal": 12,
    "estoque": [ { "tamanho": "P", "quantidade": 3 }, { "tamanho": "M", "quantidade": 5 }, { "tamanho": "G", "quantidade": 4 } ],
    "ativo": true,
    "destaque": false
  }
]
```

### Detalhe de um produto no painel
`GET /admin/produtos/{id}`

Mesmo formato do `GET /produtos/{id}`, mais `ativo`, `destaque` e `pacote` (peso e medidas da peça embalada, para o frete; só no painel, a loja não recebe). `pacote` vem sempre, com `null` nos campos não preenchidos. Também responde para produto inativo. Diferente da loja, `imagens` traz cada foto com `id`, `ordem` e `enquadramento` (`ProdutoImagemDTO`), para o painel remover e reordenar; a de `ordem` 0 é a principal. A `url` é a mesma da loja, já em 4:5.

```json
// Response 200 — ProdutoAdminDTO
{
  "id": 10,
  "nome": "Conjunto Serpente",
  "descricao": "...",
  "composicao": "...",
  "preco": 189.00,
  "categoria": { "id": 1, "nome": "Vestido", "slug": "vestido" },
  "imagens": [ { "id": 3, "url": "https://res.cloudinary.com/<cloud>/image/upload/c_fill,ar_4:5,g_auto/c_limit,w_1000/f_auto,q_auto/v1/rosie/produtos/abc123.jpg", "ordem": 0, "enquadramento": "AUTOMATICO" } ],
  "estoque": [ { "tamanho": "P", "quantidade": 3 }, { "tamanho": "M", "quantidade": 5 } ],
  "ativo": true,
  "destaque": false,
  "pacote": { "pesoKg": 0.450, "alturaCm": 6, "larguraCm": 25, "comprimentoCm": null }
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
  "estoque": [ { "tamanho": "P", "quantidade": 3 }, { "tamanho": "M", "quantidade": 5 } ],
  "pacote": { "pesoKg": 0.45, "alturaCm": 6, "larguraCm": 25, "comprimentoCm": 35 }
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
| `pacote` | Opcional (#56). Peso e medidas de **uma peça já embalada**, usados na cotação do frete e na etiqueta: `pesoKg` (maior que zero, até 30 kg, até 3 casas decimais), `alturaCm`, `larguraCm` e `comprimentoCm` (inteiros de 1 a 100). Cada um é opcional: `null` usa o pacote padrão do frete naquele campo (ver "Calcular frete"). Produtos que já existiam ficam com os quatro `null`. No PUT, `pacote` ausente (ou `null`) mantém os quatro valores atuais; presente substitui os quatro de uma vez (campo ausente ou `null` dentro dele volta para o padrão) |
| `estoque` | Opcional, **só no POST** (estoque inicial). Só os tamanhos que o produto tem: cada `tamanho` é `P`, `M`, `G` ou `GG`, sem repetir, e `quantidade` é inteiro `>= 0` (`0` = esgotado). Tamanho fora da lista não é vendido. Depois do cadastro, o estoque muda só por `PUT /admin/produtos/{id}/estoque` |

Mensagens de 400 em uso: "Informe o nome do produto", "Informe o preço", "O preço deve ser maior que zero", "Selecione a categoria", "Categoria não encontrada", "Tamanho inválido: use P, M, G ou GG", "Tamanho P repetido no estoque", "A quantidade não pode ser negativa", "O peso deve ser maior que zero", "O peso pode ser de até 30 kg", "Peso inválido: use até 3 casas decimais", "A altura da embalagem deve ser de pelo menos 1 cm", "A altura da embalagem pode ser de até 100 cm" (idem para largura e comprimento).

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
  "ativo": true,
  "pacote": { "pesoKg": 0.45, "alturaCm": 6, "larguraCm": 25, "comprimentoCm": null }
}
// Response 200 — ProdutoAdminDTO atualizado (com o estoque atual, que não muda)
```

Mesmos campos e mesmas validações do POST, **menos o `estoque`**. `destaque`, `ativo`, `descricao`, `composicao` e `pacote` ausentes (ou `null`) mantêm o valor atual; `descricao` e `composicao` vazias limpam o campo. O `pacote` vai inteiro: o formulário do painel sempre manda os quatro campos, e para voltar um deles ao padrão basta mandá-lo `null` (o Reativar da lista não manda `pacote` e não mexe nele).

**Decisão (#47): a edição não mexe no estoque, e um `estoque` no corpo é ignorado** (não dá 400). O estoque muda só por `PUT /admin/produtos/{id}/estoque` (tela "Gestão de Estoque"). Antes, o PUT substituía o estoque inteiro, e um formulário de edição aberto antes de uma mudança de estoque (pela tela de estoque ou, mais adiante, por uma venda) desfazia essa mudança ao salvar. Ignorar em vez de rejeitar é o que o back já faz com qualquer campo que o DTO não tem, e não quebra um cliente antigo que ainda mande o campo; o que importa é que ele não tem efeito.

**Reativar:** um produto arquivado volta para a loja com `PUT` e `"ativo": true` (o painel busca o produto e reenvia os outros campos). Também dá para desativar com `"ativo": false`, com o mesmo efeito do DELETE.

### Arquivar ou excluir produto
`DELETE /admin/produtos/{id}` → **204** sem corpo: **arquiva**.
`DELETE /admin/produtos/{id}?definitivo=true` → **204** sem corpo: **exclui de vez**.

```json
// Response 404 — RECURSO_NAO_ENCONTRADO (nos dois)
// Response 409 — só no definitivo, produto que já entrou em pedido ou troca
{ "erro": "Este produto já entrou em pedido ou troca e não pode ser excluído. Arquive para tirá-lo da loja: o histórico continua.", "codigo": "PRODUTO_COM_HISTORICO" }
```

**Decisão (#14): sem parâmetro, o DELETE arquiva, não apaga.** O produto fica com `ativo = false`: some de `GET /produtos`, `GET /produtos/destaques` e `GET /produtos/{id}` (404), mas continua no banco e no painel (marcado "Arquivado"), e volta com o `PUT` e `"ativo": true` (ver "Reativar"). Arquivar um produto já arquivado também responde 204. No carrinho, ele aparece como indisponível.

**Decisão (#87): a exclusão definitiva é só para produto sem histórico**, para cadastro errado, teste ou duplicado. "Histórico" é ter entrado em algum pedido (`pedido_item`, de qualquer status, inclusive cancelado ou aguardando pagamento) ou em alguma troca (`troca`): esses registros precisam continuar mostrando o produto, então o produto com histórico só pode ser arquivado (409 `PRODUTO_COM_HISTORICO`). Sem histórico, a exclusão apaga numa transação só:

- o produto, o estoque (`produto_estoque`) e as linhas das fotos (`produto_imagem`);
- o produto das sacolas onde estiver (`carrinho_item`), de qualquer cliente;
- as fotos no Cloudinary, pelo `public_id`, **depois do commit**: se o Cloudinary falhar, a exclusão continua valendo e a foto que sobrou fica registrada no log (aviso com o `public_id`), como na remoção de uma foto. Fotos antigas sem `public_id` não estão no Cloudinary e não são enviadas a ele.

Não há como desfazer. No painel, a confirmação de excluir ("Excluir de vez? Não dá para desfazer.") é diferente da de arquivar ("Tirar da loja? Dá para reativar depois."). Se um pedido for fechado com o produto no meio da exclusão, a chave estrangeira de `pedido_item` barra o `DELETE` e a resposta é o mesmo 409.

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

O nome é obrigatório, tem até 80 caracteres e é salvo sem os espaços das pontas. Dois nomes iguais sem diferenciar maiúsculas (`Bolsas` e `BOLSAS`) contam como duplicados. O `slug` (endereço da página na loja, regras em "Listar categorias" do catálogo) é gerado pela API ao criar e ao renomear; não vai no body.

### Listar categorias
`GET /admin/categorias`

Todas as categorias, ordenadas por nome, com a quantidade de produtos ligados a cada uma (ativos e inativos).

```json
// Response 200 — lista de CategoriaAdminDTO
[
  { "id": 2, "nome": "Calça", "slug": "calca", "quantidadeProdutos": 0 },
  { "id": 1, "nome": "Vestido", "slug": "vestido", "quantidadeProdutos": 4 }
]
```

### Criar categoria
`POST /admin/categorias`

```json
// Request — CategoriaRequestDTO
{ "nome": "Acessórios" }
// Response 201 — CategoriaAdminDTO
{ "id": 5, "nome": "Acessórios", "slug": "acessorios", "quantidadeProdutos": 0 }
// Response 400 — nome vazio ou com mais de 80 caracteres
{ "erro": "Informe o nome da categoria", "codigo": "DADOS_INVALIDOS" }
// Response 409 — já existe categoria com esse nome
{ "erro": "Já existe uma categoria com esse nome", "codigo": "CATEGORIA_DUPLICADA" }
```

### Renomear categoria
`PUT /admin/categorias/{id}` — mesmo body e mesmas validações do POST. Mudar só maiúsculas/minúsculas do próprio nome é permitido (e mantém o slug). O slug passa a ser o do nome novo; o anterior continua achando a categoria no `GET /categorias/{slug}`.

```json
// Response 200 — CategoriaAdminDTO ("Acessórios" renomeada; /categoria/acessorios continua abrindo)
{ "id": 5, "nome": "Bijuterias", "slug": "bijuterias", "quantidadeProdutos": 0 }
// Response 404 — RECURSO_NAO_ENCONTRADO; 409 — CATEGORIA_DUPLICADA
```

### Remover categoria
`DELETE /admin/categorias/{id}`

Só remove categoria sem nenhum produto. Produto arquivado também bloqueia, porque continua ligado à categoria (o `DELETE /admin/produtos/{id}` sem parâmetro só arquiva): para liberar a categoria, mude os produtos de categoria ou, se nunca entraram em pedido nem troca, exclua de vez (`?definitivo=true`). A tabela de medidas não bloqueia: se a categoria vazia tiver uma, ela é apagada junto.

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

## Painel admin — Banners da Home (#89) — requer autenticação ADMIN

A Roseane troca o banner da Home sozinha (RF01, RF15). Fora do escopo: banners em outras páginas.

### Listar banners
`GET /admin/banners`

Todos os banners (ativos ou não, de qualquer período), na ordem do carrossel. Além do que a Home recebe (`BannerDTO`), cada um traz `ativo`, `inicio`, `fim`, `ordem` (0, 1, 2... sem buracos), `situacao` e as imagens inteiras, sem corte, para a prévia do painel (`previaDesktop`; `previaCelular` é `null` sem imagem de celular).

`situacao`, calculada na hora pelo relógio da loja (São Paulo): `DESATIVADO` (`ativo: false`), `AGENDADO` (o início ainda não chegou), `ENCERRADO` (o fim já passou) ou `NO_AR`.

```json
// Response 200 — lista de BannerAdminDTO
[
  {
    "id": 3,
    "titulo": "Coleção de verão",
    "texto": "Peças leves para os dias quentes",
    "textoBotao": "Ver coleção",
    "link": "/categoria/vestido",
    "textoAlternativo": "Modelo com vestido florido de linho",
    "ativo": true,
    "inicio": "2026-11-01T00:00:00",
    "fim": null,
    "ordem": 0,
    "situacao": "AGENDADO",
    "imagemDesktop": { "url": "https://...", "srcset": "https://... 768w, ..." },
    "imagemCelular": { "url": "https://...", "srcset": "https://... 480w, ..." },
    "previaDesktop": "https://res.cloudinary.com/<cloud>/image/upload/c_limit,w_1600/f_auto,q_auto/v1/rosie/banners/abc.jpg",
    "previaCelular": "https://res.cloudinary.com/<cloud>/image/upload/c_limit,w_1600/f_auto,q_auto/v1/rosie/banners/def.jpg"
  }
]
```

### Criar banner
`POST /admin/banners` — multipart/form-data com as partes:

| Parte | Obrigatória | Conteúdo |
|---|---|---|
| `dados` | Sim | JSON (`Content-Type: application/json`), ver abaixo |
| `imagemDesktop` | Sim | Imagem para computador. Referência: 1920 x 640 px (3:1) |
| `imagemCelular` | Não | Imagem para celular. Referência: 1080 x 1350 px (4:5). Sem ela, o celular recebe a de desktop com corte central |

```json
// Parte "dados" — BannerRequestDTO
{
  "titulo": "Coleção de verão",
  "texto": "Peças leves para os dias quentes",
  "textoBotao": "Ver coleção",
  "link": "/categoria/vestido",
  "textoAlternativo": "Modelo com vestido florido de linho",
  "ativo": true,
  "inicio": "2026-11-01T00:00",
  "fim": "2026-12-31T23:59"
}
// Response 201 — BannerAdminDTO, sempre no fim do carrossel
// Response 400
{ "erro": "Descreva a imagem do banner (texto alternativo)", "codigo": "DADOS_INVALIDOS" }
{ "erro": "Envie a imagem para computador no campo imagemDesktop", "codigo": "DADOS_INVALIDOS" }
{ "erro": "Envie o campo dados", "codigo": "DADOS_INVALIDOS" }
{ "erro": "O link do banner precisa ser uma página da loja, como /categoria/vestido, /produtos/12 ou /busca?q=linho", "codigo": "DADOS_INVALIDOS" }
{ "erro": "Informe o link do botão, ou deixe o texto do botão vazio", "codigo": "DADOS_INVALIDOS" }
{ "erro": "O fim da exibição precisa ser depois do início", "codigo": "DADOS_INVALIDOS" }
{ "erro": "Formato não aceito: envie a foto em JPG, PNG ou WEBP", "codigo": "FORMATO_NAO_SUPORTADO" }
// Response 503 (sem CLOUDINARY_URL no servidor, ou Cloudinary fora do ar)
{ "erro": "O envio de fotos não está configurado no servidor (falta CLOUDINARY_URL)", "codigo": "ARMAZENAMENTO_INDISPONIVEL" }
```

| Campo | Regra |
|---|---|
| `textoAlternativo` | Obrigatório, até 200 caracteres: o `alt` da imagem (acessibilidade) |
| `titulo`, `texto`, `textoBotao` | Opcionais, até 80, 200 e 30 caracteres. Vazios (ou só espaços) viram `null`. `textoBotao` exige `link`; na Home, sem botão, a imagem inteira leva ao link |
| `link` | Opcional, até 300 caracteres. **Só páginas da própria loja** (o banner nunca leva para fora sem aviso): um caminho começando por uma barra (`/categoria/{slug}`, `/produtos/{id}`, `/busca?q=linho`, `/politica-de-privacidade`...), com acentos e espaços codificados (`%20`), ou o endereço completo da loja (`ROSIE_LOJA_URL`, com ou sem `www.`), gravado como caminho. URL de outro site, `//outro.site`, `javascript:`, `data:`, `mailto:` e afins: 400 |
| `inicio`, `fim` | Opcionais, data e hora **no horário de São Paulo**, sem fuso (`2026-11-01T00:00`). O banner aparece de `inicio` (inclusive) até `fim` (exclusive). `fim` precisa ser depois de `inicio` |
| `ativo` | Opcional (padrão `true`). `false` tira da Home sem apagar |
| Imagens | JPG, PNG ou WEBP de até 5 MB cada (a requisição inteira até 12 MB), conferidos pelo conteúdo como no upload de foto do produto. Ficam no Cloudinary na pasta `rosie/banners`; o original é guardado e o corte de cada tela sai na URL (ver "Banners da Home") |

Os dados e os arquivos são conferidos antes de qualquer envio ao Cloudinary. Se a gravação falhar depois do envio (ex.: a segunda imagem não subiu), as imagens já enviadas são apagadas de lá.

### Editar banner
`PUT /admin/banners/{id}` — mesmo multipart do POST, com as imagens **opcionais**: imagem enviada substitui a anterior; sem imagem, fica a atual. A parte `dados` vem completa (mesmas regras) e aceita `"removerImagemCelular": true` para voltar a usar a de desktop no celular (ignorado quando vem `imagemCelular`). Response 200 com o `BannerAdminDTO`; 404 `RECURSO_NAO_ENCONTRADO`.

As imagens substituídas ou removidas saem do Cloudinary **depois do commit**: se a edição for desfeita, o banner continua com as de antes. Falha do Cloudinary nessa hora não desfaz nada (o arquivo órfão fica registrado no log).

### Ativar ou desativar
`PUT /admin/banners/{id}/ativo`

```json
// Request
{ "ativo": false }
// Response 200 — BannerAdminDTO com "situacao": "DESATIVADO"
```

### Reordenar banners
`PUT /admin/banners/ordem`

```json
// Request: os ids de TODOS os banners, na nova ordem; o primeiro abre o carrossel
{ "banners": [5, 3, 4] }
// Response 200 — lista de BannerAdminDTO na nova ordem (ordem 0, 1, 2)
// Response 400
{ "erro": "Envie todos os banners, cada um uma vez", "codigo": "DADOS_INVALIDOS" }
```

Lista vazia responde 400 "Informe a ordem dos banners".

### Remover banner
`DELETE /admin/banners/{id}` → **204** sem corpo.

Apaga o banner (não há histórico a preservar), renumera os outros e tira as imagens do Cloudinary depois do commit (falha lá só vai para o log). Banner que não existe: 404 `RECURSO_NAO_ENCONTRADO`.

---

## Painel admin — Pedidos, etiqueta e entrega (RF12) — requer autenticação ADMIN

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

Os campos do `GET /pedidos/{id}` da cliente (menos `urlPagamento` e `prazoTroca`), mais quem comprou, o pagamento e a etiqueta.

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
  "dataEntrega": null,
  "origemEntrega": null,
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
| `dataEntrega` | Dia em que a peça chegou, informado pelo rastreio (#64) ou marcado pela Roseane (#27); `null` antes |
| `origemEntrega` | Quem confirmou a entrega: `RASTREIO` (a rotina do rastreio, #64) ou `MANUAL` (o botão "Marcar como entregue", #27; também os pedidos entregues antes da #64). `null` antes da entrega. O painel mostra "Entrega confirmada pelo rastreio" ou "Entrega marcada à mão" |

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

1. `POST /api/v2/me/cart`: põe o envio no carrinho, com o serviço escolhido no checkout (`pedido.servicoFreteId`), o remetente (`ROSIE_REMETENTE_*` e `ROSIE_CEP_ORIGEM`), o destinatário (nome, telefone e e-mail da cliente e o endereço do pedido), a declaração de conteúdo (uma linha por peça, "Conjunto Serpente (M)", com quantidade e preço pago; `non_commercial: true`, sem nota fiscal), o valor declarado (soma das peças, sem o frete) e a tag "Pedido {id}" (para achar o envio no painel do Melhor Envio). **Volume:** um pacote só (Correios não aceita mais de um por envio), com as peças empilhadas. O pacote de cada peça é o mesmo da cotação (peso e medidas do produto, com o padrão `rosie.frete.peca.*` nos campos vazios, #56): alturas e pesos somados (vezes a quantidade), largura e comprimento da maior peça. Ex.: 2 vestidos sem medidas (20 × 5 × 30 cm, 0,3 kg) + 1 calça de 25 × 8 × 35 cm e 1,2 kg → 25 × 18 × 35 cm, 1,8 kg. Usa as medidas atuais do produto na hora de gerar a etiqueta, não as da compra. O `id` devolvido vira `envioId`.
2. `POST /api/v2/me/shipment/checkout`: compra com o saldo da conta.
3. `POST /api/v2/me/shipment/generate`: gera a etiqueta.
4. `POST /api/v2/me/shipment/print` (`mode: private`): link de impressão, que vira `urlEtiqueta`.
5. `POST /api/v2/me/shipment/tracking`: código de rastreio (`tracking`, ou `melhorenvio_tracking` enquanto o da transportadora não sai).

**Idempotente, sem comprar duas vezes.** Com `urlEtiqueta` e `codigoRastreio` já no pedido, responde 200 com o pedido como está, sem chamar o Melhor Envio. Se algo falhou no meio (saldo insuficiente, Melhor Envio fora do ar), o pedido continua `PAGO` com o `envioId` gravado, e o próximo clique **retoma de onde parou**: consulta a situação do envio no Melhor Envio (`pending` → compra e gera; `released` → só gera; `generated`/`posted`/`delivered` → só busca link e rastreio), então uma compra que deu certo lá, mas cuja resposta se perdeu, não é paga de novo. Envio `canceled` ou `expired` lá (cancelado pela Roseane no painel do Melhor Envio) → 409 `ETIQUETA_CANCELADA`, e o pedido esquece o envio: o clique seguinte compra uma etiqueta nova. Etiqueta pronta mas sem rastreio ainda (o Melhor Envio às vezes demora a devolver): o pedido fica `ENVIADO` com `codigoRastreio: null`, e repetir o POST só consulta o rastreio. Dois cliques ao mesmo tempo: o segundo espera o primeiro (até 90 s; passou disso, 409 `ETIQUETA_EM_ANDAMENTO`) e devolve a mesma etiqueta. A trava fica na memória da API (o Railway roda uma instância só).

**Pode gerar:** só `PAGO`. `PAGO_COM_PENDENCIA` → 409 `PEDIDO_COM_PENDENCIA` (o estoque não baixou; a Roseane resolve antes). `AGUARDANDO_PAGAMENTO`, `PAGAMENTO_RECUSADO` e `CANCELADO` → 409 `PEDIDO_NAO_PAGO`. `ENVIADO`/`ENTREGUE` só devolvem o pedido (ou buscam o rastreio que faltava).

**Erros do Melhor Envio:** recusa com mensagem (4xx, ex.: saldo insuficiente, campo inválido) → 502 `ETIQUETA_FALHOU` com "O Melhor Envio recusou a etiqueta: " + a primeira mensagem de lá; queda, 5xx, timeout (`ETIQUETA_TIMEOUT`, 20 s por chamada) ou resposta fora do formato → 502 `ETIQUETA_FALHOU` genérico. Servidor sem `MELHOR_ENVIO_TOKEN`, `ROSIE_CEP_ORIGEM` ou os dados obrigatórios do remetente, ou token recusado (401/403) → 503 `ETIQUETA_INDISPONIVEL`. O token precisa dos escopos `cart-read`, `cart-write`, `shipping-checkout`, `shipping-generate`, `shipping-print` e `shipping-tracking`.

**Modo de desenvolvimento:** com `FRETE_SIMULADO=true` a etiqueta também é de mentira: nada é cobrado, o rastreio sai no formato `SIM00000000BR` e o link abre `/etiqueta-simulada.html` da loja. Nunca ligar em produção.

### Marcar como entregue
`POST /admin/pedidos/{id}/entregue`

A Roseane confirma que a peça chegou (#27), olhando o rastreio: o pedido passa de `ENVIADO` para `ENTREGUE` e grava `dataEntrega` (com `origemEntrega: "MANUAL"`), de onde conta o prazo de troca. Desde a #64 o sistema confirma sozinho pelo rastreio (ver "Entrega pelo rastreio"); o botão continua como plano B (transportadora sem rastreio, atraso na atualização).

```json
// Request (opcional): o dia da entrega, se não foi hoje. Sem corpo, ou sem a data, vale hoje (em São Paulo)
{ "dataEntrega": "2026-10-17" }
// Response 200 — PedidoAdminDTO com "status": "ENTREGUE" e "dataEntrega": "2026-10-17"
```

| Situação | Resposta |
|---|---|
| Pedido `ENVIADO` | 200, vira `ENTREGUE` |
| Pedido já `ENTREGUE` (à mão ou pelo rastreio) | 200 com o pedido como está (a data e a origem não mudam) |
| Qualquer outro status | 409 `PEDIDO_NAO_ENVIADO` |
| `dataEntrega` no futuro, ou antes do dia em que a etiqueta foi gerada | 400 `DADOS_INVALIDOS` |

### Entrega pelo rastreio (#64)
Sem rota: uma rotina agendada da API faz o mesmo que o botão "Marcar como entregue", sozinha.

A cada `ROSIE_RASTREIO_INTERVALO` (padrão 6 h; a primeira rodada sai 1 min depois de a API subir), a API consulta no Melhor Envio (`POST /api/v2/me/shipment/tracking`, o mesmo passo 5 da etiqueta) os pedidos `ENVIADO` que têm `envioId` e `codigoRastreio`. Quando o envio vem `delivered`, o pedido passa a `ENTREGUE` com `origemEntrega: "RASTREIO"` e `dataEntrega` igual ao dia de `delivered_at` em São Paulo (o Melhor Envio manda `"2026-10-17 14:46:00"`, no horário de Brasília; com fuso, é convertido). Sem `delivered_at`, vale o dia da consulta (e fica registrado no log). Os outros estados do rastreio (postado, em trânsito, devolvido, extraviado) não mudam nada.

| Regra | Como fica |
|---|---|
| Mesma regra do botão | O mesmo serviço do `POST /admin/pedidos/{id}/entregue`: só `ENVIADO` avança; a data não pode ser no futuro nem antes do dia da etiqueta (fora disso o pedido fica `ENVIADO` e o erro vai para o log) |
| Pedido já `ENTREGUE` | Nem é consultado; a data e a origem nunca mudam, seja a entrega marcada à mão ou por uma rodada anterior |
| Clique e rodada ao mesmo tempo | A linha do pedido fica travada até o fim da confirmação: quem chega depois encontra o pedido entregue e não muda nada. Duas rodadas ao mesmo tempo (duas instâncias da API) também não se atropelam |
| Erro do Melhor Envio num pedido | Vai para o log e a rodada segue com os próximos; o pedido é consultado de novo na rodada seguinte. Token recusado (401/403) encerra a rodada |
| Limites da API | No máximo `ROSIE_RASTREIO_LOTE` pedidos por rodada (padrão 50), um de cada vez, com `ROSIE_RASTREIO_PAUSA` entre as consultas (padrão 1 s). A fila começa pelos consultados há mais tempo, então os que ficaram de fora entram na rodada seguinte |
| Sem `MELHOR_ENVIO_TOKEN` (ou sem a configuração da etiqueta) | Não roda e avisa uma vez no log; a entrega fica para o botão |
| `ROSIE_RASTREIO_ATIVO=false` | Desliga a rotina (os testes do back rodam assim) |

**Decisão (#64): rotina agendada, não webhook.** O webhook do Melhor Envio é dos aplicativos cadastrados com OAuth (exige app, URL pública e validação de assinatura), e a loja usa um token pessoal. Algumas consultas por dia bastam para abrir o prazo de troca sem a Roseane precisar clicar.

**Modo de desenvolvimento:** com `FRETE_SIMULADO=true`, a etiqueta simulada conta como entregue no dia em que foi gerada (enquanto a API não reinicia), e a rodada seguinte marca o pedido `ENTREGUE` pelo rastreio. Para testar o botão em vez da rotina, `ROSIE_RASTREIO_ATIVO=false`.

---

## Painel admin — Trocas (#27) — requer autenticação ADMIN

Mesmas regras de token do painel. Troca que não existe: 404 `RECURSO_NAO_ENCONTRADO`.

### Listar trocas
`GET /admin/trocas`

Todas as trocas de todas as clientes, da mais recente para a mais antiga, sem paginação. Cada uma é o `TrocaDTO` da cliente mais quem pediu:

```json
// Response 200 — lista de TrocaAdminDTO
[
  {
    "id": 1,
    "pedidoId": 100,
    "cliente": { "id": 7, "nome": "Maria Silva", "email": "maria@exemplo.com", "telefone": "11988887777" },
    "produtoId": 10, "nome": "Conjunto Serpente", "imagemPrincipal": "https://res.cloudinary.com/...",
    "tamanho": "M", "quantidade": 1,
    "motivo": "DEFEITO", "freteConta": "ROSIE", "status": "SOLICITADA",
    "observacao": "A costura da manga abriu na primeira lavagem", "resposta": null,
    "etiquetaVolta": {
      "situacao": "SEM_ETIQUETA", "envioId": null, "urlEtiqueta": null, "codigoRastreio": null,
      "geradaEm": null, "erro": null, "valorEstimado": 22.90
    },
    "createdAt": "2026-08-22T10:00:00", "updatedAt": "2026-08-22T10:00:00"
  }
]
```

`etiquetaVolta` no painel (`EtiquetaVoltaAdminDTO`) é o da cliente mais: `situacao` `FALHOU` quando a última tentativa de gerar falhou, com o motivo em `erro` (some quando a etiqueta sai); `envioId`, o id do envio no Melhor Envio (gravado assim que entra no carrinho); e `valorEstimado`, o frete que a cliente pagou na ida (mesmo serviço, mesmos CEPs), para a Roseane saber quanto vai sair do saldo antes de comprar. A `TrocaAdminDTO` não traz `enderecoDevolucao`.

### Mudar o status da troca
`PUT /admin/trocas/{id}/status`

```json
// Request — TrocaStatusRequestDTO
{ "status": "APROVADA", "resposta": "Pode mandar na mesma embalagem" } // resposta opcional, até 1000 caracteres
// Response 200 — TrocaAdminDTO atualizado
```

Só pelos caminhos de "Status da troca" (`SOLICITADA` → `APROVADA`/`RECUSADA`; `APROVADA` → `CONCLUIDA`); fora deles, 409 `STATUS_TROCA_INVALIDO`. Mandar o status atual de novo só troca o recado. Sem `resposta` (ou com ela em branco), fica o recado anterior. Status ausente ou desconhecido: 400 `DADOS_INVALIDOS`.

**E-mail à cliente (#65).** Quando o status **muda** (para `APROVADA`, `RECUSADA` ou `CONCLUIDA`), a cliente recebe um e-mail no endereço da conta com o número do pedido, a peça, o tamanho, a quantidade, o novo status em linguagem simples e a `resposta` mandada **nesta** requisição, quando houver (o recado de uma mudança anterior não se repete). Na aprovação, o e-mail diz quem paga o frete de devolução (`freteConta`) e o próximo passo (#63): no defeito, que a Rosie vai gerar a etiqueta de devolução e que outro e-mail chega quando ela estiver pronta; no "não serviu", o endereço da loja para mandar a peça (ou, sem ele configurado, que a Rosie vai passar o endereço); na recusa, traz a `resposta` como motivo e os contatos da loja (WhatsApp, se `ROSIE_WHATSAPP_NUMERO` estiver definido, e Instagram). Mandar o status atual de novo **não** reenvia o e-mail. Conta excluída (LGPD, #31) não recebe e-mail.

O e-mail é um efeito colateral que não muda a resposta: sai **depois do commit**, numa fila própria da API, e a resposta não espera o envio. Se o envio falhar (Resend fora do ar, domínio não verificado, chave inválida), o status continua mudado, a resposta é a mesma 200 e o erro só vai para o log da API; não há nova tentativa. Sem `RESEND_API_KEY` (dev local, CI), o e-mail não sai e o log registra só o destinatário mascarado e o assunto.

### Gerar etiqueta de volta
`POST /admin/trocas/{id}/etiqueta-volta` (sem corpo)

Compra e gera no Melhor Envio a etiqueta de volta (logística reversa, #63) de uma troca `APROVADA` com `freteConta = ROSIE` e devolve a `TrocaAdminDTO` atualizada, com `etiquetaVolta.situacao` `GERADA`, `urlEtiqueta`, `geradaEm` e `codigoRastreio`. A troca continua `APROVADA`. A cliente passa a ver a etiqueta em `GET /trocas` e recebe um e-mail com o link e como postar.

```json
// Response 200 — TrocaAdminDTO (como na lista)
// Response 409
{ "erro": "Só troca aprovada tem etiqueta de volta: esta está SOLICITADA.", "codigo": "TROCA_NAO_APROVADA" }
{ "erro": "Nesta troca o frete de volta é por conta da cliente (a peça não serviu): ela manda a peça pelo endereço da loja que aparece na Minha Conta.", "codigo": "TROCA_FRETE_DA_CLIENTE" }
{ "erro": "O envio de volta desta troca foi cancelado no Melhor Envio. Clique em \"Gerar etiqueta de volta\" de novo para comprar outra.", "codigo": "ETIQUETA_CANCELADA" }
{ "erro": "A etiqueta desta troca já está sendo gerada. Aguarde e atualize a página.", "codigo": "ETIQUETA_EM_ANDAMENTO" }
// Response 502 / 503: os mesmos ETIQUETA_FALHOU e ETIQUETA_INDISPONIVEL de "Gerar etiqueta"
```

**Decisão (#63): pelo botão da Roseane, não sozinha na aprovação.** Como na ida, comprar a etiqueta debita o saldo do Melhor Envio: a Roseane aprova (o que nunca depende do Melhor Envio), confere e gera. Assim uma falha ou indisponibilidade do Melhor Envio não impede aprovar: a troca fica `APROVADA` sem etiqueta e o painel mostra "Gerar etiqueta de volta" para tentar quando quiser.

**Mesmos passos e garantias da etiqueta de ida** (ver "Gerar etiqueta"): carrinho → compra → geração → impressão → rastreio, cada passo gravado na troca (`envio_volta_id`, `url_etiqueta_volta`, `codigo_rastreio_volta`, `etiqueta_volta_gerada_em`), retomada de onde parou, consulta antes de comprar, envio cancelado lá → 409 `ETIQUETA_CANCELADA` e um novo no próximo clique, dois cliques juntos compram uma etiqueta só (trava por troca na memória da API) e, com etiqueta e rastreio já na troca, 200 sem chamar o Melhor Envio. Etiqueta pronta sem rastreio: repetir o POST só busca o rastreio ("Buscar rastreio" no painel). Falha do Melhor Envio (502/503): o motivo fica em `etiquetaVolta.erro` com `situacao` `FALHOU`, até a próxima tentativa dar certo.

**O envio de volta**, montado no servidor a partir da troca e do pedido:

- **Reverso, com os endereços invertidos:** `options.reverse: true`, `from` = a cliente (nome, telefone e e-mail da conta e o endereço de entrega do pedido) e `to` = a loja (`ROSIE_REMETENTE_*` e `ROSIE_CEP_ORIGEM`), como o Melhor Envio monta a logística reversa no painel dele. Tag "Troca {id} (pedido {pedidoId})".
- **Serviço:** o mesmo da ida (`pedido.servicoFreteId`). Pedido sem serviço do Melhor Envio → 409 `PEDIDO_SEM_FRETE`.
- **Conteúdo e pacote:** só a peça da troca, "Vestido Aurora (M)" × `quantidade`, pelo preço pago no pedido; valor declarado = preço pago × quantidade; volume pelo mesmo pacote da cotação e da ida (#56), vezes a quantidade.
- **Link público** (`mode: public` em `/shipment/print`): quem imprime é a cliente, sem a conta da loja no Melhor Envio. Na etiqueta só vão o endereço dela e o da loja, e o link só chega a ela (`GET /trocas` com o token dela e o e-mail).

**Pendente de conferir na sandbox** (sem token até agora, testado só com o emissor falso): se o Melhor Envio exige o CPF de quem manda (`from.document`) no envio reverso. A loja não pede CPF à cliente; se exigir, a geração volta 502 `ETIQUETA_FALHOU` com a mensagem de lá, e o CPF no cadastro vira issue própria (o mesmo ponto já aberto para o `to.document` da ida).

**E-mail à cliente:** na primeira vez que a etiqueta fica pronta, depois do commit, com o link "Imprimir etiqueta de devolução", o rastreio (quando já existe) e como postar. Repetir o POST (ou só buscar o rastreio) não reenvia. Conta excluída não recebe.

**Modo de desenvolvimento:** com `FRETE_SIMULADO=true`, o link abre `/etiqueta-simulada.html?envio=…&volta=1` da loja, com o título "Etiqueta de volta simulada".

---

## Decisão: autenticação via JWT (confirmado)

**Por quê:**
- Front-end (Netlify/Cloudflare) e back-end (Railway) ficam em **domínios diferentes** — sessão tradicional usa cookies, que esbarram em bloqueios de CORS/SameSite entre domínios distintos. JWT evita isso: o token vai no header da requisição, não depende de cookie.
- **Sem estado no servidor** = menos RAM ocupada no Railway (que cobra por uso contínuo de memória) — sessão tradicional exige guardar quem está logado na memória do servidor.
- Combinação JWT + Spring Security é extremamente comum e bem documentada.

**Trade-off aceito:** JWT não tem "logout forte" fácil (sem lista negra de tokens). Solução simples adotada: token com validade curta (2 horas). Logout forçado só em dois casos, conferidos a cada requisição com uma consulta por id: conta excluída (#31) e senha trocada (#93, pela versão da credencial na claim `vc`; ver "Esqueci minha senha").

**Implementação (#13):** suporte nativo do Spring Security (`spring-boot-starter-security-oauth2-resource-server`, `NimbusJwtEncoder`/`NimbusJwtDecoder`), sem biblioteca extra. A claim `role` vira a authority `ROLE_ADMIN`/`ROLE_CLIENTE`. No front, o token fica no `sessionStorage` e o `api.js` só o envia nas chamadas do painel: as rotas públicas do catálogo não recebem `Authorization`, porque um token vencido faria a API responder 401 mesmo nelas.

**Troca de e-mail (#61):** o token identifica o usuário pelo id, não pelo e-mail, então trocar o e-mail não derruba a sessão (ver "Troca de e-mail").

**Cliente (#21):** mesmo token e mesma validade, com `role` `CLIENTE`. No front, a sessão do cliente fica no `localStorage` (`rosie.cliente.sessao`), separada da do painel (`rosie.admin.sessao`, no `sessionStorage`): o cliente continua logado entre abas e ao reabrir o navegador, até o token vencer em 2 horas. O `api.js` só manda o token de cliente nas rotas de cliente, e um 401 nelas limpa a sessão e leva a `/entrar?voltar=<página atual>`.

---

## Próximos passos

- [ ] Definir mensagens de erro específicas conforme forem surgindo casos reais
