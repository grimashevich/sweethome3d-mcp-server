package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.TextStyle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Также запускать с SH3D_E2E_JAVA_OPTS="-Duser.language=tr -Duser.country=TR".
 * Для SH3D 7.5 нужен TurkishLocaleFixture: иначе приложение заменяет неподдерживаемый tr на en.
 * Полная команда запуска и результаты — в HANDOFF-TASK.md.
 */
class LocaleLiveIT extends LiveTestBase {

    @Test
    void uppercaseQueryFindsTheSameCatalogItems() {
        List<Map<String, Object>> all = objects(mcp.call("list_furniture_catalog"), "furniture");
        Map<String, Object> item = withAsciiI(all, "name");
        String query = str(item, "name").toUpperCase(Locale.ROOT);
        Set<String> lower = ids(objects(mcp.call("list_furniture_catalog",
                "query", str(item, "name").toLowerCase(Locale.ROOT)), "furniture"));
        assertFalse(lower.isEmpty(), "the live item must be found by its own name");
        assertEquals(lower, ids(objects(mcp.call("list_furniture_catalog", "query", query), "furniture")),
                "catalog query case folding must not depend on the server's default locale");
    }

    @Test
    void uppercaseCategoryFindsTheSameCatalogItems() {
        List<Map<String, Object>> all = objects(mcp.call("list_furniture_catalog"), "furniture");
        String category = str(withAsciiI(all, "category"), "category");
        Set<String> expected = ids(all.stream().filter(p -> category.equals(p.get("category")))
                .collect(Collectors.toList()));
        assertFalse(expected.isEmpty());
        assertEquals(expected, ids(objects(mcp.call("list_furniture_catalog",
                "category", category.toUpperCase(Locale.ROOT)), "furniture")),
                "an uppercase category must work in tr_TR too");
    }

    @Test
    void uppercaseDiningQueryWorksRegardlessOfDefaultLocale() {
        // DINING — заданная карточкой строка поиска; имена изделий берём из живого каталога.
        List<Map<String, Object>> all = objects(mcp.call("list_furniture_catalog"), "furniture");
        Set<String> expected = ids(all.stream()
                .filter(p -> str(p, "name").toUpperCase(Locale.ROOT).contains("DINING"))
                .collect(Collectors.toList()));
        assertEquals(expected, ids(objects(mcp.call("list_furniture_catalog", "query", "DINING"), "furniture")),
                "DINING must find exactly the matching live names; zero matches is valid too");
    }

    @Test
    void lowercaseRightAlignmentIsSavedAsRightAlignedText() {
        // Уточнение: tools/list перечисляет "LEFT", "CENTER", "RIGHT"; принимается и нижний регистр "right".
        String id = str(mcp.call("add_label", "text", "E2E right aligned", "x", 120, "y", 80,
                "fontSize", 24, "alignment", "right"), "id");
        Home home = saveAndReadHome("right-aligned-label");
        assertEquals(1, home.getLabels().size());
        Label label = home.getLabels().iterator().next();
        assertEquals(id, label.getId());
        assertNotNull(label.getStyle());
        assertEquals(TextStyle.Alignment.RIGHT, label.getStyle().getAlignment(),
                "right must be parsed independently of the Turkish dotted/dotless I");
        assertEquals(24, label.getStyle().getFontSize(), MODEL_EPS);
        assertEquals(120, label.getX(), MODEL_EPS);
        assertEquals(80, label.getY(), MODEL_EPS);
    }

    private static Map<String, Object> withAsciiI(List<Map<String, Object>> all, String key) {
        return all.stream().filter(p -> str(p, key).toLowerCase(Locale.ROOT).contains("i")).findFirst()
                .orElseThrow(() -> new AssertionError("No live catalog " + key + " contains ASCII i"));
    }

    private static Set<String> ids(List<Map<String, Object>> furniture) {
        return furniture.stream().map(p -> str(p, "catalogId")).collect(Collectors.toSet());
    }
}
