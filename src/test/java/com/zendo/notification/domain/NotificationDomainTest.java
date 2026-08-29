package com.zendo.notification.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationDomainTest {

    @Test
    void shouldCreateNotificationWithPendingStatus() {
        Notification n = new Notification(
                UUID.randomUUID(), "user-123", NotificationType.ORDER_CONFIRMED,
                "Order Confirmed", "Your order has been placed.");

        assertEquals(NotificationStatus.PENDING, n.getStatus());
        assertNotNull(n.getCreatedAt());
        assertNull(n.getSentAt());
        assertEquals("user-123", n.getUserId());
        assertEquals(NotificationType.ORDER_CONFIRMED, n.getType());
    }

    @Test
    void shouldTransitionFromPendingToSent() {
        Notification n = new Notification(
                UUID.randomUUID(), "user-123", NotificationType.PAYMENT_RECEIVED,
                "Payment Received", "Your payment was authorized.");

        n.markSent();

        assertEquals(NotificationStatus.SENT, n.getStatus());
        assertNotNull(n.getSentAt());
    }

    @Test
    void shouldTransitionFromPendingToFailed() {
        Notification n = new Notification(
                UUID.randomUUID(), "user-123", NotificationType.PAYMENT_FAILED,
                "Payment Failed", "Your payment could not be processed.");

        n.markFailed();

        assertEquals(NotificationStatus.FAILED, n.getStatus());
        assertNull(n.getSentAt());
    }

    @Test
    void shouldRejectTransitionFromSentToSent() {
        Notification n = new Notification(
                UUID.randomUUID(), "user-123", NotificationType.ORDER_CONFIRMED,
                "Order Confirmed", "Your order has been placed.");
        n.markSent();

        assertThrows(NotificationException.class, n::markSent);
    }

    @Test
    void shouldRejectTransitionFromSentToFailed() {
        Notification n = new Notification(
                UUID.randomUUID(), "user-123", NotificationType.ORDER_CONFIRMED,
                "Order Confirmed", "Your order has been placed.");
        n.markSent();

        assertThrows(NotificationException.class, n::markFailed);
    }

    @Test
    void shouldRejectTransitionFromFailedToSent() {
        Notification n = new Notification(
                UUID.randomUUID(), "user-123", NotificationType.ORDER_CONFIRMED,
                "Order Confirmed", "Your order has been placed.");
        n.markFailed();

        assertThrows(NotificationException.class, n::markSent);
    }

    @Test
    void shouldRejectBlankUserId() {
        assertThrows(NotificationException.class, () ->
                new Notification(UUID.randomUUID(), "", NotificationType.ORDER_CONFIRMED,
                        "Title", "Message"));
    }

    @Test
    void shouldRejectNullUserId() {
        assertThrows(NotificationException.class, () ->
                new Notification(UUID.randomUUID(), null, NotificationType.ORDER_CONFIRMED,
                        "Title", "Message"));
    }

    @Test
    void shouldRejectBlankMessage() {
        assertThrows(NotificationException.class, () ->
                new Notification(UUID.randomUUID(), "user-123", NotificationType.ORDER_CONFIRMED,
                        "Title", ""));
    }

    @Test
    void shouldRejectBlankTitle() {
        assertThrows(NotificationException.class, () ->
                new Notification(UUID.randomUUID(), "user-123", NotificationType.ORDER_CONFIRMED,
                        "", "Message"));
    }

    @Test
    void shouldRejectNullType() {
        assertThrows(NotificationException.class, () ->
                new Notification(UUID.randomUUID(), "user-123", null,
                        "Title", "Message"));
    }
}
