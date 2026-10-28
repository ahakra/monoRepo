package com.ahvakra.camelintegration.connections.infrastructure.file;

import static org.junit.jupiter.api.Assertions.*;

import com.ahvakra.camelintegration.connections.domain.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileConnectionHandlerTests {

    private final FileConnectionHandler handler = new FileConnectionHandler();

    @TempDir
    Path temporary;

    private ConnectionDefinition definition(Map<String, String> settings) {
        return new ConnectionDefinition(
            UUID.randomUUID(),
            "Files",
            "file",
            handler.validateSettings(settings),
            "",
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    void usesCamelOptionNamesAndRejectsInvalidSettings() {
        var settings = handler.validateSettings(Map.of("directoryName", temporary.toString()));
        assertEquals("file", handler.type());
        assertEquals(".processed", settings.get("move"));
        assertEquals(".invalid", settings.get("moveFailed"));
        assertEquals("none", settings.get("readLock"));
        assertEquals("500", settings.get("delay"));
        assertEquals("true", settings.get("autoCreate"));
        for (var invalid : List.of(
            Map.of("directoryName", ""),
            Map.of("directoryName", "${header.folder}"),
            Map.of("directoryName", "{{input.folder}}"),
            Map.of("directoryName", temporary.toString(), "dir", "wrong"),
            Map.of("directoryName", temporary.toString(), "include", "["),
            Map.of("directoryName", temporary.toString(), "charset", "not-a-charset"),
            Map.of("directoryName", temporary.toString(), "recursive", "yes"),
            Map.of("directoryName", temporary.toString(), "readLock", "unknown"),
            Map.of("directoryName", temporary.toString(), "delay", "0"),
            Map.of("directoryName", temporary.toString(), "initialDelay", "-1"),
            Map.of("directoryName", temporary.toString(), "maxMessagesPerPoll", "2147483648"),
            Map.of(
                "directoryName",
                temporary.toString(),
                "readLock",
                "changed",
                "readLockTimeout",
                "1000",
                "readLockCheckInterval",
                "1000"
            )
        ))
            assertThrows(InvalidConnectionException.class, () -> handler.validateSettings(invalid));
        assertThrows(InvalidConnectionException.class, () -> handler.validateSettings(null));
        assertEquals(
            "0",
            handler
                .validateSettings(
                    Map.of(
                        "directoryName",
                        temporary.toString(),
                        "readLock",
                        "changed",
                        "readLockTimeout",
                        "0"
                    )
                )
                .get("readLockTimeout")
        );
        String exact = temporary.resolve(" folder with spaces ").toString();
        assertEquals(
            exact,
            handler.validateSettings(Map.of("directoryName", exact)).get("directoryName")
        );
    }

    @Test
    void checksDirectoriesWithoutCreatingMovingOrConsumingFiles() throws Exception {
        var source = Files.createDirectory(temporary.resolve("source files"));
        var file = Files.writeString(source.resolve("records.csv"), "id,name\n1,Alice");
        var connection = definition(
            Map.of("directoryName", source.toString(), "move", "processed", "moveFailed", "invalid")
        );
        assertTrue(handler.test(connection).success());
        assertEquals("id,name\n1,Alice", Files.readString(file));
        assertFalse(Files.exists(source.resolve("processed")));
        assertFalse(Files.exists(source.resolve("invalid")));
        assertFalse(handler.test(definition(Map.of("directoryName", file.toString()))).success());
        assertFalse(
            handler
                .test(
                    definition(Map.of("directoryName", source.toString(), "move", file.toString()))
                )
                .success()
        );
        var missing = temporary.resolve("missing/input");
        assertTrue(
            handler
                .test(definition(Map.of("directoryName", missing.toString(), "autoCreate", "true")))
                .success()
        );
        assertFalse(Files.exists(missing));
        assertFalse(
            handler
                .test(
                    definition(Map.of("directoryName", missing.toString(), "autoCreate", "false"))
                )
                .success()
        );
        var expression = handler.test(
            definition(
                Map.of(
                    "directoryName",
                    source.toString(),
                    "move",
                    "processed/${date:now:yyyyMMdd}/${file:name}"
                )
            )
        );
        assertTrue(expression.success());
        assertTrue(expression.message().contains("Expression-based"));
    }

    @Test
    void bindsConfiguredOptionsToTheRealCamelFileComponentWithoutStartingAConsumer()
        throws Exception {
        var input = new HashMap<String, String>();
        input.put("directoryName", temporary.resolve("input").toString());
        input.put("move", ".processed/${file:onlyname}");
        input.put("moveFailed", ".invalid/${file:onlyname}");
        input.put("preMove", ".inprogress");
        input.put("fileName", "${date:now:yyyyMMdd}.csv");
        input.put("include", "orders[+].*\\.csv");
        input.put("exclude", ".*\\.tmp");
        input.put("includeExt", "csv,txt");
        input.put("excludeExt", "part");
        input.put("charset", "UTF-8");
        input.put("recursive", "true");
        input.put("readLock", "changed");
        var settings = handler.validateSettings(input);
        try (var context = new DefaultCamelContext()) {
            context.addComponent("file", new FileComponent());
            var parameters = new HashMap<String, Object>(settings);
            parameters.remove("directoryName");
            var endpoint = (FileEndpoint) context.getEndpoint(
                "file:" + settings.get("directoryName"),
                parameters
            );
            assertEquals(input.get("include"), endpoint.getInclude());
            assertEquals("changed", endpoint.getReadLock());
            assertTrue(endpoint.isRecursive());
            assertEquals("UTF-8", endpoint.getCharset());
            assertNotNull(endpoint.getMove());
            assertNotNull(endpoint.getMoveFailed());
            assertFalse(Files.exists(temporary.resolve("input")));
        }
    }
}
