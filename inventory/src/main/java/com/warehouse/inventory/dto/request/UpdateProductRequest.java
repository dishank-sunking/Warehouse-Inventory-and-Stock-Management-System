package com.warehouse.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 100)
    private String name;

    private String description;

    @NotBlank(message = "SKU is required")
    @Size(max = 50)
    private String sku;
}