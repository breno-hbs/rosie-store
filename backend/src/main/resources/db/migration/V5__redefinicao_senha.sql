-- Esqueci minha senha (#93), para a cliente e para o admin.
--
-- versao_credencial: sobe a cada troca de senha (pelo link ou na Minha Conta). O JWT leva a versão do momento
-- do login (claim "vc") e o filtro do JWT recusa o token de uma versão anterior: as sessões abertas antes da
-- troca deixam de valer na hora. Contas que já existem começam na versão 0, a mesma dos tokens sem a claim.
ALTER TABLE `usuario`
  ADD COLUMN `versao_credencial` int NOT NULL DEFAULT 0;

-- Link de redefinição pedido e ainda não usado: um por usuário (um pedido novo substitui o anterior), com só o
-- hash SHA-256 do token, como na troca de e-mail (email_pendente, #61). Prazo no horário de São Paulo.
CREATE TABLE `redefinicao_senha` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `usuario_id` bigint NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  `expira_em` datetime(6) NOT NULL,
  `solicitado_em` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_redefinicao_senha_usuario` (`usuario_id`),
  UNIQUE KEY `uk_redefinicao_senha_token` (`token_hash`),
  CONSTRAINT `fk_redefinicao_senha_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
