package com.rosie.backend.config;

import com.rosie.backend.dto.ErroResponseDTO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@Import(JwtConfig.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JsonMapper jsonMapper;

    @Value("${rosie.cors.origens-permitidas}")
    private String[] origensPermitidas;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint naoAutenticado = (request, response, e) -> escreverErro(response,
                HttpStatus.UNAUTHORIZED, "Autenticação necessária", "NAO_AUTENTICADO");
        AccessDeniedHandler acessoNegado = (request, response, e) -> escreverErro(response,
                HttpStatus.FORBIDDEN, "Acesso negado", "ACESSO_NEGADO");

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/admin/auth/login").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Conta do cliente (RF08): cadastro e login abertos, o resto só com token de cliente
                        .requestMatchers(HttpMethod.POST, "/auth/registrar", "/auth/login").permitAll()
                        .requestMatchers("/auth/**").hasRole("CLIENTE")
                        // Rotas da compra (#22, #24, #27): token de admin recebe 403, como token de cliente em /admin/**
                        .requestMatchers("/carrinho/**", "/pedidos/**", "/trocas/**").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/produtos", "/produtos/**", "/categorias").permitAll()
                        // Sem isso, exceções não tratadas viram 401 ao encaminhar para /error
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                // Token ausente, inválido ou expirado → 401; role errada → 403
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(naoAutenticado)
                        .accessDeniedHandler(acessoNegado)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(naoAutenticado)
                        .accessDeniedHandler(acessoNegado)
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origensPermitidas));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // A claim "role" do token (ADMIN ou CLIENTE) vira a authority ROLE_ADMIN / ROLE_CLIENTE
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private void escreverErro(HttpServletResponse response, HttpStatus status, String mensagem, String codigo)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), new ErroResponseDTO(mensagem, codigo));
    }
}
