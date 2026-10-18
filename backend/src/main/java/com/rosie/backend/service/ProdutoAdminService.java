package com.rosie.backend.service;

import com.rosie.backend.dto.EstoqueDTO;
import com.rosie.backend.dto.EstoqueProdutoDTO;
import com.rosie.backend.dto.ProdutoAdminDTO;
import com.rosie.backend.dto.ProdutoAdminResumoDTO;
import com.rosie.backend.dto.ProdutoRequestDTO;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// CRUD de produtos (RF14) e estoque por tamanho (RF16) do painel admin. Diferente do catálogo,
// enxerga os produtos inativos.
@Service
@RequiredArgsConstructor
public class ProdutoAdminService {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<ProdutoAdminResumoDTO> listar() {
        return produtoRepository.findAllByOrderByIdDesc().stream()
                .map(ProdutoAdminResumoDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProdutoAdminDTO buscarPorId(Long id) {
        return ProdutoAdminDTO.from(buscar(id));
    }

    @Transactional
    public ProdutoAdminDTO criar(ProdutoRequestDTO dados) {
        Produto produto = Produto.builder()
                .destaque(Boolean.TRUE.equals(dados.destaque()))
                .ativo(!Boolean.FALSE.equals(dados.ativo()))
                .build();
        preencher(produto, dados);

        return ProdutoAdminDTO.from(produtoRepository.save(produto));
    }

    // Mesmo body do POST; destaque e ativo ausentes mantêm o valor atual. ativo = true reativa.
    @Transactional
    public ProdutoAdminDTO atualizar(Long id, ProdutoRequestDTO dados) {
        Produto produto = buscar(id);
        if (dados.destaque() != null) produto.setDestaque(dados.destaque());
        if (dados.ativo() != null) produto.setAtivo(dados.ativo());
        preencher(produto, dados);

        return ProdutoAdminDTO.from(produtoRepository.saveAndFlush(produto));
    }

    // Remover só desativa: pedidos e carrinhos antigos continuam apontando para o produto
    @Transactional
    public void desativar(Long id) {
        Produto produto = buscar(id);
        produto.setAtivo(false);
    }

    // Diferente do PUT do produto, só mexe nos tamanhos enviados: os outros continuam como estão
    @Transactional
    public EstoqueProdutoDTO atualizarEstoque(Long id, List<EstoqueDTO> estoque) {
        Produto produto = buscar(id);
        aplicarQuantidades(produto, quantidadesPorTamanho(estoque));

        return EstoqueProdutoDTO.from(produtoRepository.saveAndFlush(produto));
    }

    private Produto buscar(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto com id " + id + " não encontrado"));
    }

    private void preencher(Produto produto, ProdutoRequestDTO dados) {
        Categoria categoria = categoriaRepository.findById(dados.categoriaId())
                .orElseThrow(() -> new DadosInvalidosException("Categoria não encontrada"));

        produto.setNome(dados.nome().trim());
        // Ausente (null) mantém o texto atual, como destaque e ativo; vazio limpa (#50)
        if (dados.descricao() != null) produto.setDescricao(textoOpcional(dados.descricao()));
        if (dados.composicao() != null) produto.setComposicao(textoOpcional(dados.composicao()));
        produto.setPreco(dados.preco());
        produto.setCategoria(categoria);
        substituirEstoque(produto, quantidadesPorTamanho(dados.estoque()));
    }

    // Texto vazio ou só com espaços vira null, e a Página de Produto esconde o item
    private String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    // O formato de cada item já foi validado no DTO; aqui só falta barrar tamanho repetido
    private Map<Tamanho, Integer> quantidadesPorTamanho(List<EstoqueDTO> estoque) {
        Map<Tamanho, Integer> quantidades = new EnumMap<>(Tamanho.class);
        if (estoque == null) return quantidades;

        for (EstoqueDTO item : estoque) {
            Tamanho tamanho = Tamanho.valueOf(item.tamanho());
            if (quantidades.put(tamanho, item.quantidade()) != null) {
                throw new DadosInvalidosException("Tamanho " + tamanho + " repetido no estoque");
            }
        }
        return quantidades;
    }

    // Atualiza as linhas existentes em vez de apagar e recriar: o Hibernate faz os INSERTs
    // antes dos DELETEs, o que bateria na unique (produto_id, tamanho)
    private void substituirEstoque(Produto produto, Map<Tamanho, Integer> quantidades) {
        produto.getEstoque().removeIf(linha -> !quantidades.containsKey(linha.getTamanho()));
        aplicarQuantidades(produto, quantidades);
    }

    // Atualiza a quantidade dos tamanhos que o produto já tem e cria os que faltam
    private void aplicarQuantidades(Produto produto, Map<Tamanho, Integer> quantidades) {
        quantidades.forEach((tamanho, quantidade) -> produto.getEstoque().stream()
                .filter(linha -> linha.getTamanho() == tamanho)
                .findFirst()
                .ifPresentOrElse(
                        linha -> linha.setQuantidade(quantidade),
                        () -> produto.getEstoque().add(ProdutoEstoque.builder()
                                .produto(produto)
                                .tamanho(tamanho)
                                .quantidade(quantidade)
                                .build())));
    }
}
