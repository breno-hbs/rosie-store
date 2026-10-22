package com.rosie.backend.service;

import com.rosie.backend.dto.CategoriaAdminDTO;
import com.rosie.backend.dto.CategoriaResumoDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.CategoriaSlugAntigo;
import com.rosie.backend.model.Slug;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.CategoriaSlugAntigoRepository;
import com.rosie.backend.repository.MedidaCategoriaRepository;
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
    private final MedidaCategoriaRepository medidaRepository;
    private final CategoriaSlugAntigoRepository slugAntigoRepository;

    public List<CategoriaResumoDTO> listar() {
        return categoriaRepository.findAll(Sort.by("nome")).stream()
                .map(CategoriaResumoDTO::from)
                .toList();
    }

    // Página da categoria na loja (#88). O slug de antes de renomear também acha a categoria, que volta com o
    // slug atual: a loja troca o endereço pelo novo.
    @Transactional(readOnly = true)
    public CategoriaResumoDTO buscarPorSlug(String slug) {
        return categoriaRepository.findBySlug(slug)
                .or(() -> slugAntigoRepository.findBySlug(slug).map(CategoriaSlugAntigo::getCategoria))
                .map(CategoriaResumoDTO::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));
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
        String slug = slugLivre(nomeLimpo, null);
        slugAntigoRepository.liberar(slug);
        Categoria categoria = salvar(Categoria.builder().nome(nomeLimpo).slug(slug).build());
        return new CategoriaAdminDTO(categoria.getId(), categoria.getNome(), categoria.getSlug(), 0);
    }

    @Transactional
    public CategoriaAdminDTO renomear(Long id, String nome) {
        Categoria categoria = buscar(id);
        String nomeLimpo = nome.strip();
        if (categoriaRepository.existsByNomeIgnoreCaseAndIdNot(nomeLimpo, id)) {
            throw new ConflitoException(MENSAGEM_DUPLICADA, "CATEGORIA_DUPLICADA");
        }
        categoria.setNome(nomeLimpo);
        trocarSlug(categoria, slugLivre(nomeLimpo, id));
        salvar(categoria);
        return new CategoriaAdminDTO(categoria.getId(), categoria.getNome(), categoria.getSlug(),
                produtoRepository.countByCategoriaId(id));
    }

    // Só remove categoria vazia: produto inativo também conta, porque continua ligado a ela no banco.
    // A tabela de medidas (#20) não bloqueia: sai junto com a categoria.
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
        medidaRepository.removerDaCategoria(id);
        slugAntigoRepository.removerDaCategoria(id);
        categoriaRepository.delete(categoria);
    }

    // Slug do nome; se outra categoria já usa, o primeiro sufixo livre ("vestido-2", "vestido-3"...).
    // Renomear sem mudar o slug (ex.: só maiúsculas) mantém o da própria categoria.
    private String slugLivre(String nome, Long idAtual) {
        String base = Slug.de(nome);
        String slug = base;
        for (int numero = 2; categoriaRepository.slugEmUso(slug, idAtual); numero++) {
            slug = Slug.candidato(base, numero);
        }
        return slug;
    }

    // Renomear muda o endereço da página: o slug anterior fica guardado para os links já divulgados
    // continuarem abrindo a categoria (até outra categoria passar a usar esse slug)
    private void trocarSlug(Categoria categoria, String novo) {
        String anterior = categoria.getSlug();
        if (novo.equals(anterior)) {
            return;
        }
        slugAntigoRepository.liberar(novo);
        slugAntigoRepository.save(CategoriaSlugAntigo.builder().slug(anterior).categoria(categoria).build());
        categoria.setSlug(novo);
    }

    private Categoria buscar(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));
    }

    // As colunas nome e slug são UNIQUE: se outra requisição gravar o mesmo nome (ou o mesmo slug) no meio do
    // caminho, vira 409
    private Categoria salvar(Categoria categoria) {
        try {
            return categoriaRepository.saveAndFlush(categoria);
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException(MENSAGEM_DUPLICADA, "CATEGORIA_DUPLICADA");
        }
    }
}
