# Contributing to SH3D MCP Plugin

Thank you for your interest in contributing!

## Reporting Bugs

Please open a [GitHub Issue](../../issues) and include:

- Sweet Home 3D version (Help → About)
- Java version (`java -version`)
- OS and version
- Steps to reproduce
- Expected vs actual behavior
- Relevant log output if available

## Development Setup

### Prerequisites

- JDK 11 or later for the build and test runner
- Maven 3.9+
- Sweet Home 3D 7.x (tested with 7.5), running on Java 8 or newer; its bundled Java 8 works

### 1. Clone the repository

```bash
git clone https://github.com/grimashevich/sweethome3d-mcp-server.git
cd sweethome3d-mcp-server
```

### 2. Get SweetHome3D.jar

The `SweetHome3D.jar` dependency is not included in the repository (46 MB). Run the setup script to obtain it:

**Linux / macOS / Git Bash:**
```bash
./scripts/setup-dev.sh
```

**Windows (CMD):**
```bat
scripts\setup-dev.bat
```

The script first looks for an existing Sweet Home 3D installation on your machine. If not found, it downloads the JAR from SourceForge automatically.

### 3. Build

```bash
./mvnw clean package
```

The plugin artifact is created at `target/sh3d-mcp-plugin-1.2.0.sh3p`.

### 4. Run tests

```bash
./mvnw test
```

### 5. Deploy to Sweet Home 3D (optional)

```bash
# Linux
cp target/sh3d-mcp-plugin-1.2.0.sh3p ~/.eteks/sweethome3d/plugins/

# macOS
cp target/sh3d-mcp-plugin-1.2.0.sh3p "$HOME/Library/Application Support/eTeks/Sweet Home 3D/plugins/"

# Windows (Git Bash)
cp target/sh3d-mcp-plugin-1.2.0.sh3p "$APPDATA/eTeks/Sweet Home 3D/plugins/"
```

Restart Sweet Home 3D. The plugin activates automatically and starts the MCP server on port 9877.

## End-to-end tests

The unit tests work on Sweet Home 3D's model classes without the application. The end-to-end tests
(`*LiveIT` classes in `src/test/java/com/sh3d/mcp/e2e`) start a real Sweet Home 3D with the freshly built
plugin and drive it over MCP the way an assistant does.

```bash
bash scripts/e2e.sh
```

The script builds the `.sh3p`, starts Sweet Home 3D from `lib/SweetHome3D.jar` on a virtual X display, waits
for the MCP server, runs the tests (`./mvnw -Pe2e verify`) and shuts everything down.
The plugin is installed into a throw-away home directory and the MCP port is picked at random, so
your own Sweet Home 3D settings are not touched and an instance you have open keeps running undisturbed.

A plain `./mvnw verify` runs unit tests and the coverage check, without starting the application.
It still needs `lib/SweetHome3D.jar` to compile and test, and on Linux some Java3D tests need a display:

```bash
xvfb-run --auto-servernum ./mvnw verify --no-transfer-progress
```

### What you need

- Linux with `bash` and `curl`
- JDK 11 or later for Maven and tests; Sweet Home 3D may use a separate Java 8 runtime via `SH3D_E2E_JAVA`
- `lib/SweetHome3D.jar` (see [Get SweetHome3D.jar](#2-get-sweethome3djar))
- Xvfb — on Debian/Ubuntu: `sudo apt-get install xvfb`

### Options

Anything after the script name goes to Maven:

```bash
bash scripts/e2e.sh -Dit.test=WallsLiveIT                          # one class
bash scripts/e2e.sh -Dit.test='DimensionLinesLiveIT#deleteRemovesOnlyThatLine'
```

| Environment variable | Meaning |
|----------------------|---------|
| `SH3D_E2E_JAVA` | `java` binary that runs Sweet Home 3D (default: `$JAVA_HOME/bin/java`, else `java`) |
| `SH3D_E2E_JAVA_OPTS` | Extra JVM options for Sweet Home 3D, e.g. `-Duser.language=fr` |
| `SH3D_E2E_PLUGIN` | Test this ready-made `.sh3p` instead of building one |
| `SH3D_E2E_PORT` | MCP port (default: a free one is picked) |
| `SH3D_E2E_STARTUP_TIMEOUT` | Seconds to wait for the MCP server (default: 120) |
| `SH3D_E2E_DISPLAY` | Use this X display instead of Xvfb, e.g. `:0` to watch the run |
| `SH3D_E2E_TOKEN_FILE` | File with a bearer token; the tests send it as `Authorization: Bearer …` |
| `SH3D_E2E_KEEP` | Set to `1` to keep the throw-away home directory |

### Application JVM and locale

Keep `JAVA_HOME` on JDK 11+ for Maven and the test runner. Select a separate Java 8
binary for Sweet Home 3D with `SH3D_E2E_JAVA` (replace the placeholder path):

```bash
SH3D_E2E_JAVA="<jdk8>/bin/java" bash scripts/e2e.sh
SH3D_E2E_JAVA_OPTS="-Duser.language=fr -Duser.country=FR" bash scripts/e2e.sh
```

`SH3D_E2E_JAVA_OPTS` changes the application JVM only; the script does not force a locale.
Catalog names follow Sweet Home 3D's language. Sweet Home 3D 7.5 falls back to English
for unsupported languages unless a matching language pack is installed.

### When a test fails

| Where | What |
|-------|------|
| `target/e2e-logs/sweethome3d.out` | Output of Sweet Home 3D, including the plugin's log lines and stack traces |
| `target/e2e-logs/sh3d-mcp.log.*` | The plugin's own log file |
| `target/e2e-logs/xvfb.log` | Output of the X server |
| `target/e2e-work/<Class>/<test>/` | Files the test wrote: saved `.sh3d` scenes, exported images |
| `target/failsafe-reports/` | Test reports |

A saved `.sh3d` scene opens in Sweet Home 3D, so you can look at what the test built.

In CI the `e2e` job runs Sweet Home 3D with JDK 8, 11 and 17, while Maven and the test runner
use JDK 11. It uploads these directories as an artifact when it fails.

### Writing an end-to-end test

Extend `LiveTestBase`: it connects to the running application and gives every test an empty scene.

- Take the expected behaviour from the command's description and schema, `README.md` and `CHANGELOG.md` —
  not from the handler's code. A test written from the code repeats the code's mistakes.
- Check the model, not only the response: `saveAndReadHome(...)` saves the scene with `save_home` and reads
  the file back through Sweet Home 3D's own `HomeFileRecorder`.
- Do not hard-code catalog names; take items from `list_furniture_catalog`. Names depend on the language
  Sweet Home 3D runs in.
- Tests share one application, so they must not depend on each other or on the order they run in.
- If a test that follows the documented behaviour fails, the plugin (or its documentation) is wrong — fix
  that, not the test.

## Adding a New Command

Each MCP tool is a single Java class. Here is the full recipe:

### 1. Create the handler class

Save this as `src/main/java/com/sh3d/mcp/command/handler/MyCommandHandler.java`.

```java
package com.sh3d.mcp.command.handler;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.util.SchemaBuilder;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.LinkedHashMap;
import java.util.Map;

public class MyCommandHandler implements CommandHandler, CommandDescriptor {
    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String name = request.getString("name");
        if (name == null || name.trim().isEmpty()) {
            return Response.error("Missing or empty parameter: name");
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", name);
            result.put("wallCount", accessor.getHome().getWalls().size());
            return result;
        });
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Returns a name and the current wall count.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("name", "Name to include in the result (non-empty)")
                .build();
    }
}
```

Key rules:
- **All `Home` mutations go through `accessor.runOnEDT()`** — direct calls from HTTP threads cause race conditions
- **No external dependencies** — the plugin JAR must be self-contained; parse JSON manually using `JsonUtil`
- Coordinates are in **centimeters** (500 = 5 metres); Y axis points **down**

### 2. Register the command

In `SH3DMcpPlugin.java`, import the handler and add one line to `createCommandRegistry()`:

```java
registry.register("my_command", new MyCommandHandler());
```

### 3. Add tests

Create `src/test/java/com/sh3d/mcp/command/handler/MyCommandHandlerTest.java` in package `com.sh3d.mcp.command.handler` and cover at least:

- Happy path
- Missing required parameters
- Invalid parameter values (null, empty, out of range)

### 4. Verify

```bash
./mvnw clean package
# deploy and test in Sweet Home 3D (see Deploy section above)
```

## Commit Conventions

This project follows [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short description>
```

| Type | When to use |
|------|-------------|
| `feat` | New command or feature |
| `fix` | Bug fix |
| `refactor` | Code restructuring without behavior change |
| `test` | Adding or improving tests |
| `docs` | Documentation only |
| `chore` | Build, dependencies, configuration |

Examples:
```
feat(command): add export_to_glb command
fix(http): handle concurrent session initialization
test(checkpoint): cover restore with empty timeline
```

## Pull Request Process

1. Fork the repository and create a branch from `main`:
   ```bash
   git checkout -b feat/my-new-command
   ```
2. Make your changes and add tests
3. Ensure `./mvnw test` passes
4. Open a Pull Request against `main` with a clear description of what and why

## Code Style

- Plugin code: Java 8 language level and API (the build enforces it with `--release 8`); tests: Java 11. Lambdas are fine.
- Standard Java naming conventions
- No external runtime dependencies — the JAR must remain self-contained
- Keep methods short and focused; prefer readability over cleverness
