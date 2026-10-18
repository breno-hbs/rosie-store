package com.rosie.backend.controller;

import com.rosie.backend.dto.OrdemImagensRequestDTO;
import com.rosie.backend.dto.ProdutoImagemDTO;
import com.rosie.backend.service.ProdutoImagemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// Fotos do produto (RF15). /admin/** já exige token ADMIN (SecurityConfig)
@RestController
@RequestMapping("/admin/produtos/{produtoId}/imagens")
@RequiredArgsConstructor
public class ProdutoImagemAdminController {

    private final ProdutoImagemService produtoImagemService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoImagemDTO enviar(@PathVariable Long produtoId, @RequestPart("arquivo") MultipartFile arquivo,
                                   @RequestParam(value = "enquadramento", required = false) String enquadramento) {
        return produtoImagemService.enviar(produtoId, arquivo, enquadramento);
    }

    @PutMapping("/ordem")
    public List<ProdutoImagemDTO> reordenar(@PathVariable Long produtoId,
                                            @Valid @RequestBody OrdemImagensRequestDTO dados) {
        return produtoImagemService.reordenar(produtoId, dados.imagens());
    }

    @DeleteMapping("/{imagemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long produtoId, @PathVariable Long imagemId) {
        produtoImagemService.remover(produtoId, imagemId);
    }
}
