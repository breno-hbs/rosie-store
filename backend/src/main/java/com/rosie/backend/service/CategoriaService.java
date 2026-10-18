package com.rosie.backend.service;

import com.rosie.backend.dto.CategoriaAdminDTO;
import com.rosie.backend.dto.CategoriaResumoDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private static final String MENSAGEM_DUPLICADA = "Já existe uma categoria com esse nome";

    private final CategoriaRepository categoriaRepository;
    private final ProdutoRepository produtoRepository;

    public List<CategoriaResumoDTO> listar() {
        return categoriaRepository.findAll(Sort.by("nome")).stream()
                .map(CategoriaResumoDTO::from)
                .toList();
    }

    // Painel admin (RF17)
    public List<CategoriaAdminDTO> listarAdmin() {
        return categoriaRepository.listarComQuantidadeDeProdutos();
    }

    @Transactional
    public CategoriaAdminDTO criar(String nome) {
        String nomeLimpo = nome.strip();
        if (categoriaRepository.existsByNomeIgnoreCase(nomeLimpo)) {
            throw new ConflitoException(MENSAGEM_DUPLICADA, "CATEGORIA_DUPLICADA");
        }
        Categoria categoria = salvar(Categoria.builder().nome(nomeLimpo).build());
        return new CategoriaAdminDTO(categoria.getId(), categoria.getNome(), 0);
    }

    @Transactional
    public CategoriaAdminDTO renomear(Long id, String nome) {
        Categoria categoria = buscar(id);
        String nomeLimpo = nome.strip();
        if (categoriaRepository.existsByNomeIgnoreCaseAndIdNot(nomeLimpo, id)) {
            throw new ConflitoException(MENSAGEM_DUPLICADA, "CATEGORIA_DUPLICADA");
        }
        categoria.setNome(nomeLimpo);
        salvar(categoria);
        return new CategoriaAdminDTO(categoria.getId(), categoria.getNome(), produtoRepository.countByCategoriaId(id));
    }

    // Só remove categoria vazia: produto inativo também conta, porque continua ligado a ela no banco
    @Transactional
    public void remover(Long id) {
        Categoria categoria = buscar(id);
        long produtos = produtoRepository.countByCategoriaId(id);
        if (produtos > 0) {
            String quantidade = produtos == 1 ? "1 produto" : produtos + " produtos";
            throw new ConflitoException(
                    "Não dá para remover \"%s\": a categoria tem %s. Mova os produtos para outra categoria antes."
                            .formatted(categoria.getNome(), quantidade),
                    "CATEGORIA_EM_USO");
        }
        categoriaRepository.delete(categoria);
    }

    private Categoria buscar(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));
    }

    // A coluna nome é UNIQUE: se outra requisição gravar o mesmo nome no meio do caminho, vira 409
    private Categoria salvar(Categoria categoria) {
        try {
            return categoriaRepository.saveAndFlush(categoria);
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException(MENSAGEM_DUPLICADA, "CATEGORIA_DUPLICADA");
        }
    }
}
