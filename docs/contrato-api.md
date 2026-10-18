# Contrato da API

Define os endpoints REST, formato de requisição/resposta e convenções compartilhadas entre Catálogo (Breno) e Painel Admin (Matheus), baseado no modelo de dados MySQL e nos requisitos funcionais (`docs/requisitos.md`).

Migrado do Notion em 17/10/2026. A partir daqui, este arquivo é a fonte de verdade: **se um endpoint mudar, atualize este arquivo no mesmo PR.**

## Status de implementação

| Endpoint | Status | DTO de resposta |
|---|---|---|
| `GET /produtos` | Implementado | `ProdutoListaResponseDTO` |
| `GET /produtos/destaques` | Implementado (público, só ativos com `destaque = true`) | lista de `ProdutoResumoDTO` |
| `GET /produtos/{id}` | Implementado (404 com `RECURSO_NAO_ENCONTRADO`, também para produto inativo) | `ProdutoDetalheDTO` |
| `GET /categorias` | Implementado (público, ordenado por nome) | lista de `CategoriaResumoDTO` |
| `POST /admin/auth/login` | Implementado (público; 401 com `CREDENCIAIS_INVALIDAS`) | `LoginResponseDTO` |
| `GET /admin/auth/me` | Implementado (exige token `ADMIN`) | `UsuarioResumoDTO` |
| Demais endpoints | Não implementados | — |

---

## Convenções gerais

- **Base URL (dev):** `http://localhost:8080`
- **Formato:** JSON em todas as requisições e respostas
- **Autenticação:** JWT via header `Authorization: Bearer {token}` — obtido no login
- **Tamanhos:** sempre um dos valores `P`, `M`, `G`, `GG`
- **Datas:** formato ISO 8601 (`2026-08-25T14:30:00`)
- **Erros:** sempre nesse formato (`ErroResponseDTO`), com o HTTP status code correspondente (400, 401, 403, 404, 409, 500)

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
| `DADOS_INVALIDOS` | 400 | Corpo ausente, JSON malformado ou campo inválido (ex.: e-mail vazio no login); `erro` traz a mensagem do primeiro campo |
| `CREDENCIAIS_INVALIDAS` | 401 | Login com e-mail inexistente, senha errada ou usuário sem permissão (mesma resposta nos três casos) |
| `NAO_AUTENTICADO` | 401 | Rota protegida sem token, ou com token inválido, adulterado ou expirado |
| `ACESSO_NEGADO` | 403 | Token válido, mas sem a role exigida (ex.: token de cliente em `/admin/**`) |

---

## Autenticação (RF08, RF13)

### Cadastro de cliente
`POST /auth/registrar`

```json
// Request
{
  "nome": "Roseane Silva",
  "email": "roseane@email.com",
  "senha": "senha123",
  "telefone": "11987654321"
}
// Response 201
{
  "id": 1,
  "nome": "Roseane Silva",
  "email": "roseane@email.com",
  "token": "eyJhbGc..."
}
```

### Login de cliente
`POST /auth/login`

```json
// Request
{ "email": "roseane@email.com", "senha": "senha123" }
// Response 200
{ "token": "eyJhbGc...", "usuario": { "id": 1, "nome": "Roseane Silva", "role": "CLIENTE" } }
```

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

A marcação é manual: o campo `destaque` do produto (padrão `false`) é editado pelo painel admin (#14); até lá, direto no banco. Sem nenhum destaque marcado, a resposta é `[]` e a Home cai na listagem comum de `GET /produtos`.

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

Pública (sem token). Só produto ativo: inativo responde 404, igual a um id que não existe. `imagens` vem na ordem de exibição (a primeira é a principal) e `estoque` vem ordenado por tamanho (`P`, `M`, `G`, `GG`), só com os tamanhos cadastrados; `quantidade: 0` significa esgotado naquele tamanho.

```json
// Response 200 — ProdutoDetalheDTO
{
  "id": 10,
  "nome": "Conjunto Serpente",
  "descricao": "Tecido leve... Trocas aceitas em caso de defeito ou produto que não serviu.",
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

## Carrinho (RF09) — requer autenticação

### Ver carrinho
`GET /carrinho`

```json
// Response 200
{
  "itens": [
    { "id": 1, "produto": { "id": 10, "nome": "Conjunto Serpente", "preco": 189.00 }, "tamanho": "M", "quantidade": 1 }
  ],
  "subtotal": 189.00
}
```

### Adicionar item
`POST /carrinho/itens`

```json
// Request
{ "produtoId": 10, "tamanho": "M", "quantidade": 1 }
// Response 201 (retorna o carrinho atualizado, igual ao GET acima)
```

### Atualizar quantidade / Remover item
- `PUT /carrinho/itens/{itemId}` — body: `{ "quantidade": 2 }`
- `DELETE /carrinho/itens/{itemId}`

---

## Pedidos e frete (RF10–RF12) — requer autenticação

### Calcular frete
`POST /frete/calcular`

```json
// Request
{ "cep": "03925-050" }
// Response 200 (via API Melhor Envio)
{ "opcoes": [ { "transportadora": "PAC", "preco": 22.90, "prazoDias": 7 }, { "transportadora": "SEDEX", "preco": 38.50, "prazoDias": 3 } ] }
```

### Finalizar pedido
`POST /pedidos`

```json
// Request
{
  "enderecoId": 5,
  "transportadora": "PAC",
  "formaPagamento": "CARTAO"
}
// Response 201
{ "id": 100, "status": "AGUARDANDO_PAGAMENTO", "valorTotal": 211.90, "codigoRastreio": null }
```

### Listar meus pedidos
`GET /pedidos`

```json
// Response 200 (usado na tela "Minha Conta")
[
  { "id": 100, "status": "ENTREGUE", "valorTotal": 211.90, "createdAt": "2026-08-20T14:30:00" },
  { "id": 98, "status": "ENVIADO", "valorTotal": 189.00, "createdAt": "2026-08-12T10:15:00" }
]
```

### Consultar pedido
`GET /pedidos/{id}`

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

### Criar produto
`POST /admin/produtos`

```json
// Request
{
  "nome": "Conjunto Serpente",
  "descricao": "...",
  "preco": 189.00,
  "categoriaId": 1,
  "destaque": false,
  "estoque": [ { "tamanho": "P", "quantidade": 3 }, { "tamanho": "M", "quantidade": 5 } ]
}
// Response 201 (retorna o produto criado, mesmo formato do GET /produtos/{id})
```

### Editar / Remover produto
- `PUT /admin/produtos/{id}` — mesmo body do POST
- `DELETE /admin/produtos/{id}`

### Upload de imagem
`POST /admin/produtos/{id}/imagens` — multipart/form-data, campo `arquivo`

```json
// Response 201
{ "id": 3, "url": "https://.../nova-imagem.jpg", "ordem": 2 }
```

### Atualizar estoque
`PUT /admin/produtos/{id}/estoque`

```json
// Request
{ "estoque": [ { "tamanho": "P", "quantidade": 10 }, { "tamanho": "M", "quantidade": 0 } ] }
```

---

## Painel admin — Categorias (RF17)

- `GET /admin/categorias` — lista todas
- `POST /admin/categorias` — body: `{ "nome": "Acessórios" }`
- `DELETE /admin/categorias/{id}`

---

## Decisão: autenticação via JWT (confirmado)

**Por quê:**
- Front-end (Netlify/Cloudflare) e back-end (Railway) ficam em **domínios diferentes** — sessão tradicional usa cookies, que esbarram em bloqueios de CORS/SameSite entre domínios distintos. JWT evita isso: o token vai no header da requisição, não depende de cookie.
- **Sem estado no servidor** = menos RAM ocupada no Railway (que cobra por uso contínuo de memória) — sessão tradicional exige guardar quem está logado na memória do servidor.
- Combinação JWT + Spring Security é extremamente comum e bem documentada.

**Trade-off aceito:** JWT não tem "logout forte" fácil (sem lista negra de tokens). Solução simples adotada: token com validade curta (2 horas), sem controle de logout forçado por enquanto — suficiente pro escopo atual.

**Implementação (#13):** suporte nativo do Spring Security (`spring-boot-starter-security-oauth2-resource-server`, `NimbusJwtEncoder`/`NimbusJwtDecoder`), sem biblioteca extra. A claim `role` vira a authority `ROLE_ADMIN`/`ROLE_CLIENTE`. No front, o token fica no `sessionStorage` e o `api.js` só o envia nas chamadas do painel: as rotas públicas do catálogo não recebem `Authorization`, porque um token vencido faria a API responder 401 mesmo nelas.

---

## Próximos passos

- [ ] Breno e Matheus revisarem juntos e ajustarem nomes de campos se necessário
- [ ] Definir mensagens de erro específicas conforme forem surgindo casos reais
