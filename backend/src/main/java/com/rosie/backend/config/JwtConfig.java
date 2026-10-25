package com.rosie.backend.config;

import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.TokenService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

// JWT assinado com HS256 (decisão em docs/contrato-api.md). O segredo vem de JWT_SECRET.
@Configuration
public class JwtConfig {

    public static final String EMISSOR = "rosie";

    // HS256 exige chave de pelo menos 256 bits
    private static final int TAMANHO_MINIMO_SEGREDO = 32;

    private final SecretKey chave;

    public JwtConfig(@Value("${rosie.jwt.segredo}") String segredo) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalStateException(
                    "JWT_SECRET precisa ter pelo menos " + TAMANHO_MINIMO_SEGREDO + " caracteres");
        }
        this.chave = new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return NimbusJwtEncoder.withSecretKey(chave).build();
    }

    // O repositório é opcional só para os testes de fatia (@WebMvcTest), que sobem a segurança sem o JPA
    @Bean
    public JwtDecoder jwtDecoder(ObjectProvider<UsuarioRepository> usuarios) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Validade (exp) e emissor; conta não excluída (#31) e senha não trocada depois do login (#93)
        OAuth2TokenValidator<Jwt> padrao = JwtValidators.createDefaultWithIssuer(EMISSOR);
        UsuarioRepository repositorio = usuarios.getIfAvailable();
        decoder.setJwtValidator(repositorio == null ? padrao
                : new DelegatingOAuth2TokenValidator<>(padrao, credencialAtual(repositorio)));
        return decoder;
    }

    // A sessão acaba na hora, sem esperar o token de 2 horas vencer (401 NAO_AUTENTICADO), quando a cliente
    // exclui a conta (#31) ou quando a senha é trocada (#93, pelo link de "esqueci minha senha" ou na Minha
    // Conta): cada troca sobe a versao_credencial do usuário, e o token de uma versão anterior deixa de valer.
    // Token sem a claim (emitido antes da #93) conta como versão 0. Uma consulta por id em cada requisição com
    // token.
    // Por que uma versão e não a data da troca: o "iat" do JWT tem só segundos, e o token do login feito logo
    // depois da troca, no mesmo segundo, seria recusado (ou um de antes, aceito).
    private static OAuth2TokenValidator<Jwt> credencialAtual(UsuarioRepository usuarios) {
        OAuth2Error recusado = new OAuth2Error("invalid_token", "Sessão encerrada", null);
        return jwt -> {
            Long id;
            try {
                id = Long.valueOf(jwt.getSubject());
            } catch (NumberFormatException e) {
                return OAuth2TokenValidatorResult.failure(recusado);
            }
            boolean valido = usuarios.buscarEstadoCredencial(id)
                    .map(estado -> estado.getExcluidaEm() == null
                            && estado.getVersaoCredencial() == versaoDoToken(jwt))
                    .orElse(true);
            return valido ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(recusado);
        };
    }

    private static int versaoDoToken(Jwt jwt) {
        Object versao = jwt.getClaim(TokenService.CLAIM_VERSAO_CREDENCIAL);
        return versao instanceof Number numero ? numero.intValue() : 0;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
