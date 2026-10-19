package com.rosie.backend.service;

import com.rosie.backend.dto.CarrinhoItemDTO;
import com.rosie.backend.dto.CarrinhoProdutoDTO;
import com.rosie.backend.dto.OpcaoFreteDTO;
import com.rosie.backend.dto.PedidoCriadoDTO;
import com.rosie.backend.dto.PedidoDTO;
import com.rosie.backend.dto.PedidoRequestDTO;
import com.rosie.backend.dto.PedidoResumoDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.FormaPagamento;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.PedidoItem;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.GatewayPagamento.Linha;
import com.rosie.backend.service.GatewayPagamento.NovaPreferencia;
import com.rosie.backend.service.GatewayPagamento.Preferencia;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// Checkout (RF10, #24). O servidor refaz tudo o que a tela mostrou: itens e preços vêm do carrinho, o
// estoque é conferido de novo e o frete é cotado de novo no Melhor Envio. O pedido nasce
// AGUARDANDO_PAGAMENTO e o estoque NÃO baixa aqui: só no pagamento aprovado (PagamentoService).
@Service
@RequiredArgsConstructor
@Transactional
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final EnderecoRepository enderecoRepository;
    private final CarrinhoItemRepository carrinhoItemRepository;
    private final UsuarioRepository usuarioRepository;
    private final FreteService freteService;
    private final GatewayPagamento gatewayPagamento;
    private final PrazoTroca prazoTroca;

    // Se o Mercado Pago falhar, a exceção desfaz o pedido inteiro (nada fica gravado sem link de pagamento)
    public PedidoCriadoDTO criar(Long clienteId, PedidoRequestDTO requisicao) {
        // Política de privacidade mudou depois do último aceite (#31): a loja mostra a nova e pede o aceite
        if (PoliticaPrivacidade.aceitePendente(usuarioRepository.getReferenceById(clienteId))) {
            throw new ConflitoException("Atualizamos a política de privacidade. Leia e aceite a nova versão para "
                    + "finalizar o pedido.", "PRIVACIDADE_PENDENTE");
        }
        Endereco endereco = enderecoRepository.findByIdAndUsuarioIdAndArquivadoFalse(requisicao.enderecoId(), clienteId)
                .orElseThrow(() -> new DadosInvalidosException("Endereço de entrega não encontrado"));

        List<CarrinhoItem> itens = carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(clienteId);
        if (itens.isEmpty()) {
            throw new ConflitoException("Sua sacola está vazia", "CARRINHO_VAZIO");
        }
        itens.forEach(PedidoService::exigirDisponivel);

        OpcaoFreteDTO frete = freteService.calcular(clienteId, endereco.getCep()).opcoes().stream()
                .filter(opcao -> opcao.id().equals(requisicao.servicoFreteId()))
                .findFirst()
                .orElseThrow(() -> new ConflitoException(
                        "Essa forma de entrega não está mais disponível para o endereço. Escolha outra.",
                        "FRETE_ALTERADO"));

        Usuario cliente = usuarioRepository.getReferenceById(clienteId);
        Pedido pedido = Pedido.builder()
                .usuario(cliente)
                .endereco(endereco)
                .formaPagamento(FormaPagamento.valueOf(requisicao.formaPagamento()))
                .transportadora(frete.transportadora())
                .servicoFrete(frete.servico())
                .servicoFreteId(frete.id())
                .prazoFreteDias(frete.prazoDias())
                .valorFrete(frete.preco())
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CarrinhoItem item : itens) {
            BigDecimal preco = item.getProduto().getPreco();
            pedido.getItens().add(PedidoItem.builder()
                    .pedido(pedido)
                    .produto(item.getProduto())
                    .tamanho(item.getTamanho())
                    .quantidade(item.getQuantidade())
                    .precoUnitario(preco)
                    .build());
            subtotal = subtotal.add(preco.multiply(BigDecimal.valueOf(item.getQuantidade())));
        }
        pedido.setValorTotal(subtotal.add(frete.preco()));
        pedidoRepository.saveAndFlush(pedido);

        Preferencia preferencia = gatewayPagamento.criarPreferencia(new NovaPreferencia(
                pedido.getId(),
                pedido.getFormaPagamento(),
                cliente.getNome(),
                cliente.getEmail(),
                linhas(pedido)));
        pedido.setPreferenciaId(preferencia.id());
        pedido.setUrlPagamento(preferencia.urlPagamento());

        return PedidoCriadoDTO.from(pedido);
    }

    // Meus Pedidos (#26): todos os pedidos da cliente, inclusive os não pagos e os cancelados
    @Transactional(readOnly = true)
    public List<PedidoResumoDTO> listar(Long clienteId) {
        return pedidoRepository.findByUsuarioIdOrderByCreatedAtDescIdDesc(clienteId).stream()
                .map(pedido -> PedidoResumoDTO.from(pedido, prazoTroca.ate(pedido)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoDTO buscar(Long clienteId, Long pedidoId) {
        Pedido pedido = buscarDaCliente(clienteId, pedidoId);
        return PedidoDTO.from(pedido, prazoTroca.ate(pedido));
    }

    Pedido buscarDaCliente(Long clienteId, Long pedidoId) {
        return pedidoRepository.findByIdAndUsuarioId(pedidoId, clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + pedidoId + " não encontrado"));
    }

    // Peça que ficou indisponível na sacola trava o pedido: a cliente ajusta no carrinho e volta
    private static void exigirDisponivel(CarrinhoItem item) {
        CarrinhoItemDTO dto = CarrinhoItemDTO.from(item);
        if (dto.disponivel()) return;

        String peca = item.getProduto().getNome() + " (" + item.getTamanho() + ")";
        switch (dto.motivoIndisponivel()) {
            case CarrinhoItemDTO.PRODUTO_INATIVO -> throw new ConflitoException(
                    peca + " não está mais à venda. Tire da sacola para continuar.", "PRODUTO_INDISPONIVEL");
            case CarrinhoItemDTO.TAMANHO_ESGOTADO -> throw new ConflitoException(
                    peca + " esgotou. Tire da sacola para continuar.", "ESTOQUE_INSUFICIENTE");
            default -> throw new ConflitoException(
                    "Só temos " + dto.estoqueDisponivel() + " de " + peca + ". Ajuste a quantidade na sacola.",
                    "ESTOQUE_INSUFICIENTE");
        }
    }

    // Uma linha por peça e o frete por último, para o Mercado Pago mostrar o mesmo total da loja
    private static List<Linha> linhas(Pedido pedido) {
        List<Linha> linhas = new ArrayList<>();
        for (PedidoItem item : pedido.getItens()) {
            CarrinhoProdutoDTO produto = CarrinhoProdutoDTO.from(item.getProduto());
            linhas.add(new Linha("produto-" + produto.id() + "-" + item.getTamanho(),
                    produto.nome() + " (tamanho " + item.getTamanho() + ")",
                    item.getQuantidade(), item.getPrecoUnitario(), produto.imagemPrincipal()));
        }
        if (pedido.getValorFrete().signum() > 0) {
            linhas.add(new Linha("frete", "Frete " + pedido.getTransportadora() + " " + pedido.getServicoFrete(),
                    1, pedido.getValorFrete(), null));
        }
        return linhas;
    }
}
