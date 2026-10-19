package com.rosie.backend.service;

import com.rosie.backend.dto.CarrinhoItemDTO;
import com.rosie.backend.dto.FreteResponseDTO;
import com.rosie.backend.dto.OpcaoFreteDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.service.CalculadoraFrete.OpcaoFrete;
import com.rosie.backend.service.CalculadoraFrete.PecaFrete;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

// Frete do carrinho da cliente (RF11, #23). Cota só as peças disponíveis, as mesmas que entram no subtotal.
// Cada peça vai com o peso e as medidas do produto, e o pacote padrão (rosie.frete.peca.*) no que estiver vazio (#56).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FreteService {

    private final CarrinhoItemRepository carrinhoItemRepository;
    private final CalculadoraFrete calculadoraFrete;
    private final PacotePeca pacotePeca;

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
        PacotePeca.Medidas pacote = pacotePeca.de(item.getProduto());
        return new PecaFrete(
                "produto-" + item.getProduto().getId() + "-" + item.getTamanho(),
                item.getQuantidade(),
                item.getProduto().getPreco(),
                pacote.pesoKg(), pacote.larguraCm(), pacote.alturaCm(), pacote.comprimentoCm());
    }
}
