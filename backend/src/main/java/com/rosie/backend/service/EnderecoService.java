package com.rosie.backend.service;

import com.rosie.backend.dto.EnderecoDTO;
import com.rosie.backend.dto.EnderecoRequestDTO;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Endereços de entrega da cliente (#24; a Minha Conta, #26, reaproveita). Um endereço que já foi usado
// num pedido não muda: editar arquiva o antigo e cria outro (id novo), remover só arquiva. Assim o pedido
// (e a etiqueta da #25) continuam apontando para onde a compra foi entregue.
@Service
@RequiredArgsConstructor
@Transactional
public class EnderecoService {

    private final EnderecoRepository enderecoRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<EnderecoDTO> listar(Long clienteId) {
        return enderecoRepository.findByUsuarioIdAndArquivadoFalseOrderByIdAsc(clienteId).stream()
                .map(EnderecoDTO::from)
                .toList();
    }

    public EnderecoDTO criar(Long clienteId, EnderecoRequestDTO requisicao) {
        Endereco endereco = Endereco.builder()
                .usuario(usuarioRepository.getReferenceById(clienteId))
                .build();
        preencher(endereco, requisicao);
        return EnderecoDTO.from(enderecoRepository.save(endereco));
    }

    public EnderecoDTO atualizar(Long clienteId, Long enderecoId, EnderecoRequestDTO requisicao) {
        Endereco endereco = buscar(clienteId, enderecoId);
        if (pedidoRepository.existsByEnderecoId(enderecoId)) {
            endereco.setArquivado(true);
            return criar(clienteId, requisicao);
        }
        preencher(endereco, requisicao);
        return EnderecoDTO.from(endereco);
    }

    public void remover(Long clienteId, Long enderecoId) {
        Endereco endereco = buscar(clienteId, enderecoId);
        if (pedidoRepository.existsByEnderecoId(enderecoId)) {
            endereco.setArquivado(true);
        } else {
            enderecoRepository.delete(endereco);
        }
    }

    private Endereco buscar(Long clienteId, Long enderecoId) {
        return enderecoRepository.findByIdAndUsuarioIdAndArquivadoFalse(enderecoId, clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Endereço " + enderecoId + " não encontrado"));
    }

    private static void preencher(Endereco endereco, EnderecoRequestDTO requisicao) {
        endereco.setCep(requisicao.cepSoDigitos());
        endereco.setLogradouro(requisicao.logradouro());
        endereco.setNumero(requisicao.numero());
        endereco.setComplemento(requisicao.complementoOuNulo());
        endereco.setBairro(requisicao.bairro());
        endereco.setCidade(requisicao.cidade());
        endereco.setEstado(requisicao.estado());
    }
}
