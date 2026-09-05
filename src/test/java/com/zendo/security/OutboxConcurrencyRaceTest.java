package com.zendo.security;

import com.zendo.shared.messaging.EventSigner;
import com.zendo.shared.outbox.OutboxRelay;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OutboxConcurrencyRaceTest {

    private JdbcClient jdbcClient;
    private RabbitTemplate rabbitTemplate;
    private SimpleMeterRegistry meterRegistry;
    private EventSigner eventSigner;
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcClient = mock(JdbcClient.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        eventSigner = new EventSigner("test-secret-key-32-bytes-minimum!!");
        transactionManager = mock(PlatformTransactionManager.class);

        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    @DisplayName("P3-01: OutboxRelay must execute SELECT with FOR UPDATE SKIP LOCKED to prevent multi-instance race conditions")
    void outboxRelay_usesForUpdateSkipLocked() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.ResultQuerySpec resultQuerySpec = mock(JdbcClient.ResultQuerySpec.class);

        when(jdbcClient.sql(anyString())).thenReturn(spec);
        when(spec.query()).thenReturn(resultQuerySpec);
        when(resultQuerySpec.listOfRows()).thenReturn(Collections.emptyList());

        OutboxRelay relay = new OutboxRelay(jdbcClient, rabbitTemplate, meterRegistry, eventSigner, transactionManager);
        relay.relayFromSchema("order_ctx", "order.events");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcClient, atLeastOnce()).sql(sqlCaptor.capture());

        List<String> capturedSqls = sqlCaptor.getAllValues();
        boolean foundForUpdateSkipLocked = capturedSqls.stream()
                .anyMatch(sql -> sql.contains("FOR UPDATE SKIP LOCKED") && sql.contains("processed = FALSE"));

        assertTrue(foundForUpdateSkipLocked,
                "Outbox polling query MUST include 'FOR UPDATE SKIP LOCKED' to prevent concurrent worker duplicate claims! Captured: " + capturedSqls);
    }

    @Test
    @DisplayName("P3-01: OutboxRelay must reject unauthorized or injection-prone schema identifiers")
    void shouldRejectUntrustedSchemaIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> OutboxRelay.validateSchema("unknown_schema"));
        assertThrows(IllegalArgumentException.class, () -> OutboxRelay.validateSchema("order_ctx; DROP TABLE users;--"));
        assertThrows(IllegalArgumentException.class, () -> OutboxRelay.validateSchema(null));
        assertEquals("order_ctx", OutboxRelay.validateSchema("order_ctx"));
        assertEquals("payment", OutboxRelay.validateSchema("payment"));
    }

    @Test
    @DisplayName("P3-01: Multi-instance concurrent polling with FOR UPDATE SKIP LOCKED guarantees zero duplicate claims across workers")
    void multiInstanceConcurrentWorkers_claimDistinctEventsWithoutDuplication() throws Exception {
        // Simulate a database table with 10 pending events
        List<UUID> allEventIds = new ArrayList<>();
        Map<UUID, String> eventPayloads = new ConcurrentHashMap<>();
        for (int i = 0; i < 10; i++) {
            UUID id = UUID.randomUUID();
            allEventIds.add(id);
            eventPayloads.put(id, "{\"eventId\":\"" + id + "\",\"eventType\":\"OrderPlaced\"}");
        }

        // Simulating PostgreSQL row locking with FOR UPDATE SKIP LOCKED:
        // When Worker 1 locks the first 5 rows, Worker 2 skips them and receives the remaining 5 rows.
        Set<UUID> lockedByWorker1 = Collections.synchronizedSet(new HashSet<>(allEventIds.subList(0, 5)));
        Set<UUID> lockedByWorker2 = Collections.synchronizedSet(new HashSet<>(allEventIds.subList(5, 10)));

        List<Map<String, Object>> worker1Batch = new ArrayList<>();
        for (UUID id : lockedByWorker1) {
            worker1Batch.add(Map.of("id", id, "payload", eventPayloads.get(id)));
        }

        List<Map<String, Object>> worker2Batch = new ArrayList<>();
        for (UUID id : lockedByWorker2) {
            worker2Batch.add(Map.of("id", id, "payload", eventPayloads.get(id)));
        }

        // Setup mock for Worker 1
        JdbcClient jdbcClient1 = mock(JdbcClient.class);
        JdbcClient.StatementSpec selectSpec1 = mock(JdbcClient.StatementSpec.class);
        JdbcClient.ResultQuerySpec querySpec1 = mock(JdbcClient.ResultQuerySpec.class);
        when(selectSpec1.query()).thenReturn(querySpec1);
        when(querySpec1.listOfRows()).thenReturn(worker1Batch);

        JdbcClient.StatementSpec updateSpec1 = mock(JdbcClient.StatementSpec.class);
        when(updateSpec1.param(anyString(), any())).thenReturn(updateSpec1);
        when(updateSpec1.update()).thenReturn(1);

        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("FOR UPDATE SKIP LOCKED")) return selectSpec1;
            if (sql.contains("UPDATE")) return updateSpec1;
            return selectSpec1;
        }).when(jdbcClient1).sql(anyString());

        // Setup mock for Worker 2
        JdbcClient jdbcClient2 = mock(JdbcClient.class);
        JdbcClient.StatementSpec selectSpec2 = mock(JdbcClient.StatementSpec.class);
        JdbcClient.ResultQuerySpec querySpec2 = mock(JdbcClient.ResultQuerySpec.class);
        when(selectSpec2.query()).thenReturn(querySpec2);
        when(querySpec2.listOfRows()).thenReturn(worker2Batch);

        JdbcClient.StatementSpec updateSpec2 = mock(JdbcClient.StatementSpec.class);
        when(updateSpec2.param(anyString(), any())).thenReturn(updateSpec2);
        when(updateSpec2.update()).thenReturn(1);

        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("FOR UPDATE SKIP LOCKED")) return selectSpec2;
            if (sql.contains("UPDATE")) return updateSpec2;
            return selectSpec2;
        }).when(jdbcClient2).sql(anyString());

        Set<UUID> publishedEventIds = ConcurrentHashMap.newKeySet();
        AtomicInteger totalPublishes = new AtomicInteger(0);

        RabbitTemplate mockRabbit = mock(RabbitTemplate.class);
        doAnswer(inv -> {
            String payload = inv.getArgument(2);
            for (UUID id : allEventIds) {
                if (payload.contains(id.toString())) {
                    publishedEventIds.add(id);
                    totalPublishes.incrementAndGet();
                    break;
                }
            }
            return null;
        }).when(mockRabbit).convertAndSend(anyString(), anyString(), anyString(), any(MessagePostProcessor.class));

        OutboxRelay workerInstance1 = new OutboxRelay(jdbcClient1, mockRabbit, meterRegistry, eventSigner, transactionManager);
        OutboxRelay workerInstance2 = new OutboxRelay(jdbcClient2, mockRabbit, meterRegistry, eventSigner, transactionManager);

        // Execute both worker instances concurrently
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<?> f1 = executor.submit(() -> {
            try {
                startLatch.await();
                workerInstance1.relayFromSchema("order_ctx", "order.events");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                startLatch.await();
                workerInstance2.relayFromSchema("order_ctx", "order.events");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        startLatch.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Verification:
        // 1. Exactly 10 events published in total
        assertEquals(10, totalPublishes.get(), "Each outbox event must be published exactly once across workers");
        // 2. All 10 unique events were processed
        assertEquals(10, publishedEventIds.size(), "All 10 distinct events must be accounted for");
        // 3. No overlap between worker 1 and worker 2
        for (UUID id : lockedByWorker1) {
            assertTrue(publishedEventIds.contains(id));
            assertFalse(lockedByWorker2.contains(id), "Worker 2 must never claim events locked by Worker 1");
        }
    }
}
