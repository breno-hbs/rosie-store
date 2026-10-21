package db.migration;

import com.rosie.backend.model.Slug;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

// Slug da categoria (#88), para a página /categoria/{slug}. Em Java, e não em SQL, para as categorias que já
// existem receberem o mesmo slug que o CategoriaService gera (tirar acentos não tem equivalente simples no
// MySQL). Repetido ganha sufixo pela ordem de criação: a primeira fica com "vestido", a segunda com "vestido-2".
// Também cria a tabela dos slugs de antes de renomear, que mantém os links antigos funcionando.
public class V3__slug_categoria extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conexao = context.getConnection();
        try (Statement comando = conexao.createStatement()) {
            comando.execute("ALTER TABLE `categoria` ADD COLUMN `slug` varchar(90) DEFAULT NULL");

            Set<String> usados = new HashSet<>();
            try (ResultSet categorias = comando.executeQuery("SELECT `id`, `nome` FROM `categoria` ORDER BY `id`");
                 PreparedStatement atualizar = conexao.prepareStatement(
                         "UPDATE `categoria` SET `slug` = ? WHERE `id` = ?")) {
                while (categorias.next()) {
                    String base = Slug.de(categorias.getString("nome"));
                    String slug = base;
                    for (int numero = 2; !usados.add(slug); numero++) {
                        slug = Slug.candidato(base, numero);
                    }
                    atualizar.setString(1, slug);
                    atualizar.setLong(2, categorias.getLong("id"));
                    atualizar.executeUpdate();
                }
            }

            comando.execute("""
                    ALTER TABLE `categoria`
                      MODIFY `slug` varchar(90) NOT NULL,
                      ADD UNIQUE KEY `uk_categoria_slug` (`slug`)
                    """);
            comando.execute("""
                    CREATE TABLE `categoria_slug_antigo` (
                      `id` bigint NOT NULL AUTO_INCREMENT,
                      `slug` varchar(90) NOT NULL,
                      `categoria_id` bigint NOT NULL,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_categoria_slug_antigo_slug` (`slug`),
                      KEY `fk_categoria_slug_antigo_categoria` (`categoria_id`),
                      CONSTRAINT `fk_categoria_slug_antigo_categoria` FOREIGN KEY (`categoria_id`) REFERENCES `categoria` (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                    """);
        }
    }
}
