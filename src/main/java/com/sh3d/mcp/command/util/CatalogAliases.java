package com.sh3d.mcp.command.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapping of standard English names to search aliases for the SH3D furniture catalog.
 *
 * <p>When a user searches for "bath" and the catalog contains "Siphon bathtub",
 * the normal substring search works fine. Aliases are a fallback for cases when
 * the English word does NOT appear as a substring in the catalog name
 * (e.g. localized catalogs or non-obvious naming).
 *
 * <p>Each alias maps to an array of alternative search terms that will be tried
 * as case-insensitive substring matches if the original query fails.
 */
public final class CatalogAliases {

    private CatalogAliases() {}

    /**
     * Alias -> alternative search terms (tried as substrings).
     * Keys are lowercase.
     */
    static final Map<String, String[]> ALIASES;

    static {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("door", new String[]{"door"});
        m.put("window", new String[]{"window"});
        m.put("bath", new String[]{"bath", "bathtub"});
        m.put("toilet", new String[]{"toilet", "wc"});
        m.put("table", new String[]{"table"});
        m.put("chair", new String[]{"chair"});
        m.put("sofa", new String[]{"sofa", "couch"});
        m.put("bed", new String[]{"bed"});
        m.put("wardrobe", new String[]{"wardrobe", "closet"});
        m.put("sink", new String[]{"sink"});
        m.put("lamp", new String[]{"lamp", "light"});
        m.put("shelf", new String[]{"shelf", "bookcase"});
        m.put("desk", new String[]{"desk"});
        m.put("oven", new String[]{"oven", "stove"});
        m.put("fridge", new String[]{"fridge", "refrigerator"});
        m.put("tv", new String[]{"tv", "television"});
        m.put("washing machine", new String[]{"washing machine"});
        m.put("dishwasher", new String[]{"dishwasher"});
        ALIASES = Collections.unmodifiableMap(m);
    }

    /**
     * Returns alternative search terms for the given query, or null if no alias exists.
     *
     * @param query lowercase search query
     * @return array of alternative substrings to try, or null
     */
    static String[] getAlternatives(String query) {
        return ALIASES.get(query);
    }
}
