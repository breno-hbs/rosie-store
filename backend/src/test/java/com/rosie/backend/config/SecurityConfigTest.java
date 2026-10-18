package com.rosie.backend.config;

import com.rosie.backend.controller.ProdutoController;
import com.rosie.backend.dto.ProdutoListaResponseDTO;
import com.rosie.backend.service.ProdutoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProdutoController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    private static final String ORIGEM_FRONT = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProdutoService produtoService;

    @Test
    void listarProdutosSemAutenticacaoEhPermitido() throws Exception {
        when(produtoService.listar(any(), anyInt(), anyInt()))
                .thenReturn(new ProdutoListaResponseDTO(List.of(), 0, 0));

        mockMvc.perform(get("/produtos"))
                .andExpect(status().isOk());
    }

    @Test
    void listarDestaquesSemAutenticacaoEhPermitido() throws Exception {
        when(produtoService.listarDestaques(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/produtos/destaques"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void preflightDoFrontRetornaAllowOrigin() throws Exception {
        mockMvc.perform(options("/produtos")
                        .header(HttpHeaders.ORIGIN, ORIGEM_FRONT)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_FRONT));
    }

    @Test
    void preflightDeOrigemNaoPermitidaEhRecusado() throws Exception {
        mockMvc.perform(options("/produtos")
                        .header(HttpHeaders.ORIGIN, "http://site-qualquer.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void rotaProtegidaSemAutenticacaoRetorna401NoFormatoDeErro() throws Exception {
        mockMvc.perform(post("/produtos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Autenticação necessária"))
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }
}
