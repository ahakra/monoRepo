package com.ahvakra.camelintegration.engine;

import static com.ahvakra.camelintegration.engine.EngineValues.*;

import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.*;
import com.dylibso.chicory.wasm.types.MemoryLimits;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Parses once per plan; each invocation receives a fresh, isolated WASM instance. */
@Component
public class WasmRuntime {

    private final int maxBytes;
    private final int maxPages;
    private final long instructionLimit;

    public WasmRuntime(
        @Value("${etl.engine.max-bytes:67108864}") int maxBytes,
        @Value("${etl.engine.wasm-max-pages:4096}") int maxPages,
        @Value("${etl.engine.wasm-instruction-limit:100000000}") long instructionLimit
    ) {
        require(
            maxBytes > 0 && maxBytes <= 67108864,
            "Engine input limit must be 1..67108864 bytes"
        );
        require(maxPages > 0 && maxPages <= 32767, "Invalid WASM memory page limit");
        require(instructionLimit > 0, "WASM instruction limit must be positive");
        this.maxBytes = maxBytes;
        this.maxPages = maxPages;
        this.instructionLimit = instructionLimit;
    }

    public int maxBytes() {
        return maxBytes;
    }

    public Module load(String directory, String filename) {
        try {
            var path = Path.of(directory).resolve(filename).toAbsolutePath().normalize();
            require(Files.size(path) <= maxBytes, "WASM file exceeds size limit");
            var module = Parser.parse(Files.readAllBytes(path));
            require(
                module.importSection().importCount() == 0,
                "ETL WASM modules must not import host functions"
            );
            require(
                module.startSection().isEmpty(),
                "ETL WASM modules must not declare a start function"
            );
            var instance = instance(module);
            require(
                instance.export("etl_abi_version").apply()[0] == 1,
                "Unsupported WASM ABI version"
            );
            var manifest = read(instance, instance.export("etl_manifest").apply()[0]);
            require(manifest.path("abiVersion").asInt() == 1, "Unsupported WASM manifest version");
            return new Module(module, manifest);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException(
                "Cannot load WASM '" + filename + "': " + e.getMessage(),
                e
            );
        }
    }

    private Instance instance(WasmModule module) {
        long[] remaining = { instructionLimit };
        return Instance.builder(module)
            .withStart(false)
            .withMemoryFactory(limits -> {
                require(limits.initialPages() <= maxPages, "WASM initial memory exceeds limit");
                return new ByteArrayMemory(
                    new MemoryLimits(
                        limits.initialPages(),
                        Math.min(maxPages, limits.maximumPages())
                    )
                );
            })
            .withUnsafeExecutionListener((instruction, stack) -> {
                if (--remaining[0] < 0 || Thread.currentThread().isInterrupted()) {
                    throw new IllegalStateException(
                        "WASM instruction budget exceeded or execution interrupted"
                    );
                }
            })
            .build();
    }

    private JsonNode read(Instance instance, long packed) {
        long pointer = packed >>> 32;
        long length = packed & 0xffff_ffffL;
        require(
            pointer > 0 &&
                pointer <= Integer.MAX_VALUE &&
                length > 0 &&
                length <= maxBytes &&
                pointer + length <= (long) instance.memory().pages() * 65536,
            "WASM returned an invalid or oversized buffer"
        );
        try {
            return JSON.readTree(instance.memory().readBytes((int) pointer, (int) length));
        } finally {
            instance.export("etl_free").apply(pointer, length);
        }
    }

    public final class Module {

        private final WasmModule module;
        private final JsonNode manifest;

        private Module(WasmModule module, JsonNode manifest) {
            this.module = module;
            this.manifest = manifest;
        }

        void validate(String kind, Collection<Port> inputs, Collection<Port> outputs) {
            require(
                manifest.path("kind").asText().equals(kind),
                "WASM module kind does not match block"
            );
            if (kind.equals("transformer")) validatePorts(manifest.path("inputs"), inputs);
            else require(
                manifest.path("input").path("kind").asText().equals("BYTES"),
                "Mapper must accept bytes"
            );
            validatePorts(manifest.path("outputs"), outputs);
        }

        private void validatePorts(JsonNode declared, Collection<Port> expected) {
            require(
                declared.isArray() && declared.size() == expected.size(),
                "WASM port count does not match saved definition"
            );
            var seen = new HashSet<String>();
            for (var actual : declared) {
                String name = actual.path("name").asText();
                require(seen.add(name), "Duplicate WASM port name: " + name);
                var port = expected
                    .stream()
                    .filter(p -> p.name().equals(name))
                    .findFirst()
                    .orElseThrow(() ->
                        new IllegalArgumentException("Unexpected WASM port: " + name)
                    );
                require(
                    port.kind().equals(actual.path("kind").asText()),
                    "WASM port kind mismatch: " + name
                );
                if (port.kind().equals("FIELD_LIST")) require(
                    JSON.valueToTree(port.fields()).equals(actual.path("fields")),
                    "WASM schema mismatch: " + name
                );
            }
        }

        JsonNode invoke(byte[] input, Collection<Port> outputs) {
            require(
                input.length > 0 && input.length <= maxBytes,
                "WASM input is empty or exceeds size limit"
            );
            var instance = instance(module);
            long pointer = instance.export("etl_alloc").apply(input.length)[0] & 0xffff_ffffL;
            require(pointer > 0 && pointer <= Integer.MAX_VALUE, "WASM input allocation failed");
            JsonNode response;
            try {
                instance.memory().write((int) pointer, input);
                response = read(
                    instance,
                    instance.export("etl_process").apply(pointer, input.length)[0]
                );
            } finally {
                instance.export("etl_free").apply(pointer, input.length);
            }
            require(response.path("abiVersion").asInt() == 1, "Unsupported WASM response version");
            require(
                !response.has("error"),
                "WASM failed: " + response.path("error").path("message").asText()
            );
            var result = response.path("outputs");
            require(
                result.isObject() && result.size() == outputs.size(),
                "WASM output names do not match saved ports"
            );
            for (var port : outputs) port.validate(result.get(port.name()));
            return result;
        }
    }
}
