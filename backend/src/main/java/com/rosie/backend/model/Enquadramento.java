package com.rosie.backend.model;

// Que parte da foto fica no corte 4:5 (#18), escolhido no painel ao enviar a foto.
// AUTOMATICO deixa o Cloudinary achar o ponto de interesse; TOPO guarda a parte de cima
// (bom para foto de corpo inteiro com a cabeça no alto); CENTRO corta por igual dos dois lados.
public enum Enquadramento {
    AUTOMATICO, CENTRO, TOPO
}
