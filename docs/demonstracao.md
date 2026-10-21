# Demonstração para a Roseane

Como mostrar a loja inteira para a Roseane antes de ela assinar a hospedagem (#28), sem pagar nada: tudo roda no computador do Breno, ela acompanha pela chamada (Teams, tela compartilhada) e abre a loja no próprio celular por um link temporário. Issue #77.

## O que é simulado

O modo de demonstração é o perfil `demo` da API (`backend/src/main/resources/application-demo.properties`) mais o modo `demo` do site (`frontend/vite.config.js` e `frontend/.env.demo`). O `scripts/demo.ps1` liga os dois.

| Parte | Na demonstração | Na loja de verdade |
|---|---|---|
| Pagamento | O "Finalizar pedido" leva a uma página da própria loja com os botões **Aprovar**, **Deixar pendente**, **Recusar** e **Cancelar**, que fazem o mesmo caminho do aviso do Mercado Pago (o pedido vira `PAGO` e o estoque baixa) | Checkout Pro do Mercado Pago (cartão ou PIX), na conta da Roseane |
| Frete | Opções fixas: Correios PAC, SEDEX e Jadlog .Package, com preço por quantidade de peças. O CEP `99999-999` mostra o caso "nenhuma transportadora atende" | Melhor Envio, com os preços da conta |
| Etiqueta | Etiqueta de mentira, sem gastar saldo: o link abre uma página de exemplo e o rastreio sai como `SIM00000000BR` | Melhor Envio, debitando o saldo |
| Entrega | O rastreio confere a cada minuto e marca o pedido enviado como **entregue** sozinho (a etiqueta simulada conta como entregue no dia) | O rastreio confere a cada 6 horas |
| E-mails | Não saem: só aparecem no log da API (destinatário mascarado e assunto) | Resend, no domínio `rosiestore.com.br` |
| Fotos | **De verdade**, no Cloudinary do Breno (o que já funciona hoje) | Cloudinary |
| Banco | `rosie_demo`, separado do `rosie_db` de desenvolvimento, criado vazio na primeira subida | MySQL do Railway |
| Admin | `admin@rosie.demo`, com uma senha gerada na primeira subida | O admin real, criado no deploy |

Nenhuma credencial real de Mercado Pago, Melhor Envio ou Resend é usada, mesmo que o `application-local.properties` tenha alguma: o perfil `demo` apaga essas chaves. A API se recusa a subir com `demo` junto do perfil `prod`.

## Antes do dia

### 1. Pedir à Roseane (uma semana antes)

Para a vitrine parecer a loja dela, e não um modelo vazio:

- [ ] **3 ou 4 produtos reais**, de categorias diferentes (Vestido, Calça, Camiseta, Cropped/Body).
- [ ] **Fotos 4:5** (retrato, ex.: 1080 × 1350), de 2 a 4 por produto: frente, costas, detalhe. JPG, PNG ou WEBP de até 5 MB. Foto de celular serve, com fundo limpo e boa luz; o painel corta em 4:5 se precisar.
- [ ] **Preço** de cada um.
- [ ] **Descrição** curta (2 ou 3 frases, como ela escreve no Instagram).
- [ ] **Composição e cuidados** (ex.: "95% viscose, 5% elastano; lavar à mão").
- [ ] **Tamanhos e quantidades** em estoque (P, M, G, GG).
- [ ] **Tabela de medidas** de cada categoria (busto, cintura, quadril, comprimento por tamanho, em cm).
- [ ] Peso e medidas da peça embalada, se ela souber (senão vale o pacote padrão de 0,3 kg).

### 2. Instalar o cloudflared (uma vez)

É o programa do link temporário (Cloudflare Quick Tunnel): gratuito, sem conta e sem cadastro. No PowerShell:

```powershell
winget install --id Cloudflare.cloudflared
```

Feche e abra o terminal depois da instalação. Confira com `cloudflared --version`. O script não instala nada sozinho: sem o cloudflared, ele mostra esse comando e para.

### 3. Pôr o WhatsApp no site (uma vez)

Ponha o número do WhatsApp da Roseane no `frontend/.env.local` (copie do `frontend/.env.example` se ainda não existir), só dígitos com DDI 55 e DDD: `VITE_WHATSAPP_NUMERO=55DDDNUMERO`. O modo demo também lê o `.env.local`. Sem o número, os botões "Comprar pelo WhatsApp" e "Falar no WhatsApp" somem do site, e a Home mostra o Instagram no lugar.

### 4. Ensaio (um ou dois dias antes)

1. Ligue o MySQL e feche a API e o `npm run dev` de desenvolvimento, se estiverem abertos (o script usa as portas 8080 e 5173).
2. Na raiz do repositório, atualizado com a `main`:

   ```powershell
   .\scripts\demo.ps1 -Tunel
   ```

   Se o Windows recusar o script (`execução de scripts foi desabilitada`), rode uma vez assim:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\demo.ps1 -Tunel
   ```

3. Na primeira subida, o script cria o banco `rosie_demo` e o admin da demo e mostra a senha **uma única vez**: anote. Para escolher a senha em vez de gerar, defina antes da primeira subida `$env:ROSIE_DEMO_ADMIN_SENHA = "..."` (8 caracteres ou mais).
4. Entre no painel (`http://localhost:5173/admin/login`) e cadastre os produtos da Roseane: categorias, tabela de medidas de cada categoria (botão "Começar com Busto, Cintura, Quadril e Comprimento"), produtos com fotos, estoque e destaques. Digite o preço com vírgula (`189,90`). Isso é o ensaio do roteiro do painel abaixo.
5. Faça o roteiro inteiro uma vez, inclusive pelo link no seu celular, e crie a **cliente de demonstração** (ex.: "Maria Demo", `maria@cliente.demo`, senha só de teste), com um pedido já entregue para mostrar a troca sem esperar: pague com "Aprovar", gere a etiqueta no painel e espere um minuto (o rastreio simulado marca entregue).
6. `Ctrl+C` no terminal encerra tudo. Os produtos, a cliente e os pedidos ficam no `rosie_demo` para o dia.

Para começar o banco da demo do zero (perde os produtos e o admin da demo): `DROP DATABASE rosie_demo;` no MySQL Workbench e suba de novo.

## No dia

1. Meia hora antes: MySQL ligado, API e `npm run dev` de desenvolvimento fechados, `.\scripts\demo.ps1 -Tunel`.
2. Espere o quadro verde **Demonstração no ar**. Ele mostra:
   - a loja no seu computador (`http://localhost:5173`) e o painel (`/admin/login`);
   - o **link para o celular** (`https://<aleatório>.trycloudflare.com`), já copiado para a área de transferência e conferido (site e API respondendo pelo link).
3. Abra o link no seu celular para conferir. Mande o link para a Roseane pelo WhatsApp **só na hora da chamada**.
4. No Teams, compartilhe **só a janela do navegador**, não a tela inteira (o terminal mostra o link e os logs).
5. Siga o roteiro abaixo.
6. No fim: `Ctrl+C` no terminal. O script fecha o túnel (o link para de abrir na hora), a API e o site.

Cada vez que o script sobe, o link é outro. Se o túnel cair no meio, o script avisa e encerra: rode de novo e mande o link novo.

### Como o script funciona

- **Java:** o `JAVA_HOME` não precisa estar definido no Windows. O script usa, nesta ordem: `-JavaHome "<pasta>"`, o `JAVA_HOME` (se for uma JDK 17 ou mais nova) ou a primeira JDK 17+ que achar em `~\.jdks` (as do IntelliJ, ex.: `openjdk-26.0.2.1`), `C:\Program Files\Eclipse Adoptium`, `\Java` e `\Microsoft`. Ele define o `JAVA_HOME` só para os processos que abre e devolve as variáveis como estavam ao terminar.
- **API:** `mvnw.cmd spring-boot:run` com `SPRING_PROFILES_ACTIVE=demo`, usando o `application-local.properties` (senha do MySQL, JWT, Cloudinary). Espera o `/actuator/health` responder.
- **Site:** `npm run demo` compila o site no modo `demo` (pasta `frontend/dist-demo`) e serve em `127.0.0.1:5173`. O site chama a API em `/api`, no mesmo endereço, e o Vite repassa para `localhost:8080`: um link só para loja e API, sem CORS. O site compilado abre bem mais rápido pelo túnel que o `npm run dev` e não expõe o código-fonte.
- **Túnel:** `cloudflared tunnel --url http://127.0.0.1:5173`. O Vite só aceita o endereço `*.trycloudflare.com` no modo `demo` (`allowedHosts`). O link do túnel vai para a API como `ROSIE_LOJA_URL` (link da etiqueta simulada da troca).
- **Logs:** `%TEMP%\rosie-demo` (`api.log`, `site.log`, `tunel.log`).
- Sem `-Tunel`, sobe só no seu computador (para ensaiar ou apresentar sem o celular dela).

| Problema | O que fazer |
|---|---|
| `A porta 8080 (ou 5173) já está em uso` | Feche a API ou o `npm run dev` de desenvolvimento (ou o IntelliJ rodando a API) |
| `O MySQL não está respondendo` | `services.msc` → `MySQL80` → Iniciar |
| `Nenhuma JDK 17 ou mais nova encontrada` | `.\scripts\demo.ps1 -Tunel -JavaHome "$HOME\.jdks\openjdk-26.0.2.1"` |
| A API não sobe com `Schema-validation` ou Flyway | O `rosie_demo` foi criado por uma versão antiga: `DROP DATABASE rosie_demo;` e suba de novo |
| Não lembro a senha do admin da demo | `DROP DATABASE rosie_demo;` e suba de novo (perde os produtos da demo) |
| O link não abre no celular | Espere alguns segundos (o endereço novo leva um tempo para valer) e confira o `tunel.log`; redes corporativas podem bloquear o `trycloudflare.com` |

## Roteiro da apresentação

Uns 30 minutos. A ideia é ela ver primeiro como a cliente compra (no celular dela, ao mesmo tempo) e depois como ela mesma cuida da loja. Mostre na tela compartilhada e peça para ela repetir no celular.

### Parte 1: a loja, como a cliente vê (no celular dela)

1. **Vitrine (Home).** O carrossel com os destaques, as categorias e os produtos. Destaque: *"é mobile-first, pensada para quem chega pelo Instagram no celular"*; as cores e a fonte seguem a identidade da Rosie; os destaques do carrossel quem escolhe é ela, no painel.
2. **Categorias.** Toque numa categoria (ex.: Vestidos): a vitrine filtra. Destaque: *"você cria e renomeia as categorias"*.
3. **Produto com fotos.** Abra um produto: fotos em 4:5 (deslizar), preço, descrição, composição e cuidados, tamanhos disponíveis. Tamanho sem estoque aparece indisponível. Escolha um tamanho e toque em "Comprar pelo WhatsApp": abre a conversa com ela já com a peça, o tamanho e o link. Destaque: *"as fotos e os textos são os seus; você mesma cadastra e troca quando quiser"*.
4. **Tabela de medidas.** Toque em "Tabela de medidas": a tabela da categoria, em cm. Destaque: *"diminui troca por tamanho errado"*.
5. **Carrinho.** Escolha o tamanho, adicione e abra o carrinho: mudar a quantidade, remover. O site não deixa pôr mais peças do que o estoque.
6. **Frete.** No carrinho, digite um CEP: aparecem as opções (PAC, SEDEX, Jadlog) com preço e prazo. Destaque: *"na loja de verdade é o preço real do Melhor Envio, com o desconto da conta; aqui é simulado"*. Se quiser, mostre o CEP `99999-999` (nenhuma transportadora atende).
7. **Conta da cliente.** Para fechar o pedido, ela entra ou cria conta (nome, e-mail, senha) e aceita a política de privacidade (LGPD). Use a **cliente de demonstração** na tela; se ela quiser testar no celular, que crie uma conta com um e-mail qualquer (nada é enviado).
8. **Checkout com pagamento simulado.** Endereço (o CEP preenche a rua), frete, forma de pagamento (cartão ou PIX) e "Finalizar pedido". Abre a página **Pagamento simulado** (com o aviso "Ambiente de desenvolvimento": é esperado na demo): explique que *"aqui entraria a tela do Mercado Pago, na sua conta; o dinheiro cai direto para você"*. Clique em **Aprovar**: o pedido aparece como pago. Se quiser, faça um segundo pedido e mostre **Recusar**: o pedido fica com o pagamento recusado e o estoque não baixa.
9. **Minha Conta.** Dados, endereços salvos, **pedidos** (status, itens, frete, código de rastreio quando enviado) e **trocas**. Destaque: *"a cliente acompanha sozinha, sem precisar te chamar no direct"*.
10. **Troca.** Num pedido entregue (o da cliente de demonstração), "Solicitar troca": escolher a peça, o motivo (defeito ou não serviu) e descrever o problema. Destaque: *"o prazo de 7 dias depois da entrega é o do Código de Defesa do Consumidor; o site calcula sozinho. Se for defeito, a Rosie paga a volta e o site gera a etiqueta; se não serviu, a cliente vê o endereço para mandar"*.

### Parte 2: o painel dela (na tela compartilhada)

Entre em `/admin/login` com o admin da demo. Diga que o acesso dela será com o e-mail dela.

1. **Produtos e fotos.** Lista com estoque e status; "Novo produto": nome, preço, categoria, descrição, composição, cuidados, peso e medidas. Envie uma foto: o corte 4:5, a ordem das fotos e a principal. Destaque: *"você mesma cadastra produtos e fotos, sem depender de ninguém"*; inativar tira da vitrine sem apagar; "destaque" põe no carrossel.
2. **Estoque.** Quantidade por tamanho, editada direto na tabela. Destaque: *"a venda aprovada baixa o estoque sozinha; esgotou, o tamanho some da vitrine"*.
3. **Categorias.** Criar, renomear, ordenar.
4. **Medidas.** Dentro de cada categoria, a tabela de medidas que a cliente vê no produto.
5. **Pedidos.** O pedido que acabou de ser feito no celular dela, já pago: itens, tamanho, endereço, frete escolhido. Filtro por status.
6. **Etiquetas.** No pedido pago, **Gerar etiqueta**: sai o link para imprimir e o código de rastreio, e o pedido vira enviado. Destaque: *"na loja de verdade, a etiqueta é paga com o saldo do Melhor Envio, mais barata que no balcão dos Correios; você imprime, cola e posta"*. Em até um minuto o rastreio simulado marca o pedido como **entregue** sozinho (na loja de verdade, quando a transportadora entrega): o prazo de troca da cliente abre.
7. **Trocas.** A troca pedida na Parte 1: aprovar ou recusar, gerar a etiqueta de volta (quando é defeito) e concluir. Destaque: *"a cliente recebe um e-mail a cada passo"* (na demo, só no log).

### Parte 3: fechamento

O que pedir a ela no fim da chamada:

- [ ] **Aprovação** do site como está, ou a lista do que mudar antes do lançamento.
- [ ] **Contas no nome dela** (os dados e o dinheiro da loja são dela):
  - **Mercado Pago**: conta de vendedora (de preferência a mesma em que ela já recebe), para o Breno criar a aplicação Checkout Pro. O dinheiro das vendas cai direto lá.
  - **Domínio** `rosiestore.com.br` no Registro.br, no CPF ou CNPJ dela.
  - **Railway** (hospedagem da API e do banco), plano Hobby, com o cartão dela; o Breno é convidado no projeto.
  - **Melhor Envio**: conta da loja, com saldo para as etiquetas.
  - E-mail de contato da loja (para o Resend e para as respostas das clientes) e o número do WhatsApp.
- [ ] **Dados da loja para a etiqueta**: nome, CPF ou CNPJ, telefone e o endereço de onde ela despacha.
- [ ] **Produtos e fotos de verdade** para o lançamento (a mesma lista do "Antes do dia", com o catálogo inteiro).
- [ ] **Custos**, explicados sem surpresa (confira os valores na página de preços de cada serviço antes da chamada):
  - Railway Hobby: assinatura mensal em dólar (na ordem de US$ 5 por mês, com uma franquia de uso incluída), cobrada no cartão.
  - Domínio `.com.br`: anuidade no Registro.br (na ordem de R$ 40 por ano).
  - Netlify (site), Cloudinary (fotos) e Resend (e-mails): planos gratuitos dão conta do volume do lançamento.
  - Mercado Pago: sem mensalidade, uma tarifa por venda (varia com a forma de pagamento e o prazo de recebimento).
  - Melhor Envio: sem mensalidade, paga cada etiqueta com o saldo.
  - O que muda em relação a hoje: a loja deixa de depender só do direct e passa a ter custo fixo baixo (hospedagem e domínio) e custo por venda (tarifa e frete).

## Cuidados de segurança

- **O link vale só enquanto o túnel estiver aberto.** Ele expõe o site e a API do computador do Breno para quem tiver o endereço. `Ctrl+C` no script fecha o túnel e o link para de abrir na hora; nunca deixe o script rodando depois da chamada.
- **Mande o link só para a Roseane**, na hora da chamada; não publique nem poste. Cada subida gera um link novo, então um link antigo não vale de novo.
- **Admin e cliente de demonstração.** Use só o admin da demo (`admin@rosie.demo`, senha gerada pelo script) e a cliente de demonstração. Nunca a senha do admin real, a de desenvolvimento ou senhas pessoais. A senha do admin da demo aparece no terminal só na primeira subida e não fica salva em lugar nenhum.
- **Nenhum dado real exposto.** A demo usa o banco `rosie_demo`, sem os dados do `rosie_db`; endereço e documento da loja na etiqueta são de exemplo; nada de pedidos, CPFs ou endereços de clientes reais. Se a Roseane criar uma conta no celular, que use um e-mail qualquer.
- **Nada é cobrado nem enviado.** Pagamento, frete e etiqueta são simulados e os e-mails ficam no log; o perfil `demo` ignora qualquer credencial real.
- **Compartilhe só a janela do navegador** no Teams: o terminal mostra o link, a senha da primeira subida e os logs.
- As fotos enviadas na demo vão para o Cloudinary do Breno; apague as de teste depois, se quiser.
- O perfil `demo` nunca vai para o Railway: o deploy sobe com `prod`, e a API não sobe com os dois juntos.
