package com.sh3d.mcp.command.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for JSON Schema objects used in {@link com.sh3d.mcp.command.CommandDescriptor#getSchema()}.
 *
 * <p>Produces the same {@code Map<String, Object>} structure as manual construction,
 * but with a concise, readable API that eliminates boilerplate.
 *
 * <pre>
 * // Before (manual):
 * Map&lt;String, Object&gt; schema = new LinkedHashMap&lt;&gt;();
 * schema.put("type", "object");
 * Map&lt;String, Object&gt; props = new LinkedHashMap&lt;&gt;();
 * props.put("id", SchemaUtil.prop("string", "Wall ID"));
 * schema.put("properties", props);
 * schema.put("required", Arrays.asList("id"));
 *
 * // After (builder):
 * SchemaBuilder.create()
 *     .requiredString("id", "Wall ID")
 *     .build();
 * </pre>
 */
public final class SchemaBuilder {

    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();

    private SchemaBuilder() {
    }

    /**
     * Creates a new schema builder.
     */
    public static SchemaBuilder create() {
        return new SchemaBuilder();
    }

    // ---- String ----

    public SchemaBuilder string(String name, String description) {
        properties.put(name, SchemaUtil.prop("string", description));
        return this;
    }

    public SchemaBuilder requiredString(String name, String description) {
        string(name, description);
        required.add(name);
        return this;
    }

    public SchemaBuilder nullableString(String name, String description) {
        properties.put(name, SchemaUtil.nullableProp("string", description));
        return this;
    }

    // ---- Number ----

    public SchemaBuilder number(String name, String description) {
        properties.put(name, SchemaUtil.prop("number", description));
        return this;
    }

    public SchemaBuilder requiredNumber(String name, String description) {
        number(name, description);
        required.add(name);
        return this;
    }

    public SchemaBuilder numberWithDefault(String name, String description, Number defaultValue) {
        properties.put(name, SchemaUtil.propWithDefault("number", description, defaultValue));
        return this;
    }

    public SchemaBuilder nullableNumber(String name, String description) {
        properties.put(name, SchemaUtil.nullableProp("number", description));
        return this;
    }

    // ---- Integer ----

    public SchemaBuilder integer(String name, String description) {
        properties.put(name, SchemaUtil.prop("integer", description));
        return this;
    }

    public SchemaBuilder requiredInteger(String name, String description) {
        integer(name, description);
        required.add(name);
        return this;
    }

    public SchemaBuilder integerWithDefault(String name, String description, int defaultValue) {
        properties.put(name, SchemaUtil.propWithDefault("integer", description, defaultValue));
        return this;
    }

    // ---- Boolean ----

    public SchemaBuilder bool(String name, String description) {
        properties.put(name, SchemaUtil.prop("boolean", description));
        return this;
    }

    public SchemaBuilder boolWithDefault(String name, String description, boolean defaultValue) {
        properties.put(name, SchemaUtil.propWithDefault("boolean", description, defaultValue));
        return this;
    }

    // ---- Enum ----

    public SchemaBuilder enumProp(String name, String description, String... values) {
        properties.put(name, SchemaUtil.enumProp(description, values));
        return this;
    }

    public SchemaBuilder enumProp(String name, String description, List<String> values) {
        properties.put(name, SchemaUtil.enumProp(description, values));
        return this;
    }

    public SchemaBuilder requiredEnum(String name, String description, String... values) {
        enumProp(name, description, values);
        required.add(name);
        return this;
    }

    public SchemaBuilder requiredEnum(String name, String description, List<String> values) {
        enumProp(name, description, values);
        required.add(name);
        return this;
    }

    // ---- Object (opaque, no inner properties) ----

    public SchemaBuilder object(String name, String description) {
        properties.put(name, SchemaUtil.prop("object", description));
        return this;
    }

    // ---- Array ----

    /**
     * Adds an array property with custom items schema and optional constraints.
     * Use {@link ArrayBuilder} for a fluent API to build the array definition.
     *
     * @param name property name
     * @param arrayDef a pre-built array property map (from {@link ArrayBuilder#build()})
     */
    public SchemaBuilder array(String name, Map<String, Object> arrayDef) {
        properties.put(name, arrayDef);
        return this;
    }

    public SchemaBuilder requiredArray(String name, Map<String, Object> arrayDef) {
        array(name, arrayDef);
        required.add(name);
        return this;
    }

    // ---- Raw property (escape hatch) ----

    /**
     * Adds a raw pre-built property map. Useful for complex or one-off schemas
     * that don't fit the standard helpers.
     */
    public SchemaBuilder raw(String name, Map<String, Object> propertyDef) {
        properties.put(name, propertyDef);
        return this;
    }

    public SchemaBuilder requiredRaw(String name, Map<String, Object> propertyDef) {
        raw(name, propertyDef);
        required.add(name);
        return this;
    }

    // ---- Build ----

    /**
     * Builds the schema as a {@code Map<String, Object>} with keys: type, properties, required.
     */
    public Map<String, Object> build() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(properties));
        schema.put("required", required.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(required));
        return schema;
    }

    // ====================================================================
    // ArrayBuilder — fluent builder for array property definitions
    // ====================================================================

    /**
     * Fluent builder for array-type JSON Schema properties.
     *
     * <pre>
     * // Array of [x, y] number pairs:
     * SchemaBuilder.array("polygon", "2D polygon points")
     *     .items(SchemaBuilder.array("point", "")
     *         .itemsOfType("number")
     *         .minItems(2).maxItems(2)
     *         .build())
     *     .minItems(3)
     *     .build();
     * </pre>
     */
    public static final class ArrayBuilder {

        private final String description;
        private Map<String, Object> items;
        private Integer minItems;
        private Integer maxItems;

        private ArrayBuilder(String description) {
            this.description = description;
        }

        /**
         * Sets items to a simple type (e.g., "number", "integer", "string").
         */
        public ArrayBuilder itemsOfType(String type) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type);
            this.items = item;
            return this;
        }

        /**
         * Sets items to a pre-built schema map (for nested arrays or object items).
         */
        public ArrayBuilder items(Map<String, Object> itemSchema) {
            this.items = itemSchema;
            return this;
        }

        public ArrayBuilder minItems(int min) {
            this.minItems = min;
            return this;
        }

        public ArrayBuilder maxItems(int max) {
            this.maxItems = max;
            return this;
        }

        /**
         * Builds the array property definition as a Map.
         */
        public Map<String, Object> build() {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", "array");
            if (description != null && !description.isEmpty()) {
                prop.put("description", description);
            }
            if (items != null) {
                prop.put("items", items);
            }
            if (minItems != null) {
                prop.put("minItems", minItems);
            }
            if (maxItems != null) {
                prop.put("maxItems", maxItems);
            }
            return prop;
        }
    }

    /**
     * Creates a new array builder.
     *
     * @param description description of the array property
     */
    public static ArrayBuilder arrayDef(String description) {
        return new ArrayBuilder(description);
    }
}
