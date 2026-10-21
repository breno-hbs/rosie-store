package com.rosie.backend.controller;

import com.rosie.backend.dto.BannerDTO;
import com.rosie.backend.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Carrossel da Home (#89): público, só os banners ativos e dentro do período, na ordem
@RestController
@RequestMapping("/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @GetMapping
    public List<BannerDTO> listar() {
        return bannerService.listarNoAr();
    }
}
