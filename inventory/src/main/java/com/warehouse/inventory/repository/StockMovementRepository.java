package com.warehouse.inventory.repository;

import com.warehouse.inventory.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Integer> {

    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Integer productId);

    List<StockMovement> findAllByOrderByCreatedAtDesc();
}