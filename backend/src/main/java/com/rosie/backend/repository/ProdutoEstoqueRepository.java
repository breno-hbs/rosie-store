package com.rosie.backend.repository;

import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.Tamanho;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProdutoEstoqueRepository extends JpaRepository<ProdutoEstoque, Long> {

    List<ProdutoEstoque> findByProdutoId(Long produtoId);

    // Usado ao adicionar no carrinho, pra checar se tem estoque daquele tamanho (RF06)
    Optional<ProdutoEstoque> findByProdutoAndTamanho(Produto produto, Tamanho tamanho);

    // Baixa do estoque no pagamento aprovado (#24): trava a linha até o fim da transação, para dois pedidos
    // aprovados ao mesmo tempo não venderem a mesma última peça
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProdutoEstoque e where e.produto.id = :produtoId and e.tamanho = :tamanho")
    Optional<ProdutoEstoque> buscarParaBaixa(Long produtoId, Tamanho tamanho);
}
