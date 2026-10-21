package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Sash;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.TexturesCategory;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Дом, в котором есть объект каждого вида, и каталоги мебели и текстур — чтобы любая команда,
 * меняющая сцену, нашла, к чему применяться: две соединённые стены, комната, стол, дверь в стене,
 * размерная линия, надпись, полилиния; в каталоге — стул, дверь и текстура кирпича.
 */
final class EverythingScene {

    static final String CHAIR = "Chair";
    static final String DOOR = "Door";
    static final String TEXTURE = "Brick";

    final Home home = new Home();
    final HomeAccessor accessor;
    final Wall wall;
    final Wall otherWall;
    final Room room;
    final HomePieceOfFurniture table;
    final HomeDoorOrWindow door;
    final DimensionLine dimensionLine;
    final Label label;
    final Polyline polyline;

    EverythingScene() {
        wall = new Wall(0, 0, 500, 0, 10, 250);
        otherWall = new Wall(500, 0, 500, 400, 10, 250);
        wall.setWallAtEnd(otherWall);
        otherWall.setWallAtStart(wall);
        home.addWall(wall);
        home.addWall(otherWall);

        room = new Room(new float[][] {{0, 0}, {500, 0}, {500, 400}, {0, 400}});
        room.setName("Living");
        home.addRoom(room);

        table = new HomePieceOfFurniture(new CatalogPieceOfFurniture("Table", null, null, 120f, 80f, 75f, true, false));
        table.setX(200);
        table.setY(200);
        home.addPieceOfFurniture(table);

        door = new HomeDoorOrWindow(catalogDoor("test#home-door", "Home door"));
        door.setX(250);
        door.setY(0);
        home.addPieceOfFurniture(door);

        dimensionLine = new DimensionLine(0, -50, 500, -50, 20);
        home.addDimensionLine(dimensionLine);

        label = new Label("Hall", 100, 300);
        home.addLabel(label);

        polyline = new Polyline(new float[][] {{0, 450}, {200, 450}});
        home.addPolyline(polyline);

        accessor = new HomeAccessor(home, preferences());
    }

    static CatalogDoorOrWindow catalogDoor(String id, String name) {
        return new CatalogDoorOrWindow(id, name, null, null, null,
                90f, 12f, 210f, 0f, false, 1f, 0f,
                new Sash[] {new Sash(0f, 1f, 1f, 0f, (float) Math.toRadians(-90))},
                null, null, true, null, null);
    }

    private static UserPreferences preferences() {
        FurnitureCatalog furniture = new FurnitureCatalog();
        FurnitureCategory category = new FurnitureCategory("Living room");
        furniture.add(category, new CatalogPieceOfFurniture("test#chair", CHAIR, null, null, null,
                45f, 50f, 90f, 0f, true, new float[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, null, true, null, null));
        furniture.add(new FurnitureCategory("Doors and windows"), catalogDoor("test#door", DOOR));

        TexturesCatalog textures = new TexturesCatalog();
        textures.add(new TexturesCategory("Walls"), new CatalogTexture(TEXTURE, null, 20f, 10f));

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(furniture);
        when(prefs.getTexturesCatalog()).thenReturn(textures);
        when(prefs.getNewWallHeight()).thenReturn(250f);
        when(prefs.getNewWallThickness()).thenReturn(10f);
        when(prefs.getNewFloorThickness()).thenReturn(12f);
        return prefs;
    }
}
