package com.ahvakra.camelintegration.telecom;

import static org.junit.jupiter.api.Assertions.*;

import com.ahvakra.camelintegration.connections.domain.ConnectionStore;
import com.ahvakra.camelintegration.connections.infrastructure.file.FileConnectionHandler;
import com.ahvakra.camelintegration.fields.domain.FieldDefinitionStore;
import com.ahvakra.camelintegration.mappers.domain.MapperStore;
import com.ahvakra.camelintegration.rules.domain.*;
import com.ahvakra.camelintegration.seed.TelecomDemoSeeder;
import com.ahvakra.camelintegration.transformers.domain.TransformerStore;
import com.ahvakra.camelintegration.workflows.application.*;
import com.ahvakra.camelintegration.workflows.domain.Workflow;
import com.ahvakra.camelintegration.workflows.domain.WorkflowStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TelecomDemoTests {

    @Autowired
    WorkflowStore workflows;

    @Autowired
    WorkflowValidator validator;

    @Autowired
    FieldDefinitionStore fields;

    @Autowired
    NormalizeRuleStore normalizations;

    @Autowired
    ConditionalRuleStore conditions;

    @Autowired
    ConnectionStore connections;

    @Autowired
    MapperStore mappers;

    @Autowired
    TransformerStore transformers;

    @Autowired
    FileConnectionHandler files;

    @Autowired
    TelecomDemoSeeder seeder;

    @Autowired
    javax.sql.DataSource dataSource;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private UUID id(int suffix) {
        return UUID.fromString("ce1e0000-0000-4000-8000-%012d".formatted(suffix));
    }

    @Test
    void outputFilenameMigrationUpgradesLegacyDefaultsAndPreservesCustomNames() {
        jdbc.update(
            "UPDATE etl_connection_settings SET setting_value = ? WHERE connection_id = ? AND setting_key = 'fileName'",
            "cdr.csv",
            id(11)
        );
        jdbc.update(
            "UPDATE etl_connection_settings SET setting_value = ? WHERE connection_id = ? AND setting_key = 'fileName'",
            "custom-sms.csv",
            id(12)
        );
        jdbc.update(
            "UPDATE etl_connection_settings SET setting_value = ? WHERE connection_id = ? AND setting_key = 'fileName'",
            "rejected.csv",
            id(13)
        );
        var connection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(
            dataSource
        );
        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
            connection,
            new org.springframework.core.io.ClassPathResource(
                "db/migration/V15__telecom_output_filenames.sql"
            )
        );
        assertEquals(
            "${file:onlyname.noext.single}-cdr.csv",
            jdbc.queryForObject(
                "SELECT setting_value FROM etl_connection_settings WHERE connection_id = ? AND setting_key = 'fileName'",
                String.class,
                id(11)
            )
        );
        assertEquals(
            "custom-sms.csv",
            jdbc.queryForObject(
                "SELECT setting_value FROM etl_connection_settings WHERE connection_id = ? AND setting_key = 'fileName'",
                String.class,
                id(12)
            )
        );
        assertEquals(
            "${file:onlyname.noext.single}-rejected.csv",
            jdbc.queryForObject(
                "SELECT setting_value FROM etl_connection_settings WHERE connection_id = ? AND setting_key = 'fileName'",
                String.class,
                id(13)
            )
        );
    }

    @Test
    void javaSeederCreatesCompleteDemoAndPreservesEditsOnRepeatedRuns() throws Exception {
        // Remove V14's historical data to exercise Java population independently.
        workflows.delete(id(70));
        for (int base : List.of(30, 40)) {
            for (int step = 0; step < 6; step++) normalizations.delete(id(base + step));
        }
        for (int suffix : List.of(50, 51)) conditions.delete(id(suffix));
        mappers.delete(id(20));
        for (int suffix : List.of(60, 61)) transformers.delete(id(suffix));
        for (int suffix : List.of(10, 11, 12, 13)) connections.delete(id(suffix));
        for (int suffix : List.of(1, 2, 3)) fields.delete(id(suffix));

        seeder.seed();
        seededGraphHasValidTypedPortsAndExistingCatalogReferences();
        sampleRecordsNormalizeAndRouteToTwoCdrTwoSmsAndThreeRejected();
        var seeded = workflows.findById(id(70)).orElseThrow();
        var edited = new Workflow(
            seeded.id(),
            "My edited demo",
            seeded.description(),
            seeded.nodes(),
            seeded.edges(),
            seeded.viewport(),
            seeded.createdAt(),
            seeded.updatedAt()
        );
        workflows.save(edited);
        int workflowCount = workflows.findAll().size();
        int ruleCount = normalizations.findAll().size();
        seeder.seed();
        assertEquals(edited, workflows.findById(id(70)).orElseThrow());
        assertEquals(workflowCount, workflows.findAll().size());
        assertEquals(ruleCount, normalizations.findAll().size());
    }

    @Test
    void seededGraphHasValidTypedPortsAndExistingCatalogReferences() {
        var workflow = workflows.findById(id(70)).orElseThrow();
        var graph = validator.validate(
            new WorkflowCommand(
                workflow.id(),
                workflow.name(),
                workflow.description(),
                workflow.nodes(),
                workflow.edges(),
                workflow.viewport()
            )
        );
        assertEquals(workflow.nodes().size(), graph.nodes().size());
        assertEquals(workflow.nodes().size() - 1, graph.edges().size());
        for (var node : workflow.nodes()) {
            var data = node.path("data");
            switch (node.path("type").asText()) {
                case "dataSource", "dataDestination" -> {
                    var connection = connections
                        .findById(UUID.fromString(data.path("connectorId").asText()))
                        .orElseThrow();
                    assertEquals("file", connection.type());
                    assertEquals(
                        connection.settings(),
                        files.validateSettings(connection.settings())
                    );
                }
                case "mapper" -> {
                    var mapper = mappers
                        .findById(UUID.fromString(data.path("mapperId").asText()))
                        .orElseThrow();
                    assertEquals(
                        fields.findById(id(1)).orElseThrow().fields(),
                        mapper.outputs().getFirst().fields()
                    );
                }
                case "transformer" -> {
                    var transformer = transformers
                        .findById(UUID.fromString(data.path("transformerId").asText()))
                        .orElseThrow();
                    transformer
                        .ports()
                        .forEach(port ->
                            assertEquals(
                                fields.findById(port.fieldDefinitionId()).orElseThrow().fields(),
                                port.fields()
                            )
                        );
                }
                case "normalizeRule" -> assertTrue(
                    normalizations
                        .findById(UUID.fromString(data.path("ruleId").asText()))
                        .isPresent()
                );
                case "conditionalRule" -> {
                    var rule = conditions
                        .findById(UUID.fromString(data.path("ruleId").asText()))
                        .orElseThrow();
                    assertFalse(
                        new ConditionalValidator()
                            .validate(rule.spec(), fields.findById(id(1)).orElseThrow().fields())
                            .isEmpty()
                    );
                }
                default -> fail("Unexpected demo block");
            }
        }
    }

    @Test
    void sampleRecordsNormalizeAndRouteToTwoCdrTwoSmsAndThreeRejected() throws Exception {
        var schema = fields.findById(id(1)).orElseThrow().fields();
        var cdr = conditions.findById(id(50)).orElseThrow().spec();
        var sms = conditions.findById(id(51)).orElseThrow().spec();
        var lines = Files.readAllLines(Path.of("../../examples/telecom/fixtures/usage.csv"));
        var header = lines.getFirst().split(",");
        var actual = new LinkedHashMap<String, String>();
        for (String line : lines.subList(1, lines.size())) {
            var cells = line.split(",", -1);
            Map<String, Object> record = new LinkedHashMap<>();
            for (int i = 0; i < header.length; i++) record.put(
                header[i],
                cells[i].isEmpty() ? null : cells[i]
            );
            record.put("normalizedcalling", null);
            record.put("normalizedcalled", null);
            var originalCalling = record.get("calling");
            var originalCalled = record.get("called");
            for (int base : List.of(30, 40)) {
                for (int step = 0; step < 6; step++) {
                    record = new NormalizeEvaluator()
                        .evaluate(
                            normalizations
                                .findById(id(base + step))
                                .orElseThrow()
                                .spec(),
                            record
                        )
                        .record();
                }
            }
            assertEquals(originalCalling, record.get("calling"));
            assertEquals(originalCalled, record.get("called"));
            assertEquals(
                record.get("record_id").equals("BAD-003") ? null : "+96171123456",
                record.get("normalizedcalling")
            );
            assertEquals("+96176234567", record.get("normalizedcalled"));
            var evaluator = new ConditionalEvaluator();
            String destination = evaluator.evaluate(cdr.expression(), schema, record).matched()
                ? "CDR"
                : evaluator.evaluate(sms.expression(), schema, record).matched()
                  ? "SMS"
                  : "rejected";
            actual.put(record.get("record_id").toString(), destination);
        }
        assertEquals(
            Map.of(
                "CALL-001",
                "CDR",
                "CALL-002",
                "CDR",
                "SMS-001",
                "SMS",
                "SMS-002",
                "SMS",
                "BAD-001",
                "rejected",
                "BAD-002",
                "rejected",
                "BAD-003",
                "rejected"
            ),
            actual
        );
    }
}
