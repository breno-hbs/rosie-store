package com.rosie.backend.service;

import com.rosie.backend.exception.ArmazenamentoIndisponivelException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

// Substitui o Cloudinary nos testes: guarda o que foi enviado e removido, sem rede nem chaves.
// O bean é compartilhado entre os testes do mesmo contexto: chame limpar() no @BeforeEach.
public class ArmazenamentoImagensFalso implements ArmazenamentoImagens {

    // Prefixo da URL guardada no banco e da URL entregue pela API (corte 4:5 automático)
    public static final String ORIGINAL = "https://res.cloudinary.com/rosie-teste/image/upload/v1/";
    public static final String ENTREGA = "https://res.cloudinary.com/rosie-teste/image/upload/"
            + "c_fill,ar_4:5,g_auto/c_limit,w_1000/f_auto,q_auto/v1/";

    public final List<byte[]> enviados = new ArrayList<>();
    public final List<String> removidos = new ArrayList<>();
    public boolean falharNaRemocao;
    // Número do envio (1, 2...) que falha como o Cloudinary fora do ar; 0 = nenhum
    public int falharNoEnvio;

    private int contador;

    @Override
    public ImagemArmazenada enviar(byte[] conteudo, String pasta) {
        if (contador + 1 == falharNoEnvio) throw new ArmazenamentoIndisponivelException("Cloudinary fora do ar (teste)");
        enviados.add(conteudo);
        contador++;
        String publicId = pasta + "/teste-" + contador;
        // Mesmo formato da secure_url do Cloudinary, para os testes verem o corte 4:5 na entrega
        return new ImagemArmazenada(ORIGINAL + publicId + ".jpg", publicId);
    }

    @Override
    public void remover(String publicId) {
        if (falharNaRemocao) throw new ArmazenamentoIndisponivelException("Cloudinary fora do ar (teste)");
        removidos.add(publicId);
    }

    public void limpar() {
        enviados.clear();
        removidos.clear();
        falharNaRemocao = false;
        falharNoEnvio = 0;
        contador = 0;
    }

    // @Import(ArmazenamentoImagensFalso.Config.class) no teste troca o storage real por este
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public ArmazenamentoImagensFalso armazenamentoImagensFalso() {
            return new ArmazenamentoImagensFalso();
        }
    }
}
