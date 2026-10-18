package com.rosie.backend.service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

// Substitui o Melhor Envio nos testes: guarda as cotações pedidas e devolve as opções programadas,
// sem rede nem token. O bean é compartilhado entre os testes do mesmo contexto: chame limpar() no @BeforeEach.
public class CalculadoraFreteFalsa implements CalculadoraFrete {

    public record Chamada(String cepDestino, List<PecaFrete> pecas) {
    }

    public final List<Chamada> chamadas = new ArrayList<>();
    public List<OpcaoFrete> opcoes = List.of();
    // Se definido, calcular() lança esta exceção (serviço fora do ar, sem configuração...)
    public RuntimeException falha;

    @Override
    public List<OpcaoFrete> calcular(String cepDestino, List<PecaFrete> pecas) {
        chamadas.add(new Chamada(cepDestino, pecas));
        if (falha != null) throw falha;
        return opcoes;
    }

    public void limpar() {
        chamadas.clear();
        opcoes = List.of();
        falha = null;
    }

    // @Import(CalculadoraFreteFalsa.Config.class) no teste troca o Melhor Envio por esta
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public CalculadoraFreteFalsa calculadoraFreteFalsa() {
            return new CalculadoraFreteFalsa();
        }
    }
}
