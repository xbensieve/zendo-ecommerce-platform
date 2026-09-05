package com.zendo.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.catalog.api.rest.CatalogController;
import com.zendo.catalog.application.CatalogUseCases;
import com.zendo.catalog.domain.Product;
import com.zendo.catalog.domain.ProductId;
import com.zendo.catalog.domain.ProductName;
import com.zendo.catalog.domain.VendorId;
import com.zendo.shared.api.rest.GlobalExceptionHandler;
import com.zendo.shared.security.AuthenticatedUser;
import com.zendo.vendor.api.VendorQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class CatalogSecurityTest {

    @Mock
    private CatalogUseCases catalogUseCases;

    @Mock
    private VendorQueryApi vendorQueryApi;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    // Authenticated user holder for custom argument resolver
    private AuthenticatedUser currentUser;

    @BeforeEach
    void setUp() {
        CatalogController controller = new CatalogController(catalogUseCases, vendorQueryApi);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return currentUser;
                    }
                })
                .build();
    }

    private static class TestUser implements AuthenticatedUser {
        private final String userId;
        private final List<String> roles;

        public TestUser(String userId, List<String> roles) {
            this.userId = userId;
            this.roles = roles;
        }

        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public List<String> getRoles() {
            return roles;
        }
    }

    @Test
    void shouldRejectProductCreationWhenCallerDoesNotOwnVendor() throws Exception {
        String attackerId = UUID.randomUUID().toString();
        String victimVendorId = UUID.randomUUID().toString();
        currentUser = new TestUser(attackerId, List.of("VENDOR"));

        when(vendorQueryApi.isVendorOwner(victimVendorId, attackerId)).thenReturn(false);

        String payload = """
            {
                "vendorId": "%s",
                "name": "Tampered Product",
                "description": "Malicious payload"
            }
        """.formatted(victimVendorId);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void shouldAllowProductCreationWhenCallerOwnsVendor() throws Exception {
        String vendorUserId = UUID.randomUUID().toString();
        String vendorId = UUID.randomUUID().toString();
        currentUser = new TestUser(vendorUserId, List.of("VENDOR"));

        when(vendorQueryApi.isVendorOwner(vendorId, vendorUserId)).thenReturn(true);
        when(catalogUseCases.createProduct(eq(vendorId), any(), any())).thenReturn("prod-123");

        String payload = """
            {
                "vendorId": "%s",
                "name": "Legitimate Product",
                "description": "Quality item"
            }
        """.formatted(vendorId);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("prod-123"));
    }

    @Test
    void shouldAllowProductCreationForAdminRegardlessOfVendorOwnership() throws Exception {
        String adminUserId = UUID.randomUUID().toString();
        String vendorId = UUID.randomUUID().toString();
        currentUser = new TestUser(adminUserId, List.of("ADMIN"));

        when(catalogUseCases.createProduct(eq(vendorId), any(), any())).thenReturn("prod-admin");

        String payload = """
            {
                "vendorId": "%s",
                "name": "Admin Product",
                "description": "Admin governance"
            }
        """.formatted(vendorId);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("prod-admin"));
    }

    @Test
    void shouldRejectVariantAdditionForUnownedProduct() throws Exception {
        String attackerId = UUID.randomUUID().toString();
        String victimVendorId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        currentUser = new TestUser(attackerId, List.of("VENDOR"));

        Product product = Product.create(VendorId.fromString(victimVendorId), new ProductName("Victim Item"), null);
        when(catalogUseCases.getProduct(productId)).thenReturn(Optional.of(product));
        when(vendorQueryApi.isVendorOwner(victimVendorId, attackerId)).thenReturn(false);

        String payload = """
            {
                "sku": "SKU-HACK",
                "priceAmount": 0.01,
                "currency": "USD"
            }
        """;

        mockMvc.perform(post("/api/v1/products/" + productId + "/variants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void shouldRejectNegativeOrZeroPriceOnVariantAddition() throws Exception {
        String vendorUserId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        currentUser = new TestUser(vendorUserId, List.of("VENDOR"));

        String payload = """
            {
                "sku": "SKU-BAD",
                "priceAmount": -10.00,
                "currency": "USD"
            }
        """;

        mockMvc.perform(post("/api/v1/products/" + productId + "/variants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void shouldRejectStatusTamperingForUnownedProduct() throws Exception {
        String attackerId = UUID.randomUUID().toString();
        String victimVendorId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        currentUser = new TestUser(attackerId, List.of("VENDOR"));

        Product product = Product.create(VendorId.fromString(victimVendorId), new ProductName("Target Item"), null);
        when(catalogUseCases.getProduct(productId)).thenReturn(Optional.of(product));
        when(vendorQueryApi.isVendorOwner(victimVendorId, attackerId)).thenReturn(false);

        String payload = """
            {
                "status": "ARCHIVED"
            }
        """;

        mockMvc.perform(patch("/api/v1/products/" + productId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }
}
