package com.rosie.backend.config;

import com.rosie.backend.service.EmissorEtiqueta;
import com.rosie.backend.service.EtiquetaIndisponivel;
import com.rosie.backend.service.EtiquetaSimulada;
import com.rosie.backend.service.MelhorEnvioEmissor;
import com.rosie.backend.service.MelhorEnvioEmissor.Remetente;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Etiqueta de envio (#25), com o mesmo token, URL e User-Agent do frete (#23) e os dados do remetente
// (a loja) em ROSIE_REMETENTE_*. Faltando o token, o CEP de origem ou um dado obrigatório do remetente, a
// API sobe do mesmo jeito (dev local, CI) e só a geração de etiqueta responde 503 ETIQUETA_INDISPONIVEL.
// FRETE_SIMULADO=true liga também a etiqueta de mentira para desenvolvimento.
@Configuration
@Slf4j
public class EtiquetaConfig {

    @Bean
    public EmissorEtiqueta emissorEtiqueta(
            @Value("${rosie.frete.simulado:false}") boolean simulado,
            @Value("${rosie.frete.melhor-envio.token:}") String token,
            @Value("${rosie.frete.melhor-envio.url:https://sandbox.melhorenvio.com.br}") String url,
            @Value("${rosie.frete.melhor-envio.user-agent:Rosie (rosiestore.com.br)}") String userAgent,
            @Value("${rosie.frete.cep-origem:}") String cepOrigem,
            @Value("${rosie.etiqueta.remetente.nome:}") String nome,
            @Value("${rosie.etiqueta.remetente.telefone:}") String telefone,
            @Value("${rosie.etiqueta.remetente.email:}") String email,
            @Value("${rosie.etiqueta.remetente.documento:}") String documento,
            @Value("${rosie.etiqueta.remetente.inscricao-estadual:}") String inscricaoEstadual,
            @Value("${rosie.etiqueta.remetente.logradouro:}") String logradouro,
            @Value("${rosie.etiqueta.remetente.numero:}") String numero,
            @Value("${rosie.etiqueta.remetente.complemento:}") String complemento,
            @Value("${rosie.etiqueta.remetente.bairro:}") String bairro,
            @Value("${rosie.etiqueta.remetente.cidade:}") String cidade,
            @Value("${rosie.etiqueta.remetente.uf:}") String uf,
            @Value("${rosie.loja.url:http://localhost:5173}") String urlLoja,
            @Value("${rosie.etiqueta.timeout:20s}") Duration timeout) {
        if (simulado) {
            log.warn("FRETE_SIMULADO ligado: a etiqueta de envio é de mentira, sem o Melhor Envio.");
            return new EtiquetaSimulada(urlLoja.trim());
        }
        if (vazio(token)) {
            log.warn("MELHOR_ENVIO_TOKEN não definido: a geração de etiqueta vai responder 503.");
            return new EtiquetaIndisponivel();
        }

        String cep = digitos(cepOrigem);
        String doc = digitos(documento);
        Map<String, Boolean> obrigatorios = new LinkedHashMap<>();
        obrigatorios.put("ROSIE_CEP_ORIGEM (8 dígitos)", cep.length() == 8);
        obrigatorios.put("ROSIE_REMETENTE_NOME", !vazio(nome));
        obrigatorios.put("ROSIE_REMETENTE_DOCUMENTO (CPF ou CNPJ)", doc.length() == 11 || doc.length() == 14);
        obrigatorios.put("ROSIE_REMETENTE_TELEFONE", !vazio(telefone));
        obrigatorios.put("ROSIE_REMETENTE_LOGRADOURO", !vazio(logradouro));
        obrigatorios.put("ROSIE_REMETENTE_NUMERO", !vazio(numero));
        obrigatorios.put("ROSIE_REMETENTE_BAIRRO", !vazio(bairro));
        obrigatorios.put("ROSIE_REMETENTE_CIDADE", !vazio(cidade));
        obrigatorios.put("ROSIE_REMETENTE_UF (sigla)", !vazio(uf) && uf.trim().length() == 2);
        List<String> faltando = new ArrayList<>();
        obrigatorios.forEach((variavel, ok) -> {
            if (!ok) faltando.add(variavel);
        });
        if (!faltando.isEmpty()) {
            log.warn("Etiqueta de envio sem configuração completa (falta {}): a geração vai responder 503.",
                    String.join(", ", faltando));
            return new EtiquetaIndisponivel();
        }

        Remetente remetente = new Remetente(nome.trim(), digitos(telefone), opcional(email), doc,
                opcional(inscricaoEstadual), cep, logradouro.trim(), numero.trim(), opcional(complemento),
                bairro.trim(), cidade.trim(), uf.trim().toUpperCase());

        // Mais folga que a cotação: a Roseane espera no painel, e comprar e gerar são mais lentos
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout);
        fabrica.setReadTimeout(timeout);
        return new MelhorEnvioEmissor(RestClient.builder().requestFactory(fabrica), url.trim(), token.trim(),
                userAgent.trim(), remetente);
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String digitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private static String opcional(String valor) {
        return vazio(valor) ? null : valor.trim();
    }
}
