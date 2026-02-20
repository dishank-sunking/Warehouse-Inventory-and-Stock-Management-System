package com.warehouse.inventory.repository;

import com.warehouse.inventory.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    boolean existsByName(String name);
    boolean existsBySku(String sku);
    boolean existsByNameAndIdNot(String name, Integer id);
    boolean existsBySkuAndIdNot(String sku, Integer id);

    List<Product> findByNameContainingIgnoreCase(String name);
}