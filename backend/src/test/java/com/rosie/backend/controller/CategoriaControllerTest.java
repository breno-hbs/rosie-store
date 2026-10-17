package com.rosie.backend.controller;

import com.rosie.backend.config.SecurityConfig;
import com.rosie.backend.dto.CategoriaResumoDTO;
import com.rosie.backend.service.CategoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoriaController.class)
@Import(SecurityConfig.class)
class CategoriaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoriaService categoriaService;

    @Test
    void listarCategoriasSemAutenticacaoRetornaIdENome() throws Exception {
        when(categoriaService.listar()).thenReturn(List.of(
                new CategoriaResumoDTO(2L, "Calça"),
                new CategoriaResumoDTO(1L, "Vestido")
        ));

        mockMvc.perform(get("/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].nome").value("Calça"))
                .andExpect(jsonPath("$[1].nome").value("Vestido"));
    }

    @Test
    void criarCategoriaSemAutenticacaoContinuaProtegido() throws Exception {
        mockMvc.perform(post("/categorias"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }
}
