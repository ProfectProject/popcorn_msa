package com.popcorn.store.domain.outbox;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

public final class OutboxHeaders {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String PRODUCER_KEY = "producer";

    private OutboxHeaders() {
    }

    public static Map<String, Object> of(String producer) {
        return of(producer, null);
    }

    public static Map<String, Object> of(String producer, Map<String, Object> extras) {
        Map<String, Object> headers = new LinkedHashMap<>();
        if (StringUtils.hasText(producer)) {
            headers.put(PRODUCER_KEY, producer);
        }
        addTraceId(headers);
        if (extras != null) {
            headers.putAll(extras);
        }
        return headers.isEmpty() ? null : headers;
    }

    private static void addTraceId(Map<String, Object> headers) {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (StringUtils.hasText(traceId)) {
            headers.put(TRACE_ID_KEY, traceId);
        }
    }
}
