package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogLight;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.LightSource;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static com.sh3d.mcp.command.handler.TestFixtures.createAccessorWithCatalog;
import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;

/**
 * I13 (issue #13): светильники из каталога ставятся светильниками.
 * <ul>
 *   <li>изделие каталога, которое в SH3D — светильник ({@code CatalogLight}), командой
 *       {@code place_furniture} создаётся как {@code HomeLight} тем же конструктором, что и в самом SH3D
 *       ({@code new HomeLight(CatalogLight)}): источники света и светящиеся материалы — из изделия каталога,
 *       начальная мощность — та, что ставит конструктор SH3D 7.5 ({@code 0.5f}; по байткоду
 *       {@code HomeLight.<init>(String, Light, String[])}: {@code ldc 0.5f; putfield power});</li>
 *   <li>после сохранения и чтения файла светильник остаётся {@code HomeLight} с той же мощностью и теми же
 *       источниками;</li>
 *   <li>прочие пути, создающие изделие из каталога ({@code place_door_or_window}, генераторы,
 *       {@code duplicate_objects}, прочие), не превращают светильник в простую мебель;</li>
 *   <li>новых параметров и полей ответа пункт не добавляет.</li>
 * </ul>
 * Эталон «как в SH3D» — {@code FurnitureController.createHomePieceOfFurniture} 7.5: для
 * {@code piece instanceof Light} — {@code new HomeLight((Light) piece, propertyNames)}.
 *
 * <p>{@code place_door_or_window} ищет только двери и окна, а светильник в SH3D не бывает дверью
 * ({@code CatalogLight} и {@code CatalogDoorOrWindow} — разные классы), {@code generate_shape} строит
 * изделие не из каталога — этим путям светильник не достаётся.
 */
class CatalogLightTest {

    private static final String LAMP = "Halogen lamp";
    private static final LightSource[] SOURCES = {
            new LightSource(0.5f, 0.5f, 0.9f, 0xFFEECC, 0.05f),
            new LightSource(0.25f, 0.5f, 0.75f, 0xFFFFFF)};
    private static final String[] MATERIALS = {"bulb", "glow"};

    @TempDir
    Path dir;

    private CatalogLight catalogLight;
    private Home home;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        catalogLight = new CatalogLight("test#halogen", LAMP, "A lamp", null, new String[0], null, null,
                null, null, null, 30f, 30f, 180f, 0f, 0f, true, SOURCES, MATERIALS, null,
                new float[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, 0, null, null,
                true, true, true, true, null, null, null, null);
        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory lights = new FurnitureCategory("Lights");
        catalog.add(lights, catalogLight);
        catalog.add(new FurnitureCategory("Living room"), new CatalogPieceOfFurniture("test#chair", "Chair", null,
                null, null, 45f, 50f, 90f, 0f, true, new float[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, null, true,
                null, null));
        home = new Home();
        accessor = createAccessorWithCatalog(home, catalog);
    }

    // ==================== place_furniture ====================

    @Test
    void placeFurnitureByNameCreatesAHomeLight() {
        HomePieceOfFurniture placed = place("name", LAMP);

        assertLightAsSweetHome3DCreatesIt(placed);
    }

    @Test
    void placeFurnitureByCatalogIdCreatesAHomeLight() {
        HomePieceOfFurniture placed = place("catalogId", "test#halogen");

        assertLightAsSweetHome3DCreatesIt(placed);
    }

    @Test
    void placeFurnitureThroughBatchCommandsCreatesAHomeLight() {
        CommandRegistry registry = new CommandRegistry();
        registry.register("place_furniture", new PlaceFurnitureHandler());
        BatchCommandsHandler batch = new BatchCommandsHandler(registry, new CheckpointManager());
        registry.register("batch_commands", batch);
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("action", "place_furniture");
        command.put("params", map("name", LAMP, "x", 100.0, "y", 100.0));

        Response response = registry.dispatch(new Request("batch_commands",
                map("commands", Collections.singletonList(command))), accessor);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(1, home.getFurniture().size());
        assertLightAsSweetHome3DCreatesIt(home.getFurniture().get(0));
    }

    @Test
    void placedLightAnswersWithTheSameFieldsAsAnyPiece() {
        Response chair = new PlaceFurnitureHandler().execute(
                makeRequest("place_furniture", "name", "Chair", "x", 10.0, "y", 10.0), accessor);
        Response lamp = new PlaceFurnitureHandler().execute(
                makeRequest("place_furniture", "name", LAMP, "x", 10.0, "y", 10.0), accessor);

        assertTrue(chair.isOk(), chair.getMessage());
        assertTrue(lamp.isOk(), lamp.getMessage());
        assertEquals(new ArrayList<>(chair.getData().keySet()), new ArrayList<>(lamp.getData().keySet()),
                "I13: the light adds no new response fields");
    }

    // ==================== Сохранение и чтение ====================

    @Test
    void placedLightSurvivesSaveAndLoad() {
        HomePieceOfFurniture placed = place("name", LAMP);
        assertTrue(placed instanceof HomeLight, "I13: place_furniture must create a HomeLight, got "
                + placed.getClass().getSimpleName());

        HomePieceOfFurniture loaded = saveAndLoad();

        assertTrue(loaded instanceof HomeLight, "I13: after save_home and load_home the light must still be a "
                + "HomeLight, got " + loaded.getClass().getSimpleName());
        assertEquals(0.5f, ((HomeLight) loaded).getPower(), 0.0001f, "I13: the power survives save and load");
        assertSources(((HomeLight) loaded).getLightSources());
    }

    @Test
    void lightWithAnotherPowerSurvivesSaveAndLoad() {
        HomeLight light = new HomeLight(catalogLight);
        light.setPower(0.75f);
        home.addPieceOfFurniture(light);

        HomePieceOfFurniture loaded = saveAndLoad();

        assertTrue(loaded instanceof HomeLight, "I13: after save_home and load_home the light must still be a "
                + "HomeLight, got " + loaded.getClass().getSimpleName());
        assertEquals(0.75f, ((HomeLight) loaded).getPower(), 0.0001f);
        assertSources(((HomeLight) loaded).getLightSources());
        assertArrayEquals(MATERIALS, ((HomeLight) loaded).getLightSourceMaterialNames());
    }

    // ==================== Прочие пути ====================

    @Test
    void duplicateObjectsKeepsALight() {
        HomeLight light = addLight(0.8f);

        Response response = new DuplicateObjectsHandler().execute(
                makeRequest("duplicate_objects", "ids", Collections.singletonList(light.getId())), accessor);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(2, home.getFurniture().size());
        HomePieceOfFurniture copy = home.getFurniture().get(1);
        assertTrue(copy instanceof HomeLight, "I13: a duplicated light must be a HomeLight, got "
                + copy.getClass().getSimpleName());
        assertEquals(0.8f, ((HomeLight) copy).getPower(), 0.0001f);
        assertSources(((HomeLight) copy).getLightSources());
        assertArrayEquals(MATERIALS, ((HomeLight) copy).getLightSourceMaterialNames());
    }

    @Test
    void groupAndUngroupKeepALight() {
        HomeLight light = addLight(0.6f);
        HomePieceOfFurniture chair = place("name", "Chair");

        Response grouped = new GroupFurnitureHandler().execute(
                makeRequest("group_furniture", "ids", Arrays.asList(light.getId(), chair.getId())), accessor);
        assertTrue(grouped.isOk(), grouped.getMessage());
        HomeFurnitureGroup group = (HomeFurnitureGroup) home.getFurniture().get(0);
        HomePieceOfFurniture member = byName(group.getFurniture(), LAMP);
        assertTrue(member instanceof HomeLight, "I13: inside a group the light stays a HomeLight");

        Response ungrouped = new UngroupFurnitureHandler().execute(
                makeRequest("ungroup_furniture", "id", group.getId()), accessor);
        assertTrue(ungrouped.isOk(), ungrouped.getMessage());
        HomePieceOfFurniture back = byName(home.getFurniture(), LAMP);
        assertTrue(back instanceof HomeLight, "I13: after ungroup the light stays a HomeLight");
        assertEquals(0.6f, ((HomeLight) back).getPower(), 0.0001f);
    }

    @Test
    void restoreCheckpointKeepsALight() {
        addLight(0.9f);
        CheckpointManager manager = new CheckpointManager();
        assertTrue(new CheckpointHandler(manager).execute(makeRequest("checkpoint"), accessor).isOk());
        assertTrue(new ClearSceneHandler(manager).execute(makeRequest("clear_scene"), accessor).isOk());

        Response restored = new RestoreCheckpointHandler(manager).execute(
                makeRequest("restore_checkpoint", "id", 0), accessor);

        assertTrue(restored.isOk(), restored.getMessage());
        HomePieceOfFurniture back = byName(home.getFurniture(), LAMP);
        assertTrue(back instanceof HomeLight, "I13: a restored light stays a HomeLight");
        assertEquals(0.9f, ((HomeLight) back).getPower(), 0.0001f);
        assertSources(((HomeLight) back).getLightSources());
    }

    @Test
    void modifyFurnitureKeepsALight() {
        HomeLight light = addLight(0.7f);

        Response response = new ModifyFurnitureHandler().execute(makeRequest("modify_furniture",
                "id", light.getId(), "x", 120.0, "width", 40.0, "name", "Desk lamp"), accessor);

        assertTrue(response.isOk(), response.getMessage());
        HomePieceOfFurniture modified = home.getFurniture().get(0);
        assertTrue(modified instanceof HomeLight);
        assertEquals(0.7f, ((HomeLight) modified).getPower(), 0.0001f);
    }

    // ==================== Helpers ====================

    private HomePieceOfFurniture place(String key, String value) {
        Response response = new PlaceFurnitureHandler().execute(
                makeRequest("place_furniture", key, value, "x", 150.0, "y", 150.0), accessor);
        assertTrue(response.isOk(), response.getMessage());
        HomePieceOfFurniture piece = ObjectResolver.findFurniture(home, (String) response.getData().get("id"));
        assertNotNull(piece, "no piece with the id place_furniture reported");
        return piece;
    }

    private HomeLight addLight(float power) {
        HomeLight light = new HomeLight(catalogLight);
        light.setPower(power);
        light.setX(50);
        light.setY(50);
        home.addPieceOfFurniture(light);
        return light;
    }

    /** Что ставит SH3D: {@code new HomeLight(catalogLight)} — источники, материалы, мощность 0.5. */
    private void assertLightAsSweetHome3DCreatesIt(HomePieceOfFurniture placed) {
        assertTrue(placed instanceof HomeLight, "I13: a catalog light must be placed as a HomeLight, got "
                + placed.getClass().getSimpleName());
        HomeLight light = (HomeLight) placed;
        HomeLight expected = new HomeLight(catalogLight);
        assertEquals(expected.getPower(), light.getPower(), 0.0f, "I13: the initial power is SH3D's 0.5");
        assertEquals(0.5f, light.getPower(), 0.0f);
        assertSources(light.getLightSources());
        assertArrayEquals(MATERIALS, light.getLightSourceMaterialNames(),
                "I13: the light source materials come from the catalog piece");
        assertEquals(expected.getName(), light.getName());
        assertEquals(expected.getCatalogId(), light.getCatalogId());
        assertEquals(expected.getWidth(), light.getWidth(), 0.0f);
        assertEquals(expected.getDepth(), light.getDepth(), 0.0f);
        assertEquals(expected.getHeight(), light.getHeight(), 0.0f);
    }

    private static void assertSources(LightSource[] actual) {
        assertNotNull(actual, "I13: the light sources come from the catalog piece");
        assertEquals(SOURCES.length, actual.length, "I13: the light sources come from the catalog piece");
        for (int i = 0; i < SOURCES.length; i++) {
            assertEquals(SOURCES[i].getX(), actual[i].getX(), 0.0f, "source " + i);
            assertEquals(SOURCES[i].getY(), actual[i].getY(), 0.0f, "source " + i);
            assertEquals(SOURCES[i].getZ(), actual[i].getZ(), 0.0f, "source " + i);
            assertEquals(SOURCES[i].getColor(), actual[i].getColor(), "source " + i);
            assertEquals(SOURCES[i].getDiameter(), actual[i].getDiameter(), "source " + i);
        }
    }

    private HomePieceOfFurniture saveAndLoad() {
        Path file = dir.resolve("lights.sh3d");
        Response saved = new SaveHomeHandler().execute(makeRequest("save_home", "filePath", file.toString()), accessor);
        assertTrue(saved.isOk(), "save_home failed: " + saved.getMessage());
        Home target = new Home();
        Response loaded = new LoadHomeHandler().execute(makeRequest("load_home", "filePath", file.toString()),
                createAccessor(target));
        assertTrue(loaded.isOk(), "load_home failed: " + loaded.getMessage());
        return byName(target.getFurniture(), LAMP);
    }

    private static HomePieceOfFurniture byName(List<HomePieceOfFurniture> furniture, String name) {
        for (HomePieceOfFurniture piece : furniture) {
            if (name.equals(piece.getName())) {
                return piece;
            }
        }
        return fail("no piece named '" + name + "' among " + furniture.size() + " pieces");
    }

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
