package com.rosie.backend.service;

import com.rosie.backend.dto.ProdutoImagemDTO;
import com.rosie.backend.exception.ArquivoInvalidoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoImagem;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.service.ArmazenamentoImagens.ImagemArmazenada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Fotos do produto no painel (RF15): upload, remoção e ordem. A foto de ordem 0 é a principal
// (imagemPrincipal na vitrine). A ordem fica sempre 0, 1, 2... sem buracos.
@Service
@Slf4j
public class ProdutoImagemService {

    private final ProdutoRepository produtoRepository;
    private final ArmazenamentoImagens armazenamento;
    private final DataSize tamanhoMaximo;

    public ProdutoImagemService(ProdutoRepository produtoRepository, ArmazenamentoImagens armazenamento,
                                @Value("${rosie.imagens.tamanho-maximo}") DataSize tamanhoMaximo) {
        this.produtoRepository = produtoRepository;
        this.armazenamento = armazenamento;
        this.tamanhoMaximo = tamanhoMaximo;
    }

    // A foto nova entra no fim da galeria
    @Transactional
    public ProdutoImagemDTO enviar(Long produtoId, MultipartFile arquivo) {
        Produto produto = buscarProduto(produtoId);
        byte[] conteudo = validar(arquivo);

        ImagemArmazenada armazenada = armazenamento.enviar(conteudo);
        ProdutoImagem imagem = ProdutoImagem.builder()
                .produto(produto)
                .url(armazenada.url())
                .publicId(armazenada.publicId())
                .ordem(Integer.MAX_VALUE)
                .build();
        produto.getImagens().add(imagem);
        renumerar(produto);

        try {
            // flush, não saveAndFlush: o merge criaria uma cópia da imagem e esta ficaria sem id
            produtoRepository.flush();
        } catch (RuntimeException e) {
            // Não deixa a foto sobrando no Cloudinary se o banco recusou
            removerDoArmazenamento(armazenada.publicId());
            throw e;
        }
        return ProdutoImagemDTO.from(imagem);
    }

    // Tira a foto do produto e apaga do Cloudinary. Se o Cloudinary falhar, a foto sai do produto
    // do mesmo jeito (fica só um arquivo órfão lá, registrado no log).
    @Transactional
    public void remover(Long produtoId, Long imagemId) {
        Produto produto = buscarProduto(produtoId);
        ProdutoImagem imagem = produto.getImagens().stream()
                .filter(i -> i.getId().equals(imagemId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Foto com id " + imagemId + " não encontrada no produto " + produtoId));

        produto.getImagens().remove(imagem);
        renumerar(produto);
        produtoRepository.flush();

        removerDoArmazenamento(imagem.getPublicId());
    }

    // Recebe os ids de todas as fotos do produto na nova ordem; o primeiro vira a foto principal
    @Transactional
    public List<ProdutoImagemDTO> reordenar(Long produtoId, List<Long> ids) {
        Produto produto = buscarProduto(produtoId);
        Map<Long, ProdutoImagem> porId = produto.getImagens().stream()
                .collect(Collectors.toMap(ProdutoImagem::getId, Function.identity()));

        // Mesmo tamanho e mesmos ids = cada foto do produto aparece exatamente uma vez
        if (ids.size() != porId.size() || !new HashSet<>(ids).equals(porId.keySet())) {
            throw new DadosInvalidosException("Envie todas as fotos do produto, cada uma uma vez");
        }
        for (int i = 0; i < ids.size(); i++) {
            porId.get(ids.get(i)).setOrdem(i);
        }
        produtoRepository.flush();

        return ids.stream().map(id -> ProdutoImagemDTO.from(porId.get(id))).toList();
    }

    private Produto buscarProduto(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto com id " + id + " não encontrado"));
    }

    // O formato é conferido pelo conteúdo, não pela extensão nem pelo Content-Type do navegador
    private byte[] validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new DadosInvalidosException("Envie a foto no campo arquivo");
        }
        if (arquivo.getSize() > tamanhoMaximo.toBytes()) {
            throw ArquivoInvalidoException.muitoGrande(tamanhoMaximo);
        }

        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException e) {
            throw new DadosInvalidosException("Não foi possível ler a foto enviada");
        }
        if (!FormatoImagem.aceito(conteudo)) {
            throw ArquivoInvalidoException.formatoNaoSuportado();
        }
        return conteudo;
    }

    // Mantém a ordem atual (empate: a mais antiga primeiro) e numera 0, 1, 2...
    private void renumerar(Produto produto) {
        List<ProdutoImagem> ordenadas = produto.getImagens().stream()
                .sorted(Comparator.comparing(ProdutoImagem::getOrdem)
                        .thenComparing(ProdutoImagem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (int i = 0; i < ordenadas.size(); i++) {
            ordenadas.get(i).setOrdem(i);
        }
    }

    // Imagens antigas (placeholders, sem public_id) não estão no Cloudinary
    private void removerDoArmazenamento(String publicId) {
        if (publicId == null) return;
        try {
            armazenamento.remover(publicId);
        } catch (RuntimeException e) {
            log.warn("Foto {} saiu do produto, mas não foi removida do armazenamento: {}", publicId, e.getMessage());
        }
    }
}
