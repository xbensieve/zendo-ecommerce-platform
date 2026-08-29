package com.zendo.identity;

import com.zendo.TestcontainersConfiguration;
import com.zendo.identity.api.IdentityQueryApi;
import com.zendo.identity.domain.User;
import com.zendo.identity.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IdentityIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.12-management"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityQueryApi identityQueryApi;

    @Autowired
    private com.zendo.identity.application.IdentityUseCases identityUseCases;

    @Test
    void shouldPersistAndRetrieveUser() {
        User user = User.createNew("john.doe.integration@example.com", "John", "Doe");
        userRepository.save(user);

        Optional<IdentityQueryApi.UserSummary> fetched = identityQueryApi.getUserById(user.getId().value());
        
        assertTrue(fetched.isPresent());
        assertEquals("john.doe.integration@example.com", fetched.get().email());
        assertEquals("John", fetched.get().firstName());
        assertEquals("Doe", fetched.get().lastName());
        assertEquals("ACTIVE", fetched.get().status());
        
        Optional<IdentityQueryApi.UserSummary> fetchedByEmail = identityQueryApi.getUserByEmail("john.doe.integration@example.com");
        assertTrue(fetchedByEmail.isPresent());
        assertEquals(user.getId().value(), fetchedByEmail.get().id());
    }

    @Test
    void shouldCreateAndManageUserLifecycleViaUseCases() {
        String email = "jane.lifecycle@example.com";
        identityUseCases.createUser(email, "Jane", "Smith");

        Optional<IdentityQueryApi.UserSummary> fetched = identityQueryApi.getUserByEmail(email);
        assertTrue(fetched.isPresent());
        assertEquals("ACTIVE", fetched.get().status());
        String userId = fetched.get().id();

        identityUseCases.suspendUser(userId);
        assertEquals("SUSPENDED", identityQueryApi.getUserById(userId).get().status());

        identityUseCases.reactivateUser(userId);
        assertEquals("ACTIVE", identityQueryApi.getUserById(userId).get().status());

        identityUseCases.deactivateUser(userId);
        assertEquals("DEACTIVATED", identityQueryApi.getUserById(userId).get().status());
    }
}
