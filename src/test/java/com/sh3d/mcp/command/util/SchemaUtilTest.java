package com.sh3d.mcp.command.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaUtilTest {

    // ==================== prop ====================

    @Nested
    class Prop {

        @Test
        void createsStringProperty() {
            Map<String, Object> result = SchemaUtil.prop("string", "A name");
            assertEquals("string", result.get("type"));
            assertEquals("A name", result.get("description"));
            assertEquals(2, result.size());
        }

        @Test
        void createsNumberProperty() {
            Map<String, Object> result = SchemaUtil.prop("number", "X coordinate");
            assertEquals("number", result.get("type"));
            assertEquals("X coordinate", result.get("description"));
        }

        @Test
        void createsBooleanProperty() {
            Map<String, Object> result = SchemaUtil.prop("boolean", "Is visible");
            assertEquals("boolean", result.get("type"));
            assertEquals("Is visible", result.get("description"));
        }

        @Test
        void createsIntegerProperty() {
            Map<String, Object> result = SchemaUtil.prop("integer", "Count");
            assertEquals("integer", result.get("type"));
            assertEquals("Count", result.get("description"));
        }

        @Test
        void doesNotContainDefaultKey() {
            Map<String, Object> result = SchemaUtil.prop("string", "A name");
            assertFalse(result.containsKey("default"));
        }

        @Test
        void doesNotContainEnumKey() {
            Map<String, Object> result = SchemaUtil.prop("string", "A name");
            assertFalse(result.containsKey("enum"));
        }

        @Test
        void returnsModifiableMap() {
            Map<String, Object> result = SchemaUtil.prop("string", "A name");
            // Should be modifiable — callers may add more keys
            result.put("extra", "value");
            assertEquals("value", result.get("extra"));
        }

        @Test
        void preservesInsertionOrder() {
            Map<String, Object> result = SchemaUtil.prop("string", "desc");
            // LinkedHashMap preserves insertion order
            Object[] keys = result.keySet().toArray();
            assertEquals("type", keys[0]);
            assertEquals("description", keys[1]);
        }

        @Test
        void handlesEmptyDescription() {
            Map<String, Object> result = SchemaUtil.prop("string", "");
            assertEquals("", result.get("description"));
        }

        @Test
        void handlesNullType() {
            Map<String, Object> result = SchemaUtil.prop(null, "desc");
            assertNull(result.get("type"));
        }

        @Test
        void handlesNullDescription() {
            Map<String, Object> result = SchemaUtil.prop("string", null);
            assertNull(result.get("description"));
        }
    }

    // ==================== propWithDefault ====================

    @Nested
    class PropWithDefault {

        @Test
        void createsPropertyWithStringDefault() {
            Map<String, Object> result = SchemaUtil.propWithDefault("string", "Mode", "auto");
            assertEquals("string", result.get("type"));
            assertEquals("Mode", result.get("description"));
            assertEquals("auto", result.get("default"));
            assertEquals(3, result.size());
        }

        @Test
        void createsPropertyWithNumericDefault() {
            Map<String, Object> result = SchemaUtil.propWithDefault("number", "Width", 100.0);
            assertEquals("number", result.get("type"));
            assertEquals("Width", result.get("description"));
            assertEquals(100.0, result.get("default"));
        }

        @Test
        void createsPropertyWithBooleanDefault() {
            Map<String, Object> result = SchemaUtil.propWithDefault("boolean", "Visible", true);
            assertEquals("boolean", result.get("type"));
            assertEquals(true, result.get("default"));
        }

        @Test
        void createsPropertyWithIntegerDefault() {
            Map<String, Object> result = SchemaUtil.propWithDefault("integer", "Count", 5);
            assertEquals("integer", result.get("type"));
            assertEquals(5, result.get("default"));
        }

        @Test
        void createsPropertyWithNullDefault() {
            Map<String, Object> result = SchemaUtil.propWithDefault("string", "Name", null);
            assertTrue(result.containsKey("default"));
            assertNull(result.get("default"));
        }

        @Test
        void preservesInsertionOrder() {
            Map<String, Object> result = SchemaUtil.propWithDefault("string", "desc", "val");
            Object[] keys = result.keySet().toArray();
            assertEquals("type", keys[0]);
            assertEquals("description", keys[1]);
            assertEquals("default", keys[2]);
        }

        @Test
        void containsTypeDescriptionAndDefault() {
            Map<String, Object> result = SchemaUtil.propWithDefault("number", "X pos", 0.0);
            assertTrue(result.containsKey("type"));
            assertTrue(result.containsKey("description"));
            assertTrue(result.containsKey("default"));
            assertEquals(3, result.size());
        }

        @Test
        void returnsModifiableMap() {
            Map<String, Object> result = SchemaUtil.propWithDefault("string", "desc", "val");
            result.put("extra", "ok");
            assertEquals("ok", result.get("extra"));
        }
    }

    // ==================== nullableProp ====================

    @Nested
    class NullableProp {

        @Test
        void createsNullableStringProperty() {
            Map<String, Object> result = SchemaUtil.nullableProp("string", "Optional name");
            Object type = result.get("type");
            assertInstanceOf(List.class, type);
            @SuppressWarnings("unchecked")
            List<String> typeList = (List<String>) type;
            assertEquals(2, typeList.size());
            assertEquals("string", typeList.get(0));
            assertEquals("null", typeList.get(1));
            assertEquals("Optional name", result.get("description"));
        }

        @Test
        void createsNullableNumberProperty() {
            Map<String, Object> result = SchemaUtil.nullableProp("number", "Height");
            @SuppressWarnings("unchecked")
            List<String> typeList = (List<String>) result.get("type");
            assertEquals(Arrays.asList("number", "null"), typeList);
        }

        @Test
        void createsNullableIntegerProperty() {
            Map<String, Object> result = SchemaUtil.nullableProp("integer", "Color");
            @SuppressWarnings("unchecked")
            List<String> typeList = (List<String>) result.get("type");
            assertEquals(Arrays.asList("integer", "null"), typeList);
        }

        @Test
        void containsOnlyTypeAndDescription() {
            Map<String, Object> result = SchemaUtil.nullableProp("string", "desc");
            assertEquals(2, result.size());
            assertTrue(result.containsKey("type"));
            assertTrue(result.containsKey("description"));
        }

        @Test
        void doesNotContainDefaultKey() {
            Map<String, Object> result = SchemaUtil.nullableProp("string", "desc");
            assertFalse(result.containsKey("default"));
        }

        @Test
        void preservesInsertionOrder() {
            Map<String, Object> result = SchemaUtil.nullableProp("string", "desc");
            Object[] keys = result.keySet().toArray();
            assertEquals("type", keys[0]);
            assertEquals("description", keys[1]);
        }

        @Test
        void returnsModifiableMap() {
            Map<String, Object> result = SchemaUtil.nullableProp("string", "desc");
            result.put("default", "none");
            assertEquals("none", result.get("default"));
        }
    }

    // ==================== enumProp (List) ====================

    @Nested
    class EnumPropList {

        @Test
        void createsEnumPropertyWithList() {
            List<String> values = Arrays.asList("top", "observer");
            Map<String, Object> result = SchemaUtil.enumProp("Camera type", values);

            assertEquals("string", result.get("type"));
            assertEquals("Camera type", result.get("description"));
            assertEquals(values, result.get("enum"));
            assertEquals(3, result.size());
        }

        @Test
        void createsEnumPropertyWithSingleValue() {
            List<String> values = Collections.singletonList("fixed");
            Map<String, Object> result = SchemaUtil.enumProp("Mode", values);

            @SuppressWarnings("unchecked")
            List<String> enumValues = (List<String>) result.get("enum");
            assertEquals(1, enumValues.size());
            assertEquals("fixed", enumValues.get(0));
        }

        @Test
        void createsEnumPropertyWithEmptyList() {
            List<String> values = Collections.emptyList();
            Map<String, Object> result = SchemaUtil.enumProp("None", values);

            @SuppressWarnings("unchecked")
            List<String> enumValues = (List<String>) result.get("enum");
            assertTrue(enumValues.isEmpty());
        }

        @Test
        void typeIsAlwaysString() {
            Map<String, Object> result = SchemaUtil.enumProp("desc", Arrays.asList("a", "b"));
            assertEquals("string", result.get("type"));
        }

        @Test
        void preservesInsertionOrder() {
            Map<String, Object> result = SchemaUtil.enumProp("desc", Arrays.asList("a", "b"));
            Object[] keys = result.keySet().toArray();
            assertEquals("type", keys[0]);
            assertEquals("description", keys[1]);
            assertEquals("enum", keys[2]);
        }

        @Test
        void preservesValueOrder() {
            List<String> values = Arrays.asList("third", "first", "second");
            Map<String, Object> result = SchemaUtil.enumProp("desc", values);

            @SuppressWarnings("unchecked")
            List<String> enumValues = (List<String>) result.get("enum");
            assertEquals("third", enumValues.get(0));
            assertEquals("first", enumValues.get(1));
            assertEquals("second", enumValues.get(2));
        }

        @Test
        void returnsModifiableMap() {
            Map<String, Object> result = SchemaUtil.enumProp("desc", Arrays.asList("a"));
            result.put("extra", "ok");
            assertEquals("ok", result.get("extra"));
        }
    }

    // ==================== enumProp (Varargs) ====================

    @Nested
    class EnumPropVarargs {

        @Test
        void createsEnumPropertyWithVarargs() {
            Map<String, Object> result = SchemaUtil.enumProp("Camera type", "top", "observer");

            assertEquals("string", result.get("type"));
            assertEquals("Camera type", result.get("description"));

            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) result.get("enum");
            assertEquals(2, values.size());
            assertEquals("top", values.get(0));
            assertEquals("observer", values.get(1));
        }

        @Test
        void createsEnumPropertyWithSingleVararg() {
            Map<String, Object> result = SchemaUtil.enumProp("Mode", "auto");

            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) result.get("enum");
            assertEquals(1, values.size());
            assertEquals("auto", values.get(0));
        }

        @Test
        void createsEnumPropertyWithNoVarargs() {
            Map<String, Object> result = SchemaUtil.enumProp("Empty");

            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) result.get("enum");
            assertTrue(values.isEmpty());
        }

        @Test
        void varargsAndListProduceSameResult() {
            Map<String, Object> fromVarargs = SchemaUtil.enumProp("desc", "a", "b", "c");
            Map<String, Object> fromList = SchemaUtil.enumProp("desc", Arrays.asList("a", "b", "c"));

            assertEquals(fromVarargs.get("type"), fromList.get("type"));
            assertEquals(fromVarargs.get("description"), fromList.get("description"));
            assertEquals(fromVarargs.get("enum"), fromList.get("enum"));
        }

        @Test
        void preservesVarargOrder() {
            Map<String, Object> result = SchemaUtil.enumProp("desc", "z", "a", "m");

            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) result.get("enum");
            assertEquals("z", values.get(0));
            assertEquals("a", values.get(1));
            assertEquals("m", values.get(2));
        }

        @Test
        void typeIsAlwaysString() {
            Map<String, Object> result = SchemaUtil.enumProp("desc", "val1", "val2");
            assertEquals("string", result.get("type"));
        }
    }

    // ==================== Cross-method comparisons ====================

    @Nested
    class CrossMethodComparisons {

        @Test
        void propAndPropWithDefaultShareTypeAndDescription() {
            Map<String, Object> simple = SchemaUtil.prop("string", "Name");
            Map<String, Object> withDefault = SchemaUtil.propWithDefault("string", "Name", "default");

            assertEquals(simple.get("type"), withDefault.get("type"));
            assertEquals(simple.get("description"), withDefault.get("description"));
        }

        @Test
        void nullablePropHasArrayTypeWhilePropHasStringType() {
            Map<String, Object> simple = SchemaUtil.prop("string", "Name");
            Map<String, Object> nullable = SchemaUtil.nullableProp("string", "Name");

            assertInstanceOf(String.class, simple.get("type"));
            assertInstanceOf(List.class, nullable.get("type"));
        }

        @Test
        void enumPropHasStringTypeNotCustomType() {
            Map<String, Object> enumResult = SchemaUtil.enumProp("desc", "a", "b");
            assertEquals("string", enumResult.get("type"));
        }

        @Test
        void allMethodsReturnMapsWithDescriptionKey() {
            assertNotNull(SchemaUtil.prop("string", "d").get("description"));
            assertNotNull(SchemaUtil.propWithDefault("string", "d", "v").get("description"));
            assertNotNull(SchemaUtil.nullableProp("string", "d").get("description"));
            assertNotNull(SchemaUtil.enumProp("d", "a").get("description"));
        }
    }
}
