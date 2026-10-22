package com.rosie.backend.controller;

import com.rosie.backend.config.SecurityConfig;
import com.rosie.backend.dto.CategoriaResumoDTO;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
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
                new CategoriaResumoDTO(2L, "Calça", "calca"),
                new CategoriaResumoDTO(1L, "Vestido", "vestido")
        ));

        mockMvc.perform(get("/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].nome").value("Calça"))
                .andExpect(jsonPath("$[0].slug").value("calca"))
                .andExpect(jsonPath("$[1].nome").value("Vestido"));
    }

    @Test
    void buscarPorSlugSemAutenticacaoRetornaACategoria() throws Exception {
        when(categoriaService.buscarPorSlug("cropped-body"))
                .thenReturn(new CategoriaResumoDTO(4L, "Cropped/Body", "cropped-body"));

        mockMvc.perform(get("/categorias/cropped-body"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.nome").value("Cropped/Body"))
                .andExpect(jsonPath("$.slug").value("cropped-body"));
    }

    @Test
    void buscarPorSlugInexistenteDa404NoFormatoDoContrato() throws Exception {
        when(categoriaService.buscarPorSlug("nao-existe"))
                .thenThrow(new RecursoNaoEncontradoException("Categoria não encontrada"));

        mockMvc.perform(get("/categorias/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Categoria não encontrada"))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void criarCategoriaSemAutenticacaoContinuaProtegido() throws Exception {
        mockMvc.perform(post("/categorias"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }
}
