package com.ahvakra.camelintegration.rules.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Pure Java evaluation shared by rule previews and future workflow execution. */
public class NormalizeEvaluator {

    public record Result(boolean matched, Map<String, Object> record) {
        public Result {
            record = Collections.unmodifiableMap(new LinkedHashMap<>(record));
        }
    }

    public Result evaluate(NormalizeSpec spec, Map<String, Object> record) {
        if (record == null) throw new InvalidRuleException("Provide a record to normalize.");
        var output = new LinkedHashMap<>(record);
        boolean matched = applyInPlace(spec, output);
        return new Result(matched, output);
    }

    /** Apply an ordered chain to an engine-owned working record without per-rule copies. */
    public boolean applyInPlace(NormalizeSpec spec, Map<String, Object> record) {
        if (record == null) throw new InvalidRuleException("Provide a record to normalize.");
        // Null, missing, or non-string values never match; do not coerce other types.
        if (!(record.get(spec.sourceField()) instanceof String source)) return false;
        int length = source.codePointCount(0, source.length());
        boolean matched = switch (spec.condition()) {
            case STARTS_WITH -> source.startsWith(spec.conditionValue());
            case ENDS_WITH -> source.endsWith(spec.conditionValue());
            case CONTAINS -> source.contains(spec.conditionValue());
            case EQUALS -> source.equals(spec.conditionValue());
            case LENGTH_EQUALS -> length == Integer.parseInt(spec.conditionValue());
            case LENGTH_GREATER_THAN -> length > Integer.parseInt(spec.conditionValue());
            case LENGTH_LESS_THAN -> length < Integer.parseInt(spec.conditionValue());
        };
        if (!matched) return false;
        String value = switch (spec.action()) {
            case COPY -> source;
            case REPLACE -> source.replace(spec.actionValue(), spec.replacementValue());
            case ADD_PREFIX -> spec.actionValue() + source;
            case ADD_SUFFIX -> source + spec.actionValue();
            case REMOVE_PREFIX -> source.startsWith(spec.actionValue())
                ? source.substring(spec.actionValue().length())
                : source;
            case REMOVE_SUFFIX -> source.endsWith(spec.actionValue())
                ? source.substring(0, source.length() - spec.actionValue().length())
                : source;
            case TRIM -> source.strip();
            case UPPERCASE -> source.toUpperCase(Locale.ROOT);
            case LOWERCASE -> source.toLowerCase(Locale.ROOT);
        };
        record.put(spec.destinationField(), value);
        return true;
    }
}
