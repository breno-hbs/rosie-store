package com.rosie.backend.service;

import com.rosie.backend.dto.CategoriaResumoDTO;
import com.rosie.backend.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public List<CategoriaResumoDTO> listar() {
        return categoriaRepository.findAll(Sort.by("nome")).stream()
                .map(CategoriaResumoDTO::from)
                .toList();
    }
}
