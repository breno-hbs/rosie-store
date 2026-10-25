package com.rosie.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Limite de pedidos de "esqueci minha senha" (#93), para o endpoint público não virar spam nem varredura de
// e-mails: no máximo N pedidos por e-mail e M por IP numa janela fixa (padrão 3 e 10 por hora). Conta todo
// pedido, exista ou não a conta, então passar do limite não diz nada sobre o e-mail.
// Na memória da API: basta para uma instância só (Railway Hobby) e zera quando ela reinicia. Com mais de uma
// instância, cada uma conta a sua parte (o limite efetivo se multiplica); aí o certo é guardar no banco.
@Component
public class LimitePedidosSenha {

    // Acima disso, cada pedido aproveita para tirar da memória as janelas que já acabaram
    private static final int CHAVES_ANTES_DA_LIMPEZA = 10_000;

    private final int limitePorEmail;
    private final int limitePorIp;
    private final Duration janela;
    private final Clock relogio;
    private final Map<String, Contagem> contagens = new ConcurrentHashMap<>();

    public LimitePedidosSenha(@Value("${rosie.senha.esqueci.limite-email:3}") int limitePorEmail,
                              @Value("${rosie.senha.esqueci.limite-ip:10}") int limitePorIp,
                              @Value("${rosie.senha.esqueci.janela:1h}") Duration janela,
                              Clock relogio) {
        this.limitePorEmail = limitePorEmail;
        this.limitePorIp = limitePorIp;
        this.janela = janela;
        this.relogio = relogio;
    }

    private record Contagem(Instant inicio, int pedidos) {
    }

    // Conta o pedido e diz se ele ainda cabe. O IP vem primeiro: pedido barrado pelo IP não gasta a cota do
    // e-mail (senão alguém com muitos pedidos travaria o link de quem não tem nada com isso por mais tempo).
    public boolean permitir(String email, String ip) {
        Instant agora = relogio.instant();
        if (contagens.size() > CHAVES_ANTES_DA_LIMPEZA) {
            contagens.values().removeIf(c -> !agora.isBefore(c.inicio().plus(janela)));
        }
        return contar("ip:" + ip, limitePorIp, agora) && contar("email:" + email, limitePorEmail, agora);
    }

    private boolean contar(String chave, int limite, Instant agora) {
        Contagem contagem = contagens.compute(chave, (k, atual) ->
                atual == null || !agora.isBefore(atual.inicio().plus(janela))
                        ? new Contagem(agora, 1)
                        : new Contagem(atual.inicio(), atual.pedidos() + 1));
        return contagem.pedidos() <= limite;
    }

    // Para os testes: o bean é um só no contexto e a contagem passaria de um teste para o outro
    public void limpar() {
        contagens.clear();
    }
}
