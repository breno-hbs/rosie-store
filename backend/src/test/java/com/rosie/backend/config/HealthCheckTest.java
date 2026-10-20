package com.rosie.backend.config;

import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Health check do Railway (#74): só /actuator/health, aberto e sem detalhes; o resto do Actuator não existe
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HealthCheckTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TokenService tokenService;

    @Test
    void healthSemAutenticacaoRespondeUpSemDetalhes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void outrosEndpointsDoActuatorNaoExistemNemParaAdmin() throws Exception {
        String tokenAdmin = tokenService.gerar(usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste")
                .email("admin-health@rosie.test")
                .senhaHash("nao-usada")
                .role(Role.ADMIN)
                .build()));

        for (String caminho : new String[] {"/actuator", "/actuator/env", "/actuator/info", "/actuator/beans",
                "/actuator/configprops", "/actuator/health/db"}) {
            mockMvc.perform(get(caminho).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void outrosEndpointsDoActuatorSemTokenRespondem401() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
