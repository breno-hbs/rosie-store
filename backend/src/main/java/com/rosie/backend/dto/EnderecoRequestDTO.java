package com.rosie.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Body de POST e PUT /enderecos (#24). O front preenche rua, bairro, cidade e UF pelo ViaCEP, mas tudo
// é conferido aqui. Espaços nas pontas saem; a UF vira maiúscula e o CEP fica só com os dígitos.
public record EnderecoRequestDTO(
        @NotBlank(message = "Informe o CEP")
        @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido: use os 8 números, ex.: 03925-050")
        String cep,

        @NotBlank(message = "Informe a rua")
        @Size(max = 200, message = "A rua pode ter no máximo 200 caracteres")
        String logradouro,

        @NotBlank(message = "Informe o número")
        @Size(max = 20, message = "O número pode ter no máximo 20 caracteres")
        String numero,

        @Size(max = 100, message = "O complemento pode ter no máximo 100 caracteres")
        String complemento,

        @NotBlank(message = "Informe o bairro")
        @Size(max = 100, message = "O bairro pode ter no máximo 100 caracteres")
        String bairro,

        @NotBlank(message = "Informe a cidade")
        @Size(max = 100, message = "A cidade pode ter no máximo 100 caracteres")
        String cidade,

        @NotBlank(message = "Informe a UF")
        @Pattern(regexp = "AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO",
                message = "UF inválida: use a sigla do estado, ex.: SP")
        String estado
) {

    public EnderecoRequestDTO {
        cep = cep == null ? null : cep.trim();
        logradouro = aparar(logradouro);
        numero = aparar(numero);
        complemento = aparar(complemento);
        bairro = aparar(bairro);
        cidade = aparar(cidade);
        estado = estado == null ? null : estado.trim().toUpperCase();
    }

    public String cepSoDigitos() {
        return cep.replace("-", "");
    }

    // Complemento em branco vira nulo
    public String complementoOuNulo() {
        return complemento == null || complemento.isEmpty() ? null : complemento;
    }

    private static String aparar(String valor) {
        return valor == null ? null : valor.trim();
    }
}
