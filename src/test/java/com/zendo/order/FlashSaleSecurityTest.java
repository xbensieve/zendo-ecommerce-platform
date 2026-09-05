package com.zendo.order;

import com.zendo.order.api.rest.FlashSaleOrderController;
import com.zendo.order.application.FlashSaleCheckoutUseCases;
import com.zendo.order.domain.OrderException;
import com.zendo.order.domain.OrderRepository;
import com.zendo.promotion.api.PromotionApi;
import com.zendo.shared.api.rest.GlobalExceptionHandler;
import com.zendo.shared.security.AuthenticatedUser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class FlashSaleSecurityTest {

    @Mock
    private FlashSaleCheckoutUseCases flashSaleCheckoutUseCases;

    @Mock
    private PromotionApi promotionApi;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MeterRegistry meterRegistry;

    private MockMvc mockMvc;
    private AuthenticatedUser currentUser;

    @BeforeEach
    void setUp() {
        FlashSaleOrderController controller = new FlashSaleOrderController(flashSaleCheckoutUseCases);
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
    void shouldRejectNegativeQuantityInFlashSalePurchase() throws Exception {
        UUID flashSaleId = UUID.randomUUID();
        currentUser = new TestUser("cust-123", List.of("CUSTOMER"));

        String payload = """
            {
                "quantity": -5
            }
        """;

        mockMvc.perform(post("/api/v1/orders/flash-sales/" + flashSaleId + "/purchase")
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.quantity").exists());
    }

    @Test
    void shouldRejectZeroQuantityInFlashSalePurchase() throws Exception {
        UUID flashSaleId = UUID.randomUUID();
        currentUser = new TestUser("cust-123", List.of("CUSTOMER"));

        String payload = """
            {
                "quantity": 0
            }
        """;

        mockMvc.perform(post("/api/v1/orders/flash-sales/" + flashSaleId + "/purchase")
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void shouldRejectExcessiveQuantityInFlashSalePurchase() throws Exception {
        UUID flashSaleId = UUID.randomUUID();
        currentUser = new TestUser("cust-123", List.of("CUSTOMER"));

        String payload = """
            {
                "quantity": 50
            }
        """;

        mockMvc.perform(post("/api/v1/orders/flash-sales/" + flashSaleId + "/purchase")
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void shouldThrowExceptionWhenCheckoutUseCasesCalledDirectlyWithNegativeQuantity() {
        Counter counter = mock(Counter.class);
        when(meterRegistry.counter(anyString())).thenReturn(counter);
        when(meterRegistry.counter(anyString(), anyString(), anyString())).thenReturn(counter);

        FlashSaleCheckoutUseCases useCases = new FlashSaleCheckoutUseCases(
                orderRepository,
                promotionApi,
                mock(com.zendo.inventory.application.InventoryUseCases.class),
                meterRegistry
        );

        assertThatThrownBy(() -> useCases.checkoutFlashSale("cust-1", "idem-1", UUID.randomUUID(), -10))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("Flash sale purchase quantity must be positive");
    }
}
