package com.rosie.backend.controller;

import com.rosie.backend.dto.EstoqueProdutoDTO;
import com.rosie.backend.dto.EstoqueRequestDTO;
import com.rosie.backend.dto.ProdutoAdminDTO;
import com.rosie.backend.dto.ProdutoAdminResumoDTO;
import com.rosie.backend.dto.ProdutoEdicaoDTO;
import com.rosie.backend.dto.ProdutoRequestDTO;
import com.rosie.backend.service.ProdutoAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// /admin/** já exige token ADMIN (SecurityConfig)
@RestController
@RequestMapping("/admin/produtos")
@RequiredArgsConstructor
public class ProdutoAdminController {

    private final ProdutoAdminService produtoAdminService;

    @GetMapping
    public List<ProdutoAdminResumoDTO> listar() {
        return produtoAdminService.listar();
    }

    @GetMapping("/{id}")
    public ProdutoAdminDTO buscarPorId(@PathVariable Long id) {
        return produtoAdminService.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoAdminDTO criar(@Valid @RequestBody ProdutoRequestDTO dados) {
        return produtoAdminService.criar(dados);
    }

    @PutMapping("/{id}")
    public ProdutoAdminDTO atualizar(@PathVariable Long id, @Valid @RequestBody ProdutoEdicaoDTO dados) {
        return produtoAdminService.atualizar(id, dados);
    }

    @PutMapping("/{id}/estoque")
    public EstoqueProdutoDTO atualizarEstoque(@PathVariable Long id, @Valid @RequestBody EstoqueRequestDTO dados) {
        return produtoAdminService.atualizarEstoque(id, dados.estoque());
    }

    // Sem parâmetro arquiva (como sempre foi); ?definitivo=true exclui de vez, só sem pedido nem troca (#87)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean definitivo) {
        if (definitivo) {
            produtoAdminService.excluirDefinitivamente(id);
        } else {
            produtoAdminService.desativar(id);
        }
    }
}
