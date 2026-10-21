package com.rosie.backend.service;

import com.rosie.backend.dto.EstoqueDTO;
import com.rosie.backend.dto.EstoqueProdutoDTO;
import com.rosie.backend.dto.PacoteDTO;
import com.rosie.backend.dto.ProdutoAdminDTO;
import com.rosie.backend.dto.ProdutoAdminResumoDTO;
import com.rosie.backend.dto.ProdutoEdicaoDTO;
import com.rosie.backend.dto.ProdutoRequestDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.ProdutoImagem;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.PedidoItemRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.TrocaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// CRUD de produtos (RF14) e estoque por tamanho (RF16) do painel admin. Diferente do catálogo,
// enxerga os produtos inativos.
@Service
@RequiredArgsConstructor
@Slf4j
public class ProdutoAdminService {

    static final String MENSAGEM_COM_HISTORICO = "Este produto já entrou em pedido ou troca e não pode ser "
            + "excluído. Arquive para tirá-lo da loja: o histórico continua.";

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final PedidoItemRepository pedidoItemRepository;
    private final TrocaRepository trocaRepository;
    private final CarrinhoItemRepository carrinhoItemRepository;
    private final ArmazenamentoImagens armazenamento;

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
        preencher(produto, dados.dadosDoProduto());
        aplicarQuantidades(produto, quantidadesPorTamanho(dados.estoque()));

        return ProdutoAdminDTO.from(produtoRepository.save(produto));
    }

    // Não mexe no estoque (#47): ele só muda por atualizarEstoque, senão um formulário aberto antes
    // desfaria a mudança. destaque e ativo ausentes mantêm o valor atual; ativo = true reativa.
    @Transactional
    public ProdutoAdminDTO atualizar(Long id, ProdutoEdicaoDTO dados) {
        Produto produto = buscar(id);
        if (dados.destaque() != null) produto.setDestaque(dados.destaque());
        if (dados.ativo() != null) produto.setAtivo(dados.ativo());
        preencher(produto, dados);

        return ProdutoAdminDTO.from(produtoRepository.saveAndFlush(produto));
    }

    // Arquivar só desativa: some da loja, mas pedidos e trocas antigos continuam apontando para o produto
    @Transactional
    public void desativar(Long id) {
        Produto produto = buscar(id);
        produto.setAtivo(false);
    }

    // Exclusão definitiva (#87), para cadastro errado, teste ou duplicado: só sem histórico (nunca entrou em
    // pedido nem em troca). Leva junto o estoque, as fotos e as linhas das sacolas. As fotos saem do
    // Cloudinary só depois do commit, e uma falha lá não desfaz a exclusão (fica só o arquivo órfão, no log).
    @Transactional
    public void excluirDefinitivamente(Long id) {
        Produto produto = buscar(id);
        if (pedidoItemRepository.existsByProdutoId(id) || trocaRepository.existsByProdutoId(id)) {
            throw new ConflitoException(MENSAGEM_COM_HISTORICO, "PRODUTO_COM_HISTORICO");
        }

        // Imagens antigas (placeholders, sem public_id) não estão no Cloudinary
        List<String> fotos = produto.getImagens().stream()
                .map(ProdutoImagem::getPublicId)
                .filter(Objects::nonNull)
                .toList();

        carrinhoItemRepository.deleteByProdutoId(id);
        produtoRepository.delete(produto);
        try {
            produtoRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // Um pedido fechado com o produto entre a conferência acima e o DELETE: a chave estrangeira barra
            throw new ConflitoException(MENSAGEM_COM_HISTORICO, "PRODUTO_COM_HISTORICO");
        }

        removerFotosDepoisDoCommit(id, fotos);
    }

    // Único caminho que muda o estoque de um produto já cadastrado. Só mexe nos tamanhos enviados:
    // os outros continuam como estão
    @Transactional
    public EstoqueProdutoDTO atualizarEstoque(Long id, List<EstoqueDTO> estoque) {
        Produto produto = buscar(id);
        aplicarQuantidades(produto, quantidadesPorTamanho(estoque));

        return EstoqueProdutoDTO.from(produtoRepository.saveAndFlush(produto));
    }

    private void removerFotosDepoisDoCommit(Long produtoId, List<String> fotos) {
        if (fotos.isEmpty()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fotos.forEach(publicId -> removerDoArmazenamento(produtoId, publicId));
            }
        });
    }

    private void removerDoArmazenamento(Long produtoId, String publicId) {
        try {
            armazenamento.remover(publicId);
        } catch (RuntimeException e) {
            log.warn("Produto {} excluído, mas a foto {} não foi removida do armazenamento: {}",
                    produtoId, publicId, e.getMessage());
        }
    }

    private Produto buscar(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto com id " + id + " não encontrado"));
    }

    private void preencher(Produto produto, ProdutoEdicaoDTO dados) {
        Categoria categoria = categoriaRepository.findById(dados.categoriaId())
                .orElseThrow(() -> new DadosInvalidosException("Categoria não encontrada"));

        produto.setNome(dados.nome().trim());
        // Ausente (null) mantém o texto atual, como destaque e ativo; vazio limpa (#50)
        if (dados.descricao() != null) produto.setDescricao(textoOpcional(dados.descricao()));
        if (dados.composicao() != null) produto.setComposicao(textoOpcional(dados.composicao()));
        produto.setPreco(dados.preco());
        produto.setCategoria(categoria);
        // Ausente mantém o pacote atual (reativar não manda); presente troca os quatro campos juntos, e o
        // que vier nulo volta para o pacote padrão do frete (#56)
        PacoteDTO pacote = dados.pacote();
        if (pacote != null) {
            produto.setPesoKg(pacote.pesoKg());
            produto.setAlturaCm(pacote.alturaCm());
            produto.setLarguraCm(pacote.larguraCm());
            produto.setComprimentoCm(pacote.comprimentoCm());
        }
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
