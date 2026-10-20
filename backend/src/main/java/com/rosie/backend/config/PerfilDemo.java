package com.rosie.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

// Modo de demonstração (#77), ligado com SPRING_PROFILES_ACTIVE=demo pelo scripts/demo.ps1: frete, etiqueta e
// pagamento de mentira, e-mail só no log e banco próprio (application-demo.properties). Junto com o perfil prod
// a API não sobe: na loja de verdade, um pagamento simulado aprovaria pedido sem cobrar.
@Configuration
@Profile("demo")
@Slf4j
public class PerfilDemo {

    public PerfilDemo(Environment ambiente) {
        if (ambiente.matchesProfiles("prod")) {
            throw new IllegalStateException(
                    "O perfil demo liga o pagamento e o frete simulados e não pode rodar junto com o prod");
        }
        log.warn("MODO DE DEMONSTRAÇÃO: pagamento, frete e etiqueta simulados, e-mails só no log, banco da demo.");
    }
}
