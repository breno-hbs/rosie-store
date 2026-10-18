package com.rosie.backend.controller;

import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoImagem;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.ArmazenamentoImagensFalso;
import com.rosie.backend.service.TokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Fotos do produto (/admin/produtos/{id}/imagens) contra o MySQL, com token de admin e o
// Cloudinary trocado por um falso; cada teste é desfeito no fim
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(ArmazenamentoImagensFalso.Config.class)
class ProdutoImagemAdminControllerTest {

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ArmazenamentoImagensFalso armazenamento;

    @Autowired
    private EntityManager entityManager;

    private String tokenAdmin;
    private String tokenCliente;
    private Produto produto;
    private List<Long> idsAntigos;

    @BeforeEach
    void setUp() {
        armazenamento.limpar();
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste")
                .email("admin-fotos@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.ADMIN)
                .build());
        Usuario cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Teste")
                .email("cliente-fotos@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.CLIENTE)
                .build());
        tokenAdmin = tokenService.gerar(admin);
        tokenCliente = tokenService.gerar(cliente);

        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Vestido fotos " + System.nanoTime()).build());
        produto = Produto.builder()
                .nome("Conjunto Serpente")
                .preco(new BigDecimal("189.00"))
                .categoria(categoria)
                .build();
        produto.getImagens().add(ProdutoImagem.builder().produto(produto).url("https://placehold.co/a").ordem(0).build());
        produto.getImagens().add(ProdutoImagem.builder().produto(produto).url("https://placehold.co/b").ordem(1).build());
        produtoRepository.save(produto);
        entityManager.flush();
        entityManager.clear();
        idsAntigos = produto.getImagens().stream().map(ProdutoImagem::getId).toList();
    }

    @Test
    void uploadResponde201ComAFotoNoFimDaGaleria() throws Exception {
        mockMvc.perform(comAdmin(multipart(rota(produto.getId())).file(arquivo("foto.jpg", JPG))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.url").value("https://res.cloudinary.test/rosie/produtos/teste-1.jpg"))
                .andExpect(jsonPath("$.ordem").value(2));
        entityManager.clear();

        // o painel recebe as fotos com id; a loja, só as URLs na ordem
        mockMvc.perform(comAdmin(get("/admin/produtos/" + produto.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagens.length()").value(3))
                .andExpect(jsonPath("$.imagens[0].id").value(idsAntigos.get(0)))
                .andExpect(jsonPath("$.imagens[0].url").value("https://placehold.co/a"))
                .andExpect(jsonPath("$.imagens[0].ordem").value(0))
                .andExpect(jsonPath("$.imagens[2].url").value("https://res.cloudinary.test/rosie/produtos/teste-1.jpg"));
        mockMvc.perform(get("/produtos/" + produto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagens[2]").value("https://res.cloudinary.test/rosie/produtos/teste-1.jpg"));
    }

    @Test
    void formatoNaoSuportadoResponde400() throws Exception {
        byte[] gif = "GIF89a\u0001\0\u0001\0".getBytes(StandardCharsets.ISO_8859_1);

        mockMvc.perform(comAdmin(multipart(rota(produto.getId())).file(arquivo("foto.gif", gif))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Formato não aceito: envie a foto em JPG, PNG ou WEBP"))
                .andExpect(jsonPath("$.codigo").value("FORMATO_NAO_SUPORTADO"));
        assertThat(armazenamento.enviados).isEmpty();
    }

    @Test
    void arquivoAcimaDoLimiteResponde400() throws Exception {
        byte[] grande = Arrays.copyOf(JPG, 5 * 1024 * 1024 + 1);

        mockMvc.perform(comAdmin(multipart(rota(produto.getId())).file(arquivo("grande.jpg", grande))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A foto passa de 5 MB: diminua o arquivo e tente de novo"))
                .andExpect(jsonPath("$.codigo").value("ARQUIVO_MUITO_GRANDE"));
    }

    @Test
    void semOCampoArquivoResponde400() throws Exception {
        mockMvc.perform(comAdmin(multipart(rota(produto.getId())).file(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", JPG))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Envie a foto no campo arquivo"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void uploadEmProdutoInexistenteResponde404() throws Exception {
        mockMvc.perform(comAdmin(multipart(rota(-1L)).file(arquivo("foto.jpg", JPG))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void removerResponde204ERenumera() throws Exception {
        mockMvc.perform(comAdmin(delete(rota(produto.getId()) + "/" + idsAntigos.get(0))))
                .andExpect(status().isNoContent());
        entityManager.clear();

        mockMvc.perform(comAdmin(get("/admin/produtos/" + produto.getId())))
                .andExpect(jsonPath("$.imagens.length()").value(1))
                .andExpect(jsonPath("$.imagens[0].id").value(idsAntigos.get(1)))
                .andExpect(jsonPath("$.imagens[0].ordem").value(0));
    }

    @Test
    void removerFotoInexistenteResponde404() throws Exception {
        mockMvc.perform(comAdmin(delete(rota(produto.getId()) + "/-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void reordenarResponde200NaNovaOrdem() throws Exception {
        mockMvc.perform(comAdmin(put(rota(produto.getId()) + "/ordem"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imagens\":[" + idsAntigos.get(1) + "," + idsAntigos.get(0) + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(idsAntigos.get(1)))
                .andExpect(jsonPath("$[0].ordem").value(0))
                .andExpect(jsonPath("$[1].id").value(idsAntigos.get(0)))
                .andExpect(jsonPath("$[1].ordem").value(1));
        entityManager.clear();

        mockMvc.perform(get("/produtos/" + produto.getId()))
                .andExpect(jsonPath("$.imagens[0]").value("https://placehold.co/b"));
    }

    @Test
    void reordenarComListaIncompletaOuVaziaResponde400() throws Exception {
        mockMvc.perform(comAdmin(put(rota(produto.getId()) + "/ordem"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imagens\":[" + idsAntigos.get(1) + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Envie todas as fotos do produto, cada uma uma vez"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));

        mockMvc.perform(comAdmin(put(rota(produto.getId()) + "/ordem"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imagens\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe a ordem das fotos"));
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(multipart(rota(produto.getId())).file(arquivo("foto.jpg", JPG)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        assertThat(armazenamento.enviados).isEmpty();
    }

    @Test
    void tokenDeClienteResponde403() throws Exception {
        mockMvc.perform(delete(rota(produto.getId()) + "/" + idsAntigos.get(0))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    void requisicaoQueNaoEMultipartResponde400() throws Exception {
        mockMvc.perform(comAdmin(post(rota(produto.getId())))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .content("sem partes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    private MockMultipartFile arquivo(String nome, byte[] conteudo) {
        return new MockMultipartFile("arquivo", nome, "image/jpeg", conteudo);
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> B comAdmin(B requisicao) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin);
    }

    private String rota(Long id) {
        return "/admin/produtos/" + id + "/imagens";
    }
}
