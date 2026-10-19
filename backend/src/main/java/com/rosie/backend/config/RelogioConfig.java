package com.rosie.backend.config;

import com.rosie.backend.service.PrazoTroca;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Relógio da loja, no fuso de São Paulo (o Railway roda em UTC). Um bean só para os testes poderem parar o
// tempo (#64): quem precisa de "hoje" ou "agora" recebe este Clock em vez de chamar LocalDate.now().
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio() {
        return Clock.system(PrazoTroca.FUSO_LOJA);
    }
}
