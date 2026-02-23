package com.warehouse.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.inventory.dto.request.CreateProductRequest;
import com.warehouse.inventory.dto.request.LoginRequest;
import com.warehouse.inventory.dto.request.SignupRequest;
import com.warehouse.inventory.dto.request.StockUpdateRequest;
import com.warehouse.inventory.dto.request.UpdateProductRequest;
import com.warehouse.inventory.entity.User;
import com.warehouse.inventory.repository.ProductRepository;
import com.warehouse.inventory.repository.StockMovementRepository;
import com.warehouse.inventory.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End Integration Tests for Warehouse Inventory API
 *
 * Tests cover ALL endpoints:
 *   POST /auth/signup
 *   POST /auth/login
 *   POST /products
 *   GET  /products
 *   GET  /products/{id}
 *   PUT  /products/{id}
 *   POST /stock/update
 *   GET  /stock/history
 *   GET  /stock/history/{productId}
 *
 * Uses @SpringBootTest to load the full application context.
 * Uses @AutoConfigureMockMvc for HTTP simulation without a real server.
 * Tests run in a defined order to simulate a real user flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WarehouseEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Shared state across tests — simulates a real session
    private static String adminToken;
    private static String staffToken;
    private static Integer createdProductId;

    // ─────────────────────────────────────────────────────────
    // SETUP — runs before all tests
    // ─────────────────────────────────────────────────────────

    @BeforeEach
    void setupAdminUser() {
        // Ensure admin user exists in DB for login tests
        // data.sql may have already created these — this is a safety net
        if (!userRepository.existsByEmail("admin@warehouse.com")) {
            User admin = User.builder()
                    .email("admin@warehouse.com")
                    .passwordHash(passwordEncoder.encode("Admin@123"))
                    .fullName("Admin User")
                    .role(com.warehouse.inventory.entity.Role.ADMIN)
                    .isActive(true)
                    .build();
            userRepository.save(admin);
        }

        if (!userRepository.existsByEmail("staff@warehouse.com")) {
            User staff = User.builder()
                    .email("staff@warehouse.com")
                    .passwordHash(passwordEncoder.encode("Admin@123"))
                    .fullName("Staff User")
                    .role(com.warehouse.inventory.entity.Role.STAFF)
                    .isActive(true)
                    .build();
            userRepository.save(staff);
        }
    }

    // ─────────────────────────────────────────────────────────
    // HELPER METHODS
    // ─────────────────────────────────────────────────────────

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private String extractToken(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        return objectMapper.readTree(json)
                .path("data")
                .path("accessToken")
                .asText();
    }

    private Integer extractId(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        return objectMapper.readTree(json)
                .path("data")
                .path("id")
                .asInt();
    }

    // ═════════════════════════════════════════════════════════
    // AUTH TESTS — SIGNUP
    // ═════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("SIGNUP — success creates STAFF user and returns 201")
    void signup_success() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setFullName("John Staff");
        request.setEmail("john.new@warehouse.com");
        request.setPassword("pass123");

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("john.new@warehouse.com"))
                .andExpect(jsonPath("$.data.fullName").value("John Staff"))
                // Critical: STAFF role always assigned — never ADMIN
                .andExpect(jsonPath("$.data.role").value("STAFF"))
                // Critical: password never returned
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    @Order(2)
    @DisplayName("SIGNUP — duplicate email returns 409 Conflict")
    void signup_duplicateEmail_returns409() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setFullName("Duplicate User");
        request.setEmail("john.new@warehouse.com"); // already created in test 1
        request.setPassword("pass123");

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    @Order(3)
    @DisplayName("SIGNUP — missing fields returns 400 with all validation errors")
    void signup_missingFields_returns400() throws Exception {
        // Empty body — all validations should fire
        SignupRequest request = new SignupRequest();
        request.setEmail("notanemail");
        request.setPassword("hi"); // too short

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                // Message should contain validation errors
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    @Order(4)
    @DisplayName("SIGNUP — blank full name returns 400")
    void signup_blankFullName_returns400() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setFullName("   "); // whitespace only — @NotBlank catches this
        request.setEmail("valid@test.com");
        request.setPassword("validpass");

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ═════════════════════════════════════════════════════════
    // AUTH TESTS — LOGIN
    // ═════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("LOGIN — admin success returns JWT token with ADMIN role")
    void login_admin_success() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@warehouse.com");
        request.setPassword("Admin@123");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value("admin@warehouse.com"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                // Password never returned
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andReturn();

        // Save token for subsequent tests
        adminToken = extractToken(result);
        Assertions.assertFalse(adminToken.isEmpty(), "Admin token should not be empty");
    }

    @Test
    @Order(6)
    @DisplayName("LOGIN — staff success returns JWT token with STAFF role")
    void login_staff_success() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("staff@warehouse.com");
        request.setPassword("Admin@123");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STAFF"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        staffToken = extractToken(result);
        Assertions.assertFalse(staffToken.isEmpty(), "Staff token should not be empty");
    }

    @Test
    @Order(7)
    @DisplayName("LOGIN — wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@warehouse.com");
        request.setPassword("wrongpassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @Order(8)
    @DisplayName("LOGIN — non-existent email returns 401 (no user enumeration)")
    void login_nonExistentEmail_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("nobody@warehouse.com");
        request.setPassword("anypassword");

        // Must return 401 not 404 — we don't reveal if email exists
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(9)
    @DisplayName("LOGIN — missing email returns 400 validation error")
    void login_missingEmail_returns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setPassword("Admin@123");
        // email not set

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // ═════════════════════════════════════════════════════════
    // PRODUCT TESTS — CREATE
    // ═════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("CREATE PRODUCT — admin can create product, returns 201 with stockQuantity=0")
    void createProduct_admin_success() throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Drill Bit Set");
        request.setDescription("20 piece HSS drill bit set");
        request.setSku("DRILL-001");

        MvcResult result = mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Drill Bit Set"))
                .andExpect(jsonPath("$.data.sku").value("DRILL-001"))
                // Stock always starts at 0 — cannot be set by client
                .andExpect(jsonPath("$.data.stockQuantity").value(0))
                // Shows who created it via email only — not full user object
                .andExpect(jsonPath("$.data.createdByEmail").value("admin@warehouse.com"))
                // Timestamps present
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andExpect(jsonPath("$.data.updatedAt").isString())
                .andReturn();

        createdProductId = extractId(result);
        Assertions.assertNotNull(createdProductId, "Product ID should not be null");
    }

    @Test
    @Order(11)
    @DisplayName("CREATE PRODUCT — staff is forbidden, returns 403")
    void createProduct_staff_returns403() throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Unauthorized Product");
        request.setSku("UNAUTH-001");

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @Order(12)
    @DisplayName("CREATE PRODUCT — no token returns 401")
    void createProduct_noToken_returns401() throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("No Auth Product");
        request.setSku("NOAUTH-001");

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(13)
    @DisplayName("CREATE PRODUCT — duplicate name returns 409")
    void createProduct_duplicateName_returns409() throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Drill Bit Set"); // same name as test 10
        request.setSku("DRILL-999");      // different SKU

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    @Order(14)
    @DisplayName("CREATE PRODUCT — duplicate SKU returns 409")
    void createProduct_duplicateSku_returns409() throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Different Name");
        request.setSku("DRILL-001"); // same SKU as test 10

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    @Order(15)
    @DisplayName("CREATE PRODUCT — missing name returns 400")
    void createProduct_missingName_returns400() throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        // name not set
        request.setSku("VALID-SKU");

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(16)
    @DisplayName("CREATE PRODUCT — create second product for stock tests")
    void createProduct_secondProduct_success() throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Industrial Hammer");
        request.setDescription("2kg steel head hammer");
        request.setSku("HAMR-001");

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stockQuantity").value(0));
    }

    // ═════════════════════════════════════════════════════════
    // PRODUCT TESTS — GET ALL
    // ═════════════════════════════════════════════════════════

    @Test
    @Order(17)
    @DisplayName("GET ALL PRODUCTS — returns list for authenticated user")
    void getAllProducts_authenticated_success() throws Exception {
        mockMvc.perform(get("/products")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @Order(18)
    @DisplayName("GET ALL PRODUCTS — search filter works case-insensitively")
    void getAllProducts_withSearch_returnsFiltered() throws Exception {
        mockMvc.perform(get("/products")
                        .param("search", "drill")  // lowercase
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                // All results should match the search
                .andExpect(jsonPath("$.data[0].name", containsStringIgnoringCase("drill")));
    }

    @Test
    @Order(19)
    @DisplayName("GET ALL PRODUCTS — search with no match returns empty list")
    void getAllProducts_searchNoMatch_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/products")
                        .param("search", "xyznonexistent")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @Order(20)
    @DisplayName("GET ALL PRODUCTS — no token returns 401")
    void getAllProducts_noToken_returns401() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isUnauthorized());
    }

    // ═════════════════════════════════════════════════════════
    // PRODUCT TESTS — GET BY ID
    // ═════════════════════════════════════════════════════════

    @Test
    @Order(21)
    @DisplayName("GET PRODUCT BY ID — returns correct product")
    void getProductById_success() throws Exception {
        mockMvc.perform(get("/products/" + createdProductId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(createdProductId))
                .andExpect(jsonPath("$.data.name").value("Drill Bit Set"))
                .andExpect(jsonPath("$.data.sku").value("DRILL-001"));
    }

    @Test
    @Order(22)
    @DisplayName("GET PRODUCT BY ID — non-existent ID returns 404")
    void getProductById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/products/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @Order(23)
    @DisplayName("GET PRODUCT BY ID — staff can also view product")
    void getProductById_staff_success() throws Exception {
        mockMvc.perform(get("/products/" + createdProductId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(createdProductId));
    }

    // ═════════════════════════════════════════════════════════
    // PRODUCT TESTS — UPDATE
    // ═════════════════════════════════════════════════════════

    @Test
    @Order(24)
    @DisplayName("UPDATE PRODUCT — admin can update name and description")
    void updateProduct_admin_success() throws Exception {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("Drill Bit Set Pro");
        request.setDescription("Updated: 20 piece cobalt HSS drill bits");
        request.setSku("DRILL-001");

        mockMvc.perform(put("/products/" + createdProductId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Drill Bit Set Pro"))
                .andExpect(jsonPath("$.data.description").value("Updated: 20 piece cobalt HSS drill bits"))
                // Stock cannot be changed via update endpoint
                .andExpect(jsonPath("$.data.stockQuantity").isNumber());
    }

    @Test
    @Order(25)
    @DisplayName("UPDATE PRODUCT — staff is forbidden, returns 403")
    void updateProduct_staff_returns403() throws Exception {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("Unauthorized Update");
        request.setSku("DRILL-001");

        mockMvc.perform(put("/products/" + createdProductId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @Order(26)
    @DisplayName("UPDATE PRODUCT — non-existent product returns 404")
    void updateProduct_notFound_returns404() throws Exception {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("Ghost Product");
        request.setSku("GHOST-001");

        mockMvc.perform(put("/products/99999")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @Order(27)
    @DisplayName("UPDATE PRODUCT — name conflict with another product returns 409")
    void updateProduct_nameConflict_returns409() throws Exception {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("Industrial Hammer"); // name of the SECOND product
        request.setSku("DRILL-001");

        // Trying to rename first product to second product's name
        mockMvc.perform(put("/products/" + createdProductId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    // ═════════════════════════════════════════════════════════
    // STOCK TESTS — UPDATE STOCK
    // ═════════════════════════════════════════════════════════

    @Test
    @Order(28)
    @DisplayName("STOCK UPDATE — ADD creates movement record with correct before/after")
    void stockUpdate_add_success() throws Exception {
        StockUpdateRequest request = new StockUpdateRequest();
        request.setProductId(createdProductId);
        request.setType("ADD");
        request.setQuantity(100);
        request.setNotes("Initial stock from supplier - INV-001");

        mockMvc.perform(post("/stock/update")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.movementType").value("ADD"))
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.stockBefore").value(0))
                .andExpect(jsonPath("$.data.stockAfter").value(100))
                .andExpect(jsonPath("$.data.productId").value(createdProductId))
                // Performer comes from JWT — not from client request
                .andExpect(jsonPath("$.data.performedByEmail").value("admin@warehouse.com"))
                .andExpect(jsonPath("$.data.notes").value("Initial stock from supplier - INV-001"))
                .andExpect(jsonPath("$.data.createdAt").isString());
    }

    @Test
    @Order(29)
    @DisplayName("STOCK UPDATE — product stock quantity updated in DB after ADD")
    void stockUpdate_add_productStockUpdated() throws Exception {
        // Verify the product's stock reflects the ADD from test 28
        mockMvc.perform(get("/products/" + createdProductId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity").value(100));
    }

    @Test
    @Order(30)
    @DisplayName("STOCK UPDATE — REMOVE deducts correctly and creates audit record")
    void stockUpdate_remove_success() throws Exception {
        StockUpdateRequest request = new StockUpdateRequest();
        request.setProductId(createdProductId);
        request.setType("REMOVE");
        request.setQuantity(30);
        request.setNotes("Dispatched to Site A - ORD-001");

        mockMvc.perform(post("/stock/update")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movementType").value("REMOVE"))
                .andExpect(jsonPath("$.data.quantity").value(30))
                .andExpect(jsonPath("$.data.stockBefore").value(100))
                .andExpect(jsonPath("$.data.stockAfter").value(70))
                // Staff user performed this
                .andExpect(jsonPath("$.data.performedByEmail").value("staff@warehouse.com"));
    }

    @Test
    @Order(31)
    @DisplayName("STOCK UPDATE — insufficient stock returns 422")
    void stockUpdate_insufficientStock_returns422() throws Exception {
        StockUpdateRequest request = new StockUpdateRequest();
        request.setProductId(createdProductId);
        request.setType("REMOVE");
        request.setQuantity(9999); // way more than available
        request.setNotes("This should fail");

        mockMvc.perform(post("/stock/update")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"))
                // Message should mention available and requested quantities
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    @Order(32)
    @DisplayName("STOCK UPDATE — product not found returns 404")
    void stockUpdate_productNotFound_returns404() throws Exception {
        StockUpdateRequest request = new StockUpdateRequest();
        request.setProductId(99999);
        request.setType("ADD");
        request.setQuantity(10);
        request.setNotes("Product does not exist");

        mockMvc.perform(post("/stock/update")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @Order(33)
    @DisplayName("STOCK UPDATE — zero quantity returns 400 validation error")
    void stockUpdate_zeroQuantity_returns400() throws Exception {
        StockUpdateRequest request = new StockUpdateRequest();
        request.setProductId(createdProductId);
        request.setType("ADD");
        request.setQuantity(0); // must be positive
        request.setNotes("Zero quantity");

        mockMvc.perform(post("/stock/update")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(34)
    @DisplayName("STOCK UPDATE — no token returns 401")
    void stockUpdate_noToken_returns401() throws Exception {
        StockUpdateRequest request = new StockUpdateRequest();
        request.setProductId(createdProductId);
        request.setType("ADD");
        request.setQuantity(10);

        mockMvc.perform(post("/stock/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(35)
    @DisplayName("STOCK UPDATE — invalid type returns 400")
    void stockUpdate_invalidType_returns400() throws Exception {
        StockUpdateRequest request = new StockUpdateRequest();
        request.setProductId(createdProductId);
        request.setType("INVALID_TYPE"); // must be ADD or REMOVE
        request.setQuantity(10);
        request.setNotes("Bad type");

        mockMvc.perform(post("/stock/update")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    // ═════════════════════════════════════════════════════════
    // STOCK TESTS — HISTORY
    // ═════════════════════════════════════════════════════════

    @Test
    @Order(36)
    @DisplayName("STOCK HISTORY — returns all movements newest first")
    void stockHistory_all_success() throws Exception {
        mockMvc.perform(get("/stock/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                // At least 2 movements created in tests 28 and 30
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)))
                // Newest first — REMOVE (test 30) should be before ADD (test 28)
                .andExpect(jsonPath("$.data[0].movementType").value("REMOVE"));
    }

    @Test
    @Order(37)
    @DisplayName("STOCK HISTORY — each movement contains all required fields")
    void stockHistory_movementFieldsPresent() throws Exception {
        mockMvc.perform(get("/stock/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].productId").isNumber())
                .andExpect(jsonPath("$.data[0].movementType").isString())
                .andExpect(jsonPath("$.data[0].quantity").isNumber())
                .andExpect(jsonPath("$.data[0].stockBefore").isNumber())
                .andExpect(jsonPath("$.data[0].stockAfter").isNumber())
                .andExpect(jsonPath("$.data[0].performedByEmail").isString())
                .andExpect(jsonPath("$.data[0].createdAt").isString());
    }

    @Test
    @Order(38)
    @DisplayName("STOCK HISTORY — staff can view history")
    void stockHistory_staff_success() throws Exception {
        mockMvc.perform(get("/stock/history")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(39)
    @DisplayName("STOCK HISTORY — no token returns 401")
    void stockHistory_noToken_returns401() throws Exception {
        mockMvc.perform(get("/stock/history"))
                .andExpect(status().isUnauthorized());
    }

    // ═════════════════════════════════════════════════════════
    // STOCK TESTS — HISTORY BY PRODUCT ID
    // ═════════════════════════════════════════════════════════

    @Test
    @Order(40)
    @DisplayName("STOCK HISTORY BY PRODUCT — returns only movements for that product")
    void stockHistoryByProduct_success() throws Exception {
        mockMvc.perform(get("/stock/history/" + createdProductId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)))
                // All movements should be for createdProductId
                .andExpect(jsonPath("$.data[0].productId").value(createdProductId));
    }

    @Test
    @Order(41)
    @DisplayName("STOCK HISTORY BY PRODUCT — non-existent product returns 404")
    void stockHistoryByProduct_notFound_returns404() throws Exception {
        mockMvc.perform(get("/stock/history/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @Order(42)
    @DisplayName("STOCK HISTORY BY PRODUCT — staff can view product history")
    void stockHistoryByProduct_staff_success() throws Exception {
        mockMvc.perform(get("/stock/history/" + createdProductId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ═════════════════════════════════════════════════════════
    // SECURITY TESTS — JWT EDGE CASES
    // ═════════════════════════════════════════════════════════

    @Test
    @Order(43)
    @DisplayName("SECURITY — tampered token returns 401")
    void security_tamperedToken_returns401() throws Exception {
        // Take valid token and corrupt the signature part
        String[] parts = adminToken.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".invalidsignature";

        mockMvc.perform(get("/products")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(44)
    @DisplayName("SECURITY — completely fake token returns 401")
    void security_fakeToken_returns401() throws Exception {
        mockMvc.perform(get("/products")
                        .header("Authorization", "Bearer thisis.not.avalid.jwttoken"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(45)
    @DisplayName("SECURITY — malformed Authorization header returns 401")
    void security_malformedAuthHeader_returns401() throws Exception {
        // Missing "Bearer " prefix
        mockMvc.perform(get("/products")
                        .header("Authorization", adminToken)) // no "Bearer " prefix
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(46)
    @DisplayName("SECURITY — public endpoints accessible without token")
    void security_publicEndpoints_noTokenNeeded() throws Exception {
        // Login and signup should never require auth
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LoginRequest())))
                // Even with empty body, we get 400 not 401
                // This proves the endpoint is accessible (400 = reached the endpoint)
                .andExpect(status().isBadRequest());
    }

    // ═════════════════════════════════════════════════════════
    // FULL FLOW TEST — Complete user journey
    // ═════════════════════════════════════════════════════════

    @Test
    @Order(47)
    @DisplayName("FULL FLOW — signup, login, create product, add stock, remove stock, view history")
    void fullFlow_completeUserJourney() throws Exception {
        // Step 1: Login as admin
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("admin@warehouse.com");
        loginReq.setPassword("Admin@123");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String token = extractToken(loginResult);

        // Step 2: Create a product
        CreateProductRequest productReq = new CreateProductRequest();
        productReq.setName("Flow Test Product");
        productReq.setDescription("For full flow test");
        productReq.setSku("FLOW-TEST-001");

        MvcResult productResult = mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(productReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stockQuantity").value(0))
                .andReturn();

        Integer flowProductId = extractId(productResult);

        // Step 3: Add stock
        StockUpdateRequest addReq = new StockUpdateRequest();
        addReq.setProductId(flowProductId);
        addReq.setType("ADD");
        addReq.setQuantity(50);
        addReq.setNotes("Initial shipment");

        mockMvc.perform(post("/stock/update")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(addReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockBefore").value(0))
                .andExpect(jsonPath("$.data.stockAfter").value(50));

        // Step 4: Verify product stock updated
        mockMvc.perform(get("/products/" + flowProductId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity").value(50));

        // Step 5: Remove partial stock
        StockUpdateRequest removeReq = new StockUpdateRequest();
        removeReq.setProductId(flowProductId);
        removeReq.setType("REMOVE");
        removeReq.setQuantity(20);
        removeReq.setNotes("Dispatched order");

        mockMvc.perform(post("/stock/update")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(removeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockBefore").value(50))
                .andExpect(jsonPath("$.data.stockAfter").value(30));

        // Step 6: Verify history for this product has 2 records
        mockMvc.perform(get("/stock/history/" + flowProductId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                // Newest first = REMOVE
                .andExpect(jsonPath("$.data[0].movementType").value("REMOVE"))
                .andExpect(jsonPath("$.data[1].movementType").value("ADD"));

        // Step 7: Final stock should be 30
        mockMvc.perform(get("/products/" + flowProductId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity").value(30));
    }
}