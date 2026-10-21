package com.rosie.backend.controller;

import com.rosie.backend.model.Banner;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.BannerRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.ArmazenamentoImagensFalso;
import com.rosie.backend.service.PrazoTroca;
import com.rosie.backend.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Banners da Home (#89): GET /banners público e /admin/banners com token de admin, contra o MySQL, com o
// Cloudinary falso e o relógio parado em 19/10/2026, 12h em São Paulo. Cada teste começa sem banners e é
// desfeito no fim.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({ArmazenamentoImagensFalso.Config.class, BannerControllerTest.RelogioParado.class})
class BannerControllerTest {

    static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 19, 12, 0);
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @TestConfiguration
    static class RelogioParado {

        @Bean
        @Primary
        Clock relogioParado() {
            return Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), PrazoTroca.FUSO_LOJA);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private BannerRepository bannerRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ArmazenamentoImagensFalso armazenamento;

    private String tokenAdmin;
    private String tokenCliente;

    @BeforeEach
    void setUp() {
        armazenamento.limpar();
        bannerRepository.deleteAllInBatch();
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste")
                .email("admin-banners@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.ADMIN)
                .build());
        Usuario cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Teste")
                .email("cliente-banners@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.CLIENTE)
                .build());
        tokenAdmin = tokenService.gerar(admin);
        tokenCliente = tokenService.gerar(cliente);
    }

    @Test
    void homeRecebeSemTokenSoOsAtivosDoPeriodoNaOrdem() throws Exception {
        salvar("Segundo", 1, true, null, null);
        salvar("Primeiro", 0, true, AGORA.minusDays(1), AGORA.plusDays(1));
        salvar("Desativado", 2, false, null, null);
        salvar("Agendado", 3, true, AGORA.plusDays(1), null);
        salvar("Encerrado", 4, true, null, AGORA.minusMinutes(1));

        mockMvc.perform(get("/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].titulo").value("Primeiro"))
                .andExpect(jsonPath("$[0].textoAlternativo").value("Banner Primeiro"))
                .andExpect(jsonPath("$[0].imagemDesktop.url").value("https://placehold.co/1920x640"))
                .andExpect(jsonPath("$[0].imagemDesktop.srcset").doesNotExist())
                .andExpect(jsonPath("$[0].ativo").doesNotExist())
                .andExpect(jsonPath("$[1].titulo").value("Segundo"));
    }

    @Test
    void criaBannerComImagensEDados() throws Exception {
        String dados = """
                {"titulo": "Coleção de verão", "texto": "Peças leves", "textoBotao": "Ver coleção",
                 "link": "/categoria/vestido", "textoAlternativo": "Modelo de vestido florido",
                 "inicio": "2026-10-19T00:00:00", "fim": "2026-12-31T23:59:00"}
                """;

        mockMvc.perform(comAdmin(multipart("/admin/banners")
                        .file(json(dados))
                        .file(imagem("imagemDesktop"))
                        .file(imagem("imagemCelular"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.ordem").value(0))
                .andExpect(jsonPath("$.situacao").value("NO_AR"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.inicio").value("2026-10-19T00:00:00"))
                .andExpect(jsonPath("$.link").value("/categoria/vestido"))
                .andExpect(jsonPath("$.imagemDesktop.url",
                        endsWith("/c_fill,ar_3:1,g_auto/c_limit,w_1280/f_auto,q_auto/v1/rosie/banners/teste-1.jpg")))
                .andExpect(jsonPath("$.imagemDesktop.srcset", endsWith("teste-1.jpg 1920w")))
                .andExpect(jsonPath("$.imagemCelular.url",
                        endsWith("/c_fill,ar_4:5,g_auto/c_limit,w_750/f_auto,q_auto/v1/rosie/banners/teste-2.jpg")))
                .andExpect(jsonPath("$.previaDesktop", endsWith("/c_limit,w_1600/f_auto,q_auto/v1/rosie/banners/teste-1.jpg")));

        mockMvc.perform(get("/banners"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].textoBotao").value("Ver coleção"));
    }

    @Test
    void linkExternoOuJavascriptERecusado() throws Exception {
        for (String link : new String[]{"https://golpe.com/promo", "javascript:alert(1)", "//golpe.com"}) {
            mockMvc.perform(comAdmin(multipart("/admin/banners")
                            .file(json("{\"textoAlternativo\": \"Vestido\", \"link\": \"" + link + "\"}"))
                            .file(imagem("imagemDesktop"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                    .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.startsWith("O link do banner precisa ser")));
        }
    }

    @Test
    void textoAlternativoEImagemDeDesktopSaoObrigatorios() throws Exception {
        mockMvc.perform(comAdmin(multipart("/admin/banners")
                        .file(json("{\"titulo\": \"Sem descrição\"}"))
                        .file(imagem("imagemDesktop"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Descreva a imagem do banner (texto alternativo)"));

        mockMvc.perform(comAdmin(multipart("/admin/banners").file(json("{\"textoAlternativo\": \"Vestido\"}"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Envie a imagem para computador no campo imagemDesktop"));

        mockMvc.perform(comAdmin(multipart("/admin/banners").file(imagem("imagemDesktop"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Envie o campo dados"));
    }

    @Test
    void periodoInvertidoERecusado() throws Exception {
        mockMvc.perform(comAdmin(multipart("/admin/banners")
                        .file(json("{\"textoAlternativo\": \"Vestido\", \"inicio\": \"2026-12-01T00:00:00\", "
                                + "\"fim\": \"2026-11-01T00:00:00\"}"))
                        .file(imagem("imagemDesktop"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("O fim da exibição precisa ser depois do início"));
    }

    @Test
    void imagemQueNaoEFotoERecusada() throws Exception {
        mockMvc.perform(comAdmin(multipart("/admin/banners")
                        .file(json("{\"textoAlternativo\": \"Vestido\"}"))
                        .file(new MockMultipartFile("imagemDesktop", "a.pdf", "application/pdf", "%PDF-1.4".getBytes()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FORMATO_NAO_SUPORTADO"));
    }

    @Test
    void editaSemReenviarAsImagens() throws Exception {
        Banner banner = salvar("Antes", 0, true, null, null);

        mockMvc.perform(comAdmin(multipart(HttpMethod.PUT, "/admin/banners/" + banner.getId())
                        .file(json("{\"titulo\": \"Depois\", \"textoAlternativo\": \"Vestido\", \"ativo\": true, "
                                + "\"inicio\": \"2026-10-20T08:00:00\"}"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Depois"))
                .andExpect(jsonPath("$.situacao").value("AGENDADO"))
                .andExpect(jsonPath("$.imagemDesktop.url").value("https://placehold.co/1920x640"));

        mockMvc.perform(get("/banners")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void desativaReordenaERemove() throws Exception {
        Banner a = salvar("A", 0, true, null, null);
        Banner b = salvar("B", 1, true, null, null);

        mockMvc.perform(comAdmin(put("/admin/banners/" + a.getId() + "/ativo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\": false}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("DESATIVADO"));

        mockMvc.perform(comAdmin(put("/admin/banners/ordem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"banners\": [" + b.getId() + ", " + a.getId() + "]}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(b.getId()))
                .andExpect(jsonPath("$[0].ordem").value(0))
                .andExpect(jsonPath("$[1].ordem").value(1));

        mockMvc.perform(comAdmin(put("/admin/banners/ordem")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"banners\": [" + b.getId() + "]}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Envie todos os banners, cada um uma vez"));

        mockMvc.perform(comAdmin(delete("/admin/banners/" + b.getId()))).andExpect(status().isNoContent());
        mockMvc.perform(comAdmin(delete("/admin/banners/" + b.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));

        mockMvc.perform(comAdmin(get("/admin/banners")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(a.getId()))
                .andExpect(jsonPath("$[0].ordem").value(0));
    }

    @Test
    void painelExigeTokenDeAdmin() throws Exception {
        mockMvc.perform(get("/admin/banners"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mockMvc.perform(get("/admin/banners").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        mockMvc.perform(multipart("/admin/banners").file(json("{\"textoAlternativo\": \"Vestido\"}"))
                        .file(imagem("imagemDesktop")))
                .andExpect(status().isUnauthorized());
    }

    private Banner salvar(String titulo, int ordem, boolean ativo, LocalDateTime inicio, LocalDateTime fim) {
        return bannerRepository.saveAndFlush(Banner.builder()
                .titulo(titulo)
                .textoAlternativo("Banner " + titulo)
                .imagemDesktopUrl("https://placehold.co/1920x640")
                .ativo(ativo)
                .inicio(inicio)
                .fim(fim)
                .ordem(ordem)
                .build());
    }

    private static MockMultipartFile json(String conteudo) {
        return new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE,
                conteudo.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile imagem(String campo) {
        return new MockMultipartFile(campo, campo + ".jpg", "image/jpeg", JPG);
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> B comAdmin(B requisicao) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin);
    }
}
