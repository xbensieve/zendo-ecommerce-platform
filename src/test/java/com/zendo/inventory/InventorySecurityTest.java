package com.zendo.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.inventory.api.rest.InventoryController;
import com.zendo.inventory.application.InventoryUseCases;
import com.zendo.inventory.domain.InventoryItem;
import com.zendo.shared.api.rest.GlobalExceptionHandler;
import com.zendo.shared.security.AuthenticatedUser;
import com.zendo.vendor.api.VendorQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class InventorySecurityTest {

    @Mock
    private InventoryUseCases inventoryUseCases;

    @Mock
    private VendorQueryApi vendorQueryApi;

    private MockMvc mockMvc;
    private AuthenticatedUser currentUser;

    @BeforeEach
    void setUp() {
        InventoryController controller = new InventoryController(inventoryUseCases, vendorQueryApi);
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
    void shouldRejectInventoryAdjustmentWhenCallerDoesNotOwnProductVendor() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID victimVendorId = UUID.randomUUID();
        String attackerId = UUID.randomUUID().toString();
        currentUser = new TestUser(attackerId, List.of("VENDOR"));

        InventoryItem mockItem = InventoryItem.reconstitute(productId, victimVendorId, 100, 0);
        when(inventoryUseCases.getInventoryItem(productId)).thenReturn(Optional.of(mockItem));
        when(vendorQueryApi.isVendorOwner(victimVendorId.toString(), attackerId)).thenReturn(false);

        String payload = """
            {
                "newOnHandQty": 9999,
                "referenceId": "hack-ref-1"
            }
        """;

        mockMvc.perform(post("/api/v1/inventory/" + productId + "/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void shouldAllowInventoryAdjustmentWhenCallerOwnsProductVendor() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        String vendorUserId = UUID.randomUUID().toString();
        currentUser = new TestUser(vendorUserId, List.of("VENDOR"));

        InventoryItem mockItem = InventoryItem.reconstitute(productId, vendorId, 100, 0);
        when(inventoryUseCases.getInventoryItem(productId)).thenReturn(Optional.of(mockItem));
        when(vendorQueryApi.isVendorOwner(vendorId.toString(), vendorUserId)).thenReturn(true);

        String payload = """
            {
                "newOnHandQty": 250,
                "referenceId": "restock-ref-1"
            }
        """;

        mockMvc.perform(post("/api/v1/inventory/" + productId + "/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(inventoryUseCases).adjustInventory(eq(productId), eq(250), eq("restock-ref-1"));
    }

    @Test
    void shouldRejectNegativeStockQuantityAdjustment() throws Exception {
        UUID productId = UUID.randomUUID();
        String vendorUserId = UUID.randomUUID().toString();
        currentUser = new TestUser(vendorUserId, List.of("VENDOR"));

        String payload = """
            {
                "newOnHandQty": -50,
                "referenceId": "negative-stock"
            }
        """;

        mockMvc.perform(post("/api/v1/inventory/" + productId + "/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}
