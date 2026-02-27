package com.sh3d.mcp.command.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CatalogAliasesTest {

    @Test
    void testKnownAliasReturnsAlternatives() {
        String[] alts = CatalogAliases.getAlternatives("bath");
        assertNotNull(alts);
        assertEquals(2, alts.length);
        assertEquals("bath", alts[0]);
        assertEquals("bathtub", alts[1]);
    }

    @Test
    void testSingleAlternativeAlias() {
        String[] alts = CatalogAliases.getAlternatives("door");
        assertNotNull(alts);
        assertEquals(1, alts.length);
        assertEquals("door", alts[0]);
    }

    @Test
    void testUnknownAliasReturnsNull() {
        assertNull(CatalogAliases.getAlternatives("helicopter"));
    }

    @Test
    void testAllAliasesHaveNonEmptyAlternatives() {
        for (var entry : CatalogAliases.ALIASES.entrySet()) {
            assertNotNull(entry.getValue(), "Alias '" + entry.getKey() + "' has null alternatives");
            assertTrue(entry.getValue().length > 0,
                    "Alias '" + entry.getKey() + "' has empty alternatives");
        }
    }

    @Test
    void testAliasKeysAreLowercase() {
        for (String key : CatalogAliases.ALIASES.keySet()) {
            assertEquals(key.toLowerCase(), key,
                    "Alias key '" + key + "' must be lowercase");
        }
    }

    @Test
    void testMultiWordAlias() {
        String[] alts = CatalogAliases.getAlternatives("washing machine");
        assertNotNull(alts);
        assertEquals(1, alts.length);
        assertEquals("washing machine", alts[0]);
    }

    @Test
    void testMapIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () ->
                CatalogAliases.ALIASES.put("test", new String[]{"test"}));
    }
}
