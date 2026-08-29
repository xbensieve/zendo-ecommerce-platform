package com.zendo.shared.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RabbitMetricsAspect {

    private final MeterRegistry meterRegistry;

    public RabbitMetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(rabbitListener)")
    public Object recordRabbitMetrics(ProceedingJoinPoint joinPoint, RabbitListener rabbitListener) throws Throwable {
        String queue = rabbitListener.queues().length > 0 ? rabbitListener.queues()[0] : "unknown";
        
        meterRegistry.counter("messages_consumed_total", "queue", queue).increment();
        
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            meterRegistry.counter("messages_failed_total", "queue", queue, "exception", e.getClass().getSimpleName()).increment();
            throw e;
        }
    }
}
