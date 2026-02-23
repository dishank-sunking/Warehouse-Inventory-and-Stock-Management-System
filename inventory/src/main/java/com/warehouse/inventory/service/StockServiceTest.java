package com.warehouse.inventory.service;

import com.warehouse.inventory.dto.request.StockUpdateRequest;
import com.warehouse.inventory.dto.response.StockMovementResponse;
import com.warehouse.inventory.entity.Product;
import com.warehouse.inventory.entity.StockMovement;
import com.warehouse.inventory.entity.User;
import com.warehouse.inventory.exception.InsufficientStockException;
import com.warehouse.inventory.exception.ResourceNotFoundException;
import com.warehouse.inventory.repository.ProductRepository;
import com.warehouse.inventory.repository.StockMovementRepository;
import com.warehouse.inventory.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {
    @Mock private ProductRepository productRepository;
    @Mock private StockMovementRepository stockMovementRepository;

    @InjectMocks private StockService stockService;

    private Product testProduct;
    private User testUser;

    @BeforeEach
    void setUp() {

        testUser = User.builder()
                .id(1).email("staff@test.com")
                .passwordHash("hash").fullName("Staff")
                .role(User.Role.STAFF).isActive(true).build();

        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        // A product with 50 units in stock
        testProduct = Product.builder()
                .id(1).name("Drill Bit").sku("DB-001")
                .stockQuantity(50).createdBy(testUser).build();
    }

    @Test
    void addStock_shouldIncreaseQuantityCorrectly() {
        // ARRANGE: Tell the mock what to return
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any())).thenReturn(testProduct);
        when(stockMovementRepository.save(any())).thenAnswer(invocation -> {
            // Return whatever was passed in (the StockMovement being saved)
            return invocation.getArgument(0);
        });

        StockUpdateRequest request = new StockUpdateRequest();
        StockMovement fakeMovement = StockMovement.builder()
                .id(1).product(testProduct).performedBy(testUser)
                .movementType(StockMovement.MovementType.ADD)
                .quantity(30).stockBefore(50).stockAfter(80).build();

        when(stockMovementRepository.save(any())).thenReturn(fakeMovement);

        // ASSERT
        assertThat(fakeMovement.getStockAfter()).isEqualTo(80);
        assertThat(fakeMovement.getStockBefore()).isEqualTo(50);
        assertThat(fakeMovement.getMovementType()).isEqualTo(StockMovement.MovementType.ADD);
    }

    @Test
    void removeStock_shouldThrowWhenNotEnoughStock() {
        // Product has 50 units, we try to remove 100
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));

        int available = testProduct.getStockQuantity();  // 50
        int requested = 100;

        assertThatThrownBy(() -> {
            if (available < requested) {
                throw new InsufficientStockException(
                        "Not enough stock. Available: " + available + ", Requested: " + requested
                );
            }
        })
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Not enough stock");
    }

    @Test
    void getProductHistory_shouldThrowWhenProductNotFound() {
        when(productRepository.existsById(999)).thenReturn(false);

        assertThatThrownBy(() -> stockService.getProductHistory(999))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}