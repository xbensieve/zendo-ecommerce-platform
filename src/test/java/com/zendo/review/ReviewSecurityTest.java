package com.zendo.review;

import com.zendo.order.api.OrderQueryApi;
import com.zendo.review.api.ReviewQueryApi;
import com.zendo.review.api.rest.ReviewController;
import com.zendo.review.application.ReviewUseCases;
import com.zendo.shared.api.rest.GlobalExceptionHandler;
import com.zendo.shared.security.AuthenticatedUser;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class ReviewSecurityTest {

    @Mock
    private ReviewUseCases reviewUseCases;

    @Mock
    private ReviewQueryApi reviewQueryApi;

    private MockMvc mockMvc;
    private AuthenticatedUser currentUser;

    @BeforeEach
    void setUp() {
        ReviewController controller = new ReviewController(reviewUseCases, reviewQueryApi);
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
    void shouldRejectCrossProductReviewForgeryAttempt() throws Exception {
        String customerId = "cust-attacker";
        UUID orderItemIdForProductA = UUID.randomUUID();
        UUID targetProductB = UUID.randomUUID();
        currentUser = new TestUser(customerId, List.of("CUSTOMER"));

        when(reviewUseCases.createReview(eq(customerId), eq(orderItemIdForProductA), eq(targetProductB), eq(5), eq("Forged Review")))
                .thenThrow(new ReviewUseCases.UnauthorizedReviewException("Customer is not eligible to review this item. Item may not belong to the customer, was not purchased for this product, or order is not paid."));

        String payload = """
            {
                "orderItemId": "%s",
                "productId": "%s",
                "rating": 5,
                "content": "Forged Review"
            }
        """.formatted(orderItemIdForProductA, targetProductB);

        mockMvc.perform(post("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not eligible")));
    }

    @Test
    void shouldRejectInvalidRatingBoundary() throws Exception {
        currentUser = new TestUser("cust-1", List.of("CUSTOMER"));

        String payload = """
            {
                "orderItemId": "%s",
                "productId": "%s",
                "rating": 6,
                "content": "Invalid rating"
            }
        """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.rating").exists());
    }

    @Test
    void shouldRejectOversizedContent() throws Exception {
        currentUser = new TestUser("cust-1", List.of("CUSTOMER"));
        String hugeContent = "A".repeat(1001);

        String payload = """
            {
                "orderItemId": "%s",
                "productId": "%s",
                "rating": 5,
                "content": "%s"
            }
        """.formatted(UUID.randomUUID(), UUID.randomUUID(), hugeContent);

        mockMvc.perform(post("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.content").exists());
    }
}
