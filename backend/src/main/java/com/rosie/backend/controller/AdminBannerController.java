package com.rosie.backend.controller;

import com.rosie.backend.dto.BannerAdminDTO;
import com.rosie.backend.dto.BannerAtivoRequestDTO;
import com.rosie.backend.dto.BannerRequestDTO;
import com.rosie.backend.dto.OrdemBannersRequestDTO;
import com.rosie.backend.service.BannerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// Banners da Home no painel (#89). /admin/** já exige token ADMIN (SecurityConfig).
// Criar e editar são multipart: a parte "dados" (JSON) e as imagens "imagemDesktop" e "imagemCelular".
@RestController
@RequestMapping("/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerService bannerService;

    @GetMapping
    public List<BannerAdminDTO> listar() {
        return bannerService.listarAdmin();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BannerAdminDTO criar(@Valid @RequestPart("dados") BannerRequestDTO dados,
                                @RequestPart(value = "imagemDesktop", required = false) MultipartFile imagemDesktop,
                                @RequestPart(value = "imagemCelular", required = false) MultipartFile imagemCelular) {
        return bannerService.criar(dados, imagemDesktop, imagemCelular);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BannerAdminDTO atualizar(@PathVariable Long id, @Valid @RequestPart("dados") BannerRequestDTO dados,
                                    @RequestPart(value = "imagemDesktop", required = false) MultipartFile imagemDesktop,
                                    @RequestPart(value = "imagemCelular", required = false) MultipartFile imagemCelular) {
        return bannerService.atualizar(id, dados, imagemDesktop, imagemCelular);
    }

    @PutMapping("/{id}/ativo")
    public BannerAdminDTO alterarAtivo(@PathVariable Long id, @Valid @RequestBody BannerAtivoRequestDTO dados) {
        return bannerService.alterarAtivo(id, dados.ativo());
    }

    @PutMapping("/ordem")
    public List<BannerAdminDTO> reordenar(@Valid @RequestBody OrdemBannersRequestDTO dados) {
        return bannerService.reordenar(dados.banners());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id) {
        bannerService.remover(id);
    }
}
