package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.TexturesCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Централизованный поиск по каталогам мебели и текстур.
 *
 * <p>Алгоритм поиска мебели по имени:
 * <ol>
 *   <li>Если указан catalogId — exact match по piece.getId()</li>
 *   <li>Собрать exact matches (case-insensitive) и substring matches за один проход</li>
 *   <li>1 exact match → вернуть</li>
 *   <li>&gt;1 exact match → ошибка disambiguации с кандидатами</li>
 *   <li>0 exact, ≥1 substring → вернуть первый (обратная совместимость)</li>
 *   <li>0 substring → не найдено</li>
 * </ol>
 *
 * <p>Thread-safe: каталоги SH3D read-only после инициализации.
 */
public final class CatalogSearchUtil {

    private CatalogSearchUtil() {}

    // ======================== Result types ========================

    /** Result of a furniture catalog search: found piece, not-found, or disambiguation error. */
    public static final class FurnitureSearchResult {
        private final CatalogPieceOfFurniture found;
        private final String error;

        private FurnitureSearchResult(CatalogPieceOfFurniture found, String error) {
            this.found = found;
            this.error = error;
        }

        public static FurnitureSearchResult of(CatalogPieceOfFurniture piece) {
            return new FurnitureSearchResult(piece, null);
        }

        public static FurnitureSearchResult notFound() {
            return new FurnitureSearchResult(null, null);
        }

        public static FurnitureSearchResult error(String message) {
            return new FurnitureSearchResult(null, message);
        }

        public boolean isFound() { return found != null; }
        public boolean isError() { return error != null; }
        public CatalogPieceOfFurniture getFound() { return found; }
        public String getError() { return error; }
    }

    /** Result of a texture catalog search: found texture or not-found. */
    public static final class TextureSearchResult {
        private final CatalogTexture found;
        private final String error;

        private TextureSearchResult(CatalogTexture found, String error) {
            this.found = found;
            this.error = error;
        }

        public static TextureSearchResult of(CatalogTexture texture) {
            return new TextureSearchResult(texture, null);
        }

        public static TextureSearchResult notFound() {
            return new TextureSearchResult(null, null);
        }

        public boolean isFound() { return found != null; }
        public CatalogTexture getFound() { return found; }
    }

    // ======================== Furniture search ========================

    /**
     * Ищет мебель по catalogId или name.
     *
     * @param catalog   каталог мебели
     * @param name      поисковый запрос (case-insensitive), nullable если catalogId указан
     * @param catalogId ID элемента каталога (exact match, приоритет над name), nullable
     * @param filter    дополнительный фильтр (например isDoorOrWindow), nullable
     * @return результат: found, notFound или error (disambiguация)
     */
    public static FurnitureSearchResult findFurniture(
            FurnitureCatalog catalog,
            String name,
            String catalogId,
            Predicate<CatalogPieceOfFurniture> filter) {

        // 1. Поиск по catalogId (приоритет)
        if (catalogId != null && !catalogId.trim().isEmpty()) {
            for (FurnitureCategory category : catalog.getCategories()) {
                for (CatalogPieceOfFurniture piece : category.getFurniture()) {
                    if (filter != null && !filter.test(piece)) continue;
                    if (catalogId.equals(piece.getId())) {
                        return FurnitureSearchResult.of(piece);
                    }
                }
            }
            return FurnitureSearchResult.error(
                    "Furniture not found by catalogId: '" + catalogId + "'");
        }

        // 2. Поиск по name
        if (name == null || name.trim().isEmpty()) {
            return FurnitureSearchResult.error(
                    "Either 'name' or 'catalogId' must be provided");
        }

        String lowerQuery = name.toLowerCase();
        List<CatalogPieceOfFurniture> exactMatches = new ArrayList<>();
        List<CatalogPieceOfFurniture> substringMatches = new ArrayList<>();

        for (FurnitureCategory category : catalog.getCategories()) {
            for (CatalogPieceOfFurniture piece : category.getFurniture()) {
                if (filter != null && !filter.test(piece)) continue;
                String pieceName = piece.getName();
                if (pieceName == null) continue;

                String lowerName = pieceName.toLowerCase();
                if (lowerName.equals(lowerQuery)) {
                    exactMatches.add(piece);
                } else if (lowerName.contains(lowerQuery)) {
                    substringMatches.add(piece);
                }
            }
        }

        if (exactMatches.size() == 1) {
            return FurnitureSearchResult.of(exactMatches.get(0));
        }

        if (exactMatches.size() > 1) {
            return FurnitureSearchResult.error(
                    buildDisambiguationError(name, exactMatches));
        }

        if (!substringMatches.isEmpty()) {
            return FurnitureSearchResult.of(substringMatches.get(0));
        }

        // 3. Alias fallback: try alternative search terms
        String[] alternatives = CatalogAliases.getAlternatives(lowerQuery);
        if (alternatives != null) {
            for (String alt : alternatives) {
                String lowerAlt = alt.toLowerCase();
                for (FurnitureCategory category : catalog.getCategories()) {
                    for (CatalogPieceOfFurniture piece : category.getFurniture()) {
                        if (filter != null && !filter.test(piece)) continue;
                        String pieceName = piece.getName();
                        if (pieceName == null) continue;
                        if (pieceName.toLowerCase().contains(lowerAlt)) {
                            return FurnitureSearchResult.of(piece);
                        }
                    }
                }
            }
        }

        return FurnitureSearchResult.notFound();
    }

    // ======================== Texture search ========================

    /**
     * Ищет текстуру по имени (exact match, case-sensitive).
     * Опционально фильтрует по категории (substring, case-insensitive).
     *
     * @param catalog  каталог текстур
     * @param name     имя текстуры (exact match)
     * @param category фильтр по категории, nullable
     * @return результат: found или notFound
     */
    public static TextureSearchResult findTexture(
            TexturesCatalog catalog, String name, String category) {

        String lowerCategory = category != null ? category.toLowerCase() : null;

        for (TexturesCategory cat : catalog.getCategories()) {
            String catName = cat.getName();
            if (catName == null) continue;
            if (lowerCategory != null && !catName.toLowerCase().contains(lowerCategory)) continue;

            for (CatalogTexture texture : cat.getTextures()) {
                if (name.equals(texture.getName())) {
                    return TextureSearchResult.of(texture);
                }
            }
        }
        return TextureSearchResult.notFound();
    }

    // ======================== Helpers ========================

    private static String buildDisambiguationError(
            String query, List<CatalogPieceOfFurniture> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("Multiple furniture found matching '").append(query)
                .append("'. Specify 'catalogId' to select one:");
        for (CatalogPieceOfFurniture piece : candidates) {
            sb.append("\n  - name='").append(piece.getName()).append("'");
            String id = piece.getId();
            if (id != null) {
                sb.append(", catalogId='").append(id).append("'");
            }
            FurnitureCategory cat = piece.getCategory();
            if (cat != null) {
                sb.append(", category='").append(cat.getName()).append("'");
            }
            sb.append(String.format(", size=%.0fx%.0fx%.0f",
                    piece.getWidth(), piece.getDepth(), piece.getHeight()));
        }
        return sb.toString();
    }
}
