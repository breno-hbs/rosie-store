package com.rosie.backend.controller;

import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.service.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// Upload por HTTP de verdade (Tomcat em porta aleatória), sem CLOUDINARY_URL: confere o 503 de
// quem não configurou o storage e o 400 do limite de tamanho aplicado pelo próprio servidor.
// Sem @Transactional (o servidor roda em outra thread): o produto criado é apagado no fim.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "rosie.cloudinary.url=")
class ProdutoImagemSemArmazenamentoTest {

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final String FRONTEIRA = "----rosie-teste";

    @LocalServerPort
    private int porta;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private final HttpClient http = HttpClient.newHttpClient();
    private Produto produto;

    @AfterEach
    void apagarProduto() {
        if (produto != null) {
            produtoRepository.deleteById(produto.getId());
            categoriaRepository.deleteById(produto.getCategoria().getId());
        }
    }

    @Test
    void semCloudinaryConfiguradoUploadResponde503() throws Exception {
        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Vestido sem storage " + System.nanoTime()).build());
        produto = produtoRepository.save(Produto.builder()
                .nome("Produto sem storage")
                .preco(new BigDecimal("99.90"))
                .categoria(categoria)
                .build());

        HttpResponse<String> resposta = enviar(produto.getId(), JPG);

        assertThat(resposta.statusCode()).isEqualTo(503);
        assertThat(resposta.body())
                .contains("\"codigo\":\"ARMAZENAMENTO_INDISPONIVEL\"")
                .contains("CLOUDINARY_URL");
    }

    @Test
    void arquivoAcimaDoLimiteDoServidorResponde400() throws Exception {
        // o multipart é recusado antes do controller, então nem precisa de produto
        HttpResponse<String> resposta = enviar(-1L, Arrays.copyOf(JPG, 6 * 1024 * 1024));

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.body()).contains("\"codigo\":\"ARQUIVO_MUITO_GRANDE\"");
    }

    private HttpResponse<String> enviar(Long produtoId, byte[] conteudo) throws Exception {
        Usuario admin = Usuario.builder().id(1L).nome("Roseane Teste").role(Role.ADMIN).build();

        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        corpo.write(("--" + FRONTEIRA + "\r\n"
                + "Content-Disposition: form-data; name=\"arquivo\"; filename=\"foto.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        corpo.write(conteudo);
        corpo.write(("\r\n--" + FRONTEIRA + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest requisicao = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + porta + "/admin/produtos/" + produtoId + "/imagens"))
                .header("Authorization", "Bearer " + tokenService.gerar(admin))
                .header("Content-Type", "multipart/form-data; boundary=" + FRONTEIRA)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
                .build();
        return http.send(requisicao, HttpResponse.BodyHandlers.ofString());
    }
}
