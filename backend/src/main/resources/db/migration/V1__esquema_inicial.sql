-- Esquema inicial (#74): o mesmo que o Hibernate gerava com ddl-auto=update até a v1.0, exportado de um
-- banco limpo com mysqldump --no-data. Os nomes das constraints (UK... e FK...) são os que o Hibernate
-- gera, para bater com os bancos de desenvolvimento criados antes do Flyway (baseline na versão 1).
-- Daqui em diante, toda mudança de esquema entra numa migração nova (V2__..., V3__...): nunca edite esta.

CREATE TABLE `usuario` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(150) NOT NULL,
  `excluida_em` datetime(6) DEFAULT NULL,
  `nome` varchar(150) NOT NULL,
  `privacidade_aceita_em` datetime(6) DEFAULT NULL,
  `privacidade_versao` varchar(20) DEFAULT NULL,
  `role` enum('ADMIN','CLIENTE') NOT NULL,
  `senha_hash` varchar(255) NOT NULL,
  `telefone` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5171l57faosmj8myawaucatdw` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `categoria` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nome` varchar(80) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKprx5elpv558ah8pk8x18u56yc` (`nome`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `categoria_medida` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nome` varchar(40) NOT NULL,
  `ordem` int NOT NULL,
  `valor_g` varchar(20) DEFAULT NULL,
  `valor_gg` varchar(20) DEFAULT NULL,
  `valor_m` varchar(20) DEFAULT NULL,
  `valor_p` varchar(20) DEFAULT NULL,
  `categoria_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKeh6u7n92r3vgfbw26gwr12124` (`categoria_id`),
  CONSTRAINT `FKeh6u7n92r3vgfbw26gwr12124` FOREIGN KEY (`categoria_id`) REFERENCES `categoria` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `produto` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `altura_cm` int DEFAULT NULL,
  `ativo` bit(1) NOT NULL,
  `composicao` text,
  `comprimento_cm` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `descricao` text,
  `destaque` bit(1) NOT NULL DEFAULT b'0',
  `largura_cm` int DEFAULT NULL,
  `nome` varchar(150) NOT NULL,
  `peso_kg` decimal(5,3) DEFAULT NULL,
  `preco` decimal(10,2) NOT NULL,
  `categoria_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKopu9jggwnamfv0c8k2ri3kx0a` (`categoria_id`),
  CONSTRAINT `FKopu9jggwnamfv0c8k2ri3kx0a` FOREIGN KEY (`categoria_id`) REFERENCES `categoria` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `produto_estoque` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quantidade` int NOT NULL,
  `tamanho` enum('G','GG','M','P') NOT NULL,
  `produto_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKf1xtadqjvjna6xssivdwh151r` (`produto_id`,`tamanho`),
  CONSTRAINT `FK1t3515wgcxluunsaoqbylxayx` FOREIGN KEY (`produto_id`) REFERENCES `produto` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `produto_imagem` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enquadramento` enum('AUTOMATICO','CENTRO','TOPO') DEFAULT NULL,
  `ordem` int NOT NULL,
  `public_id` varchar(255) DEFAULT NULL,
  `url` varchar(500) NOT NULL,
  `produto_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8myxvllnyj328ty17l26enss` (`produto_id`),
  CONSTRAINT `FK8myxvllnyj328ty17l26enss` FOREIGN KEY (`produto_id`) REFERENCES `produto` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `carrinho_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quantidade` int NOT NULL,
  `tamanho` enum('G','GG','M','P') NOT NULL,
  `produto_id` bigint NOT NULL,
  `usuario_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKc5kbrmee120so87ws7j4rua59` (`usuario_id`,`produto_id`,`tamanho`),
  KEY `FKd25u6jmgha2difw0d696sffja` (`produto_id`),
  CONSTRAINT `FKanm5e6f5qlpxa8914w9h712ir` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`),
  CONSTRAINT `FKd25u6jmgha2difw0d696sffja` FOREIGN KEY (`produto_id`) REFERENCES `produto` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `endereco` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `arquivado` bit(1) NOT NULL DEFAULT b'0',
  `bairro` varchar(100) NOT NULL,
  `cep` varchar(9) NOT NULL,
  `cidade` varchar(100) NOT NULL,
  `complemento` varchar(100) DEFAULT NULL,
  `estado` varchar(2) NOT NULL,
  `logradouro` varchar(200) NOT NULL,
  `numero` varchar(20) NOT NULL,
  `usuario_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKekdpb8k6gmp3lllla9d1qgmxk` (`usuario_id`),
  CONSTRAINT `FKekdpb8k6gmp3lllla9d1qgmxk` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `email_pendente` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `expira_em` datetime(6) NOT NULL,
  `novo_email` varchar(150) NOT NULL,
  `solicitado_em` datetime(6) NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  `usuario_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKnqt8ptftv58fvy71dno6mcxax` (`token_hash`),
  UNIQUE KEY `UKph9v8gksrfsergtjehj0ir4m7` (`usuario_id`),
  CONSTRAINT `FK9ggikwl092n00yrn2lq8gqg51` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- status, forma_pagamento e origem_entrega são VARCHAR (@JdbcTypeCode na entidade), mas o Hibernate gera um
-- CHECK com os valores do enum: um valor novo no enum Java precisa de uma migração que troque o CHECK
-- (ALTER TABLE pedido DROP CHECK pedido_chk_3, ADD CONSTRAINT pedido_chk_3 CHECK (...)). Vale também para troca.
CREATE TABLE `pedido` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `codigo_rastreio` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `data_entrega` date DEFAULT NULL,
  `melhor_envio_id` varchar(50) DEFAULT NULL,
  `etiqueta_gerada_em` datetime(6) DEFAULT NULL,
  `forma_pagamento` varchar(20) DEFAULT NULL,
  `origem_entrega` varchar(20) DEFAULT NULL,
  `pagamento_id` varchar(50) DEFAULT NULL,
  `pago_em` datetime(6) DEFAULT NULL,
  `pendencia` varchar(500) DEFAULT NULL,
  `prazo_frete_dias` int DEFAULT NULL,
  `preferencia_id` varchar(100) DEFAULT NULL,
  `rastreio_consultado_em` datetime(6) DEFAULT NULL,
  `servico_frete` varchar(50) DEFAULT NULL,
  `servico_frete_id` int DEFAULT NULL,
  `status` varchar(30) NOT NULL,
  `transportadora` varchar(50) DEFAULT NULL,
  `url_etiqueta` varchar(500) DEFAULT NULL,
  `url_pagamento` varchar(500) DEFAULT NULL,
  `valor_frete` decimal(10,2) NOT NULL,
  `valor_total` decimal(10,2) NOT NULL,
  `endereco_id` bigint NOT NULL,
  `usuario_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKsot2og0lvhm2t4kuu9s2obef3` (`endereco_id`),
  KEY `FK6uxomgomm93vg965o8brugt00` (`usuario_id`),
  CONSTRAINT `FK6uxomgomm93vg965o8brugt00` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`),
  CONSTRAINT `FKsot2og0lvhm2t4kuu9s2obef3` FOREIGN KEY (`endereco_id`) REFERENCES `endereco` (`id`),
  CONSTRAINT `pedido_chk_1` CHECK ((`forma_pagamento` in (_utf8mb4'CARTAO',_utf8mb4'PIX'))),
  CONSTRAINT `pedido_chk_2` CHECK ((`origem_entrega` in (_utf8mb4'MANUAL',_utf8mb4'RASTREIO'))),
  CONSTRAINT `pedido_chk_3` CHECK ((`status` in (_utf8mb4'AGUARDANDO_PAGAMENTO',_utf8mb4'PAGAMENTO_RECUSADO',_utf8mb4'PAGO',_utf8mb4'PAGO_COM_PENDENCIA',_utf8mb4'ENVIADO',_utf8mb4'ENTREGUE',_utf8mb4'CANCELADO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `pedido_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `preco_unitario` decimal(10,2) NOT NULL,
  `quantidade` int NOT NULL,
  `tamanho` enum('G','GG','M','P') NOT NULL,
  `pedido_id` bigint NOT NULL,
  `produto_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKeyouxfvoi291lpo5168e6wpej` (`pedido_id`),
  KEY `FK8eyfr31j751fjws2y012awmpg` (`produto_id`),
  CONSTRAINT `FK8eyfr31j751fjws2y012awmpg` FOREIGN KEY (`produto_id`) REFERENCES `produto` (`id`),
  CONSTRAINT `FKeyouxfvoi291lpo5168e6wpej` FOREIGN KEY (`pedido_id`) REFERENCES `pedido` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `troca` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `codigo_rastreio_volta` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `envio_volta_id` varchar(50) DEFAULT NULL,
  `erro_etiqueta_volta` varchar(500) DEFAULT NULL,
  `etiqueta_volta_gerada_em` datetime(6) DEFAULT NULL,
  `motivo` enum('DEFEITO','NAO_SERVIU') NOT NULL,
  `observacao` varchar(1000) DEFAULT NULL,
  `quantidade` int NOT NULL,
  `resposta` varchar(1000) DEFAULT NULL,
  `status` varchar(30) NOT NULL,
  `tamanho` varchar(5) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `url_etiqueta_volta` varchar(500) DEFAULT NULL,
  `pedido_id` bigint NOT NULL,
  `produto_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKstyfmfcub9m90i3pk7qintved` (`pedido_id`),
  KEY `FKln9lswso403no5rgy5cwc6qxs` (`produto_id`),
  CONSTRAINT `FKln9lswso403no5rgy5cwc6qxs` FOREIGN KEY (`produto_id`) REFERENCES `produto` (`id`),
  CONSTRAINT `FKstyfmfcub9m90i3pk7qintved` FOREIGN KEY (`pedido_id`) REFERENCES `pedido` (`id`),
  CONSTRAINT `troca_chk_1` CHECK ((`status` in (_utf8mb4'SOLICITADA',_utf8mb4'APROVADA',_utf8mb4'RECUSADA',_utf8mb4'CONCLUIDA'))),
  CONSTRAINT `troca_chk_2` CHECK ((`tamanho` in (_utf8mb4'P',_utf8mb4'M',_utf8mb4'G',_utf8mb4'GG')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
