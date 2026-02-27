package com.sh3d.mcp.command.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaBuilderTest {

    // ==================== Basic structure ====================

    @Nested
    class BasicStructure {

        @Test
        void emptyBuilderProducesValidSchema() {
            Map<String, Object> schema = SchemaBuilder.create().build();
            assertEquals("object", schema.get("type"));
            assertNotNull(schema.get("properties"));
            assertTrue(((Map<?, ?>) schema.get("properties")).isEmpty());
            assertNotNull(schema.get("required"));
            assertTrue(((List<?>) schema.get("required")).isEmpty());
        }

        @Test
        void schemaHasThreeKeys() {
            Map<String, Object> schema = SchemaBuilder.create().build();
            assertEquals(3, schema.size());
            assertTrue(schema.containsKey("type"));
            assertTrue(schema.containsKey("properties"));
            assertTrue(schema.containsKey("required"));
        }

        @Test
        void emptyRequiredIsEmptyList() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .string("name", "A name")
                    .build();
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertEquals(Collections.emptyList(), required);
        }
    }

    // ==================== String properties ====================

    @Nested
    class StringProperties {

        @Test
        void stringProperty() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .string("name", "Wall name")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> nameProp = (Map<String, Object>) props.get("name");
            assertEquals("string", nameProp.get("type"));
            assertEquals("Wall name", nameProp.get("description"));
        }

        @Test
        void requiredStringProperty() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .requiredString("id", "Wall ID")
                    .build();
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertEquals(Arrays.asList("id"), required);
        }

        @Test
        void nullableStringProperty() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .nullableString("color", "Color or null")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> colorProp = (Map<String, Object>) props.get("color");
            assertEquals(Arrays.asList("string", "null"), colorProp.get("type"));
        }
    }

    // ==================== Number properties ====================

    @Nested
    class NumberProperties {

        @Test
        void numberProperty() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .number("x", "X coordinate")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> xProp = (Map<String, Object>) props.get("x");
            assertEquals("number", xProp.get("type"));
        }

        @Test
        void requiredNumberProperty() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .requiredNumber("x", "X coordinate")
                    .requiredNumber("y", "Y coordinate")
                    .build();
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertEquals(Arrays.asList("x", "y"), required);
        }

        @Test
        void numberWithDefault() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .numberWithDefault("thickness", "Wall thickness", 10)
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> thickProp = (Map<String, Object>) props.get("thickness");
            assertEquals("number", thickProp.get("type"));
            assertEquals(10, thickProp.get("default"));
        }

        @Test
        void nullableNumber() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .nullableNumber("heightAtEnd", "Height at end")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) props.get("heightAtEnd");
            assertEquals(Arrays.asList("number", "null"), prop.get("type"));
        }
    }

    // ==================== Integer properties ====================

    @Nested
    class IntegerProperties {

        @Test
        void integerProperty() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .integer("count", "Item count")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) props.get("count");
            assertEquals("integer", prop.get("type"));
        }

        @Test
        void requiredInteger() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .requiredInteger("steps", "Number of steps")
                    .build();
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertTrue(required.contains("steps"));
        }

        @Test
        void integerWithDefault() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .integerWithDefault("divisions", "Segments", 32)
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) props.get("divisions");
            assertEquals("integer", prop.get("type"));
            assertEquals(32, prop.get("default"));
        }
    }

    // ==================== Boolean properties ====================

    @Nested
    class BooleanProperties {

        @Test
        void boolProperty() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .bool("visible", "Is visible")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) props.get("visible");
            assertEquals("boolean", prop.get("type"));
        }

        @Test
        void boolWithDefault() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .boolWithDefault("allLevelsVisible", "All levels visible", false)
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) props.get("allLevelsVisible");
            assertEquals("boolean", prop.get("type"));
            assertEquals(false, prop.get("default"));
        }
    }

    // ==================== Enum properties ====================

    @Nested
    class EnumProperties {

        @Test
        void enumWithVarargs() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .enumProp("mode", "Drawing mode", "FILL", "OUTLINE")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) props.get("mode");
            assertEquals("string", prop.get("type"));
            assertEquals(Arrays.asList("FILL", "OUTLINE"), prop.get("enum"));
        }

        @Test
        void enumWithList() {
            List<String> values = Arrays.asList("top", "observer");
            Map<String, Object> schema = SchemaBuilder.create()
                    .enumProp("type", "Camera type", values)
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) props.get("type");
            assertEquals(values, prop.get("enum"));
        }

        @Test
        void requiredEnumVarargs() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .requiredEnum("mode", "Mode", "extrude", "mesh")
                    .build();
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertTrue(required.contains("mode"));
        }

        @Test
        void requiredEnumList() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .requiredEnum("mode", "Mode", Arrays.asList("a", "b"))
                    .build();
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertTrue(required.contains("mode"));
        }
    }

    // ==================== Object properties ====================

    @Nested
    class ObjectProperties {

        @Test
        void opaqueObject() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .object("operandA", "First operand")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) props.get("operandA");
            assertEquals("object", prop.get("type"));
            assertEquals("First operand", prop.get("description"));
        }
    }

    // ==================== Array properties ====================

    @Nested
    class ArrayProperties {

        @Test
        void simpleArray() {
            Map<String, Object> arrayDef = SchemaBuilder.arrayDef("List of names")
                    .itemsOfType("string")
                    .build();
            Map<String, Object> schema = SchemaBuilder.create()
                    .array("names", arrayDef)
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) props.get("names");
            assertEquals("array", prop.get("type"));
            assertEquals("List of names", prop.get("description"));
            @SuppressWarnings("unchecked")
            Map<String, Object> items = (Map<String, Object>) prop.get("items");
            assertEquals("string", items.get("type"));
        }

        @Test
        void arrayWithMinMaxItems() {
            Map<String, Object> arrayDef = SchemaBuilder.arrayDef("Points")
                    .itemsOfType("number")
                    .minItems(2)
                    .maxItems(2)
                    .build();
            assertEquals(2, arrayDef.get("minItems"));
            assertEquals(2, arrayDef.get("maxItems"));
        }

        @Test
        void nestedArrayForPolygon() {
            // Array of [x, y] pairs — mimics polygon schema in GenerateShapeHandler
            Map<String, Object> pointDef = SchemaBuilder.arrayDef("")
                    .itemsOfType("number")
                    .minItems(2)
                    .maxItems(2)
                    .build();
            Map<String, Object> polygonDef = SchemaBuilder.arrayDef("2D polygon points")
                    .items(pointDef)
                    .minItems(3)
                    .build();
            Map<String, Object> schema = SchemaBuilder.create()
                    .array("polygon", polygonDef)
                    .build();

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> polygonProp = (Map<String, Object>) props.get("polygon");
            assertEquals("array", polygonProp.get("type"));
            assertEquals(3, polygonProp.get("minItems"));
            @SuppressWarnings("unchecked")
            Map<String, Object> items = (Map<String, Object>) polygonProp.get("items");
            assertEquals("array", items.get("type"));
        }

        @Test
        void arrayWithObjectItems() {
            // Mimics batch_commands: array of {action, params} objects
            Map<String, Object> itemSchema = SchemaBuilder.create()
                    .requiredString("action", "Command name")
                    .object("params", "Command parameters")
                    .build();
            Map<String, Object> arrayDef = SchemaBuilder.arrayDef("Commands to execute")
                    .items(itemSchema)
                    .maxItems(50)
                    .build();
            Map<String, Object> schema = SchemaBuilder.create()
                    .requiredArray("commands", arrayDef)
                    .build();

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertTrue(required.contains("commands"));

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> cmdProp = (Map<String, Object>) props.get("commands");
            assertEquals(50, cmdProp.get("maxItems"));
            @SuppressWarnings("unchecked")
            Map<String, Object> itemDef = (Map<String, Object>) cmdProp.get("items");
            assertEquals("object", itemDef.get("type"));
        }

        @Test
        void requiredArray() {
            Map<String, Object> arrayDef = SchemaBuilder.arrayDef("Items")
                    .itemsOfType("number")
                    .build();
            Map<String, Object> schema = SchemaBuilder.create()
                    .requiredArray("data", arrayDef)
                    .build();
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertTrue(required.contains("data"));
        }

        @Test
        void arrayWithEmptyDescription() {
            Map<String, Object> arrayDef = SchemaBuilder.arrayDef("")
                    .itemsOfType("number")
                    .build();
            assertFalse(arrayDef.containsKey("description"));
        }

        @Test
        void arrayWithNoItems() {
            Map<String, Object> arrayDef = SchemaBuilder.arrayDef("Some array")
                    .build();
            assertEquals("array", arrayDef.get("type"));
            assertFalse(arrayDef.containsKey("items"));
        }
    }

    // ==================== Raw properties ====================

    @Nested
    class RawProperties {

        @Test
        void rawPropertyPassedThrough() {
            Map<String, Object> custom = new java.util.LinkedHashMap<>();
            custom.put("type", "integer");
            custom.put("description", "Color as RGB int");
            Map<String, Object> schema = SchemaBuilder.create()
                    .raw("color", custom)
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertSame(custom, props.get("color"));
        }

        @Test
        void requiredRaw() {
            Map<String, Object> custom = new java.util.LinkedHashMap<>();
            custom.put("type", "string");
            Map<String, Object> schema = SchemaBuilder.create()
                    .requiredRaw("id", custom)
                    .build();
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertTrue(required.contains("id"));
        }
    }

    // ==================== Complex scenario ====================

    @Nested
    class ComplexScenarios {

        @Test
        void deleteWallPattern() {
            // Mimics DeleteWallHandler.getSchema()
            Map<String, Object> schema = SchemaBuilder.create()
                    .requiredString("id", "Wall ID from get_state")
                    .build();

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertEquals(1, props.size());
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertEquals(Arrays.asList("id"), required);
        }

        @Test
        void createWallPattern() {
            // Mimics CreateWallHandler.getSchema()
            Map<String, Object> schema = SchemaBuilder.create()
                    .requiredNumber("xStart", "X start in cm")
                    .requiredNumber("yStart", "Y start in cm")
                    .requiredNumber("xEnd", "X end in cm")
                    .requiredNumber("yEnd", "Y end in cm")
                    .numberWithDefault("thickness", "Thickness in cm", 10)
                    .numberWithDefault("height", "Height in cm", 0)
                    .build();

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertEquals(6, props.size());
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertEquals(Arrays.asList("xStart", "yStart", "xEnd", "yEnd"), required);
        }

        @Test
        void modifyWallPattern() {
            // Mimics ModifyWallHandler.getSchema() — nullable + required
            Map<String, Object> schema = SchemaBuilder.create()
                    .requiredString("id", "Wall ID")
                    .number("height", "Wall height")
                    .nullableNumber("heightAtEnd", "Height at end")
                    .nullableString("color", "Color as hex")
                    .number("shininess", "Shininess value")
                    .build();

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertEquals(5, props.size());
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertEquals(Arrays.asList("id"), required);
        }

        @Test
        void setEnvironmentPattern() {
            // All optional, empty required
            Map<String, Object> schema = SchemaBuilder.create()
                    .string("groundColor", "Ground color")
                    .nullableString("groundTexture", "Ground texture or null")
                    .string("skyColor", "Sky color")
                    .number("wallsAlpha", "Transparency")
                    .enumProp("drawingMode", "Drawing mode", "FILL", "OUTLINE", "FILL_AND_OUTLINE")
                    .bool("allLevelsVisible", "All levels visible")
                    .build();

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertTrue(required.isEmpty());
        }

        @Test
        void buildReturnsCopy() {
            SchemaBuilder builder = SchemaBuilder.create()
                    .requiredString("id", "ID");
            Map<String, Object> schema1 = builder.build();
            Map<String, Object> schema2 = builder.build();
            assertNotSame(schema1.get("properties"), schema2.get("properties"));
            assertNotSame(schema1.get("required"), schema2.get("required"));
        }

        @Test
        void propertyOrderPreserved() {
            Map<String, Object> schema = SchemaBuilder.create()
                    .string("z", "Z")
                    .string("a", "A")
                    .string("m", "M")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            Object[] keys = props.keySet().toArray();
            assertEquals("z", keys[0]);
            assertEquals("a", keys[1]);
            assertEquals("m", keys[2]);
        }
    }
}
