# Kanban — Tarefas do Projeto

Migrado do Notion em 17/10/2026. A partir daqui, este arquivo é a fonte de verdade das tarefas.

## Como usar

- Cada card é uma linha de tabela na seção do seu status: **A fazer**, **Em andamento** ou **Feito**.
- Para mudar o status, mova a linha para a seção correspondente no mesmo PR da mudança.
- Colunas: **Card**, **Feature** (Catálogo, Painel Admin, Geral, Planejamento), **Fase** (1. Descoberta, 2. Planejamento Técnico, 3. Design, 4. Desenvolvimento, 5. Testes & Ajustes, 6. Lançamento), **Prioridade** (Alta, Média, Baixa), **Responsável**.

## Em andamento

| Card | Feature | Fase | Prioridade | Responsável |
|---|---|---|---|---|

## A fazer

| Card | Feature | Fase | Prioridade | Responsável |
|---|---|---|---|---|
| Configurar Railway (back-end + MySQL) e Netlify/Cloudflare (front-end) | Planejamento | 2. Planejamento Técnico | Média | — |
| Definir origem dos destaques da Home (hero e Best Sellers): hoje vêm de GET /produtos, sem endpoint próprio | Catálogo | 4. Desenvolvimento | Média | Breno |
| Construir Página de Produto (detalhes, tamanho, galeria) | Catálogo | 4. Desenvolvimento | Média | Breno |
| Implementar responsividade mobile do catálogo | Catálogo | 4. Desenvolvimento | Média | Breno |
| Construir tela Minha Conta (pedidos, dados pessoais, endereços) + endpoint GET /pedidos | Catálogo | 4. Desenvolvimento | Média | — |
| Implementar fluxo de troca/devolução (motivo: defeito vs não serviu) com regra de frete | Catálogo | 4. Desenvolvimento | Média | Breno |
| Integrar cálculo de frete via API Melhor Envio (não Correios direto) | Catálogo | 4. Desenvolvimento | Média | Breno |
| Rodar seed de categorias em produção (Hibernate ddl-auto não popula dados iniciais) | Catálogo | 4. Desenvolvimento | Baixa | — |
| Criar autenticação/login do painel admin | Painel Admin | 4. Desenvolvimento | Média | Matheus |
| Criar CRUD de produtos (criar, editar, remover) | Painel Admin | 4. Desenvolvimento | Média | Matheus |
| Implementar upload de imagem no painel admin | Painel Admin | 4. Desenvolvimento | Média | Matheus |
| Considerar corte automático de imagem para 4:5 no upload (melhoria futura) | Painel Admin | 4. Desenvolvimento | Baixa | — |

## Feito

| Card | Feature | Fase | Prioridade | Responsável |
|---|---|---|---|---|
| Reunião com a Roseane — Terça 11/08, 19h (Teams) | Planejamento | 1. Descoberta | Alta | Breno, Matheus |
| Realizar reunião de descoberta com a Roseane — Terça 11/08, 19h (Teams) | Planejamento | 1. Descoberta | Alta | Breno, Matheus |
| Ouvir gravação da reunião e anotar respostas da Roseane | Planejamento | 1. Descoberta | Alta | Breno, Matheus |
| Definir escopo do MVP (catálogo + WhatsApp vs e-commerce completo) | Planejamento | 1. Descoberta | Alta | Breno, Matheus |
| Confirmar stack (Spring Boot + React) | Planejamento | 1. Descoberta | Alta | Breno, Matheus |
| Escolher banco de dados (MySQL) | Planejamento | 1. Descoberta | Alta | Breno, Matheus |
| Hospedagem definida: Railway + Netlify/Cloudflare Pages | Planejamento | 1. Descoberta | Alta | Breno, Matheus |
| Definir repositório GitHub | Geral | 1. Descoberta | Alta | Breno, Matheus |
| Dividir responsabilidades por feature | Geral | 1. Descoberta | Alta | Breno, Matheus |
| Cronograma visual do projeto | Planejamento | 1. Descoberta | Média | Breno, Matheus |
| Moodboard com direções visuais | Geral | 1. Descoberta | Média | Breno, Matheus |
| Wireframe navegável (Home + Produto) | Geral | 1. Descoberta | Média | Breno, Matheus |
| Traduzir respostas da Rose em requisitos funcionais/não funcionais | Planejamento | 2. Planejamento Técnico | Média | Breno |
| Registrar domínio (Registro.br) | Planejamento | 2. Planejamento Técnico | Média | Breno |
| Alinhar prazo estimado de entrega (validar 8 semanas) | Planejamento | 2. Planejamento Técnico | Média | Breno |
| Modelar banco de dados (MySQL) | Planejamento | 2. Planejamento Técnico | Média | Breno |
| Definir contrato da API (endpoints e formato do JSON) com o Matheus | Geral | 3. Design | Alta | Breno |
| Protótipo navegável completo no Figma | Geral | 3. Design | Média | Breno |
| Adicionar tela Minha Conta e botão de acesso no header (Figma) | Geral | 3. Design | Média | — |
| Débito técnico do Figma: componentizar Nav/Footer/Botão e criar variáveis de espaçamento | Geral | 3. Design | Baixa | — |
| Inicializar projeto Spring Boot (backend) com dependências e conexão MySQL | Geral | 4. Desenvolvimento | Alta | Breno |
| Inicializar projeto React (frontend) com Vite | Geral | 4. Desenvolvimento | Alta | Breno |
| Criar entidades JPA (10 tabelas, incluindo Troca que faltava no schema) | Geral | 4. Desenvolvimento | Alta | Breno |
| Criar repositórios JPA (10 interfaces Spring Data) | Catálogo | 4. Desenvolvimento | Alta | Breno |
| Criar endpoint de listagem de produtos | Catálogo | 4. Desenvolvimento | Alta | Breno |
| Criar endpoint de detalhe do produto (GET /produtos/{id}) com tratamento de erro 404 | Catálogo | 4. Desenvolvimento | Alta | Breno |
| Configurar Spring Security (rotas públicas vs protegidas) | Geral | 4. Desenvolvimento | Média | Breno |
| Construir tela Home (listagem, categorias, destaques) | Catálogo | 4. Desenvolvimento | Média | Breno |
