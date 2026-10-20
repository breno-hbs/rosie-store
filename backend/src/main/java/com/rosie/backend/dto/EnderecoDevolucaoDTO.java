package com.rosie.backend.dto;

// Endereço da loja para a cliente mandar a peça por conta própria (troca "não serviu", #63). cep já
// formatado ("01310-100").
public record EnderecoDevolucaoDTO(String nome, String logradouro, String numero, String complemento, String bairro,
                                   String cidade, String uf, String cep) {

    // Numa linha, para o e-mail: "Rosie Store, Avenida Paulista, 1000, Sala 5, Bela Vista, São Paulo/SP, CEP 01310-100"
    public String emUmaLinha() {
        StringBuilder linha = new StringBuilder(nome).append(", ").append(logradouro).append(", ").append(numero);
        if (complemento != null) linha.append(", ").append(complemento);
        return linha.append(", ").append(bairro).append(", ").append(cidade).append("/").append(uf)
                .append(", CEP ").append(cep).toString();
    }
}
