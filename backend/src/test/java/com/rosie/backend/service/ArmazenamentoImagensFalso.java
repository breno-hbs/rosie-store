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

    public final List<byte[]> enviados = new ArrayList<>();
    public final List<String> removidos = new ArrayList<>();
    public boolean falharNaRemocao;

    private int contador;

    @Override
    public ImagemArmazenada enviar(byte[] conteudo) {
        enviados.add(conteudo);
        contador++;
        String publicId = "rosie/produtos/teste-" + contador;
        return new ImagemArmazenada("https://res.cloudinary.test/" + publicId + ".jpg", publicId);
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
