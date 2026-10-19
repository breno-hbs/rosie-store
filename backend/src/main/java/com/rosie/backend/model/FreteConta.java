package com.rosie.backend.model;

// Quem paga o frete de volta da troca (RNF08): ROSIE se a peça veio com defeito, CLIENTE se não serviu
public enum FreteConta {
    ROSIE, CLIENTE;

    public static FreteConta para(MotivoTroca motivo) {
        return motivo == MotivoTroca.DEFEITO ? ROSIE : CLIENTE;
    }
}
