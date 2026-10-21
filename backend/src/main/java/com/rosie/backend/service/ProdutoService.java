package com.rosie.backend.service;

import com.rosie.backend.dto.ProdutoDetalheDTO;
import com.rosie.backend.dto.ProdutoListaResponseDTO;
import com.rosie.backend.dto.ProdutoResumoDTO;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.repository.ProdutoRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    public static final int LIMITE_MAXIMO_DESTAQUES = 50;

    // Busca maior que isso é cortada (sem erro); termos além do limite são ignorados
    public static final int TAMANHO_MAXIMO_BUSCA = 100;
    public static final int TERMOS_MAXIMOS_BUSCA = 5;

    private final ProdutoRepository produtoRepository;

    public ProdutoListaResponseDTO listar(Long categoriaId, String busca, int pagina, int tamanho) {
        Pageable pageable = PageRequest.of(pagina, tamanho);
        List<String> termos = termosDaBusca(busca);

        if (!termos.isEmpty()) {
            return ProdutoListaResponseDTO.from(produtoRepository.findAll(buscaPorTermos(categoriaId, termos), pageable));
        }

        var page = (categoriaId != null)
                ? produtoRepository.findByCategoriaIdAndAtivoTrue(categoriaId, pageable)
                : produtoRepository.findByAtivoTrue(pageable);

        return ProdutoListaResponseDTO.from(page);
    }

    // "Vestido  midi " -> ["vestido", "midi"]; vazia ou só espaços -> [] (lista sem busca)
    static List<String> termosDaBusca(String busca) {
        if (busca == null) return List.of();
        String cortada = busca.length() > TAMANHO_MAXIMO_BUSCA ? busca.substring(0, TAMANHO_MAXIMO_BUSCA) : busca;
        return Arrays.stream(cortada.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(termo -> !termo.isEmpty())
                .distinct()
                .limit(TERMOS_MAXIMOS_BUSCA)
                .toList();
    }

    // Só ativos; cada termo precisa aparecer no nome, na descrição, na composição ou no nome da categoria
    // ("vestido linho": um vestido de linho). Maiúsculas e acentos não contam pela collation do banco
    // (utf8mb4_0900_ai_ci): "calca" acha "Calça".
    private static Specification<Produto> buscaPorTermos(Long categoriaId, List<String> termos) {
        return (root, query, cb) -> {
            Join<Produto, Categoria> categoria = root.join("categoria");
            List<Predicate> filtros = new ArrayList<>();
            filtros.add(cb.isTrue(root.get("ativo")));
            if (categoriaId != null) {
                filtros.add(cb.equal(categoria.get("id"), categoriaId));
            }
            for (String termo : termos) {
                String padrao = "%" + escaparLike(termo) + "%";
                filtros.add(cb.or(
                        cb.like(cb.lower(root.get("nome")), padrao, ESCAPE_LIKE),
                        cb.like(cb.lower(root.get("descricao")), padrao, ESCAPE_LIKE),
                        cb.like(cb.lower(root.get("composicao")), padrao, ESCAPE_LIKE),
                        cb.like(cb.lower(categoria.get("nome")), padrao, ESCAPE_LIKE)));
            }
            return cb.and(filtros.toArray(Predicate[]::new));
        };
    }

    // "%" e "_" digitados pela cliente são texto, não curinga do LIKE. Escape "!" e não a barra: no MySQL a
    // barra também escapa dentro do literal SQL
    private static final char ESCAPE_LIKE = '!';

    private static String escaparLike(String termo) {
        return termo.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    // limite fora de 1..LIMITE_MAXIMO_DESTAQUES é ajustado em vez de recusado
    public List<ProdutoResumoDTO> listarDestaques(int limite) {
        int limiteAjustado = Math.max(1, Math.min(limite, LIMITE_MAXIMO_DESTAQUES));

        return produtoRepository.findByAtivoTrueAndDestaqueTrueOrderByIdDesc(Limit.of(limiteAjustado)).stream()
                .map(ProdutoResumoDTO::from)
                .toList();
    }

    public ProdutoDetalheDTO buscarPorId(Long id) {
        Produto produto = produtoRepository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto com id " + id + " não encontrado"));

        return ProdutoDetalheDTO.from(produto);
    }
}