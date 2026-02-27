package com.sh3d.mcp.command.util;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static utility methods for constructing JSON Schema property maps
 * used in {@link CommandDescriptor#getSchema()} implementations.
 */
public final class SchemaUtil {

    private SchemaUtil() {
    }

    /**
     * Creates a simple typed property.
     */
    public static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    /**
     * Creates a typed property with a default value.
     */
    public static Map<String, Object> propWithDefault(String type, String description, Object defaultValue) {
        Map<String, Object> p = prop(type, description);
        p.put("default", defaultValue);
        return p;
    }

    /**
     * Creates a nullable typed property (type is an array of [type, "null"]).
     */
    public static Map<String, Object> nullableProp(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", Arrays.asList(type, "null"));
        p.put("description", description);
        return p;
    }

    /**
     * Creates a string enum property from a list of allowed values.
     */
    public static Map<String, Object> enumProp(String description, List<String> values) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "string");
        p.put("description", description);
        p.put("enum", values);
        return p;
    }

    /**
     * Creates a string enum property from varargs of allowed values.
     */
    public static Map<String, Object> enumProp(String description, String... values) {
        return enumProp(description, Arrays.asList(values));
    }
}
