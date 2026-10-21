package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;

/** B2 review 3: missing lookAt coordinates must never leave a partially moved camera. */
class SetCameraIncompleteLookAtTest {

    static Stream<Arguments> incompleteTargets() {
        return Stream.of(false, true).flatMap(observer -> Stream.of(
                Arguments.of(observer, "x", point("y", 200.0)),
                Arguments.of(observer, "y", point("x", 200.0)),
                Arguments.of(observer, "x", point("z", 50.0))));
    }

    @ParameterizedTest(name = "observer active = {0}, missing {1}, lookAt = {2}")
    @MethodSource("incompleteTargets")
    void incompleteLookAtIsRejectedBeforeAnyCameraChange(boolean observerActive, String missing,
                                                       Map<String, Object> lookAt) {
        Home home = new Home();
        if (observerActive) {
            home.setCamera(home.getObserverCamera());
        }
        HomeAccessor accessor = createAccessor(home);
        String before = ModelDump.of(home);
        CommandRegistry registry = new CommandRegistry();
        registry.register("set_camera", new SetCameraHandler());
        Request request = makeRequest("set_camera", "mode", "observer", "x", 1234.0,
                "y", 2345.0, "z", 180.0, "fov", 75.0, "lookAt", lookAt);

        Response response = registry.dispatch(request, accessor);

        assertAll(
                () -> assertTrue(response.isError()),
                () -> assertTrue(response.getMessage() != null && response.getMessage().contains("lookAt")
                                && response.getMessage().contains(missing)
                                && !response.getMessage().startsWith("Internal error"),
                        "Expected a clear validation error: " + response.getMessage()),
                () -> assertEquals("", ModelDump.diff(before, ModelDump.of(home)),
                        "Neither observer state nor the active camera may change"));
    }

    private static Map<String, Object> point(String coordinate, double value) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put(coordinate, value);
        return point;
    }
}
