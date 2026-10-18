package com.rosie.backend.dto;

import com.rosie.backend.model.Endereco;

// Endereço de entrega da cliente (#24). cep só com os 8 dígitos; estado é a sigla da UF.
public record EnderecoDTO(
        Long id,
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String estado
) {

    public static EnderecoDTO from(Endereco endereco) {
        return new EnderecoDTO(
                endereco.getId(),
                endereco.getCep(),
                endereco.getLogradouro(),
                endereco.getNumero(),
                endereco.getComplemento(),
                endereco.getBairro(),
                endereco.getCidade(),
                endereco.getEstado()
        );
    }
}
