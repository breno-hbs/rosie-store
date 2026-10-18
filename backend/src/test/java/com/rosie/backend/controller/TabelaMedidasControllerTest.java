package com.rosie.backend.controller;

import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.MedidaCategoriaRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Tabela de medidas (#20): leitura pública e edição com token de admin, contra o MySQL.
// Cada teste roda numa transação desfeita no fim.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TabelaMedidasControllerTest {

    private static final String TABELA_VESTIDO = """
            {"medidas": [
              {"nome": "Busto", "valores": {"P": "84–88", "M": "89–93", "G": "94–98", "GG": "99–104"}},
              {"nome": "Comprimento", "valores": {"M": "92"}}
            ]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MedidaCategoriaRepository medidaRepository;

    @Autowired
    private TokenService tokenService;

    private String tokenAdmin;
    private String tokenCliente;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        tokenAdmin = tokenService.gerar(usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste")
                .email("admin-medidas@rosie.test")
                .senhaHash("nao-usada")
                .role(Role.ADMIN)
                .build()));
        tokenCliente = tokenService.gerar(usuarioRepository.save(Usuario.builder()
                .nome("Cliente Teste")
                .email("cliente-medidas@rosie.test")
                .senhaHash("nao-usada")
                .role(Role.CLIENTE)
                .build()));
        categoria = categoriaRepository.save(Categoria.builder().nome("Medidas " + System.nanoTime()).build());
    }

    @Test
    void publicoSemTabelaRetornaListaVazia() throws Exception {
        mockMvc.perform(get(caminhoPublico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaId").value(categoria.getId()))
                .andExpect(jsonPath("$.categoria").value(categoria.getNome()))
                .andExpect(jsonPath("$.unidade").value("cm"))
                .andExpect(jsonPath("$.medidas.length()").value(0));
    }

    @Test
    void publicoMostraATabelaSalvaPeloAdminSemToken() throws Exception {
        mockMvc.perform(comoAdmin(put(caminhoAdmin())).content(TABELA_VESTIDO))
                .andExpect(status().isOk());

        mockMvc.perform(get(caminhoPublico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medidas.length()").value(2))
                .andExpect(jsonPath("$.medidas[0].nome").value("Busto"))
                .andExpect(jsonPath("$.medidas[0].valores.P").value("84–88"))
                .andExpect(jsonPath("$.medidas[0].valores.GG").value("99–104"))
                .andExpect(jsonPath("$.medidas[1].nome").value("Comprimento"))
                .andExpect(jsonPath("$.medidas[1].valores.P").value(""))
                .andExpect(jsonPath("$.medidas[1].valores.M").value("92"));
    }

    @Test
    void publicoComCategoriaInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/categorias/-1/medidas"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Categoria não encontrada"))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void adminBuscaATabela() throws Exception {
        mockMvc.perform(comoAdmin(put(caminhoAdmin())).content(TABELA_VESTIDO));

        mockMvc.perform(comoAdmin(get(caminhoAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaId").value(categoria.getId()))
                .andExpect(jsonPath("$.medidas[0].nome").value("Busto"));
    }

    @Test
    void salvarRetorna200ComATabelaNaOrdemEnviada() throws Exception {
        mockMvc.perform(comoAdmin(put(caminhoAdmin())).content(TABELA_VESTIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaId").value(categoria.getId()))
                .andExpect(jsonPath("$.unidade").value("cm"))
                .andExpect(jsonPath("$.medidas[0].nome").value("Busto"))
                .andExpect(jsonPath("$.medidas[1].nome").value("Comprimento"))
                .andExpect(jsonPath("$.medidas[1].valores.GG").value(""));

        assertThat(medidaRepository.findByCategoriaIdOrderByOrdem(categoria.getId())).hasSize(2);
    }

    @Test
    void salvarSemNomeRetorna400() throws Exception {
        mockMvc.perform(comoAdmin(put(caminhoAdmin())).content("{\"medidas\": [{\"nome\": \"  \", \"valores\": {}}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe o nome da medida"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void salvarComNomeRepetidoRetorna400() throws Exception {
        mockMvc.perform(comoAdmin(put(caminhoAdmin()))
                        .content("{\"medidas\": [{\"nome\": \"Busto\"}, {\"nome\": \"BUSTO\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A medida \"BUSTO\" aparece mais de uma vez"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void salvarComValorLongoDemaisRetorna400() throws Exception {
        mockMvc.perform(comoAdmin(put(caminhoAdmin()))
                        .content("{\"medidas\": [{\"nome\": \"Busto\", \"valores\": {\"P\": \"%s\"}}]}"
                                .formatted("1".repeat(21))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Cada valor da tabela pode ter no máximo 20 caracteres"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void salvarComNomeLongoDemaisRetorna400() throws Exception {
        mockMvc.perform(comoAdmin(put(caminhoAdmin()))
                        .content("{\"medidas\": [{\"nome\": \"%s\"}]}".formatted("x".repeat(41))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("O nome da medida pode ter no máximo 40 caracteres"));
    }

    @Test
    void salvarComTamanhoInvalidoRetorna400() throws Exception {
        mockMvc.perform(comoAdmin(put(caminhoAdmin()))
                        .content("{\"medidas\": [{\"nome\": \"Busto\", \"valores\": {\"XG\": \"110\"}}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Tamanho inválido: use P, M, G ou GG"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void salvarComMaisDe20MedidasRetorna400() throws Exception {
        StringBuilder linhas = new StringBuilder();
        for (int i = 1; i <= 21; i++) {
            linhas.append(i > 1 ? "," : "").append("{\"nome\": \"Medida ").append(i).append("\"}");
        }
        mockMvc.perform(comoAdmin(put(caminhoAdmin())).content("{\"medidas\": [" + linhas + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A tabela pode ter no máximo 20 medidas"));
    }

    @Test
    void salvarSemListaRetorna400() throws Exception {
        mockMvc.perform(comoAdmin(put(caminhoAdmin())).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe as medidas"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void salvarEmCategoriaInexistenteRetorna404() throws Exception {
        mockMvc.perform(comoAdmin(put("/admin/categorias/-1/medidas")).content(TABELA_VESTIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void removerRetorna204EOLinkPublicoFicaSemMedidas() throws Exception {
        mockMvc.perform(comoAdmin(put(caminhoAdmin())).content(TABELA_VESTIDO));

        mockMvc.perform(comoAdmin(delete(caminhoAdmin())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(caminhoPublico()))
                .andExpect(jsonPath("$.medidas.length()").value(0));
    }

    @Test
    void removerEmCategoriaInexistenteRetorna404() throws Exception {
        mockMvc.perform(comoAdmin(delete("/admin/categorias/-1/medidas")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void removerCategoriaComTabelaLevaAsMedidasJunto() throws Exception {
        mockMvc.perform(comoAdmin(put(caminhoAdmin())).content(TABELA_VESTIDO));

        mockMvc.perform(comoAdmin(delete("/admin/categorias/" + categoria.getId())))
                .andExpect(status().isNoContent());

        assertThat(medidaRepository.findByCategoriaIdOrderByOrdem(categoria.getId())).isEmpty();
    }

    @Test
    void edicaoSemTokenRetorna401() throws Exception {
        mockMvc.perform(put(caminhoAdmin()).contentType(MediaType.APPLICATION_JSON).content(TABELA_VESTIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void edicaoComTokenDeClienteRetorna403() throws Exception {
        mockMvc.perform(put(caminhoAdmin())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TABELA_VESTIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    void escritaNaRotaPublicaContinuaProtegida() throws Exception {
        mockMvc.perform(put(caminhoPublico()).contentType(MediaType.APPLICATION_JSON).content(TABELA_VESTIDO))
                .andExpect(status().isUnauthorized());
    }

    private String caminhoPublico() {
        return "/categorias/" + categoria.getId() + "/medidas";
    }

    private String caminhoAdmin() {
        return "/admin/categorias/" + categoria.getId() + "/medidas";
    }

    private MockHttpServletRequestBuilder comoAdmin(MockHttpServletRequestBuilder requisicao) {
        return requisicao
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON);
    }
}
