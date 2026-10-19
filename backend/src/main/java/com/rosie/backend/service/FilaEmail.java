package com.rosie.backend.service;

import com.rosie.backend.service.EnviadorEmail.Email;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

// Saída dos e-mails da loja (#65). Garante duas coisas a quem dispara um e-mail:
// - só sai depois do commit: se a transação for desfeita, o e-mail não sai (nada de avisar uma mudança
//   que não aconteceu). Fora de transação, sai na hora.
// - nunca derruba a ação: o envio roda numa thread própria, um de cada vez e na ordem em que chegaram, e
//   uma falha do provedor só vai para o log. A resposta da API não espera o provedor.
// Não há nova tentativa: o e-mail é só um aviso e a fonte da verdade é o site.
@Component
@Slf4j
public class FilaEmail {

    private final EnviadorEmail enviador;
    private final ExecutorService fila = Executors.newSingleThreadExecutor(tarefa -> {
        Thread thread = new Thread(tarefa, "fila-email");
        thread.setDaemon(true);
        return thread;
    });

    public FilaEmail(EnviadorEmail enviador) {
        this.enviador = enviador;
    }

    public void enviarDepoisDoCommit(Email email) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enfileirar(email);
                }
            });
        } else {
            enfileirar(email);
        }
    }

    private void enfileirar(Email email) {
        try {
            fila.execute(() -> enviar(email));
        } catch (RejectedExecutionException e) {
            log.error("E-mail não enviado (a API está desligando): para {}, assunto \"{}\"",
                    email.paraMascarado(), email.assunto());
        }
    }

    private void enviar(Email email) {
        try {
            enviador.enviar(email);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar o e-mail para {}, assunto \"{}\": {}", email.paraMascarado(),
                    email.assunto(), e.getMessage());
        }
    }

    // Ao desligar a API, espera um pouco os e-mails que já estavam na fila
    @PreDestroy
    void encerrar() throws InterruptedException {
        fila.shutdown();
        if (!fila.awaitTermination(15, TimeUnit.SECONDS)) {
            log.warn("A API desligou com e-mails ainda na fila; eles não foram enviados.");
            fila.shutdownNow();
        }
    }
}
