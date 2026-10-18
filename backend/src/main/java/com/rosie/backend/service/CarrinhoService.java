package com.rosie.backend.service;

import com.rosie.backend.dto.CarrinhoDTO;
import com.rosie.backend.dto.CarrinhoItemDTO;
import com.rosie.backend.dto.CarrinhoItemRequestDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Carrinho da cliente (RF09, #22). Só confere o estoque: quem baixa o estoque é o checkout (#24).
@Service
@RequiredArgsConstructor
@Transactional
public class CarrinhoService {

    private final CarrinhoItemRepository carrinhoItemRepository;
    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;

    // Não apaga item indisponível: ele volta marcado (CarrinhoItemDTO.disponivel = false)
    @Transactional(readOnly = true)
    public CarrinhoDTO buscar(Long clienteId) {
        return CarrinhoDTO.from(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(clienteId));
    }

    // Mesmo produto e tamanho somam no item que já existe
    public CarrinhoDTO adicionar(Long clienteId, CarrinhoItemRequestDTO requisicao) {
        Produto produto = produtoRepository.findById(requisicao.produtoId())
                .orElseThrow(() -> new DadosInvalidosException("Produto não encontrado"));
        exigirAtivo(produto);
        Tamanho tamanho = Tamanho.valueOf(requisicao.tamanho());

        CarrinhoItem item = carrinhoItemRepository
                .findByUsuarioIdAndProdutoIdAndTamanho(clienteId, produto.getId(), tamanho)
                .orElseGet(() -> CarrinhoItem.builder()
                        .usuario(usuarioRepository.getReferenceById(clienteId))
                        .produto(produto)
                        .tamanho(tamanho)
                        .quantidade(0)
                        .build());

        int naSacola = item.getQuantidade();
        exigirEstoque(produto, tamanho, naSacola + requisicao.quantidade(), naSacola);
        item.setQuantidade(naSacola + requisicao.quantidade());
        carrinhoItemRepository.save(item);

        return buscar(clienteId);
    }

    public CarrinhoDTO atualizarQuantidade(Long clienteId, Long itemId, int quantidade) {
        CarrinhoItem item = buscarItem(clienteId, itemId);
        exigirAtivo(item.getProduto());
        exigirEstoque(item.getProduto(), item.getTamanho(), quantidade, 0);
        item.setQuantidade(quantidade);

        return buscar(clienteId);
    }

    public CarrinhoDTO remover(Long clienteId, Long itemId) {
        carrinhoItemRepository.delete(buscarItem(clienteId, itemId));
        return buscar(clienteId);
    }

    private CarrinhoItem buscarItem(Long clienteId, Long itemId) {
        return carrinhoItemRepository.findByIdAndUsuarioId(itemId, clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item " + itemId + " não está no seu carrinho"));
    }

    private void exigirAtivo(Produto produto) {
        if (!Boolean.TRUE.equals(produto.getAtivo())) {
            throw new ConflitoException("Este produto não está mais à venda", "PRODUTO_INDISPONIVEL");
        }
    }

    // naSacola só entra na mensagem: explica por que somar ao que já está no carrinho passou do estoque
    private void exigirEstoque(Produto produto, Tamanho tamanho, int quantidade, int naSacola) {
        int estoque = CarrinhoItemDTO.estoqueDoTamanho(produto, tamanho);
        if (quantidade <= estoque) return;

        String mensagem;
        if (estoque == 0) {
            mensagem = "O tamanho " + tamanho + " está esgotado";
        } else if (naSacola > 0) {
            mensagem = "Só temos " + estoque + " no tamanho " + tamanho + ", e você já tem " + naSacola + " na sacola";
        } else {
            mensagem = "Só temos " + estoque + " no tamanho " + tamanho;
        }
        throw new ConflitoException(mensagem, "ESTOQUE_INSUFICIENTE");
    }
}
