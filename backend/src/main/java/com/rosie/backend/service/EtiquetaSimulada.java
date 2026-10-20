package com.rosie.backend.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

// Modo de desenvolvimento (liga junto com FRETE_SIMULADO=true): envios de mentira guardados na memória,
// sem chamar o Melhor Envio nem gastar saldo. Nunca ligar em produção. O link de impressão abre uma página
// estática da loja (frontend/public/etiqueta-simulada.html) e o rastreio sai no formato SIM00000000BR.
// Etiqueta gerada já conta como entregue no dia, para a rotina do rastreio (#64) poder ser vista funcionando
// (ROSIE_RASTREIO_ATIVO=false a desliga, para testar o botão "Marcar como entregue").
public class EtiquetaSimulada implements EmissorEtiqueta {

    private final String urlLoja;
    private final Map<String, SituacaoEnvio> envios = new ConcurrentHashMap<>();

    public EtiquetaSimulada(String urlLoja) {
        this.urlLoja = urlLoja.endsWith("/") ? urlLoja.substring(0, urlLoja.length() - 1) : urlLoja;
    }

    @Override
    public String adicionarAoCarrinho(NovoEnvio envio) {
        String id = UUID.randomUUID().toString();
        envios.put(id, new SituacaoEnvio(Etapa.NO_CARRINHO, null));
        return id;
    }

    // Envio que a memória não conhece (a API reiniciou no meio) volta como recém-posto no carrinho
    @Override
    public SituacaoEnvio consultar(String envioId) {
        return envios.getOrDefault(envioId, new SituacaoEnvio(Etapa.NO_CARRINHO, null));
    }

    @Override
    public void comprar(String envioId) {
        envios.put(envioId, new SituacaoEnvio(Etapa.COMPRADO, null));
    }

    @Override
    public void gerar(String envioId) {
        String rastreio = "SIM%08dBR".formatted(ThreadLocalRandom.current().nextInt(100_000_000));
        envios.put(envioId, new SituacaoEnvio(Etapa.GERADO, rastreio, true, LocalDate.now(PrazoTroca.FUSO_LOJA)));
    }

    @Override
    public String linkImpressao(String envioId) {
        return urlLoja + "/etiqueta-simulada.html?envio=" + envioId;
    }

    @Override
    public String linkImpressaoPublico(String envioId) {
        return linkImpressao(envioId) + "&volta=1";
    }
}
