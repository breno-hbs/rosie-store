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
- **Erros:** sempre nesse formato (`ErroResponseDTO`), com o HTTP status code correspondente (400, 401, 403, 404, 409, 500, 503)

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
| `CLOUDINARY_URL` | Não (sem ela, o upload de fotos responde 503) | Credencial do Cloudinary, no formato `cloudinary://<api_key>:<api_secret>@<cloud_name>`; ver "Upload de imagem" |

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

## Decisão: autenticação via JWT (confirmado)

**Por quê:**
- Front-end (Netlify/Cloudflare) e back-end (Railway) ficam em **domínios diferentes** — sessão tradicional usa cookies, que esbarram em bloqueios de CORS/SameSite entre domínios distintos. JWT evita isso: o token vai no header da requisição, não depende de cookie.
- **Sem estado no servidor** = menos RAM ocupada no Railway (que cobra por uso contínuo de memória) — sessão tradicional exige guardar quem está logado na memória do servidor.
- Combinação JWT + Spring Security é extremamente comum e bem documentada.

**Trade-off aceito:** JWT não tem "logout forte" fácil (sem lista negra de tokens). Solução simples adotada: token com validade curta (2 horas), sem controle de logout forçado por enquanto — suficiente pro escopo atual.

**Implementação (#13):** suporte nativo do Spring Security (`spring-boot-starter-security-oauth2-resource-server`, `NimbusJwtEncoder`/`NimbusJwtDecoder`), sem biblioteca extra. A claim `role` vira a authority `ROLE_ADMIN`/`ROLE_CLIENTE`. No front, o token fica no `sessionStorage` e o `api.js` só o envia nas chamadas do painel: as rotas públicas do catálogo não recebem `Authorization`, porque um token vencido faria a API responder 401 mesmo nelas.

---

## Próximos passos

- [ ] Definir mensagens de erro específicas conforme forem surgindo casos reais
