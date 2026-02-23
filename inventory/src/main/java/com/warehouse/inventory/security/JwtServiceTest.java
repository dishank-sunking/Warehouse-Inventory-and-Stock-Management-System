package com.warehouse.inventory.security;

import com.warehouse.inventory.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey",
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "expiration", 86400000L);

        User user = User.builder()
                .id(1)
                .email("test@test.com")
                .passwordHash("doesnt-matter")
                .fullName("Test User")
                .role(User.Role.ADMIN)
                .isActive(true)
                .build();

        userDetails = new CustomUserDetails(user);
    }

    @Test
    void shouldGenerateNonEmptyToken() {
        String token = jwtService.generateToken(userDetails);
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void shouldExtractCorrectEmailFromToken() {
        String token = jwtService.generateToken(userDetails);
        String extractedEmail = jwtService.extractEmail(token);
        assertThat(extractedEmail).isEqualTo("test@test.com");
    }

    @Test
    void shouldReturnTrueForValidToken() {
        String token = jwtService.generateToken(userDetails);
        boolean isValid = jwtService.isTokenValid(token, userDetails);
        assertThat(isValid).isTrue();
    }

    @Test
    void shouldReturnFalseForWrongUser() {
        String token = jwtService.generateToken(userDetails);

        User otherUser = User.builder()
                .id(2)
                .email("other@test.com")
                .passwordHash("hash")
                .fullName("Other")
                .role(User.Role.STAFF)
                .isActive(true)
                .build();
        CustomUserDetails otherDetails = new CustomUserDetails(otherUser);

        boolean isValid = jwtService.isTokenValid(token, otherDetails);
        assertThat(isValid).isFalse();
    }
}