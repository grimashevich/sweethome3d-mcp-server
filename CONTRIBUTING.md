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

- JDK 11 or later
- Maven 3.9+
- Sweet Home 3D 6.0+ installed locally (for `setup-dev.sh`)

### 1. Clone the repository

```bash
git clone https://github.com/grimashevich/sweethome3d-mcp-server.git
cd sweethome3d-mcp-server
```

### 2. Get SweetHome3D.jar

The `SweetHome3D.jar` dependency is not included in the repository (46 MB). Run the setup script to obtain it:

**Linux / macOS / Git Bash:**
```bash
bash scripts/setup-dev.sh
```

**Windows (CMD):**
```bat
scripts\setup-dev.bat
```

The script first looks for an existing Sweet Home 3D installation on your machine. If not found, it downloads the JAR from SourceForge automatically.

### 3. Build

```bash
mvn clean package
```

The plugin artifact is created at `target/sh3d-mcp-plugin-1.0.0.sh3p`.

### 4. Run tests

```bash
mvn test
```

### 5. Deploy to Sweet Home 3D (optional)

```bash
# Linux / macOS
cp target/sh3d-mcp-plugin-1.0.0.sh3p ~/.sweethome3d/plugins/

# Windows (Git Bash)
cp target/sh3d-mcp-plugin-1.0.0.sh3p "$APPDATA/eTeks/Sweet Home 3D/plugins/"
```

Restart Sweet Home 3D. The plugin activates automatically and starts the MCP server on port 9877.

## Adding a New Command

Each MCP tool is a single Java class. Here is the full recipe:

### 1. Create the handler class

```java
package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class MyCommandHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Read parameters
        String param = request.getStringParam("param");

        // All Home mutations must run on the Event Dispatch Thread
        accessor.runOnEDT(() -> {
            accessor.getHome().doSomething(param);
            return null;
        });

        return Response.success(Map.of("result", "ok"));
    }

    @Override
    public String getDescription() {
        return "One-line description shown to Claude in tools/list";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("type", "string");
        param.put("description", "Description of this parameter");
        props.put("param", param);

        schema.put("properties", props);
        schema.put("required", Arrays.asList("param"));
        return schema;
    }
}
```

Key rules:
- **All `Home` mutations go through `accessor.runOnEDT()`** — direct calls from HTTP threads cause race conditions
- **No external dependencies** — the plugin JAR must be self-contained; parse JSON manually using `JsonUtil`
- Coordinates are in **centimeters** (500 = 5 metres); Y axis points **down**

### 2. Register the command

In `SH3DMcpPlugin.java`, add one line to `createCommandRegistry()`:

```java
registry.register("my_command", new MyCommandHandler());
```

### 3. Add tests

Create `src/test/java/com/sh3d/mcp/command/MyCommandHandlerTest.java` and cover at least:
- Happy path
- Missing required parameters
- Invalid parameter values (null, empty, out of range)

### 4. Verify

```bash
mvn clean package
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
3. Ensure `mvn test` passes
4. Open a Pull Request against `main` with a clear description of what and why

## Code Style

- Java 11, no lambdas or APIs above Java 11 baseline
- Standard Java naming conventions
- No external runtime dependencies — the JAR must remain self-contained
- Keep methods short and focused; prefer readability over cleverness
