package com.rosie.backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// CRUD do painel contra o MySQL, com token de admin; cada teste roda numa transação desfeita no fim
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProdutoAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private TokenService tokenService;

    private String tokenAdmin;
    private String tokenCliente;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste")
                .email("admin-produtos@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.ADMIN)
                .build());
        Usuario cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Teste")
                .email("cliente-produtos@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.CLIENTE)
                .build());
        tokenAdmin = tokenService.gerar(admin);
        tokenCliente = tokenService.gerar(cliente);
        categoria = categoriaRepository.save(Categoria.builder().nome("Vestido teste " + System.nanoTime()).build());
    }

    @Test
    void criarRetorna201ComProdutoNoFormatoDoDetalhe() throws Exception {
        mockMvc.perform(comAdmin(post("/admin/produtos")).content(corpo("Conjunto Serpente", "189.00", categoria.getId(),
                        "[{\"tamanho\":\"M\",\"quantidade\":5},{\"tamanho\":\"P\",\"quantidade\":3}]", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Conjunto Serpente"))
                .andExpect(jsonPath("$.preco").value(189.00))
                .andExpect(jsonPath("$.categoria.id").value(categoria.getId()))
                .andExpect(jsonPath("$.imagens").isEmpty())
                .andExpect(jsonPath("$.estoque[0].tamanho").value("P"))
                .andExpect(jsonPath("$.estoque[1].quantidade").value(5))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.destaque").value(true));
    }

    @Test
    void fluxoCompletoEditaDesativaEReativa() throws Exception {
        String criado = mockMvc.perform(comAdmin(post("/admin/produtos"))
                        .content(corpo("Original", "100.00", categoria.getId(), "[{\"tamanho\":\"GG\",\"quantidade\":2}]", false)))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(criado, "$.id");

        mockMvc.perform(comAdmin(put("/admin/produtos/" + id)).content(corpoEdicao("Editado", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Editado"))
                .andExpect(jsonPath("$.destaque").value(true))
                .andExpect(jsonPath("$.estoque[0].tamanho").value("GG"));

        mockMvc.perform(comAdmin(delete("/admin/produtos/" + id)))
                .andExpect(status().isNoContent());

        // some da vitrine, mas continua no painel como inativo
        mockMvc.perform(get("/produtos/" + id))
                .andExpect(status().isNotFound());
        mockMvc.perform(comAdmin(get("/admin/produtos/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(comAdmin(get("/admin/produtos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].ativo").value(false))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].estoqueTotal").value(2))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].categoria.nome").value(categoria.getNome()));

        mockMvc.perform(comAdmin(put("/admin/produtos/" + id)).content("""
                        {"nome":"Editado","preco":120.00,"categoriaId":%d,"ativo":true}
                        """.formatted(categoria.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.destaque").value(true));
        mockMvc.perform(get("/produtos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").doesNotExist())
                .andExpect(jsonPath("$.destaque").doesNotExist());
    }

    // #47: um formulário aberto antes de mexer no estoque reenviava o estoque antigo
    @Test
    void editarComEstoqueNoCorpoNaoAlteraOEstoque() throws Exception {
        String criado = mockMvc.perform(comAdmin(post("/admin/produtos")).content(corpo("Original", "100.00",
                        categoria.getId(), "[{\"tamanho\":\"P\",\"quantidade\":3},{\"tamanho\":\"GG\",\"quantidade\":0}]", false)))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(criado, "$.id");

        mockMvc.perform(comAdmin(put("/admin/produtos/" + id + "/estoque"))
                        .content("{\"estoque\":[{\"tamanho\":\"P\",\"quantidade\":0},{\"tamanho\":\"GG\",\"quantidade\":2}]}"))
                .andExpect(status().isOk());

        // corpo antigo, com o estoque de quando a edição abriu e um tamanho a mais
        mockMvc.perform(comAdmin(put("/admin/produtos/" + id)).content(corpo("Editado", "100.00", categoria.getId(),
                        "[{\"tamanho\":\"P\",\"quantidade\":3},{\"tamanho\":\"M\",\"quantidade\":9}]", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Editado"))
                .andExpect(jsonPath("$.estoque.length()").value(2))
                .andExpect(jsonPath("$.estoque[0].tamanho").value("P"))
                .andExpect(jsonPath("$.estoque[0].quantidade").value(0))
                .andExpect(jsonPath("$.estoque[1].tamanho").value("GG"))
                .andExpect(jsonPath("$.estoque[1].quantidade").value(2));

        mockMvc.perform(comAdmin(get("/admin/produtos/" + id)))
                .andExpect(jsonPath("$.estoque.length()").value(2))
                .andExpect(jsonPath("$.estoque[0].quantidade").value(0))
                .andExpect(jsonPath("$.estoque[1].quantidade").value(2));
    }

    @Test
    void nomeVazioRetorna400() throws Exception {
        esperarDadosInvalidos(corpo("  ", "100.00", categoria.getId(), "[]", false), "Informe o nome do produto");
    }

    @Test
    void precoZeroRetorna400() throws Exception {
        esperarDadosInvalidos(corpo("Produto", "0", categoria.getId(), "[]", false), "O preço deve ser maior que zero");
    }

    @Test
    void categoriaInexistenteRetorna400() throws Exception {
        esperarDadosInvalidos(corpo("Produto", "100.00", -1L, "[]", false), "Categoria não encontrada");
    }

    @Test
    void tamanhoForaDaGradeRetorna400() throws Exception {
        esperarDadosInvalidos(corpo("Produto", "100.00", categoria.getId(), "[{\"tamanho\":\"XG\",\"quantidade\":1}]", false),
                "Tamanho inválido: use P, M, G ou GG");
    }

    @Test
    void tamanhoRepetidoRetorna400() throws Exception {
        esperarDadosInvalidos(corpo("Produto", "100.00", categoria.getId(),
                "[{\"tamanho\":\"P\",\"quantidade\":1},{\"tamanho\":\"P\",\"quantidade\":2}]", false),
                "Tamanho P repetido no estoque");
    }

    @Test
    void quantidadeNegativaRetorna400() throws Exception {
        esperarDadosInvalidos(corpo("Produto", "100.00", categoria.getId(), "[{\"tamanho\":\"P\",\"quantidade\":-1}]", false),
                "A quantidade não pode ser negativa");
    }

    @Test
    void pacoteDoFreteSoApareceNoPainelEEdicaoSemPacoteMantem() throws Exception {
        String criado = mockMvc.perform(comAdmin(post("/admin/produtos")).content(corpoComPacote(
                        "{\"pesoKg\":0.85,\"alturaCm\":8,\"larguraCm\":25,\"comprimentoCm\":35}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pacote.pesoKg").value(0.85))
                .andExpect(jsonPath("$.pacote.alturaCm").value(8))
                .andExpect(jsonPath("$.pacote.larguraCm").value(25))
                .andExpect(jsonPath("$.pacote.comprimentoCm").value(35))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(criado, "$.id");

        // o DTO público não leva o pacote
        mockMvc.perform(get("/produtos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pacote").doesNotExist());

        // PUT sem "pacote" (como o Reativar da lista) mantém o peso e as medidas
        mockMvc.perform(comAdmin(put("/admin/produtos/" + id)).content(corpoEdicao("Editado", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pacote.pesoKg").value(0.85))
                .andExpect(jsonPath("$.pacote.comprimentoCm").value(35));

        // PUT com o pacote troca os quatro: o que vem nulo volta para o padrão
        mockMvc.perform(comAdmin(put("/admin/produtos/" + id)).content("""
                        {"nome":"Editado","preco":120.00,"categoriaId":%d,"pacote":{"pesoKg":1.2,"alturaCm":null}}
                        """.formatted(categoria.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pacote.pesoKg").value(1.2))
                .andExpect(jsonPath("$.pacote.alturaCm").value(nullValue()))
                .andExpect(jsonPath("$.pacote.larguraCm").value(nullValue()))
                .andExpect(jsonPath("$.pacote.comprimentoCm").value(nullValue()));
    }

    @Test
    void produtoSemPacoteVemComOsCamposNulos() throws Exception {
        mockMvc.perform(comAdmin(post("/admin/produtos")).content(corpo("Produto", "100.00", categoria.getId(), "[]", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pacote.pesoKg").value(nullValue()))
                .andExpect(jsonPath("$.pacote.alturaCm").value(nullValue()));
    }

    @Test
    void pesoZeroOuAcimaDe30KgRetorna400() throws Exception {
        esperarDadosInvalidos(corpoComPacote("{\"pesoKg\":0}"), "O peso deve ser maior que zero");
        esperarDadosInvalidos(corpoComPacote("{\"pesoKg\":-0.3}"), "O peso deve ser maior que zero");
        esperarDadosInvalidos(corpoComPacote("{\"pesoKg\":30.5}"), "O peso pode ser de até 30 kg");
        esperarDadosInvalidos(corpoComPacote("{\"pesoKg\":0.1234}"), "Peso inválido: use até 3 casas decimais");
    }

    @Test
    void medidaForaDe1A100CmRetorna400() throws Exception {
        esperarDadosInvalidos(corpoComPacote("{\"alturaCm\":0}"), "A altura da embalagem deve ser de pelo menos 1 cm");
        esperarDadosInvalidos(corpoComPacote("{\"larguraCm\":101}"), "A largura da embalagem pode ser de até 100 cm");
        esperarDadosInvalidos(corpoComPacote("{\"comprimentoCm\":-5}"),
                "O comprimento da embalagem deve ser de pelo menos 1 cm");
    }

    @Test
    void pacoteInvalidoNaEdicaoRetorna400() throws Exception {
        String criado = mockMvc.perform(comAdmin(post("/admin/produtos")).content(corpo("Produto", "100.00",
                        categoria.getId(), "[]", false)))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(criado, "$.id");

        mockMvc.perform(comAdmin(put("/admin/produtos/" + id)).content("""
                        {"nome":"Produto","preco":100.00,"categoriaId":%d,"pacote":{"comprimentoCm":150}}
                        """.formatted(categoria.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("O comprimento da embalagem pode ser de até 100 cm"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void produtoInexistenteRetorna404() throws Exception {
        mockMvc.perform(comAdmin(put("/admin/produtos/-1")).content(corpo("Produto", "100.00", categoria.getId(), "[]", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mockMvc.perform(comAdmin(delete("/admin/produtos/-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/admin/produtos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void tokenDeClienteRetorna403() throws Exception {
        mockMvc.perform(post("/admin/produtos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Produto", "100.00", categoria.getId(), "[]", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    private void esperarDadosInvalidos(String corpo, String mensagem) throws Exception {
        mockMvc.perform(comAdmin(post("/admin/produtos")).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(mensagem))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    private MockHttpServletRequestBuilder comAdmin(MockHttpServletRequestBuilder requisicao) {
        return requisicao
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String corpo(String nome, String preco, Long categoriaId, String estoque, boolean destaque) {
        return """
                {"nome":"%s","descricao":"Tecido leve","preco":%s,"categoriaId":%d,"destaque":%b,"estoque":%s}
                """.formatted(nome, preco, categoriaId, destaque, estoque);
    }

    private String corpoComPacote(String pacote) {
        return """
                {"nome":"Calça Jeans","preco":149.90,"categoriaId":%d,"estoque":[],"pacote":%s}
                """.formatted(categoria.getId(), pacote);
    }

    private String corpoEdicao(String nome, boolean destaque) {
        return """
                {"nome":"%s","descricao":"Tecido leve","preco":120.00,"categoriaId":%d,"destaque":%b}
                """.formatted(nome, categoria.getId(), destaque);
    }
}
