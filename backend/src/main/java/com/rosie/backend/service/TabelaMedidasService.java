package com.rosie.backend.service;

import com.rosie.backend.dto.MedidaDTO;
import com.rosie.backend.dto.TabelaMedidasDTO;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.MedidaCategoria;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.MedidaCategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Tabela de medidas por categoria (#20): a Página de Produto lê, a Roseane edita no painel
@Service
@RequiredArgsConstructor
public class TabelaMedidasService {

    public static final String UNIDADE = "cm";

    private final CategoriaRepository categoriaRepository;
    private final MedidaCategoriaRepository medidaRepository;

    @Transactional(readOnly = true)
    public TabelaMedidasDTO buscar(Long categoriaId) {
        Categoria categoria = buscarCategoria(categoriaId);
        List<MedidaDTO> medidas = medidaRepository.findByCategoriaIdOrderByOrdem(categoriaId).stream()
                .map(MedidaDTO::from)
                .toList();
        return new TabelaMedidasDTO(categoria.getId(), categoria.getNome(), UNIDADE, medidas);
    }

    // Troca a tabela inteira pela recebida, na ordem da lista. Nomes e valores sem espaços nas pontas.
    @Transactional
    public TabelaMedidasDTO salvar(Long categoriaId, List<MedidaDTO> medidas) {
        Categoria categoria = buscarCategoria(categoriaId);

        Set<String> nomes = new HashSet<>();
        List<MedidaCategoria> linhas = new ArrayList<>();
        for (MedidaDTO medida : medidas) {
            String nome = medida.nome().strip();
            if (!nomes.add(nome.toLowerCase(Locale.ROOT))) {
                throw new DadosInvalidosException("A medida \"%s\" aparece mais de uma vez".formatted(nome));
            }
            MedidaCategoria linha = MedidaCategoria.builder()
                    .categoria(categoria)
                    .nome(nome)
                    .ordem(linhas.size())
                    .build();
            Map<String, String> valores = medida.valores() == null ? Map.of() : medida.valores();
            for (Tamanho tamanho : Tamanho.values()) {
                linha.setValor(tamanho, textoOpcional(valores.get(tamanho.name())));
            }
            linhas.add(linha);
        }

        medidaRepository.removerDaCategoria(categoriaId);
        medidaRepository.saveAll(linhas);
        return new TabelaMedidasDTO(categoria.getId(), categoria.getNome(), UNIDADE,
                linhas.stream().map(MedidaDTO::from).toList());
    }

    // Apaga a tabela inteira: a categoria volta a não ter medidas e o link some da Página de Produto
    @Transactional
    public void remover(Long categoriaId) {
        buscarCategoria(categoriaId);
        medidaRepository.removerDaCategoria(categoriaId);
    }

    private Categoria buscarCategoria(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));
    }

    private static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
