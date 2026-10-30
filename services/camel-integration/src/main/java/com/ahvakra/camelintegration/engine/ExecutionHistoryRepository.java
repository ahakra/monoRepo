package com.ahvakra.camelintegration.engine;

import static com.ahvakra.camelintegration.engine.EngineValues.*;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/** Each trace and its batch/stage summaries commit together, independently of route/config transactions. */
@Component
public class ExecutionHistoryRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private UUID historyId;

    public ExecutionHistoryRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public synchronized UUID historyId() {
        if (historyId == null) {
            var ids = jdbc.query(
                "SELECT history_id FROM etl_execution_history_metadata WHERE id = 1",
                (rs, row) -> rs.getObject(1, UUID.class)
            );
            if (ids.isEmpty()) {
                try {
                    jdbc.update(
                        "INSERT INTO etl_execution_history_metadata(id, history_id) VALUES (1, ?)",
                        UUID.randomUUID()
                    );
                } catch (DuplicateKeyException concurrentInitialization) {
                    /* Another backend initialized the same database. */
                }
                ids = jdbc.query(
                    "SELECT history_id FROM etl_execution_history_metadata WHERE id = 1",
                    (rs, row) -> rs.getObject(1, UUID.class)
                );
            }
            historyId = ids.getFirst();
        }
        return historyId;
    }

    WorkflowTraceStore.Event append(WorkflowTraceStore.Event draft, UUID owner) {
        historyId();
        return transaction.execute(ignored -> {
            // Serializes commits for a workflow so incremental event cursors cannot skip an earlier commit.
            jdbc.query(
                "SELECT id FROM etl_workflows WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getObject(1),
                draft.workflowId()
            );
            long sequence = Objects.requireNonNull(
                jdbc.queryForObject("SELECT nextval('etl_execution_event_seq')", Long.class)
            );
            ensureRun(draft, owner, sequence);
            UUID executionId =
                draft.exchangeId() == null
                    ? null
                    : UUID.nameUUIDFromBytes(
                          (draft.runId() + ":" + draft.exchangeId()).getBytes(
                              StandardCharsets.UTF_8
                          )
                      );
            if (executionId != null) ensureBatch(draft, executionId, sequence);
            var details = new LinkedHashMap<String, Object>(draft.details());
            if (executionId != null) details.put("executionId", executionId.toString());
            var event = new WorkflowTraceStore.Event(
                sequence,
                draft.timestamp(),
                draft.workflowId(),
                draft.runId(),
                draft.exchangeId(),
                draft.level(),
                draft.type(),
                draft.nodeId(),
                draft.nodeIds(),
                draft.step(),
                draft.stageType(),
                draft.fileName(),
                draft.message(),
                Map.copyOf(details)
            );
            jdbc.update(
                "INSERT INTO etl_execution_events(sequence, workflow_id, run_id, execution_id, created_at, type, level, event_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                sequence,
                event.workflowId(),
                event.runId(),
                executionId,
                time(event.timestamp()),
                event.type(),
                event.level(),
                JSON.writeValueAsString(event)
            );
            updateRun(event);
            if (executionId != null) {
                updateBatch(event, executionId);
                updateStages(event, executionId);
            }
            return event;
        });
    }

    private void ensureRun(WorkflowTraceStore.Event event, UUID owner, long sequence) {
        if (exists("SELECT COUNT(*) FROM etl_execution_runs WHERE id = ?", event.runId())) return;
        var names = jdbc.query(
            "SELECT name FROM etl_workflows WHERE id = ?",
            (rs, row) -> rs.getString(1),
            event.workflowId()
        );
        jdbc.update(
            "INSERT INTO etl_execution_runs(id, workflow_id, workflow_name, owner_id, start_sequence, status, created_at, updated_at, plan_json) VALUES (?, ?, ?, ?, ?, 'STARTING', ?, ?, '[]')",
            event.runId(),
            event.workflowId(),
            names.isEmpty() ? event.workflowId().toString() : names.getFirst(),
            owner,
            sequence,
            time(event.timestamp()),
            time(event.timestamp())
        );
    }

    private void ensureBatch(WorkflowTraceStore.Event event, UUID id, long sequence) {
        if (exists("SELECT COUNT(*) FROM etl_executions WHERE id = ?", id)) return;
        String description = Objects.toString(
            event.details().get("batchDescription"),
            event.fileName() == null ? "Imported batch" : event.fileName()
        );
        String source = Objects.toString(
            event.details().get("sourceType"),
            event.fileName() == null ? "batch" : "file"
        );
        Instant created =
            event.details().get("batchCreatedAt") instanceof String text
                ? Instant.parse(text)
                : event.timestamp();
        jdbc.update(
            "INSERT INTO etl_executions(id, workflow_id, run_id, exchange_id, start_sequence, description, source_type, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'running', ?, ?)",
            id,
            event.workflowId(),
            event.runId(),
            event.exchangeId(),
            sequence,
            description,
            source,
            time(created),
            time(event.timestamp())
        );
        String plan = jdbc.queryForObject(
            "SELECT plan_json FROM etl_execution_runs WHERE id = ?",
            String.class,
            event.runId()
        );
        int order = 0;
        for (var node : JSON.readTree(plan))
            insertStage(
                id,
                node.path("id").asText(),
                node.path("name").asText(),
                node.path("type").asText(),
                order++,
                event.timestamp()
            );
    }

    private void updateRun(WorkflowTraceStore.Event event) {
        String state = switch (event.type()) {
            case "COMPILING" -> "STARTING";
            case "WORKFLOW_STARTED" -> "RUNNING";
            case "STOP_REQUESTED" -> "STOPPING";
            case "WORKFLOW_STOPPED" -> "STOPPED";
            case "START_FAILED" -> "FAILED";
            case "WORKFLOW_INTERRUPTED" -> "INTERRUPTED";
            default -> null;
        };
        jdbc.update(
            "UPDATE etl_execution_runs SET updated_at = ? WHERE id = ?",
            time(event.timestamp()),
            event.runId()
        );
        if (state != null) {
            boolean terminal = Set.of("STOPPED", "FAILED", "INTERRUPTED").contains(state);
            jdbc.update(
                "UPDATE etl_execution_runs SET status = ?, completed_at = ? WHERE id = ?",
                state,
                terminal ? time(event.timestamp()) : null,
                event.runId()
            );
        }
        if (event.type().equals("PLAN_READY") && event.details().containsKey("nodes")) jdbc.update(
            "UPDATE etl_execution_runs SET plan_json = ? WHERE id = ?",
            JSON.writeValueAsString(event.details().get("nodes")),
            event.runId()
        );
        if (
            event.type().equals("WORKFLOW_STARTED") &&
            event.details().containsKey("sourceConfiguration")
        ) jdbc.update(
            "UPDATE etl_execution_runs SET source_configuration_json = ? WHERE id = ?",
            JSON.writeValueAsString(event.details().get("sourceConfiguration")),
            event.runId()
        );
        if (event.level().equals("ERROR")) jdbc.update(
            "UPDATE etl_execution_runs SET last_error = ? WHERE id = ?",
            event.message(),
            event.runId()
        );
    }

    private void updateBatch(WorkflowTraceStore.Event event, UUID id) {
        String state = switch (event.type()) {
            case "FILE_COMPLETED", "BATCH_COMPLETED" -> "completed";
            case "FILE_FAILED", "BATCH_FAILED" -> "failed";
            case "BATCH_INTERRUPTED" -> "interrupted";
            default -> null;
        };
        jdbc.update(
            "UPDATE etl_executions SET updated_at = ? WHERE id = ?",
            time(event.timestamp()),
            id
        );
        if (state == null) return;
        String previous = jdbc.queryForObject(
            "SELECT status FROM etl_executions WHERE id = ?",
            String.class,
            id
        );
        jdbc.update(
            "UPDATE etl_executions SET status = ?, completed_at = ?, duration_ms = ?, last_error = ? WHERE id = ?",
            state,
            time(event.timestamp()),
            number(event.details().get("durationMs")),
            state.equals("completed") ? null : event.message(),
            id
        );
        if (previous.equals("running")) {
            String count = state.equals("completed") ? "successful_files" : "failed_files";
            jdbc.update(
                "UPDATE etl_execution_runs SET " + count + " = " + count + " + 1 WHERE id = ?",
                event.runId()
            );
        }
        if (state.equals("interrupted")) jdbc.update(
            "UPDATE etl_execution_stages SET status = 'interrupted', error = ?, message = ?, updated_at = ? WHERE execution_id = ? AND status = 'running'",
            event.message(),
            event.message(),
            time(event.timestamp()),
            id
        );
    }

    private void insertStage(
        UUID execution,
        String node,
        String name,
        String type,
        int order,
        Instant timestamp
    ) {
        jdbc.update(
            "INSERT INTO etl_execution_stages(execution_id, node_id, stage_order, name, type, status, updated_at) VALUES (?, ?, ?, ?, ?, 'pending', ?)",
            execution,
            node,
            order,
            name,
            type,
            time(timestamp)
        );
    }

    private void updateStages(WorkflowTraceStore.Event event, UUID execution) {
        String state = switch (event.type()) {
            case "STEP_STARTED", "FILE_PICKED", "BATCH_STARTED" -> "running";
            case
                "STEP_COMPLETED",
                "NORMALIZATION_COMPLETED",
                "OUTPUT_WRITTEN",
                "FILE_READ" -> "completed";
            case "STEP_SKIPPED" -> "skipped";
            case "STEP_FAILED", "FILE_FAILED", "BATCH_FAILED" -> "failed";
            default -> null;
        };
        if (state == null) return;
        JsonNode details = JSON.valueToTree(event.details());
        var nodeIds =
            event.nodeIds().isEmpty() && event.nodeId() != null
                ? List.of(event.nodeId())
                : event.nodeIds();
        for (String node : nodeIds) {
            var records = jdbc.query(
                "SELECT * FROM etl_execution_stages WHERE execution_id = ? AND node_id = ?",
                (rs, row) -> stage(rs),
                execution,
                node
            );
            if (records.isEmpty()) {
                String name = details
                    .path("nodeLabels")
                    .path(node)
                    .asText(event.step() == null ? node : event.step());
                insertStage(
                    execution,
                    node,
                    name,
                    kind(event.stageType()),
                    10000,
                    event.timestamp()
                );
                records = jdbc.query(
                    "SELECT * FROM etl_execution_stages WHERE execution_id = ? AND node_id = ?",
                    (rs, row) -> stage(rs),
                    execution,
                    node
                );
            }
            var old = records.getFirst();
            String name = details.path("nodeLabels").path(node).asText(old.name());
            Long duration = number(event.details().get("durationMs"));
            boolean chain = "normalizeChain".equals(event.stageType());
            boolean fileFailure = Set.of("FILE_FAILED", "BATCH_FAILED").contains(event.type());
            jdbc.update(
                "UPDATE etl_execution_stages SET name = ?, type = ?, status = ?, inputs_json = ?, outputs_json = ?, matched_count = ?, total_count = ?, byte_count = ?, record_count = ?, duration_ms = ?, chain_duration_ms = ?, output_path = ?, message = ?, error = ?, updated_at = ? WHERE execution_id = ? AND node_id = ?",
                name,
                event.stageType() == null ? old.type() : kind(event.stageType()),
                state,
                jsonOr(event.details().get("inputs"), old.inputs()),
                jsonOr(event.details().get("outputs"), old.outputs()),
                or(number(event.details().get("matched")), old.matched()),
                or(number(event.details().get("total")), old.total()),
                or(number(event.details().get("bytes")), old.bytes()),
                or(number(event.details().get("records")), old.records()),
                !chain && !fileFailure && duration != null ? duration : old.durationMs(),
                chain && duration != null ? duration : old.chainDurationMs(),
                event.details().getOrDefault("outputPath", old.outputPath()),
                event.message(),
                state.equals("failed") ? event.message() : old.error(),
                time(event.timestamp()),
                execution,
                node
            );
        }
    }

    public WorkflowTraceStore.Page events(UUID workflow, long after, int limit) {
        require(
            after >= 0 && limit >= 1 && limit <= 10000,
            "Use a nonnegative cursor and a limit of 1..10000"
        );
        var bounds = jdbc
            .query(
                "SELECT MIN(sequence), MAX(sequence) FROM etl_execution_events WHERE workflow_id = ?",
                (rs, row) -> new long[] { rs.getLong(1), rs.getLong(2) },
                workflow
            )
            .getFirst();
        boolean reset = after > bounds[1];
        boolean initial = after == 0 || reset;
        List<WorkflowTraceStore.Event> events = jdbc.query(
            "SELECT event_json FROM etl_execution_events WHERE workflow_id = ? AND sequence > ? ORDER BY sequence " +
                (initial ? "DESC" : "ASC") +
                " LIMIT ?",
            (rs, row) -> JSON.readValue(rs.getString(1), WorkflowTraceStore.Event.class),
            workflow,
            initial ? 0 : after,
            limit
        );
        if (initial) {
            events = new ArrayList<>(events);
            Collections.reverse(events);
        }
        long next = events.isEmpty() ? (reset ? 0 : after) : events.getLast().sequence();
        return new WorkflowTraceStore.Page(
            historyId(),
            List.copyOf(events),
            next,
            bounds[0],
            bounds[1],
            false,
            reset
        );
    }

    public record Batch(
        UUID executionId,
        UUID workflowId,
        UUID runId,
        String id,
        String key,
        String description,
        String sourceType,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Long durationMs,
        String error,
        long cursor
    ) {}

    public record BatchPage(List<Batch> items, Long nextBefore, boolean hasMore) {}

    public record Stage(
        String id,
        String name,
        String type,
        String status,
        Map<String, Long> inputs,
        Map<String, Long> outputs,
        Long matched,
        Long total,
        Long bytes,
        Long records,
        Long durationMs,
        Long chainDurationMs,
        String outputPath,
        String message,
        String error
    ) {}

    public record Detail(Batch batch, List<Stage> stages, List<WorkflowTraceStore.Event> events) {}

    public record Run(
        UUID id,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        long successfulFiles,
        long failedFiles,
        String error,
        ApacheCamelDynamicEngine.SourceConfiguration sourceConfiguration
    ) {}

    public BatchPage batches(UUID workflow, Long before, int limit) {
        require(limit > 0 && limit <= 200, "Use a batch page size of 1..200");
        var items = jdbc.query(
            "SELECT * FROM etl_executions WHERE workflow_id = ? AND start_sequence < ? ORDER BY start_sequence DESC LIMIT ?",
            (rs, row) -> batch(rs),
            workflow,
            before == null ? Long.MAX_VALUE : before,
            limit + 1
        );
        boolean more = items.size() > limit;
        items = List.copyOf(items.subList(0, Math.min(items.size(), limit)));
        return new BatchPage(items, items.isEmpty() ? null : items.getLast().cursor(), more);
    }

    public Detail detail(UUID workflow, UUID execution) {
        var batches = jdbc.query(
            "SELECT * FROM etl_executions WHERE workflow_id = ? AND id = ?",
            (rs, row) -> batch(rs),
            workflow,
            execution
        );
        if (batches.isEmpty()) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Execution batch not found"
        );
        var stages = jdbc.query(
            "SELECT * FROM etl_execution_stages WHERE execution_id = ? ORDER BY stage_order, node_id",
            (rs, row) -> stage(rs),
            execution
        );
        var events = jdbc.query(
            "SELECT event_json FROM etl_execution_events WHERE execution_id = ? ORDER BY sequence",
            (rs, row) -> JSON.readValue(rs.getString(1), WorkflowTraceStore.Event.class),
            execution
        );
        return new Detail(batches.getFirst(), stages, events);
    }

    public Optional<Run> latestRun(UUID workflow) {
        return jdbc
            .query(
                "SELECT * FROM etl_execution_runs WHERE workflow_id = ? ORDER BY start_sequence DESC LIMIT 1",
                (rs, row) ->
                    new Run(
                        rs.getObject("id", UUID.class),
                        rs.getString("status"),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at"),
                        instant(rs, "completed_at"),
                        rs.getLong("successful_files"),
                        rs.getLong("failed_files"),
                        rs.getString("last_error"),
                        rs.getString("source_configuration_json") == null
                            ? null
                            : JSON.readValue(
                                  rs.getString("source_configuration_json"),
                                  ApacheCamelDynamicEngine.SourceConfiguration.class
                              )
                    ),
                workflow
            )
            .stream()
            .findFirst();
    }

    /** This local engine has one backend owner; old in-flight work is historical, never auto-resumed. */
    public void recoverInterrupted(UUID owner) {
        var unfinished = jdbc.query(
            "SELECT id, workflow_id FROM etl_execution_runs WHERE owner_id <> ? AND status IN ('STARTING', 'RUNNING', 'STOPPING')",
            (rs, row) -> new UUID[] { rs.getObject(1, UUID.class), rs.getObject(2, UUID.class) },
            owner
        );
        for (var run : unfinished) {
            var batches = jdbc.query(
                "SELECT * FROM etl_executions WHERE run_id = ? AND status = 'running'",
                (rs, row) -> batch(rs),
                run[0]
            );
            for (var batch : batches)
                append(
                    recoveryEvent(
                        run[1],
                        run[0],
                        batch.id(),
                        "BATCH_INTERRUPTED",
                        "Backend restarted before this batch finished",
                        Map.of(
                            "batchDescription",
                            batch.description(),
                            "sourceType",
                            batch.sourceType(),
                            "batchCreatedAt",
                            batch.createdAt().toString()
                        )
                    ),
                    owner
                );
            append(
                recoveryEvent(
                    run[1],
                    run[0],
                    null,
                    "WORKFLOW_INTERRUPTED",
                    "Backend restarted; this workflow was not resumed",
                    Map.of()
                ),
                owner
            );
        }
    }

    private WorkflowTraceStore.Event recoveryEvent(
        UUID workflow,
        UUID run,
        String exchange,
        String type,
        String message,
        Map<String, Object> details
    ) {
        return new WorkflowTraceStore.Event(
            0,
            Instant.now(),
            workflow,
            run,
            exchange,
            "ERROR",
            type,
            null,
            List.of(),
            null,
            "workflow",
            null,
            message,
            details
        );
    }

    private Batch batch(ResultSet rs) throws SQLException {
        UUID run = rs.getObject("run_id", UUID.class);
        String exchange = rs.getString("exchange_id");
        return new Batch(
            rs.getObject("id", UUID.class),
            rs.getObject("workflow_id", UUID.class),
            run,
            exchange,
            run + ":" + exchange,
            rs.getString("description"),
            rs.getString("source_type"),
            rs.getString("status"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            number(rs.getObject("duration_ms")),
            rs.getString("last_error"),
            rs.getLong("start_sequence")
        );
    }

    private Stage stage(ResultSet rs) throws SQLException {
        return new Stage(
            rs.getString("node_id"),
            rs.getString("name"),
            rs.getString("type"),
            rs.getString("status"),
            counts(rs.getString("inputs_json")),
            counts(rs.getString("outputs_json")),
            number(rs.getObject("matched_count")),
            number(rs.getObject("total_count")),
            number(rs.getObject("byte_count")),
            number(rs.getObject("record_count")),
            number(rs.getObject("duration_ms")),
            number(rs.getObject("chain_duration_ms")),
            rs.getString("output_path"),
            rs.getString("message"),
            rs.getString("error")
        );
    }

    private Map<String, Long> counts(String text) {
        if (text == null) return null;
        var values = new LinkedHashMap<String, Long>();
        JSON.readTree(text)
            .properties()
            .forEach(e -> values.put(e.getKey(), e.getValue().longValue()));
        return values;
    }

    private boolean exists(String query, Object arg) {
        return Objects.requireNonNull(jdbc.queryForObject(query, Integer.class, arg)) > 0;
    }

    private static OffsetDateTime time(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Long or(Long value, Long fallback) {
        return value == null ? fallback : value;
    }

    private static String jsonOr(Object value, Object fallback) {
        return value == null && fallback == null
            ? null
            : JSON.writeValueAsString(value == null ? fallback : value);
    }

    private static String kind(String kind) {
        return "normalizeChain".equals(kind) ? "normalizeRule" : kind;
    }
}
