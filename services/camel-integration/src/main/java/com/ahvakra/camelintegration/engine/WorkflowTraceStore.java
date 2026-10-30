package com.ahvakra.camelintegration.engine;

import static com.ahvakra.camelintegration.engine.EngineValues.*;

import java.time.Instant;
import java.util.*;
import org.apache.camel.Exchange;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Durable execution history with a matching structured server log. */
@Component
public class WorkflowTraceStore {

    static final String INPUT_FILE = WorkflowTraceStore.class.getName() + ".inputFile";
    static final String BATCH_DESCRIPTION =
        WorkflowTraceStore.class.getName() + ".batchDescription";
    static final String BATCH_CREATED_AT = WorkflowTraceStore.class.getName() + ".batchCreatedAt";
    static final String SOURCE_TYPE = WorkflowTraceStore.class.getName() + ".sourceType";
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("etl.workflow");
    private final UUID ownerId = UUID.randomUUID();
    private final ExecutionHistoryRepository history;

    public WorkflowTraceStore(ExecutionHistoryRepository history) {
        this.history = history;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        history.historyId();
        history.recoverInterrupted(ownerId);
    }

    public ExecutionHistoryRepository history() {
        return history;
    }

    public record Event(
        long sequence,
        Instant timestamp,
        UUID workflowId,
        UUID runId,
        String exchangeId,
        String level,
        String type,
        String nodeId,
        List<String> nodeIds,
        String step,
        String stageType,
        String fileName,
        String message,
        Map<String, Object> details
    ) {}

    public record Page(
        UUID sessionId,
        List<Event> events,
        long nextAfter,
        long oldestSequence,
        long newestSequence,
        boolean truncated,
        boolean reset
    ) {}

    Event emit(
        UUID workflowId,
        UUID runId,
        Exchange exchange,
        String level,
        String type,
        String nodeId,
        List<String> nodeIds,
        String step,
        String message,
        Map<String, Object> details,
        Throwable failure
    ) {
        var context = new LinkedHashMap<String, Object>(details);
        if (exchange != null) {
            for (var attribute : Map.of(
                "batchDescription",
                BATCH_DESCRIPTION,
                "batchCreatedAt",
                BATCH_CREATED_AT,
                "sourceType",
                SOURCE_TYPE
            ).entrySet()) {
                var value = exchange.getProperty(attribute.getValue());
                if (value != null) context.putIfAbsent(attribute.getKey(), value);
            }
        }
        Event event = history.append(
            new Event(
                0,
                Instant.now(),
                workflowId,
                runId,
                exchange == null ? null : exchange.getExchangeId(),
                level,
                type,
                nodeId,
                List.copyOf(nodeIds),
                step,
                details.get("stageType") instanceof String stageType ? stageType : null,
                exchange == null ? null : exchange.getProperty(INPUT_FILE, String.class),
                message == null ? "" : message.substring(0, Math.min(message.length(), 4000)),
                Map.copyOf(context)
            ),
            ownerId
        );
        String line = JSON.writeValueAsString(event);
        if (level.equals("ERROR")) LOG.error("{}", line, failure);
        else LOG.info("{}", line);
        return event;
    }

    public Page read(UUID workflowId, long after, int limit) {
        return history.events(workflowId, after, limit);
    }

    static Map<String, Long> counts(Map<String, JsonNode> values) {
        var result = new LinkedHashMap<String, Long>();
        values.forEach((name, value) ->
            result.put(
                name + (value.isBinary() ? " (bytes)" : ""),
                value.isArray()
                    ? (long) value.size()
                    : value.isBinary()
                      ? (long) value.binaryValue().length
                      : 1L
            )
        );
        return result;
    }
}
