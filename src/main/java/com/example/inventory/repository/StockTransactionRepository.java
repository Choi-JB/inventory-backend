package com.example.inventory.repository;

import com.example.inventory.entity.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long>, JpaSpecificationExecutor<StockTransaction> {
    boolean existsByProductId(Long productId);
    Optional<StockTransaction> findById(Long id);

}
