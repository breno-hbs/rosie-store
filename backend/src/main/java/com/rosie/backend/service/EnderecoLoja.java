package com.rosie.backend.service;

import com.rosie.backend.dto.EnderecoDevolucaoDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

// Para onde a cliente manda a peça quando o frete de volta é dela (troca "não serviu", #63): o mesmo endereço
// que sai como remetente na etiqueta de ida (ROSIE_REMETENTE_* e ROSIE_CEP_ORIGEM). Sem algum dado obrigatório,
// endereco() devolve null e a loja diz à cliente que a Rosie vai passar o endereço.
@Component
public class EnderecoLoja {

    private final EnderecoDevolucaoDTO endereco;

    public EnderecoLoja(@Value("${rosie.etiqueta.remetente.nome:}") String nome,
                        @Value("${rosie.etiqueta.remetente.logradouro:}") String logradouro,
                        @Value("${rosie.etiqueta.remetente.numero:}") String numero,
                        @Value("${rosie.etiqueta.remetente.complemento:}") String complemento,
                        @Value("${rosie.etiqueta.remetente.bairro:}") String bairro,
                        @Value("${rosie.etiqueta.remetente.cidade:}") String cidade,
                        @Value("${rosie.etiqueta.remetente.uf:}") String uf,
                        @Value("${rosie.frete.cep-origem:}") String cepOrigem) {
        String cep = cepOrigem == null ? "" : cepOrigem.replaceAll("\\D", "");
        boolean completo = cep.length() == 8 && uf != null && uf.trim().length() == 2
                && Stream.of(nome, logradouro, numero, bairro, cidade).noneMatch(EnderecoLoja::vazio);
        this.endereco = completo
                ? new EnderecoDevolucaoDTO(nome.trim(), logradouro.trim(), numero.trim(),
                        vazio(complemento) ? null : complemento.trim(), bairro.trim(), cidade.trim(),
                        uf.trim().toUpperCase(), cep.substring(0, 5) + "-" + cep.substring(5))
                : null;
    }

    public EnderecoDevolucaoDTO endereco() {
        return endereco;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
