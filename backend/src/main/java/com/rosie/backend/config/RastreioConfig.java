package com.rosie.backend.config;

import com.rosie.backend.service.RastreioEntregas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

// Agenda a entrega pelo rastreio (#64). Decisão: rotina agendada, não webhook. O webhook do Melhor Envio é dos
// aplicativos OAuth (exige app cadastrado, URL pública e validação de assinatura), e a loja usa um token pessoal;
// consultar algumas vezes por dia basta para abrir o prazo de troca sem a Roseane precisar clicar.
// ROSIE_RASTREIO_ATIVO=false desliga (os testes desligam pelo pom.xml). Sem MELHOR_ENVIO_TOKEN, a rodada não
// faz nada (RastreioEntregas).
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "rosie.rastreio.ativo", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RastreioConfig {

    private final RastreioEntregas rastreioEntregas;

    @Scheduled(initialDelayString = "${rosie.rastreio.atraso-inicial:1m}",
            fixedDelayString = "${rosie.rastreio.intervalo:6h}")
    public void atualizarEntregas() {
        try {
            rastreioEntregas.executar();
        } catch (RuntimeException e) {
            // Banco fora do ar, por exemplo: a próxima rodada tenta de novo
            log.error("Rodada do rastreio de entregas falhou", e);
        }
    }
}
