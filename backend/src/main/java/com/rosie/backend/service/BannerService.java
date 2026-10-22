package com.rosie.backend.service;

import com.rosie.backend.dto.BannerAdminDTO;
import com.rosie.backend.dto.BannerDTO;
import com.rosie.backend.dto.BannerRequestDTO;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Banner;
import com.rosie.backend.repository.BannerRepository;
import com.rosie.backend.service.ArmazenamentoImagens.ImagemArmazenada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Banners do carrossel da Home (#89, RF01/RF15). A Home recebe só os ativos dentro do período, na ordem;
// o painel vê todos. As imagens vão para o Cloudinary (pasta rosie/banners) e só saem de lá depois do commit:
// se a transação for desfeita, o banner continua com as imagens de antes e as recém-enviadas é que são apagadas.
@Service
@Slf4j
public class BannerService {

    private final BannerRepository bannerRepository;
    private final ArmazenamentoImagens armazenamento;
    private final LinkBanner linkBanner;
    private final Clock relogio;
    private final DataSize tamanhoMaximo;

    public BannerService(BannerRepository bannerRepository, ArmazenamentoImagens armazenamento,
                         LinkBanner linkBanner, Clock relogio,
                         @Value("${rosie.imagens.tamanho-maximo}") DataSize tamanhoMaximo) {
        this.bannerRepository = bannerRepository;
        this.armazenamento = armazenamento;
        this.linkBanner = linkBanner;
        this.relogio = relogio;
        this.tamanhoMaximo = tamanhoMaximo;
    }

    // GET /banners: o relógio da loja está no fuso de São Paulo, o mesmo do período gravado
    public List<BannerDTO> listarNoAr() {
        return bannerRepository.listarNoAr(agora()).stream().map(BannerDTO::from).toList();
    }

    public List<BannerAdminDTO> listarAdmin() {
        LocalDateTime agora = agora();
        return bannerRepository.findAllByOrderByOrdemAscIdAsc().stream()
                .map(banner -> BannerAdminDTO.from(banner, agora))
                .toList();
    }

    // O banner novo entra no fim do carrossel. Os dados e os arquivos são conferidos antes de enviar
    // qualquer imagem, para um erro de digitação não deixar arquivo sobrando no Cloudinary.
    @Transactional
    public BannerAdminDTO criar(BannerRequestDTO dados, MultipartFile imagemDesktop, MultipartFile imagemCelular) {
        Banner banner = new Banner();
        aplicar(banner, dados);
        byte[] desktop = FormatoImagem.ler(imagemDesktop, tamanhoMaximo,
                "Envie a imagem para computador no campo imagemDesktop");
        byte[] celular = lerOpcional(imagemCelular);

        ImagemArmazenada armazenadaDesktop = enviar(desktop);
        banner.setImagemDesktopUrl(armazenadaDesktop.url());
        banner.setImagemDesktopPublicId(armazenadaDesktop.publicId());
        if (celular != null) {
            ImagemArmazenada armazenadaCelular = enviar(celular);
            banner.setImagemCelularUrl(armazenadaCelular.url());
            banner.setImagemCelularPublicId(armazenadaCelular.publicId());
        }
        banner.setOrdem((int) bannerRepository.count());

        bannerRepository.saveAndFlush(banner);
        return BannerAdminDTO.from(banner, agora());
    }

    // Imagem enviada substitui a anterior (que sai do Cloudinary depois do commit); sem imagem, fica a atual.
    // removerImagemCelular volta a usar a de desktop no celular (ignorado quando vem imagemCelular).
    @Transactional
    public BannerAdminDTO atualizar(Long id, BannerRequestDTO dados, MultipartFile imagemDesktop,
                                    MultipartFile imagemCelular) {
        Banner banner = buscar(id);
        aplicar(banner, dados);
        byte[] desktop = lerOpcional(imagemDesktop);
        byte[] celular = lerOpcional(imagemCelular);

        if (desktop != null) {
            removerDepoisDoCommit(banner.getImagemDesktopPublicId());
            ImagemArmazenada armazenada = enviar(desktop);
            banner.setImagemDesktopUrl(armazenada.url());
            banner.setImagemDesktopPublicId(armazenada.publicId());
        }
        if (celular != null || Boolean.TRUE.equals(dados.removerImagemCelular())) {
            removerDepoisDoCommit(banner.getImagemCelularPublicId());
            ImagemArmazenada armazenada = celular == null ? null : enviar(celular);
            banner.setImagemCelularUrl(armazenada == null ? null : armazenada.url());
            banner.setImagemCelularPublicId(armazenada == null ? null : armazenada.publicId());
        }

        bannerRepository.flush();
        return BannerAdminDTO.from(banner, agora());
    }

    @Transactional
    public BannerAdminDTO alterarAtivo(Long id, boolean ativo) {
        Banner banner = buscar(id);
        banner.setAtivo(ativo);
        bannerRepository.flush();
        return BannerAdminDTO.from(banner, agora());
    }

    // Recebe os ids de todos os banners na nova ordem; o primeiro abre o carrossel
    @Transactional
    public List<BannerAdminDTO> reordenar(List<Long> ids) {
        Map<Long, Banner> porId = bannerRepository.findAll().stream()
                .collect(Collectors.toMap(Banner::getId, Function.identity()));

        // Mesmo tamanho e mesmos ids = cada banner aparece exatamente uma vez
        if (ids.size() != porId.size() || !new HashSet<>(ids).equals(porId.keySet())) {
            throw new DadosInvalidosException("Envie todos os banners, cada um uma vez");
        }
        for (int i = 0; i < ids.size(); i++) {
            porId.get(ids.get(i)).setOrdem(i);
        }
        bannerRepository.flush();

        LocalDateTime agora = agora();
        return ids.stream().map(id -> BannerAdminDTO.from(porId.get(id), agora)).toList();
    }

    // Apaga o banner, renumera os outros e tira as imagens do Cloudinary depois do commit
    @Transactional
    public void remover(Long id) {
        Banner banner = buscar(id);
        bannerRepository.delete(banner);
        bannerRepository.flush();

        List<Banner> restantes = bannerRepository.findAllByOrderByOrdemAscIdAsc();
        for (int i = 0; i < restantes.size(); i++) {
            restantes.get(i).setOrdem(i);
        }

        removerDepoisDoCommit(banner.getImagemDesktopPublicId());
        removerDepoisDoCommit(banner.getImagemCelularPublicId());
    }

    private Banner buscar(Long id) {
        return bannerRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Banner com id " + id + " não encontrado"));
    }

    // Campos de texto, link e período (as imagens são tratadas por quem chama)
    private void aplicar(Banner banner, BannerRequestDTO dados) {
        String link = linkBanner.validar(dados.link());
        String textoBotao = limpo(dados.textoBotao());
        if (textoBotao != null && link == null) {
            throw new DadosInvalidosException("Informe o link do botão, ou deixe o texto do botão vazio");
        }
        if (dados.inicio() != null && dados.fim() != null && !dados.fim().isAfter(dados.inicio())) {
            throw new DadosInvalidosException("O fim da exibição precisa ser depois do início");
        }

        banner.setTitulo(limpo(dados.titulo()));
        banner.setTexto(limpo(dados.texto()));
        banner.setTextoBotao(textoBotao);
        banner.setLink(link);
        banner.setTextoAlternativo(dados.textoAlternativo().strip());
        banner.setAtivo(dados.ativo() == null || dados.ativo());
        banner.setInicio(dados.inicio());
        banner.setFim(dados.fim());
    }

    private byte[] lerOpcional(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) return null;
        return FormatoImagem.ler(arquivo, tamanhoMaximo, "Envie a imagem do banner");
    }

    // A imagem recém-enviada sai do Cloudinary se a transação não for confirmada (ex.: o banco recusou,
    // ou a segunda imagem falhou depois da primeira)
    private ImagemArmazenada enviar(byte[] conteudo) {
        ImagemArmazenada armazenada = armazenamento.enviar(conteudo, ArmazenamentoImagens.PASTA_BANNERS);
        depoisDaTransacao(armazenada.publicId(), false);
        return armazenada;
    }

    private void removerDepoisDoCommit(String publicId) {
        depoisDaTransacao(publicId, true);
    }

    // noCommit = true: remove só se a transação for confirmada; false: só se não for
    private void depoisDaTransacao(String publicId, boolean noCommit) {
        // Imagens sem public_id não estão no Cloudinary
        if (publicId == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if ((status == STATUS_COMMITTED) == noCommit) removerDoArmazenamento(publicId);
            }
        });
    }

    // Falha do Cloudinary não desfaz nada: fica só um arquivo órfão lá, registrado no log
    private void removerDoArmazenamento(String publicId) {
        try {
            armazenamento.remover(publicId);
        } catch (RuntimeException e) {
            log.warn("Imagem de banner {} não foi removida do armazenamento: {}", publicId, e.getMessage());
        }
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(relogio.withZone(PrazoTroca.FUSO_LOJA));
    }

    private static String limpo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
