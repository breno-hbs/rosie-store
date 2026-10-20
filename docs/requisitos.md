# Requisitos — Projeto Rosie

Documento de Requisitos Funcionais e Não Funcionais.
Versão final — pós-reunião de descoberta com a Roseane (11/08/2026).
Migrado do Notion em 17/10/2026. A partir daqui, este arquivo é a fonte de verdade.

## Requisitos funcionais (RF)

### Catálogo
- **RF01** — O sistema deve exibir a Home com produtos em destaque e categorias.
- **RF02** — O sistema deve listar produtos por categoria. Categorias iniciais: Vestido, Calça, Camiseta, Cropped/Body; outras, como Armarinho, a Roseane cria pelo painel (decisão de 19/10/2026, issue #29).
- **RF03** — O sistema deve exibir página de produto individual (fotos, descrição, tamanho, preço).
- **RF04** — O sistema deve permitir filtrar/navegar produtos por categoria.
- **RF05** — O sistema deve ser responsivo (mobile-first).
- **RF06** — Cada produto deve ter tamanho disponível dentre P, M, G, GG.
- **RF07** — Cada produto deve exibir, na própria descrição, a política de troca: "Trocas aceitas em caso de defeito ou produto que não serviu. Frete de devolução por conta da Rosie em caso de defeito; por conta do cliente nos demais casos."

### Conta e autenticação (cliente)
- **RF08** — O cliente deve criar conta (cadastro + login) para finalizar a compra — checkout como convidado não será permitido.

### Carrinho e compra
- **RF09** — O sistema deve permitir adicionar produtos ao carrinho.
- **RF10** — O sistema deve permitir finalizar compra com pagamento integrado.
- **RF11** — O sistema deve calcular frete automaticamente via API do Melhor Envio, a partir do CEP do cliente.
- **RF12** — O sistema deve gerar etiqueta de envio após a compra (via Melhor Envio).

### Painel admin
- **RF13** — O sistema deve ter autenticação/login exclusivo para a Roseane (painel admin).
- **RF14** — O painel deve permitir cadastrar, editar e remover produtos (CRUD completo).
- **RF15** — O painel deve permitir upload de fotos de produto.
- **RF16** — O painel deve permitir controle de estoque (quantidade disponível por produto/tamanho).
- **RF17** — O painel deve permitir cadastrar novas coleções/categorias sem depender dos desenvolvedores.

### Troca e devolução
- **RF18** — O sistema deve permitir ao cliente informar o motivo da troca (defeito ou não serviu) ao solicitar devolução.

## Requisitos não funcionais (RNF)

- **RNF01** — O site deve ser responsivo em dispositivos móveis (prioridade alta — tráfego majoritariamente mobile).
- **RNF02** — O acesso ao painel admin deve ser restrito e autenticado (só a Roseane acessa).
- **RNF03** — O sistema deve seguir a identidade visual definida: paleta Tinta/Papel/Pedra/Fumaça, fundo claro com acentos em preto, fonte "The Seasons" no título "ROSIE", tipografia clean no restante.
- **RNF04** — O banco de dados (MySQL) deve ter backup, já que dados de estoque e pedidos são críticos pro negócio.
- **RNF05** — A hospedagem deve manter o site no ar de forma estável (Railway + Netlify/Cloudflare).
- **RNF06** — O sistema deve estar em conformidade com a LGPD (coleta dados pessoais no cadastro/checkout).
- **RNF07** — As senhas dos clientes e da Roseane devem ser armazenadas de forma segura (hash), nunca em texto puro.
- **RNF08** — A lógica de frete de devolução deve calcular automaticamente quem arca com o custo, com base no motivo informado (Rosie paga se defeito; cliente paga se não serviu).
- **RNF09** — As fotos de produto devem seguir a proporção 4:5 (retrato), dimensão de referência 1000 x 1250px — orientação visível na tela de cadastro do painel admin.

---

**Status:** requisitos fechados após validação de todos os pontos em aberto da reunião de descoberta.
