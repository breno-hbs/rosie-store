package com.rosie.backend.dto;

import java.time.LocalDate;

// GET /privacidade (#31): versão e vigência da política em vigor, mostradas na página da loja
public record PoliticaPrivacidadeDTO(String versao, LocalDate vigencia) {
}
