package com.rosie.backend.service;

import com.rosie.backend.dto.CarrinhoItemDTO;
import com.rosie.backend.dto.FreteResponseDTO;
import com.rosie.backend.dto.OpcaoFreteDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.service.CalculadoraFrete.OpcaoFrete;
import com.rosie.backend.service.CalculadoraFrete.PecaFrete;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

// Frete do carrinho da cliente (RF11, #23). Cota só as peças disponíveis, as mesmas que entram no subtotal.
// O produto ainda não tem peso nem medidas: toda peça vai com o pacote padrão de rosie.frete.peca.*.
@Service
@Transactional(readOnly = true)
public class FreteService {

    private final CarrinhoItemRepository carrinhoItemRepository;
    private final CalculadoraFrete calculadoraFrete;
    private final BigDecimal pesoKg;
    private final int larguraCm;
    private final int alturaCm;
    private final int comprimentoCm;

    public FreteService(CarrinhoItemRepository carrinhoItemRepository,
                        CalculadoraFrete calculadoraFrete,
                        @Value("${rosie.frete.peca.peso-kg}") BigDecimal pesoKg,
                        @Value("${rosie.frete.peca.largura-cm}") int larguraCm,
                        @Value("${rosie.frete.peca.altura-cm}") int alturaCm,
                        @Value("${rosie.frete.peca.comprimento-cm}") int comprimentoCm) {
        this.carrinhoItemRepository = carrinhoItemRepository;
        this.calculadoraFrete = calculadoraFrete;
        this.pesoKg = pesoKg;
        this.larguraCm = larguraCm;
        this.alturaCm = alturaCm;
        this.comprimentoCm = comprimentoCm;
    }

    public FreteResponseDTO calcular(Long clienteId, String cep) {
        String cepDestino = cep.replace("-", "");

        List<PecaFrete> pecas = carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(clienteId).stream()
                .filter(item -> CarrinhoItemDTO.from(item).disponivel())
                .map(this::peca)
                .toList();
        if (pecas.isEmpty()) {
            throw new ConflitoException("Sua sacola não tem peças disponíveis para calcular o frete", "CARRINHO_VAZIO");
        }

        List<OpcaoFreteDTO> opcoes = calculadoraFrete.calcular(cepDestino, pecas).stream()
                .sorted(Comparator.comparing(OpcaoFrete::preco).thenComparingInt(OpcaoFrete::prazoDias))
                .map(OpcaoFreteDTO::from)
                .toList();
        return new FreteResponseDTO(cepDestino, opcoes);
    }

    // Valor declarado = preço atual da peça (seguro do envio)
    private PecaFrete peca(CarrinhoItem item) {
        return new PecaFrete(
                "produto-" + item.getProduto().getId() + "-" + item.getTamanho(),
                item.getQuantidade(),
                item.getProduto().getPreco(),
                pesoKg, larguraCm, alturaCm, comprimentoCm);
    }
}
