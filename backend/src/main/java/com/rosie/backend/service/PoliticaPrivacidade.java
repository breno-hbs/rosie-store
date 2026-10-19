package com.rosie.backend.service;

import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;

import java.time.LocalDate;

// Política de privacidade em vigor (LGPD, RNF06, #31). Este é o único lugar da versão: o cadastro e o
// reaceite gravam VERSAO no usuário, e a página /politica-de-privacidade da loja mostra a versão e a
// vigência lidas de GET /privacidade. O texto fica no front (pages/PoliticaPrivacidade.jsx).
// Mudou o texto? Troque a VIGENCIA para o dia em que o texto novo entra no ar: a versão muda junto, e
// quem aceitou a anterior precisa aceitar a nova no próximo login ou checkout.
public final class PoliticaPrivacidade {

    public static final LocalDate VIGENCIA = LocalDate.of(2026, 10, 19);

    // A versão é a data de vigência ("2026-10-19"); cabe nos 20 caracteres de usuario.privacidade_versao
    public static final String VERSAO = VIGENCIA.toString();

    private PoliticaPrivacidade() {
    }

    // Cliente que não aceitou a versão em vigor (aceitou uma antiga, ou nenhuma). Admin não tem aceite.
    public static boolean aceitePendente(Usuario usuario) {
        return usuario.getRole() == Role.CLIENTE && !VERSAO.equals(usuario.getPrivacidadeVersao());
    }
}
