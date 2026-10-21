package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.DoorOrWindow;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeShelfUnit;
import com.eteks.sweethome3d.model.Light;
import com.eteks.sweethome3d.model.PieceOfFurniture;
import com.eteks.sweethome3d.model.ShelfUnit;

/** Creates the same furniture subtype as SH3D's FurnitureController. */
public final class HomeFurnitureFactory {

    private HomeFurnitureFactory() {
    }

    public static HomePieceOfFurniture create(PieceOfFurniture piece) {
        if (piece instanceof DoorOrWindow) {
            return new HomeDoorOrWindow((DoorOrWindow) piece);
        }
        if (piece instanceof Light) {
            return new HomeLight((Light) piece);
        }
        if (piece instanceof ShelfUnit) {
            return new HomeShelfUnit((ShelfUnit) piece);
        }
        return new HomePieceOfFurniture(piece);
    }
}
