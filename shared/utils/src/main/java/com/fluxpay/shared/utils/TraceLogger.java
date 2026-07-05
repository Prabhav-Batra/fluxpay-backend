package com.fluxpay.shared.utils;

import java.util.logging.Logger;

import java.util.Map;
import java.util.UUID;

/**
 * Universal Engine Contract - Observability Logger
 * Emits standard logs with Confidence Scores and Trace Metadata.
 */
public class TraceLogger {

    private static final Logger log = Logger.getLogger(TraceLogger.class.getName());

    /**
     * Emits a trace log for a business action.
     *
     * @param action      The business action being performed (e.g. "CREATE_ORDER")
     * @param confidence  The confidence score of the operation (0.0 to 1.0)
     * @param metadata    Additional trace metadata (e.g., entity IDs, internal keys)
     */
    public static void emit(String action, double confidence, Map<String, Object> metadata) {
        String traceId = UUID.randomUUID().toString();
        log.info(String.format("[TRACE] Action: %s, TraceId: %s, Confidence: %f, Metadata: %s", 
                 action, traceId, confidence, metadata.toString()));
    }
}
