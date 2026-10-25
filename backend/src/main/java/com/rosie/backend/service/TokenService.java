package com.rosie.backend.service;

import com.rosie.backend.config.JwtConfig;
import com.rosie.backend.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final Duration validade;

    public TokenService(JwtEncoder jwtEncoder, @Value("${rosie.jwt.validade}") Duration validade) {
        this.jwtEncoder = jwtEncoder;
        this.validade = validade;
    }

    // Claim com a versão da credencial (#93): o JwtConfig recusa o token emitido antes da última troca de senha
    public static final String CLAIM_VERSAO_CREDENCIAL = "vc";

    // subject = id do usuário; "role" vira a authority no SecurityConfig
    public String gerar(Usuario usuario) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.EMISSOR)
                .subject(String.valueOf(usuario.getId()))
                .issuedAt(agora)
                .expiresAt(agora.plus(validade))
                .claim("nome", usuario.getNome())
                .claim("role", usuario.getRole().name())
                .claim(CLAIM_VERSAO_CREDENCIAL, usuario.getVersaoCredencial())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
