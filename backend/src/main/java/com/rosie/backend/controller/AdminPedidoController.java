package com.rosie.backend.controller;

import com.rosie.backend.dto.EntregaRequestDTO;
import com.rosie.backend.dto.PedidoAdminDTO;
import com.rosie.backend.dto.PedidoAdminResumoDTO;
import com.rosie.backend.service.EtiquetaService;
import com.rosie.backend.service.PedidoAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Pedidos no painel, etiqueta de envio (RF12, #25) e entrega (#27). /admin/** já exige role ADMIN no
// SecurityConfig.
@RestController
@RequestMapping("/admin/pedidos")
@RequiredArgsConstructor
public class AdminPedidoController {

    private final PedidoAdminService pedidoAdminService;
    private final EtiquetaService etiquetaService;

    @GetMapping
    public List<PedidoAdminResumoDTO> listar() {
        return pedidoAdminService.listar();
    }

    @GetMapping("/{id}")
    public PedidoAdminDTO buscar(@PathVariable Long id) {
        return pedidoAdminService.buscar(id);
    }

    // Compra e gera a etiqueta no Melhor Envio; repetir não compra outra (devolve a que já existe)
    @PostMapping("/{id}/etiqueta")
    public PedidoAdminDTO gerarEtiqueta(@PathVariable Long id) {
        return etiquetaService.gerar(id);
    }

    // A Roseane marca que a peça chegou (#27); sem corpo (ou sem data), a entrega é hoje
    @PostMapping("/{id}/entregue")
    public PedidoAdminDTO marcarEntregue(@PathVariable Long id,
                                         @RequestBody(required = false) EntregaRequestDTO requisicao) {
        return pedidoAdminService.marcarEntregue(id, requisicao == null ? null : requisicao.dataEntrega());
    }
}
