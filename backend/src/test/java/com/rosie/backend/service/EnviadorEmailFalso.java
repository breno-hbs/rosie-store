package com.rosie.backend.service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

// Substitui o Resend nos testes: guarda os e-mails na memória, sem rede nem chave. O envio sai numa thread
// da FilaEmail, então os testes esperam com aguardar(). O bean é compartilhado entre os testes do mesmo
// contexto: chame limpar() no @BeforeEach.
public class EnviadorEmailFalso implements EnviadorEmail {

    private final List<Email> enviados = new ArrayList<>();
    private int tentativas;
    // Falha programada: toda tentativa de envio lança esta exceção
    private RuntimeException falha;

    @Override
    public synchronized void enviar(Email email) {
        tentativas++;
        notifyAll();
        if (falha != null) throw falha;
        enviados.add(email);
    }

    // Espera até chegarem n tentativas de envio (no máximo 5 s) e devolve os e-mails que saíram
    public synchronized List<Email> aguardar(int n) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5000;
        while (tentativas < n) {
            long falta = limite - System.currentTimeMillis();
            if (falta <= 0) break;
            wait(falta);
        }
        return List.copyOf(enviados);
    }

    public synchronized int tentativas() {
        return tentativas;
    }

    public synchronized void falharCom(RuntimeException falha) {
        this.falha = falha;
    }

    public synchronized void limpar() {
        enviados.clear();
        tentativas = 0;
        falha = null;
    }

    // @Import(EnviadorEmailFalso.Config.class) no teste troca o Resend (ou o modo log) por este
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public EnviadorEmailFalso enviadorEmailFalso() {
            return new EnviadorEmailFalso();
        }
    }
}
