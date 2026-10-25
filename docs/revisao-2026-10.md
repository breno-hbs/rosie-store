# Revisão do projeto — outubro de 2026

Revisão completa do front (`frontend/src`) e do back (`backend/`) antes da reunião em que a Roseane vai testar a loja (issue #97). Nada foi corrigido aqui: cada achado traz a correção sugerida, e a lista do fim diz o que pode virar issue.

Base: a `main` no merge do PR #96 (botão de mostrar senha).

## Como foi feita

- **Lido:** `CLAUDE.md`, `docs/requisitos.md`, o `docs/contrato-api.md` inteiro, `docs/deploy.md`, `docs/demonstracao.md` e as 45 issues do GitHub (43 fechadas, 2 abertas).
- **Back:** segurança (`SecurityConfig`, `JwtConfig`), tratamento de erros, os services da compra (carrinho, checkout, pagamento, estoque), da conta (cadastro, login, senha, exclusão), da troca e do catálogo, os repositórios, as migrações do Flyway, os `application*.properties`, o `Dockerfile` e o `railway.json`. Varredura de todos os controllers (rotas, DTOs de entrada e saída) e de todos os `log.*` (dado pessoal no log).
- **Front:** `api.js`, `App.jsx`, `index.css`, `netlify.toml`, `vite.config.js` e as telas de estoque, checkout, produto, rodapé e política de privacidade. Varredura de todos os `.css` e `.jsx` atrás de cor literal, sombra, gradiente, `font-family` e media query `max-width`.
- **Rodado:** a API desta branch subiu na porta 8091, contra o `rosie_db` local de desenvolvimento, para confirmar as respostas de erro do achado M1 (só `GET` e um login com e-mail inexistente, sem gravar nada); depois foi derrubada. A demonstração (8080 e 5173) não foi tocada. Os testes automatizados (`./mvnw test`, `npm run lint`, `npm run build`) não foram rodados aqui, porque o PR só mexe em `docs/`; o CI roda os dois.

## Resumo

O projeto está em bom estado para a reunião. A arquitetura é consistente, o código é bem comentado em português e as decisões ficam registradas no contrato. Os fluxos críticos foram feitos com cuidado: o servidor refaz preço, estoque e frete no checkout, o webhook do Mercado Pago é assinado e idempotente, com travas no pedido e no estoque, e a exclusão de conta anonimiza em vez de apagar. São 579 testes no back.

O que pesa antes do lançamento são quatro pontos de **prioridade alta**:

1. o login (da loja e do painel) não limita tentativas;
2. a política de privacidade ainda tem campos "a preencher";
3. as integrações reais (Mercado Pago, Melhor Envio, Resend) nunca foram testadas ponta a ponta;
4. o pagamento simulado pode ser ligado em produção por uma variável.

Nenhum dos quatro atrapalha a demonstração. Os dois primeiros dependem de dados e decisões da Roseane, então valem para a pauta da reunião.

| Prioridade | Quantidade |
|---|---|
| Alta | 4 |
| Média | 11 |
| Baixa | 12 |

### O que está bom

Dito para ficar registrado, porque são partes que não precisam mudar:

- **Regras do `CLAUDE.md` respeitadas.** Nenhum controller devolve entidade JPA: os 67 endpoints respondem DTOs (`record`), e nenhum DTO tem campo de entidade. Todo erro de regra de negócio sai como `{"erro", "codigo"}` pelo `GlobalExceptionHandler`, e o 401/403 do Spring Security também (`SecurityConfig.java:125`). No front não há nenhuma cor literal fora das variáveis do `:root` (`index.css:3`), nenhuma sombra nem gradiente, a fonte é só a Jost (`var(--fonte)`) e as 41 media queries de largura são todas `min-width` (mobile-first).
- **Contrato e código batem nos endpoints.** Os 67 endpoints dos controllers estão na tabela de status do contrato, e todo endpoint da tabela existe no código. As diferenças são de detalhe (achado B1).
- **Autorização das rotas.** `/admin/**` exige `ADMIN`; as rotas da cliente exigem `CLIENTE`, e todo recurso da cliente é buscado pelo id **e** pelo dono (`findByIdAndUsuarioId` em pedido, item da sacola e endereço; troca com o filtro do dono). Pedido, endereço ou item de outra cliente responde 404, sem revelar que existe.
- **Checkout e pagamento.** O servidor ignora preço e frete do front (`PedidoService.java:51`), o webhook confere a assinatura e busca o status no Mercado Pago (nunca no corpo do aviso), trava o pedido e o estoque em ordem fixa (`PagamentoService.java:111`), e falta de estoque ou valor diferente vira `PAGO_COM_PENDENCIA` sem estoque negativo. Há testes de concorrência (`PagamentoConcorrenteTests`, `EtiquetaConcorrenteTests`).
- **Conta e LGPD.** Senha em BCrypt com o limite de 72 bytes conferido, tempo constante no login com e-mail inexistente (`AuthService.java:44`), tokens dos links guardados só como hash SHA-256, versão da credencial que derruba as sessões na troca de senha, exclusão que anonimiza e mantém o fiscal, logs com e-mail mascarado e sem SQL com valores em produção.
- **Produção.** Flyway com `ddl-auto=validate`, stack trace desligado no perfil `prod` (`application-prod.properties:9`), Actuator só com `/health`, fuso de São Paulo no `railway.json`, contêiner sem root, cabeçalhos de segurança no Netlify, e o perfil `demo` que se recusa a subir junto com o `prod`.
- **Testes do back.** 579 testes, com fakes para Cloudinary, Melhor Envio, Mercado Pago e Resend: o CI não precisa de nenhuma chave.

---

## Achados — prioridade alta

### A1. Login sem limite de tentativas, inclusive o do painel

- **Onde:** `backend/src/main/java/com/rosie/backend/service/AuthService.java:90` (login da loja e do painel), `controller/AdminAuthController.java:25`, `controller/AuthController.java:45`.
- **Por que importa:** qualquer pessoa pode testar senhas sem parar no `POST /admin/auth/login`. Quem acertar a senha da Roseane tem o painel inteiro: produtos, pedidos com nome, e-mail, telefone e endereço das clientes, e o botão que compra etiquetas com o saldo do Melhor Envio. A senha mínima é de 8 caracteres. O "esqueci minha senha" já tem limite (`LimitePedidosSenha`); o login, que é o alvo mais óbvio, não tem.
- **Correção sugerida:** reaproveitar o desenho do `LimitePedidosSenha` (janela fixa na memória, por e-mail e por IP) para o login da loja e do painel, com um limite mais baixo no painel (ex.: 5 erros por e-mail em 15 minutos), respondendo 429 `MUITAS_TENTATIVAS`. Contar só as tentativas que erram, para a dona da conta não ser travada por quem digita certo. Documentar no contrato e cobrir com teste.

### A2. Política de privacidade com campos "a preencher"

- **Onde:** `frontend/src/pages/PoliticaPrivacidade.jsx:108`, `:114`, `:120`, `:126` (quem é a controladora, CPF ou CNPJ, e-mail, encarregado), `:218` (transferência internacional), `:239` e `:243` (prazos de guarda), `:275` e `:349` (e-mail para os pedidos da titular).
- **Por que importa:** a página está no ar para qualquer visitante, e o cadastro exige aceitar esta política (RNF06). Sem a identificação da controladora e um canal de contato, a política não cumpre o que a LGPD pede (arts. 9º e 41) e a titular não tem como exercer os direitos que a própria página promete. Os prazos de guarda também estão em aberto.
- **Correção sugerida:** levar a lista para a reunião e pegar com a Roseane: nome ou razão social, CPF ou CNPJ, e-mail de contato e quem responde pelos dados. Os prazos de guarda dependem da contabilidade dela (fiscal) e de uma orientação jurídica (Marco Civil). Preencher antes do lançamento. Como ninguém de verdade aceitou a versão atual, a `VIGENCIA` (`PoliticaPrivacidade.java:15`) pode ficar como está; se a loja já estiver no ar quando o texto mudar, trocar a vigência para pedir o novo aceite.

### A3. Integrações reais nunca testadas ponta a ponta

- **Onde:** o próprio contrato registra (`docs/contrato-api.md:1678`): "sem token até agora, testado só com o emissor falso". Vale para o Mercado Pago (`MercadoPagoGateway`), o Melhor Envio (`MelhorEnvioCalculadora`, `MelhorEnvioEmissor`: frete, etiqueta de ida, etiqueta de volta e rastreio) e o Resend.
- **Por que importa:** é o caminho do dinheiro e da entrega. Os testes conferem o formato que a documentação de cada serviço descreve, mas só a sandbox mostra o que eles recusam de verdade. Um ponto concreto já levantado no contrato: o Melhor Envio pode exigir o CPF da cliente (`to.document` na ida, `from.document` na volta) para a declaração de conteúdo, e a loja não pede CPF. O emissor só manda o documento da loja (`MelhorEnvioEmissor.java:285`). Se exigir, a primeira etiqueta de verdade volta 502 `ETIQUETA_FALHOU`, e o cadastro precisa de um campo novo, com impacto na política de privacidade.
- **Correção sugerida:** uma issue (antes da #28) para uma rodada na sandbox com as contas da Roseane: preferência e webhook do Mercado Pago com `ROSIE_API_URL` pública (pode ser o túnel da demo), pagamento aprovado e recusado com os cartões de teste, cotação, etiqueta de ida e de volta, rastreio e um e-mail pelo domínio verificado no Resend. Registrar o resultado no contrato. Se o CPF for obrigatório, abrir a issue do campo de CPF.

### A4. O pagamento simulado pode ser ligado em produção

- **Onde:** `backend/src/main/java/com/rosie/backend/config/PagamentoConfig.java:33`, `config/FreteConfig.java:31`, `config/EtiquetaConfig.java:49`. A única trava é `config/PerfilDemo.java:17`, que só impede o perfil `demo` junto com o `prod`.
- **Por que importa:** com `PAGAMENTO_SIMULADO=true` nas variáveis do Railway (copiada de um `.env` de teste, por exemplo), a API sobe normalmente no perfil `prod`, e qualquer cliente logada aprova o próprio pedido sem pagar (`POST /pedidos/{id}/pagamento-simulado`). O estoque baixa, o pedido vira `PAGO` e aparece no painel pronto para a Roseane gerar a etiqueta e despachar. Com `FRETE_SIMULADO=true`, o frete cobrado e a etiqueta são de mentira. O aviso no log (`log.warn`) é fácil de não ver.
- **Correção sugerida:** no perfil `prod`, a API se recusa a subir com `rosie.pagamento.simulado` ou `rosie.frete.simulado` ligados, do mesmo jeito que o `PerfilDemo` faz hoje (são poucas linhas e um teste como o `PerfilDemoTest`). Citar a regra em `docs/deploy.md`.

---

## Achados — prioridade média

### M1. Erros que escapam do formato `{"erro", "codigo"}`

- **Onde:** `backend/src/main/java/com/rosie/backend/exception/GlobalExceptionHandler.java:25` não trata parâmetro de tipo errado, método ou `Content-Type` não aceitos nem exceção inesperada; `service/ProdutoService.java:37` monta o `PageRequest` sem conferir os números.
- **Por que importa:** é regra do `CLAUDE.md` e do contrato ("Erros: sempre nesse formato"). Confirmado na API rodando:

  | Requisição | Resposta |
  |---|---|
  | `GET /produtos?pagina=-1` | **500** `{"timestamp","status","error","trace"...}` |
  | `GET /produtos?tamanho=0` | **500**, mesmo formato |
  | `GET /produtos/abc`, `GET /produtos?categoriaId=x`, `GET /produtos/destaques?limite=abc` | 400 no formato padrão do Spring |
  | `POST /auth/login` com `Content-Type: text/plain` | 415 no formato padrão do Spring |
  | `GET /rota-que-nao-existe` sem token | 401 `NAO_AUTENTICADO` (a rota não existe, mas a resposta diz que falta login) |

  Em produção o `trace` some (`include-stacktrace=never`), mas o formato continua outro, e a loja mostra "Erro 500" (`api.js:149`), sem mensagem legível. Um 500 inesperado também não fica registrado com contexto no log.
- **Correção sugerida:** acrescentar ao handler: `MethodArgumentTypeMismatchException` e `MissingServletRequestParameterException` → 400 `DADOS_INVALIDOS`; `HttpRequestMethodNotSupportedException` → 405; `HttpMediaTypeNotSupportedException` → 415; `NoResourceFoundException` → 404 `RECURSO_NAO_ENCONTRADO`; e um `@ExceptionHandler(Exception.class)` final → 500 `ERRO_INTERNO` com `log.error` da exceção. Em `GET /produtos`, ajustar `pagina` (mínimo 0) e `tamanho` (1 a 48) sem erro, como já é feito com o `limite` dos destaques. Documentar `ERRO_INTERNO` na tabela de códigos.

### M2. Consultas N+1 nas listagens, sem teto no tamanho da página

- **Onde:** `backend/src/main/java/com/rosie/backend/dto/ProdutoResumoDTO.java:20` e `:26` (fotos e estoque de cada produto), `dto/ProdutoAdminResumoDTO.java:23`, `dto/CarrinhoProdutoDTO.java:19`; `controller/ProdutoController.java:25` (o `tamanho` aceita qualquer número). Nenhum `default_batch_fetch_size` em `application.properties`.
- **Por que importa:** cada produto da vitrine dispara uma consulta para as fotos, uma para o estoque e uma para a categoria. Uma página de 12 produtos faz cerca de 38 consultas, e a sacola e a lista do painel seguem o mesmo padrão. Com o catálogo pequeno do lançamento, passa. Mas `GET /produtos?tamanho=100000` é público e devolve o catálogo inteiro, com três consultas por produto, numa requisição que qualquer um repete (confirmado: a API aceitou e devolveu tudo). No Railway, cobrado por uso, isso pesa.
- **Correção sugerida:** uma linha em `application.properties`, `spring.jpa.properties.hibernate.default_batch_fetch_size=50`, junta as consultas das coleções em lotes (de cerca de 38 para cerca de 5 por página) sem mexer em código. Com o teto do `tamanho` (M1), o caminho de abuso fecha. Opcional: `spring.jpa.open-in-view=false` com `@Transactional(readOnly = true)` no `ProdutoService`, que hoje depende do OSIV para carregar as coleções (ver B11).

### M3. Um pedido novo a cada "Finalizar", e pedido não pago que nunca vence

- **Onde:** `backend/src/main/java/com/rosie/backend/service/PedidoService.java:51` e `:98` (todo `POST /pedidos` grava um pedido novo); `service/MercadoPagoGateway.java` (a preferência sai sem data de expiração).
- **Por que importa:** a cliente que vai ao Mercado Pago, volta e clica "Finalizar pedido" de novo gera um segundo pedido `AGUARDANDO_PAGAMENTO` com a mesma sacola, e assim por diante. O painel da Roseane enche de pedidos que nunca vão ser pagos, e eles não somem sozinhos: só o PIX expira no Mercado Pago, o link do cartão fica aberto. Semanas depois, a cliente ainda pode pagar um link antigo pelo preço antigo. O estoque é conferido na aprovação, então isso termina em `PAGO_COM_PENDENCIA` (e esbarra no M4).
- **Correção sugerida:** no checkout, cancelar os pedidos `AGUARDANDO_PAGAMENTO`/`PAGAMENTO_RECUSADO` anteriores da mesma cliente (ou reaproveitar o aberto quando a sacola e o frete forem os mesmos). Mandar `expires: true` e `expiration_date_to` na preferência (ex.: 48 horas) e cancelar, numa rotina como a do rastreio, os pedidos não pagos com mais de N dias. O painel pode esconder os cancelados por padrão.

### M4. O painel não tem ação para pedido com pendência, cancelamento nem estorno

- **Onde:** o contrato registra (`docs/contrato-api.md:845`): "resolver a pendência pelo painel ainda não existe". `service/PedidoAdminService.java` só lista, mostra, gera etiqueta e marca entregue. `service/ContaService.java:41` conta `PAGO_COM_PENDENCIA` como pedido em andamento.
- **Por que importa:** um pedido `PAGO_COM_PENDENCIA` (faltou estoque ou o valor não bate) fica assim para sempre. A Roseane resolve por fora (WhatsApp, estorno no Mercado Pago), mas o pedido continua "Pago com pendência" no painel, a etiqueta continua bloqueada mesmo depois de ela repor o estoque, e a cliente **nunca consegue excluir a conta** (409 `CONTA_COM_PEDIDO_EM_ANDAMENTO`), o que esbarra no direito do art. 18 da LGPD. Também não há como cancelar um pedido pago a pedido da cliente, nem registrar que o dinheiro foi devolvido.
- **Correção sugerida:** uma issue de "ações do pedido no painel": (a) liberar a pendência (confere e baixa o estoque e o pedido vira `PAGO`, pronto para a etiqueta), (b) cancelar com estorno feito (`CANCELADO`, com a observação e sem baixar estoque), e de preferência (c) o estorno pela API do Mercado Pago. Mostrar à Roseane na reunião como o caso aparece hoje.

### M5. Venda feita com a Gestão de Estoque aberta pode ser desfeita ao salvar

- **Onde:** `frontend/src/components/AdminEstoque.jsx:62` (manda a quantidade digitada como valor final), `backend/src/main/java/com/rosie/backend/service/ProdutoAdminService.java:127` (grava sem conferir o valor anterior).
- **Por que importa:** é o problema da #47 do outro lado. A Roseane abre o estoque com P = 3, uma venda aprovada baixa para 2, e ela digita 5 para registrar duas peças que chegaram: o estoque vai para 5, não para 4, e a loja passa a vender uma peça que não existe. O caminho natural é ela deixar a tela aberta enquanto confere as peças.
- **Correção sugerida:** mandar junto a quantidade que a tela mostrava (`quantidadeAnterior` por tamanho) e responder 409 `ESTOQUE_ALTERADO` quando o banco tiver outra, para a tela recarregar a linha e avisar. Alternativa mais simples: recarregar a linha antes de salvar e pedir confirmação quando o valor tiver mudado.

### M6. Pôr na sacola exige login (o requisito pede conta só para finalizar)

- **Onde:** `frontend/src/pages/Produto.jsx:186` e `:213` levam ao `/entrar` quem não está logada; a sacola só existe no servidor (`/carrinho/**` exige `CLIENTE`, `SecurityConfig.java:61`).
- **Por que importa:** o RF08 diz que a conta é obrigatória "para finalizar a compra", e o RF09 trata a sacola à parte. Hoje a visitante que toca em "Adicionar à sacola" vai para o login antes de ver a sacola, e loja de roupa costuma perder venda nesse ponto. Foi uma decisão consciente (#22: sacola no servidor), mas ela não aparece nos requisitos.
- **Correção sugerida:** decidir com a Roseane na reunião. Se ela preferir sacola sem login: guardar a sacola da visitante no `localStorage` e juntar com a do servidor no login ou no cadastro. Se ficar como está, registrar a decisão no `requisitos.md`.

### M7. Rodapé com texto provisório e itens sem link

- **Onde:** `frontend/src/components/Footer.jsx:31` ("Espaço curto sobre a marca, reforçando o posicionamento e convidando a seguir nas redes."), `:10` ("Todos os produtos", "Novidades", "Mais vendidos" sem link), `:13` ("Sobre", "Envio" sem link), `:20` ("E-mail" sem link).
- **Por que importa:** aparece em todas as páginas da loja. É a primeira coisa que a Roseane vai estranhar na reunião, e na loja publicada passa a impressão de site inacabado. Itens que parecem link e não fazem nada também confundem quem navega.
- **Correção sugerida:** pegar com a Roseane o texto da marca, o e-mail de contato e o que ela quer em "Sobre" e "Envio". Enquanto isso, ligar "Todos os produtos" à listagem e tirar os itens sem destino (ou ligar "Novidades" e "Mais vendidos" aos destaques). Mostrar "E-mail" como `mailto:` quando houver.

### M8. Nenhum e-mail sobre o pedido, nem para a cliente nem para a Roseane

- **Onde:** só a troca (`service/EmailTroca.java`), a troca de e-mail (`MudancaEmailService`) e a senha (`RedefinicaoSenhaService`) usam a `FilaEmail`. `PagamentoService.aprovar` (`:86`), `EtiquetaService.gerar` (`:85`) e a entrega não mandam nada.
- **Por que importa:** não é um RF escrito, mas toda loja manda "pedido confirmado" e "seu pedido foi enviado, o rastreio é ...". Hoje a cliente só sabe do rastreio se entrar na Minha Conta (o Mercado Pago manda o comprovante dele, sem os dados da loja). E a Roseane não é avisada de venda nova: precisa abrir o painel para descobrir que tem pedido para despachar.
- **Correção sugerida:** reaproveitar a `FilaEmail` e o `ModeloEmail` (o mesmo desenho da #65): e-mail à cliente no `PAGO` (resumo do pedido) e no `ENVIADO` (código e link do Melhor Rastreio), e um aviso à loja (`ROSIE_EMAIL_RESPONDER_PARA` ou uma variável nova) em todo pedido `PAGO` ou `PAGO_COM_PENDENCIA`. Perguntar à Roseane se ela quer o aviso por e-mail ou se o do Mercado Pago basta.

### M9. Testes que faltam nas regras críticas

- **Onde:** regras do back sem nenhum teste que as exercite:
  - troca em aberto segura a exclusão da conta: `service/ContaService.java:111` (há teste só do pedido a caminho);
  - pagamento aprovado depois da exclusão da conta vira pendência: `service/PagamentoService.java:97`;
  - pagamento aprovado de um pedido `CANCELADO` (PIX expirado e pago depois) é processado: `PagamentoService.java:87`;
  - aviso de recusa atrasado não desfaz um pedido já pago: `PagamentoService.java:162` (`mudarSeEmAberto`);
  - pagamento estornado não muda o pedido: `PagamentoService.java:65`.

  No front não há nenhum teste: o `package.json` não tem script de teste, e o CI só roda lint e build.
- **Por que importa:** são exatamente os casos raros do dinheiro e da LGPD, os que ninguém testa à mão e que quebram sem aviso num refactor. No front, `api.js` concentra sessão, 401, `destinoSeguro` (o que impede o redirecionamento aberto) e o link do pagamento simulado, sem nenhuma proteção automática.
- **Correção sugerida:** cinco testes no `PagamentoServiceTests` e no `ContaServiceTests`, no mesmo estilo dos que existem. No front, o Vitest (sem mudar o Vite) com testes de funções puras primeiro: `destinoSeguro`, `tokenExpirado`/`lerSessao`, `linkPagamento`, `formato.js` e o prazo de `troca.js`. Depois, acrescentar `npm test` ao workflow `frontend.yml`.

### M10. O roteiro da demonstração promete o que a tela não tem

- **Onde:** `docs/demonstracao.md:127` ("Categorias. Criar, renomear, **ordenar**": as categorias só saem por nome, `AdminCategorias.jsx:14`), `:129` ("Pedidos ... **Filtro por status**": `AdminPedidos.jsx` não tem filtro), `:126` ("esgotou, o tamanho **some** da vitrine": o tamanho fica desabilitado, não some).
- **Por que importa:** é o roteiro da reunião. Seguido como está, o Breno vai procurar na tela um botão que não existe, na frente da Roseane.
- **Correção sugerida:** corrigir as três frases no roteiro de teste que vem a seguir (ou no próprio `demonstracao.md`). Se a Roseane quiser ordenar as categorias ou filtrar os pedidos, vira issue.

### M11. Issue #28 (deploy) desatualizada, e as duas issues abertas dependem da Roseane

- **Onde:** corpo da issue #28: cita `public/_redirects` (substituído pelo `netlify.toml` na #74) e "`ddl-auto` revisado para produção" (feito na #74: Flyway com `validate`). A #30 (backup) depende da #28.
- **Por que importa:** são as duas únicas issues abertas, e são as que faltam para o RNF04 e o RNF05. As duas dependem das contas no nome da Roseane (Railway, domínio, Mercado Pago, Melhor Envio), que estão na lista de fechamento do `demonstracao.md`. Com a #28 desatualizada, o "pronto quando" dela não reflete o que falta.
- **Correção sugerida:** atualizar a #28 com o que falta de fato (contas, variáveis do `docs/deploy.md`, domínio e HTTPS, o A3 e o A4 antes de abrir a loja) e definir na reunião quando a Roseane cria as contas.

---

## Achados — prioridade baixa

### B1. Contrato: códigos de erro e variáveis fora das tabelas

- **Onde:** `docs/contrato-api.md:98` a `:137` ("Códigos de erro em uso") e `:443` (variáveis do remetente).
- **Por que importa:** o contrato é a fonte de verdade (`CLAUDE.md`). Os endpoints batem, mas:
  - Sete códigos que o código usa não estão na tabela de códigos, só no texto das seções: `PEDIDO_NAO_PAGO`, `PEDIDO_COM_PENDENCIA`, `ETIQUETA_CANCELADA`, `ETIQUETA_EM_ANDAMENTO` (409), `ETIQUETA_FALHOU` (502), `ETIQUETA_INDISPONIVEL` (503) e `PEDIDO_SEM_FRETE` (409). A lista de status da convenção (`:89`) também não inclui o 405 e o 415 que a API devolve (ver M1).
  - A tabela de configuração (`:443`) não cita `ROSIE_REMETENTE_TELEFONE`, `ROSIE_REMETENTE_EMAIL`, `ROSIE_REMETENTE_DOCUMENTO` e `ROSIE_REMETENTE_INSCRICAO_ESTADUAL`. O telefone e o documento são obrigatórios para a etiqueta (`EtiquetaConfig.java:63`); estão só no `docs/deploy.md:158`.
  - O exemplo de resposta do login admin (`:407`) não traz `privacidadePendente`, que a API devolve (`:208` diz isso).
  - `:1695` diz que o `api.js` "só o envia nas chamadas do painel"; desde a #21 ele manda também o token da cliente nas rotas dela (`:1699` corrige, mas as duas frases se contradizem).
  - `GET /actuator/health` é público (`SecurityConfig.java:83`) e não aparece na tabela de status nem na de acesso.
- **Correção sugerida:** acrescentar as sete linhas e as variáveis às tabelas, corrigir o exemplo e a frase, citar o health check. Dá para fazer junto com o M1, que mexe na mesma tabela.

### B2. Funções e constantes copiadas no front

- **Onde:** `mensagemDeErro` está em 14 arquivos (`AdminBannerForm.jsx:89`, `AdminBanners.jsx:6`, `AdminCategorias.jsx:7`, `AdminEstoque.jsx:7`, `AdminMedidas.jsx:9`, `AdminProdutoForm.jsx:72`, `AdminProdutoFotos.jsx:75`, `AdminProdutos.jsx:13`, `pedidosAdmin.js:23`, `AdminLogin.jsx:9`, `Cadastro.jsx:12`, `Entrar.jsx:10`, `EsqueciSenha.jsx:7`, `RedefinirSenha.jsx:8`); `TAMANHOS` em 4 (`AdminEstoque.jsx:5`, `AdminMedidas.jsx:5`, `AdminProdutoForm.jsx:6`, `TabelaMedidasModal.jsx:4`); `formatarDataHora` em 2 com saídas diferentes (`formato.js:15` lê o texto, `pedidosAdmin.js:19` passa por `Date` e pelo fuso do navegador).
- **Por que importa:** é o "padrões diferentes para a mesma coisa". Mudar a mensagem de "sem conexão" exige editar 14 arquivos, e as duas datas podem divergir no painel quando o navegador não está em Brasília.
- **Correção sugerida:** `mensagemDeErro(erro, padrao)` num lugar só (ex.: `erros.js`, ao lado de `ApiError`), `TAMANHOS` em `formato.js` (ou num `catalogo.js`), e uma `formatarDataHora` só, a do `formato.js`. Refactor mecânico, sem mudança visível.

### B3. Arquivos grandes demais

- **Onde:** front: `api.js` (749 linhas), `AdminBannerForm.jsx` (580), `ContaDados.jsx` (424), `AdminProdutoForm.jsx` (422), `Produto.jsx` (410), `AdminProdutoFotos.jsx` (406), `AdminMedidas.jsx` (405), `MinhaConta.css` (737). Back: `EtiquetaService.java` (394), `MelhorEnvioEmissor.java` (354).
- **Por que importa:** cada um ainda se lê bem (bem comentados), mas é onde as próximas mudanças vão ficar caras. O `api.js` mistura catálogo, sessão, conta, compra, painel e banners.
- **Correção sugerida:** só quando mexer neles. O `api.js` pode virar `api/` com um arquivo por área (`sessao.js`, `catalogo.js`, `conta.js`, `compra.js`, `admin.js`) e a `requisicao` num só, mantendo os mesmos nomes exportados. No back, a ida e a volta do `EtiquetaService` podem ser duas classes com o passo a passo comum.

### B4. Mensagem enganosa na Gestão de Estoque, e tamanho que não sai

- **Onde:** `frontend/src/components/AdminEstoque.jsx:28` ("Para tirar o tamanho, edite o produto"); `docs/contrato-api.md:1241` ("não há como descadastrar um tamanho").
- **Por que importa:** desde a #47 a edição do produto mostra o estoque só para leitura, então a Roseane segue a dica e não encontra como tirar o tamanho. Um tamanho cadastrado por engano fica para sempre como "esgotado" na página do produto.
- **Correção sugerida:** trocar a mensagem por "Use 0 para esgotado". Se a Roseane quiser tirar tamanhos, uma rota `DELETE /admin/produtos/{id}/estoque/{tamanho}` (recusando o tamanho que estiver numa sacola ou num pedido aberto).

### B5. Leitura do id do token repetida nos controllers

- **Onde:** `Long.valueOf(jwt.getSubject())` em 9 controllers (ex.: `controller/PagamentoSimuladoController.java:28`).
- **Por que importa:** pouco: é repetição, e um controller novo pode esquecer de pegar o id do token e confiar num id da URL.
- **Correção sugerida:** um `HandlerMethodArgumentResolver` com uma anotação `@ClienteId Long clienteId`, ou um método estático único.

### B6. Token de usuário que não existe mais é aceito

- **Onde:** `backend/src/main/java/com/rosie/backend/config/JwtConfig.java:85` (`.orElse(true)`).
- **Por que importa:** hoje nenhuma linha de `usuario` é apagada (a exclusão anonimiza), então não há efeito. Mas se alguém apagar um usuário direto no banco, os tokens dele seguem valendo por até 2 horas. A role também vem só do token, sem conferir a do banco.
- **Correção sugerida:** `.orElse(false)` e, já que a consulta acontece de qualquer jeito, trazer a role na projeção `EstadoCredencial` e recusar o token com a role diferente.

### B7. Relógio da loja usado só em parte do código

- **Onde:** o `Clock` de `RelogioConfig.java:15` é usado na troca, no rastreio e na senha, mas `LocalDateTime.now()` direto aparece em `AuthService.java:64`, `ContaService.java:92` e `:140`, `EtiquetaService.java:142` e `:220`, `PagamentoService.java:93`.
- **Por que importa:** funciona porque o `railway.json` e o `Dockerfile` passam `-Duser.timezone=America/Sao_Paulo`. Rodando o jar sem a flag numa máquina em UTC, essas datas saem 3 horas adiantadas, e esses pontos não dão para congelar nos testes.
- **Correção sugerida:** injetar o `Clock` e usar `LocalDateTime.now(relogio)`, como o `RedefinicaoSenhaService` já faz.

### B8. Corpo da resposta de serviço externo no log

- **Onde:** `service/MelhorEnvioCalculadora.java:83`, `service/MelhorEnvioEmissor.java:216`, `service/MercadoPagoGateway.java:142` (`e.getResponseBodyAsString()`).
- **Por que importa:** os erros do Melhor Envio na etiqueta (e do Mercado Pago na preferência) podem repetir os dados enviados: nome, endereço, e-mail e telefone da cliente. Vão para o log do Railway, que o resto do código cuida de manter sem dado pessoal (RNF06).
- **Correção sugerida:** registrar só o status e a mensagem de erro extraída (o código já extrai a primeira mensagem para o 502), cortada em uns 300 caracteres.

### B9. Sem Content-Security-Policy no front

- **Onde:** `frontend/netlify.toml:21` (cabeçalhos), com o token da cliente no `localStorage` (`api.js:10`).
- **Por que importa:** o React escapa o conteúdo e não há `dangerouslySetInnerHTML`, então o risco é baixo. Mas o token no `localStorage` é lido por qualquer script que rodar na página, e uma CSP é a segunda barreira se uma dependência for comprometida.
- **Correção sugerida:** `Content-Security-Policy` no `netlify.toml` com `default-src 'self'`, `connect-src` para a API e o ViaCEP, `img-src` para o Cloudinary, `font-src` e `style-src` para o Google Fonts, e `frame-ancestors 'none'`. Testar no deploy de preview do Netlify antes.

### B10. Comentário desatualizado

- **Onde:** `backend/src/main/java/com/rosie/backend/model/StatusPedido.java:19` ("Acompanhar o rastreio para marcar sozinho fica para outra issue": feito na #64).
- **Por que importa:** pouco: confunde quem lê.
- **Correção sugerida:** "Marcado pelo rastreio (#64) ou pela Roseane no painel (#27)".

### B11. Open Session in View ligado por padrão

- **Onde:** `spring.jpa.open-in-view` não está configurado (o Spring liga e avisa na subida); `service/ProdutoService.java:26` não tem `@Transactional` e monta os DTOs carregando as coleções preguiçosas.
- **Por que importa:** a conexão com o banco fica presa até a resposta ser escrita, e o N+1 do M2 fica escondido. Com o volume atual, não faz diferença.
- **Correção sugerida:** junto com o M2: `@Transactional(readOnly = true)` no `ProdutoService` e `spring.jpa.open-in-view=false`, rodando os testes para achar algum outro ponto que dependa do OSIV.

### B12. Índices para quando o volume crescer

- **Onde:** `db/migration/V1__esquema_inicial.sql`: sem índice em `pedido.status` (rotina do rastreio), `troca.status`, nem `produto (ativo, destaque)`.
- **Por que importa:** hoje não importa (as chaves estrangeiras já têm índice e as tabelas são pequenas). Fica registrado para não esquecer.
- **Correção sugerida:** nada agora. Se o painel ficar lento com milhares de pedidos, uma migração `V6` com esses índices.

---

## Contrato x código

| Ponto | Situação |
|---|---|
| Endpoints no código e no contrato | Os 67 batem nos dois sentidos (conferido por script) |
| Endpoint no código e fora do contrato | `GET /actuator/health` (B1) |
| Endpoint no contrato e fora do código | Nenhum |
| Códigos de erro fora da tabela | 7 (B1) |
| Respostas fora do formato de erro | Parâmetro inválido, 405, 415 e 500 inesperado (M1) |
| Entidade JPA na resposta | Nenhuma |
| Variáveis fora da tabela de configuração | 4 do remetente (B1) |

## Requisitos x implementado

| Requisito | Situação |
|---|---|
| RF01 Home com destaques e categorias | Feito (destaques, banners pelo painel, categorias com foto) |
| RF02 Lista por categoria | Feito (página própria por categoria, #88) |
| RF03 Página de produto | Feito |
| RF04 Filtrar por categoria | Feito, mais a busca do header (#82) |
| RF05 Responsivo | Feito (mobile-first em todo o CSS) |
| RF06 Tamanhos P, M, G, GG | Feito; um tamanho cadastrado não sai mais (B4) |
| RF07 Política de troca no produto | **Feito diferente, para melhor:** um bloco fixo em toda página de produto (`troca.js:5`), em vez de estar no texto da descrição; não depende de a Roseane digitar em cada produto |
| RF08 Conta para finalizar | Feito, mas a conta é exigida já para pôr na sacola (M6) |
| RF09 Sacola | Feito |
| RF10 Pagamento integrado | Feito no código; nunca testado na sandbox (A3) |
| RF11 Frete pelo Melhor Envio | Feito no código; nunca testado na sandbox (A3) |
| RF12 Etiqueta após a compra | **Feito diferente:** sai pelo botão da Roseane, não sozinha (decisão #25, para ela conferir antes de gastar o saldo); nunca testado na sandbox (A3) |
| RF13 Login exclusivo da Roseane | Feito; sem limite de tentativas (A1) |
| RF14 CRUD de produtos | Feito ("remover" arquiva; a exclusão de vez é só sem histórico, #87) |
| RF15 Upload de fotos | Feito (Cloudinary, corte 4:5 na entrega) |
| RF16 Estoque por tamanho | Feito; risco de desfazer uma venda (M5) |
| RF17 Categorias pelo painel | Feito (sem ordenar, M10) |
| RF18 Motivo da troca | Feito, com logística reversa (#63) e e-mail a cada passo (#65) |
| RNF01 Mobile | Feito |
| RNF02 Painel restrito | Feito (role `ADMIN`, sessão separada no `sessionStorage`) |
| RNF03 Identidade visual | Feito (cores e Jost por variáveis; a "The Seasons" do "ROSIE" vem na imagem do logo) |
| RNF04 Backup do MySQL | **Falta** (issue #30 aberta, depende da #28) |
| RNF05 Hospedagem estável | **Falta** (issue #28 aberta, desatualizada: M11) |
| RNF06 LGPD | Pela metade: consentimento, exclusão e anonimização feitos; política com campos em aberto (A2); pendência bloqueia a exclusão (M4) |
| RNF07 Senha com hash | Feito (BCrypt) |
| RNF08 Quem paga a volta | Feito (calculado no servidor pelo motivo) |
| RNF09 Fotos 4:5 | Feito (orientação no cadastro e corte na URL) |

Além dos requisitos, o que uma loja no ar costuma ter e aqui falta: e-mails do pedido (M8) e as ações de pendência, cancelamento e estorno no painel (M4).

## Para o roteiro da reunião

O roteiro de teste é o próximo passo, fora desta revisão. Pontos daqui que valem para ele:

- **Pedir à Roseane:** os dados da política de privacidade (A2), o texto da marca e o e-mail do rodapé (M7), e se ela quer o aviso de venda nova por e-mail (M8).
- **Decidir com ela:** a sacola sem login (M6); se quer ordenar as categorias e filtrar os pedidos (M10).
- **Mostrar com cuidado:** o roteiro do `demonstracao.md` cita ordenar categorias e filtrar pedidos, que não existem (M10). Um pedido com pendência não tem saída pelo painel (M4): se ele aparecer na demo, explicar que vai ganhar a ação.
- **Não afeta a demo:** A1, A3 e A4 são de produção. A demo roda no perfil `demo`, com pagamento e frete simulados de propósito.

## Sugestões de issue

Em ordem de prioridade. A coluna "Tipo" segue os labels do repositório; os achados de baixa sem linha aqui (B5, B6, B10, B11, B12) cabem como tarefa num PR de outra issue que passe por eles, ou ficam só registrados.

| # | Título sugerido | Tipo | Áreas | Prioridade | Achados |
|---|---|---|---|---|---|
| 1 | `feat: limite de tentativas no login da loja e do painel` | `tipo:feature` | `area:back-end` | alta | A1 |
| 2 | `chore: preencher a política de privacidade com os dados da Roseane` | `tipo:chore` | `area:front-end` | alta | A2 |
| 3 | `chore: testar Mercado Pago, Melhor Envio e Resend na sandbox` | `tipo:chore` | `area:back-end`, `area:infra` | alta | A3 |
| 4 | `fix: API não sobe em produção com pagamento ou frete simulado` | `tipo:bug` | `area:back-end` | alta | A4 |
| 5 | `fix: todo erro da API no formato erro/codigo` | `tipo:bug` | `area:back-end` | média | M1, B1 |
| 6 | `fix: N+1 e tamanho de página sem limite nas listagens` | `tipo:bug` | `area:back-end` | média | M2, B11 |
| 7 | `feat: cancelar pedidos não pagos antigos e evitar pedido repetido no checkout` | `tipo:feature` | `area:back-end` | média | M3 |
| 8 | `feat: resolver pendência, cancelar e estornar pedido no painel` | `tipo:feature` | `area:back-end`, `area:front-end` | média | M4 |
| 9 | `fix: salvar o estoque não desfaz venda feita com a tela aberta` | `tipo:bug` | `area:back-end`, `area:front-end` | média | M5, B4 |
| 10 | `feat: sacola sem login` (só se a Roseane quiser) | `tipo:feature` | `area:front-end`, `area:back-end` | média | M6 |
| 11 | `feat: rodapé com o texto e os links da loja` | `tipo:feature` | `area:front-end` | média | M7 |
| 12 | `feat: e-mails de pedido confirmado, pedido enviado e venda nova` | `tipo:feature` | `area:back-end` | média | M8 |
| 13 | `chore: testes das regras raras de pagamento e exclusão, e testes do front` | `tipo:chore` | `area:back-end`, `area:front-end` | média | M9 |
| 14 | atualizar o corpo da #28 (sem issue nova) | — | — | média | M11 |
| 15 | `chore: unificar mensagemDeErro, TAMANHOS e formatarDataHora no front` | `tipo:chore` | `area:front-end` | baixa | B2, B3 |
| 16 | `chore: injetar o relógio da loja e limpar o log de serviços externos` | `tipo:chore` | `area:back-end` | baixa | B7, B8 |
| 17 | `chore: Content-Security-Policy no Netlify` | `tipo:chore` | `area:infra` | baixa | B9 |

O M10 (roteiro da demo) entra no roteiro de teste da reunião, sem issue própria. A `feature:*` de cada issue fica para quando ela for criada: as de pedido e pagamento são `feature:compra`, e as do painel, `feature:painel-admin`.
