-- Banners do carrossel da Home (#89), gerenciados no painel. As imagens ficam no Cloudinary (url + public_id,
-- como em produto_imagem); a de celular é opcional. Período em horário de São Paulo, sem fuso (como o resto do
-- banco); início ou fim nulos = sem limite daquele lado.

CREATE TABLE `banner` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `titulo` varchar(80) DEFAULT NULL,
  `texto` varchar(200) DEFAULT NULL,
  `texto_botao` varchar(30) DEFAULT NULL,
  `link` varchar(300) DEFAULT NULL,
  `texto_alternativo` varchar(200) NOT NULL,
  `imagem_desktop_url` varchar(500) NOT NULL,
  `imagem_desktop_public_id` varchar(255) DEFAULT NULL,
  `imagem_celular_url` varchar(500) DEFAULT NULL,
  `imagem_celular_public_id` varchar(255) DEFAULT NULL,
  `ativo` bit(1) NOT NULL,
  `inicio` datetime(6) DEFAULT NULL,
  `fim` datetime(6) DEFAULT NULL,
  `ordem` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_banner_ordem` (`ordem`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
