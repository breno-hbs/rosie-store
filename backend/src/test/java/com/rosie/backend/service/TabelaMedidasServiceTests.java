package com.rosie.backend.service;

import com.rosie.backend.dto.MedidaDTO;
import com.rosie.backend.dto.TabelaMedidasDTO;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.MedidaCategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Usa o MySQL local; @Transactional desfaz os inserts ao fim de cada teste
@SpringBootTest
@Transactional
class TabelaMedidasServiceTests {

    @Autowired
    private TabelaMedidasService tabelaMedidasService;

    @Autowired
    private CategoriaService categoriaService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MedidaCategoriaRepository medidaRepository;

    private Categoria categoria;

    @BeforeEach
    void setUp() {
        categoria = categoriaRepository.save(Categoria.builder().nome("Medidas " + System.nanoTime()).build());
    }

    @Test
    void categoriaSemTabelaVoltaComListaVazia() {
        TabelaMedidasDTO tabela = tabelaMedidasService.buscar(categoria.getId());

        assertThat(tabela.categoriaId()).isEqualTo(categoria.getId());
        assertThat(tabela.categoria()).isEqualTo(categoria.getNome());
        assertThat(tabela.unidade()).isEqualTo("cm");
        assertThat(tabela.medidas()).isEmpty();
    }

    @Test
    void salvarGuardaNaOrdemDaListaComTodosOsTamanhos() {
        tabelaMedidasService.salvar(categoria.getId(), List.of(
                medida("Busto", Map.of("P", "84–88", "M", "89–93", "G", "94–98", "GG", "99–104")),
                medida("Comprimento", Map.of("M", "92"))));

        TabelaMedidasDTO tabela = tabelaMedidasService.buscar(categoria.getId());

        assertThat(tabela.medidas()).extracting(MedidaDTO::nome).containsExactly("Busto", "Comprimento");
        assertThat(tabela.medidas().get(0).valores())
                .containsExactly(Map.entry("P", "84–88"), Map.entry("M", "89–93"),
                        Map.entry("G", "94–98"), Map.entry("GG", "99–104"));
        // Tamanho que não veio fica vazio, mas a chave sempre aparece
        assertThat(tabela.medidas().get(1).valores())
                .containsExactly(Map.entry("P", ""), Map.entry("M", "92"), Map.entry("G", ""), Map.entry("GG", ""));
    }

    @Test
    void salvarTiraEspacosDasPontasEValorEmBrancoViraVazio() {
        Map<String, String> valores = new HashMap<>();
        valores.put("P", "  70 ");
        valores.put("M", "   ");
        valores.put("G", null);

        TabelaMedidasDTO tabela = tabelaMedidasService.salvar(categoria.getId(), List.of(medida("  Cintura ", valores)));

        assertThat(tabela.medidas()).singleElement().satisfies(medida -> {
            assertThat(medida.nome()).isEqualTo("Cintura");
            assertThat(medida.valores()).containsEntry("P", "70").containsEntry("M", "").containsEntry("G", "");
        });
        assertThat(medidaRepository.findByCategoriaIdOrderByOrdem(categoria.getId()))
                .singleElement().satisfies(linha -> assertThat(linha.getValorM()).isNull());
    }

    @Test
    void salvarDeNovoSubstituiAsLinhasAntigas() {
        tabelaMedidasService.salvar(categoria.getId(), List.of(medida("Busto", Map.of()), medida("Cintura", Map.of())));

        tabelaMedidasService.salvar(categoria.getId(), List.of(medida("Quadril", Map.of("P", "90")), medida("Busto", Map.of())));

        assertThat(tabelaMedidasService.buscar(categoria.getId()).medidas())
                .extracting(MedidaDTO::nome).containsExactly("Quadril", "Busto");
        assertThat(medidaRepository.findByCategoriaIdOrderByOrdem(categoria.getId())).hasSize(2);
    }

    @Test
    void salvarListaVaziaApagaATabela() {
        tabelaMedidasService.salvar(categoria.getId(), List.of(medida("Busto", Map.of())));

        TabelaMedidasDTO tabela = tabelaMedidasService.salvar(categoria.getId(), List.of());

        assertThat(tabela.medidas()).isEmpty();
        assertThat(medidaRepository.findByCategoriaIdOrderByOrdem(categoria.getId())).isEmpty();
    }

    @Test
    void salvarComNomeRepetidoSemDiferenciarMaiusculasEhRecusado() {
        tabelaMedidasService.salvar(categoria.getId(), List.of(medida("Busto", Map.of())));

        assertThatThrownBy(() -> tabelaMedidasService.salvar(categoria.getId(),
                List.of(medida("Quadril", Map.of()), medida("quadril ", Map.of()))))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("A medida \"quadril\" aparece mais de uma vez");
        // Nada mudou
        assertThat(tabelaMedidasService.buscar(categoria.getId()).medidas())
                .extracting(MedidaDTO::nome).containsExactly("Busto");
    }

    @Test
    void tabelaDeUmaCategoriaNaoMexeNaDeOutra() {
        Categoria outra = categoriaRepository.save(Categoria.builder().nome("Outra " + System.nanoTime()).build());
        tabelaMedidasService.salvar(outra.getId(), List.of(medida("Cintura", Map.of())));

        tabelaMedidasService.salvar(categoria.getId(), List.of(medida("Busto", Map.of())));
        tabelaMedidasService.remover(categoria.getId());

        assertThat(tabelaMedidasService.buscar(outra.getId()).medidas())
                .extracting(MedidaDTO::nome).containsExactly("Cintura");
    }

    @Test
    void removerApagaATabela() {
        tabelaMedidasService.salvar(categoria.getId(), List.of(medida("Busto", Map.of())));

        tabelaMedidasService.remover(categoria.getId());

        assertThat(tabelaMedidasService.buscar(categoria.getId()).medidas()).isEmpty();
    }

    @Test
    void removerCategoriaVaziaLevaAsMedidasJunto() {
        tabelaMedidasService.salvar(categoria.getId(), List.of(medida("Busto", Map.of("P", "84"))));

        categoriaService.remover(categoria.getId());

        assertThat(categoriaRepository.findById(categoria.getId())).isEmpty();
        assertThat(medidaRepository.findByCategoriaIdOrderByOrdem(categoria.getId())).isEmpty();
    }

    @Test
    void categoriaInexistenteDa404() {
        assertThatThrownBy(() -> tabelaMedidasService.buscar(-1L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Categoria não encontrada");
        assertThatThrownBy(() -> tabelaMedidasService.salvar(-1L, List.of()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> tabelaMedidasService.remover(-1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private static MedidaDTO medida(String nome, Map<String, String> valores) {
        return new MedidaDTO(nome, valores);
    }
}
