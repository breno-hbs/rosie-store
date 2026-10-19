package com.rosie.backend.service;

import com.rosie.backend.model.Produto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// Peso e medidas de uma peça embalada (#56). Uma montagem só para a cotação (FreteService) e a etiqueta
// (EtiquetaService): se cada uma montasse o pacote do seu jeito, o frete cobrado da cliente e o da etiqueta
// comprada pela Roseane poderiam divergir. Cada campo vazio no produto usa o pacote padrão (rosie.frete.peca.*).
@Component
public class PacotePeca {

    private final BigDecimal pesoKg;
    private final int larguraCm;
    private final int alturaCm;
    private final int comprimentoCm;

    public PacotePeca(@Value("${rosie.frete.peca.peso-kg}") BigDecimal pesoKg,
                      @Value("${rosie.frete.peca.largura-cm}") int larguraCm,
                      @Value("${rosie.frete.peca.altura-cm}") int alturaCm,
                      @Value("${rosie.frete.peca.comprimento-cm}") int comprimentoCm) {
        this.pesoKg = pesoKg;
        this.larguraCm = larguraCm;
        this.alturaCm = alturaCm;
        this.comprimentoCm = comprimentoCm;
    }

    public Medidas de(Produto produto) {
        return new Medidas(
                produto.getPesoKg() != null ? produto.getPesoKg() : pesoKg,
                produto.getLarguraCm() != null ? produto.getLarguraCm() : larguraCm,
                produto.getAlturaCm() != null ? produto.getAlturaCm() : alturaCm,
                produto.getComprimentoCm() != null ? produto.getComprimentoCm() : comprimentoCm);
    }

    public record Medidas(BigDecimal pesoKg, int larguraCm, int alturaCm, int comprimentoCm) {
    }
}
