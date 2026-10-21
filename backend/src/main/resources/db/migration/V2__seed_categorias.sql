-- Categorias iniciais da loja (#29). A Roseane cria outras pelo painel (/admin/categorias).
-- Idempotente: o banco de desenvolvimento, marcado como V1 pelo baseline, já pode ter estas
-- categorias; a comparação segue a collation da coluna (utf8mb4_0900_ai_ci), sem diferenciar
-- maiúsculas nem acentos, a mesma regra da chave única de `nome`.
INSERT INTO `categoria` (`nome`)
SELECT nova.nome
FROM (
    SELECT 'Vestido' AS nome
    UNION ALL SELECT 'Calça'
    UNION ALL SELECT 'Camiseta'
    UNION ALL SELECT 'Cropped/Body'
) AS nova
WHERE NOT EXISTS (
    SELECT 1 FROM `categoria` existente WHERE existente.`nome` = nova.nome
);
