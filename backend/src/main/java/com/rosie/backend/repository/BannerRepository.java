package com.rosie.backend.repository;

import com.rosie.backend.model.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    // Painel: todos, na ordem do carrossel
    List<Banner> findAllByOrderByOrdemAscIdAsc();

    // Home (GET /banners): ativos e dentro do período; o fim é exclusivo (às 18h do fim, já saiu)
    @Query("""
            select b from Banner b
            where b.ativo = true
              and (b.inicio is null or b.inicio <= :agora)
              and (b.fim is null or b.fim > :agora)
            order by b.ordem asc, b.id asc
            """)
    List<Banner> listarNoAr(@Param("agora") LocalDateTime agora);
}
