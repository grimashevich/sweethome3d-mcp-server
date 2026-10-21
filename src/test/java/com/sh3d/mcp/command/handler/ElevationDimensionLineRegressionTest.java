package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;

/** B2 review 1: coincident plan coordinates do not make an elevation dimension empty. */
class ElevationDimensionLineRegressionTest {

    private Home home;
    private HomeAccessor accessor;
    private DimensionLine line;

    @BeforeEach
    void setUp() {
        home = new Home();
        accessor = createAccessor(home);
        line = new DimensionLine(100, 100, 0, 100, 100, 200, 20);
        home.addDimensionLine(line);
        assertTrue(line.isElevationDimensionLine());
    }

    @Test
    void elevationDimensionAcceptsAnOffsetEdit() {
        Response response = new ModifyDimensionLineHandler().execute(makeRequest("modify_dimension_line",
                "id", line.getId(), "offset", 30.0), accessor);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(30f, line.getOffset());
        assertElevationDimensionAt(100, 100);
    }

    @Test
    void elevationDimensionCanBeMovedWithBothEndpoints() {
        Response response = new ModifyDimensionLineHandler().execute(makeRequest("modify_dimension_line",
                "id", line.getId(), "xStart", 150.0, "yStart", 175.0,
                "xEnd", 150.0, "yEnd", 175.0, "offset", 40.0), accessor);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(40f, line.getOffset());
        assertElevationDimensionAt(150, 175);
    }

    @Test
    void coincidentEndpointsAtTheSameNonzeroElevationAreStillRejected() {
        DimensionLine empty = new DimensionLine(100, 100, 200, 100, 100, 200, 20);
        home.addDimensionLine(empty);
        String before = ModelDump.of(home);

        Response response = new ModifyDimensionLineHandler().execute(makeRequest("modify_dimension_line",
                "id", empty.getId(), "offset", 30.0), accessor);

        assertTrue(response.isError());
        assertEquals("", ModelDump.diff(before, ModelDump.of(home)));
    }

    private void assertElevationDimensionAt(float x, float y) {
        assertEquals(x, line.getXStart());
        assertEquals(x, line.getXEnd());
        assertEquals(y, line.getYStart());
        assertEquals(y, line.getYEnd());
        assertEquals(0f, line.getElevationStart());
        assertEquals(200f, line.getElevationEnd());
        assertEquals(200f, line.getLength());
        assertTrue(line.isElevationDimensionLine());
        assertSame(line, home.getDimensionLines().iterator().next());
    }
}
