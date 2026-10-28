package com.ahvakra.camelintegration.connections.infrastructure.file;

import com.ahvakra.camelintegration.connections.domain.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

/** File-consumer configuration using Camel 4.22 endpoint option names. */
@Component
public class FileConnectionHandler implements ConnectionTypeHandler {

    private static final Set<String> KEYS = Set.of(
        "directoryName",
        "move",
        "moveFailed",
        "preMove",
        "fileName",
        "include",
        "exclude",
        "includeExt",
        "excludeExt",
        "charset",
        "recursive",
        "autoCreate",
        "delay",
        "initialDelay",
        "maxMessagesPerPoll",
        "readLock",
        "readLockTimeout",
        "readLockCheckInterval",
        "readLockMinAge",
        "readLockMinLength"
    );
    private static final Set<String> LOCKS = Set.of(
        "none",
        "markerFile",
        "changed",
        "fileLock",
        "rename",
        "idempotent",
        "idempotent-changed",
        "idempotent-rename"
    );

    public String type() {
        return "file";
    }

    public Map<String, String> validateSettings(Map<String, String> settings) {
        if (
            settings == null ||
            !KEYS.containsAll(settings.keySet()) ||
            settings.values().stream().anyMatch(Objects::isNull)
        ) throw new InvalidConnectionException(
            "Invalid File settings. Use supported Camel File option names."
        );
        var result = new HashMap<String, String>();
        settings.forEach((key, value) -> {
            if (
                value.length() > 2048 || value.indexOf('\0') >= 0
            ) throw new InvalidConnectionException(
                "File setting '" +
                    key +
                    "' must have up to 2048 characters and cannot contain a null character."
            );
            if (!value.isEmpty()) result.put(key, value);
        });
        String directory = result.get("directoryName");
        if (
            directory == null ||
            directory.isBlank() ||
            directory.contains("${") ||
            directory.contains("{{")
        ) throw new InvalidConnectionException(
            "Enter a literal source directory (directoryName), without dynamic expressions or property placeholders."
        );
        path(directory, "source directory");
        result.putIfAbsent("move", ".processed");
        result.putIfAbsent("moveFailed", ".invalid");
        for (String key : List.of("move", "moveFailed", "preMove")) {
            String value = result.get(key);
            if (value != null && !value.contains("${")) {
                if (value.isBlank()) throw new InvalidConnectionException(
                    "Enter a valid " + key + " directory or Camel file expression."
                );
                path(value, key);
            }
        }
        for (String key : List.of("include", "exclude")) {
            try {
                if (result.containsKey(key)) Pattern.compile(
                    result.get(key),
                    Pattern.CASE_INSENSITIVE
                );
            } catch (PatternSyntaxException e) {
                throw new InvalidConnectionException(
                    "Enter a valid regular expression for " + key + "."
                );
            }
        }
        if (result.containsKey("charset")) {
            try {
                result.put("charset", Charset.forName(result.get("charset")).name());
            } catch (IllegalArgumentException e) {
                throw new InvalidConnectionException(
                    "Choose a supported character encoding, for example UTF-8."
                );
            }
        }
        bool(result, "recursive", "false");
        bool(result, "autoCreate", "true");
        number(result, "delay", "500", 1, Long.MAX_VALUE);
        number(result, "initialDelay", "1000", 0, Long.MAX_VALUE);
        number(result, "maxMessagesPerPoll", "0", 0, Integer.MAX_VALUE);
        number(result, "readLockTimeout", "10000", 0, Long.MAX_VALUE);
        number(result, "readLockCheckInterval", "1000", 1, Long.MAX_VALUE);
        number(result, "readLockMinAge", "0", 0, Long.MAX_VALUE);
        number(result, "readLockMinLength", "1", 0, Long.MAX_VALUE);
        String readLock = result.getOrDefault("readLock", "none");
        if (!LOCKS.contains(readLock)) throw new InvalidConnectionException(
            "Choose a supported Camel File readLock strategy."
        );
        result.put("readLock", readLock);
        long timeout = Long.parseLong(result.get("readLockTimeout"));
        long interval = Long.parseLong(result.get("readLockCheckInterval"));
        if (
            Set.of(
                "changed",
                "fileLock",
                "rename",
                "idempotent-changed",
                "idempotent-rename"
            ).contains(readLock) &&
            timeout > 0 &&
            timeout <= interval
        ) throw new InvalidConnectionException(
            "readLockTimeout must be greater than readLockCheckInterval, or 0 to wait indefinitely."
        );
        return result;
    }

    public ConnectionTestResult test(ConnectionDefinition connection) {
        try {
            var settings = connection.settings();
            Path source = path(settings.get("directoryName"), "source directory")
                .toAbsolutePath()
                .normalize();
            boolean sourceExists = Files.exists(source);
            if (
                !sourceExists && !Boolean.parseBoolean(settings.get("autoCreate"))
            ) return new ConnectionTestResult(
                false,
                "Source directory does not exist. Create it or enable autoCreate for future execution."
            );
            checkDirectory(source, "Source directory");
            boolean expression = false;
            for (String key : List.of("move", "moveFailed", "preMove")) {
                String destination = settings.get(key);
                if (destination == null) continue;
                if (destination.contains("${")) {
                    expression = true;
                    continue;
                }
                Path target = path(destination, key);
                checkDirectory(
                    target.isAbsolute() ? target : source.resolve(target),
                    key + " directory"
                );
            }
            return new ConnectionTestResult(
                true,
                "Directory access checks passed. No folders or files were created, moved, or consumed." +
                    (!sourceExists ? " The source can be created when the connector runs." : "") +
                    (expression
                        ? " Expression-based destinations need checking during execution."
                        : "")
            );
        } catch (InvalidConnectionException | IllegalArgumentException | SecurityException e) {
            return new ConnectionTestResult(
                false,
                e.getMessage() == null
                    ? "Cannot access the configured directories."
                    : e.getMessage()
            );
        }
    }

    private static void checkDirectory(Path target, String label) {
        Path existing = target.toAbsolutePath().normalize();
        while (existing != null && !Files.exists(existing)) {
            if (Files.isSymbolicLink(existing)) throw new InvalidConnectionException(
                label + " contains a broken symbolic link."
            );
            existing = existing.getParent();
        }
        if (existing == null || !Files.isDirectory(existing)) throw new InvalidConnectionException(
            label + " must be a directory, not a regular file."
        );
        if (
            !Files.isReadable(existing) ||
            !Files.isWritable(existing) ||
            !Files.isExecutable(existing)
        ) throw new InvalidConnectionException(
            label +
                " (or its nearest existing parent) needs read, write, and directory access for the Java service."
        );
    }

    private static Path path(String value, String label) {
        try {
            return Path.of(value);
        } catch (InvalidPathException e) {
            throw new InvalidConnectionException("Enter a valid " + label + ".");
        }
    }

    private static void bool(Map<String, String> settings, String key, String fallback) {
        String value = settings.getOrDefault(key, fallback);
        if (!Set.of("true", "false").contains(value)) throw new InvalidConnectionException(
            key + " must be true or false."
        );
        settings.put(key, value);
    }

    private static void number(
        Map<String, String> settings,
        String key,
        String fallback,
        long min,
        long max
    ) {
        try {
            long value = Long.parseLong(settings.getOrDefault(key, fallback));
            if (value < min || value > max) throw new NumberFormatException();
            settings.put(key, Long.toString(value));
        } catch (NumberFormatException e) {
            throw new InvalidConnectionException(
                key + " must be a whole number between " + min + " and " + max + "."
            );
        }
    }
}
