package com.zendo.notification.domain;

import com.zendo.shared.exception.DomainException;

public class NotificationException extends DomainException {

    public NotificationException(String message) {
        super(message);
    }

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
