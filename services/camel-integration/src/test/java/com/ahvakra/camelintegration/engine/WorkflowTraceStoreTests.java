package com.ahvakra.camelintegration.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.apache.camel.CamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WorkflowTraceStoreTests {

    @Autowired
    ExecutionHistoryRepository history;

    @Autowired
    CamelContext camel;

    @Test
    void retainsEventsAcrossStoreRecreationAndSupportsDurableCursors() {
        var store = new WorkflowTraceStore(history);
        UUID workflow = UUID.randomUUID(),
            run = UUID.randomUUID();
        var first = emit(store, workflow, run, null, "WORKFLOW_STARTED", Map.of());
        for (int i = 0; i < 15; i++) emit(
            store,
            workflow,
            run,
            null,
            "STEP_COMPLETED",
            Map.of("durationMs", i)
        );
        var all = store.read(workflow, 0, 100);
        assertEquals(16, all.events().size());
        assertEquals(first.sequence(), all.oldestSequence());
        var recreated = new WorkflowTraceStore(new ExecutionHistoryRepository(jdbc, transactions));
        assertEquals(all, recreated.read(workflow, 0, 100));
        assertEquals(15, recreated.read(workflow, first.sequence(), 100).events().size());
        assertTrue(store.read(workflow, Long.MAX_VALUE, 10).reset());
        assertTrue(store.read(UUID.randomUUID(), 0, 10).events().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.read(workflow, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> store.read(workflow, 0, 0));
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void persistsBatchStagesPaginatesAndMarksOnlyUnfinishedWorkInterrupted() {
        var store = new WorkflowTraceStore(history);
        UUID workflow = UUID.randomUUID(),
            run = UUID.randomUUID();
        emit(
            store,
            workflow,
            run,
            null,
            "PLAN_READY",
            Map.of(
                "nodes",
                List.of(Map.of("id", "node", "name", "Normalize calling", "type", "normalizeRule"))
            )
        );
        emit(store, workflow, run, null, "WORKFLOW_STARTED", Map.of());
        var first = new DefaultExchange(camel);
        first.setProperty(WorkflowTraceStore.INPUT_FILE, "usage.csv");
        first.setProperty(WorkflowTraceStore.BATCH_DESCRIPTION, "usage.csv");
        first.setProperty(WorkflowTraceStore.SOURCE_TYPE, "file");
        emit(store, workflow, run, first, "STEP_STARTED", Map.of("stageType", "normalizeRule"));
        emit(
            store,
            workflow,
            run,
            first,
            "STEP_COMPLETED",
            Map.of("stageType", "normalizeRule", "matched", 7, "total", 8, "durationMs", 12)
        );
        emit(store, workflow, run, first, "FILE_COMPLETED", Map.of("durationMs", 20));
        var second = new DefaultExchange(camel);
        second.setProperty(WorkflowTraceStore.INPUT_FILE, "next.csv");
        emit(store, workflow, run, second, "STEP_STARTED", Map.of("stageType", "normalizeRule"));
        var page = history.batches(workflow, null, 1);
        assertTrue(page.hasMore());
        assertEquals("next.csv", page.items().getFirst().description());
        var older = history.batches(workflow, page.nextBefore(), 1);
        assertFalse(older.hasMore());
        var completed = history.detail(workflow, older.items().getFirst().executionId());
        assertEquals("completed", completed.batch().status());
        assertEquals("Normalize calling", completed.stages().getFirst().name());
        assertEquals(7L, completed.stages().getFirst().matched());
        assertEquals(12L, completed.stages().getFirst().durationMs());
        assertEquals(3, completed.events().size());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
            history.detail(UUID.randomUUID(), completed.batch().executionId())
        );
        history.recoverInterrupted(UUID.randomUUID());
        var interrupted = history.detail(workflow, page.items().getFirst().executionId());
        assertEquals("interrupted", interrupted.batch().status());
        assertEquals("interrupted", interrupted.stages().getFirst().status());
        assertEquals(
            "completed",
            history.detail(workflow, completed.batch().executionId()).batch().status()
        );
        assertEquals(1, history.latestRun(workflow).orElseThrow().successfulFiles());
        assertEquals(1, history.latestRun(workflow).orElseThrow().failedFiles());
        assertEquals("INTERRUPTED", history.latestRun(workflow).orElseThrow().status());
    }

    @Test
    void eventAndSummariesRollBackTogetherWhenWritingAStageFails() {
        UUID workflow = UUID.randomUUID(),
            run = UUID.randomUUID();
        var exchange = new DefaultExchange(camel);
        var invalid = new WorkflowTraceStore.Event(
            0,
            java.time.Instant.now(),
            workflow,
            run,
            exchange.getExchangeId(),
            "INFO",
            "STEP_STARTED",
            "x".repeat(121),
            List.of("x".repeat(121)),
            "Too long stage id",
            "mapper",
            "input.csv",
            "Starting",
            Map.of()
        );
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
            history.append(invalid, UUID.randomUUID())
        );
        assertTrue(history.events(workflow, 0, 100).events().isEmpty());
        assertTrue(history.batches(workflow, null, 50).items().isEmpty());
        assertTrue(history.latestRun(workflow).isEmpty());
    }

    private WorkflowTraceStore.Event emit(
        WorkflowTraceStore store,
        UUID workflow,
        UUID run,
        org.apache.camel.Exchange exchange,
        String type,
        Map<String, Object> details
    ) {
        boolean stage = type.startsWith("STEP_");
        return store.emit(
            workflow,
            run,
            exchange,
            "INFO",
            type,
            stage ? "node" : null,
            stage ? List.of("node") : List.of(),
            stage ? "Normalize calling" : null,
            type,
            details,
            null
        );
    }
}
