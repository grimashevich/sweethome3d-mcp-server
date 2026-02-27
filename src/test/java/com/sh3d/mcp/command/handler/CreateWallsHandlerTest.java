package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sh3d.mcp.bridge.ObjectResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class CreateWallsHandlerTest {

    private CreateWallsHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new CreateWallsHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testNegativeThicknessReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 100.0);
        params.put("height", 100.0);
        params.put("thickness", -5.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("thickness"));
        assertEquals(0, home.getWalls().size());
    }

    @Test
    void testCreateRoomAdds4Walls() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 500.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals(4, resp.getData().get("wallsCreated"));

        Collection<Wall> walls = home.getWalls();
        assertEquals(4, walls.size());
    }

    @Test
    void testWallCoordinates() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 100.0);
        params.put("y", 200.0);
        params.put("width", 400.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        List<Wall> walls = new ArrayList<>(home.getWalls());
        // Порядок добавления: w1(AB), w2(BC), w3(CD), w4(DA)
        // Ищем стену по координатам — A(100,200)→B(500,200)
        Wall top = findWall(walls, 100, 200, 500, 200);
        assertNotNull(top, "Top wall A→B not found");

        // B(500,200)→C(500,500)
        Wall right = findWall(walls, 500, 200, 500, 500);
        assertNotNull(right, "Right wall B→C not found");

        // C(500,500)→D(100,500)
        Wall bottom = findWall(walls, 500, 500, 100, 500);
        assertNotNull(bottom, "Bottom wall C→D not found");

        // D(100,500)→A(100,200)
        Wall left = findWall(walls, 100, 500, 100, 200);
        assertNotNull(left, "Left wall D→A not found");
    }

    @Test
    void testWallsConnectedInLoop() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 400.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        List<Wall> walls = new ArrayList<>(home.getWalls());
        Wall top = findWall(walls, 0, 0, 400, 0);
        assertNotNull(top);

        // Обход по контуру через wallAtEnd
        Wall current = top;
        int count = 0;
        do {
            assertNotNull(current.getWallAtEnd(), "Wall chain broken at step " + count);
            current = current.getWallAtEnd();
            count++;
        } while (current != top && count < 10);

        assertEquals(4, count, "Loop should contain exactly 4 walls");
    }

    @Test
    void testDefaultThickness() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 100.0);
        params.put("height", 100.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        for (Wall w : home.getWalls()) {
            assertEquals(10.0f, w.getThickness(), 0.01f);
        }
    }

    @Test
    void testCustomThickness() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 100.0);
        params.put("height", 100.0);
        params.put("thickness", 25.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        for (Wall w : home.getWalls()) {
            assertEquals(25.0f, w.getThickness(), 0.01f);
        }
    }

    @Test
    void testNegativeWidthReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", -100.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("width"));
        assertEquals(0, home.getWalls().size());
    }

    @Test
    void testZeroHeightReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 100.0);
        params.put("height", 0.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("height"));
        assertEquals(0, home.getWalls().size());
    }

    @Test
    void testMissingRequiredParamThrows() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        // width отсутствует

        Request req = new Request("create_walls", params);
        assertThrows(IllegalArgumentException.class, () -> handler.execute(req, accessor));
    }

    @Test
    void testResponseMessage() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 500.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals("Room 500x300 created", resp.getData().get("message"));
    }

    // --- Wall height tests ---

    @Test
    void testDefaultWallHeightFallback250() {
        // Без параметра wallHeight + Home.getWallHeight() == 0 → 250 см
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 400.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        for (Wall w : home.getWalls()) {
            assertEquals(250.0f, w.getHeight(), 0.01f,
                    "Default wall height should be 250 cm");
        }
    }

    @Test
    void testWallHeightFromHomeDefault() {
        // Home(320) → стены получают 320 см
        home = new Home(320f);
        accessor = createAccessor(home);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 400.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        for (Wall w : home.getWalls()) {
            assertEquals(320.0f, w.getHeight(), 0.01f,
                    "Wall height should come from Home default");
        }
    }

    @Test
    void testExplicitWallHeightParameter() {
        // wallHeight=400 → все стены 400 см
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 400.0);
        params.put("height", 300.0);
        params.put("wallHeight", 400.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        for (Wall w : home.getWalls()) {
            assertEquals(400.0f, w.getHeight(), 0.01f,
                    "Wall height should match explicit wallHeight parameter");
        }
    }

    @Test
    void testExplicitWallHeightOverridesHomeDefault() {
        // Home default=320, wallHeight=400 → параметр приоритетнее
        home = new Home(320f);
        accessor = createAccessor(home);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 400.0);
        params.put("height", 300.0);
        params.put("wallHeight", 400.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        for (Wall w : home.getWalls()) {
            assertEquals(400.0f, w.getHeight(), 0.01f,
                    "Explicit wallHeight should override Home default");
        }
    }

    @Test
    void testWallHeightZeroFallsBackToHomeDefault() {
        // wallHeight=0 (явно) + Home default=300 → 300 см
        home = new Home(300f);
        accessor = createAccessor(home);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 400.0);
        params.put("height", 300.0);
        params.put("wallHeight", 0.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        for (Wall w : home.getWalls()) {
            assertEquals(300.0f, w.getHeight(), 0.01f,
                    "wallHeight=0 should fall back to Home default");
        }
    }

    @Test
    void testAllFourWallsHaveSameHeight() {
        // Все 4 стены должны иметь одинаковую высоту
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 500.0);
        params.put("height", 400.0);
        params.put("wallHeight", 275.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        List<Wall> walls = new ArrayList<>(home.getWalls());
        assertEquals(4, walls.size());

        Float firstHeight = walls.get(0).getHeight();
        assertNotNull(firstHeight, "Wall height should not be null");
        for (int i = 1; i < walls.size(); i++) {
            assertEquals(firstHeight, walls.get(i).getHeight(), 0.01f,
                    "Wall " + i + " height should match wall 0");
        }
    }

    // --- Wall IDs in response ---

    @Test
    @SuppressWarnings("unchecked")
    void testResponseContainsWallIds() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 500.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<String> wallIds = (List<String>) resp.getData().get("wallIds");
        assertNotNull(wallIds, "Response must contain wallIds");
        assertEquals(4, wallIds.size(), "wallIds must have 4 elements");
        for (String id : wallIds) {
            assertNotNull(id);
            assertFalse(id.isEmpty());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testWallIdsMatchHomeState() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 100.0);
        params.put("y", 200.0);
        params.put("width", 400.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        List<String> wallIds = (List<String>) resp.getData().get("wallIds");

        // top: A(100,200)->B(500,200)
        Wall top = ObjectResolver.findWall(home, wallIds.get(0));
        assertNotNull(top);
        assertEquals(100f, top.getXStart(), 0.01f);
        assertEquals(200f, top.getYStart(), 0.01f);
        assertEquals(500f, top.getXEnd(), 0.01f);

        // right: B(500,200)->C(500,500)
        Wall right = ObjectResolver.findWall(home, wallIds.get(1));
        assertNotNull(right);
        assertEquals(500f, right.getXStart(), 0.01f);
        assertEquals(200f, right.getYStart(), 0.01f);
        assertEquals(500f, right.getYEnd(), 0.01f);

        // bottom: C(500,500)->D(100,500)
        Wall bottom = ObjectResolver.findWall(home, wallIds.get(2));
        assertNotNull(bottom);
        assertEquals(500f, bottom.getXStart(), 0.01f);
        assertEquals(500f, bottom.getYStart(), 0.01f);

        // left: D(100,500)->A(100,200)
        Wall left = ObjectResolver.findWall(home, wallIds.get(3));
        assertNotNull(left);
        assertEquals(100f, left.getXStart(), 0.01f);
        assertEquals(500f, left.getYStart(), 0.01f);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testWallIdsWithPreexistingWalls() {
        // Добавляем 2 стены до вызова create_walls
        Wall pre1 = new Wall(0, 0, 100, 0, 10);
        Wall pre2 = new Wall(100, 0, 100, 100, 10);
        home.addWall(pre1);
        home.addWall(pre2);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 200.0);
        params.put("y", 200.0);
        params.put("width", 300.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        List<String> wallIds = (List<String>) resp.getData().get("wallIds");
        // IDs should be different from pre-existing walls
        for (String id : wallIds) {
            assertNotEquals(pre1.getId(), id);
            assertNotEquals(pre2.getId(), id);
        }
        assertEquals(6, home.getWalls().size());
    }

    private Wall findWall(List<Wall> walls, float xs, float ys, float xe, float ye) {
        for (Wall w : walls) {
            if (Math.abs(w.getXStart() - xs) < 0.01f
                    && Math.abs(w.getYStart() - ys) < 0.01f
                    && Math.abs(w.getXEnd() - xe) < 0.01f
                    && Math.abs(w.getYEnd() - ye) < 0.01f) {
                return w;
            }
        }
        return null;
    }
}
