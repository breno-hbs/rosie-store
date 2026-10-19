package com.rosie.backend.service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Substitui o Melhor Envio nos testes da etiqueta: guarda os envios na memória e conta cada chamada, sem
// rede, token nem saldo. O bean é compartilhado entre os testes do mesmo contexto: chame limpar() no @BeforeEach.
public class EmissorEtiquetaFalso implements EmissorEtiqueta {

    public static final String LINK = "https://sandbox.melhorenvio.com.br/imprimir/falso-";

    public final List<NovoEnvio> enviosNoCarrinho = new ArrayList<>();
    public final List<String> compras = new ArrayList<>();
    public final List<String> geracoes = new ArrayList<>();
    public int consultas;
    private final Map<String, SituacaoEnvio> envios = new HashMap<>();

    // Código que o Melhor Envio devolve depois de gerar; null = ainda sem rastreio
    public String rastreio = "ME123456789BR";
    // Falha programada por passo: "carrinho", "consultar", "comprar", "gerar" ou "link"
    public final Map<String, RuntimeException> falhas = new HashMap<>();
    // Segura o carrinho por um tempo, para dois cliques simultâneos se cruzarem
    public long demoraNoCarrinhoMs;

    @Override
    public String adicionarAoCarrinho(NovoEnvio envio) {
        dormir(demoraNoCarrinhoMs);
        synchronized (this) {
            falharSe("carrinho");
            enviosNoCarrinho.add(envio);
            String id = "envio-" + enviosNoCarrinho.size();
            envios.put(id, new SituacaoEnvio(Etapa.NO_CARRINHO, null));
            return id;
        }
    }

    @Override
    public synchronized SituacaoEnvio consultar(String envioId) {
        consultas++;
        falharSe("consultar");
        return envios.get(envioId);
    }

    @Override
    public synchronized void comprar(String envioId) {
        falharSe("comprar");
        compras.add(envioId);
        envios.put(envioId, new SituacaoEnvio(Etapa.COMPRADO, null));
    }

    @Override
    public synchronized void gerar(String envioId) {
        falharSe("gerar");
        geracoes.add(envioId);
        envios.put(envioId, new SituacaoEnvio(Etapa.GERADO, rastreio));
    }

    @Override
    public synchronized String linkImpressao(String envioId) {
        falharSe("link");
        return LINK + envioId;
    }

    // O rastreio saiu depois (o Melhor Envio às vezes demora a devolver o código)
    public synchronized void liberarRastreio(String envioId, String codigo) {
        envios.put(envioId, new SituacaoEnvio(Etapa.GERADO, codigo));
    }

    // A Roseane cancelou o envio no painel do Melhor Envio
    public synchronized void cancelar(String envioId) {
        envios.put(envioId, new SituacaoEnvio(Etapa.CANCELADO, null));
    }

    public synchronized void limpar() {
        enviosNoCarrinho.clear();
        compras.clear();
        geracoes.clear();
        consultas = 0;
        envios.clear();
        rastreio = "ME123456789BR";
        falhas.clear();
        demoraNoCarrinhoMs = 0;
    }

    private void falharSe(String passo) {
        RuntimeException falha = falhas.get(passo);
        if (falha != null) throw falha;
    }

    private static void dormir(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // @Import(EmissorEtiquetaFalso.Config.class) no teste troca o Melhor Envio por este
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public EmissorEtiquetaFalso emissorEtiquetaFalso() {
            return new EmissorEtiquetaFalso();
        }
    }
}
