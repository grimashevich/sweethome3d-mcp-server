# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.2.0] - 2026-10-21

### Security

- `load_home` reads validated XML without deserializing the serialized `Home` entry or resolving XML external entities. It requires a regular file with exactly one `Home.xml`, refuses DOCTYPE and external model/texture/image content, and limits uncompressed XML to 64 MB of bytes actually read. Its temporary copy keeps owner-only permissions throughout copying, validation and reading. It also refuses non-regular and empty files and stops copying past the size the file reported (procfs pseudo-files), and refuses content Sweet Home 3D would resolve as a URL even when the scheme has non-ASCII letters (JRE 8). Based on security fixes by Oskar Lindh ([@Olindh](https://github.com/Olindh)).
- Optional bearer-token authentication is **off by default**. Enable **Require a token** in **Tools → MCP Server...**; changes apply on the next request, including existing sessions, and persist in `sh3d-mcp.properties`. `-Dsh3d.mcp.requireToken=true|false` overrides the saved setting. With authentication enabled, `/mcp` requires a matching bearer token (`401` otherwise); `/health` remains token-free. Tokens are compared in constant time and stored in owner-only `sh3d-mcp.token` / `sh3d-mcp.token.headers` files. Failed rotation keeps the old token active; a server requiring an unavailable token refuses to start. With authentication off, token files are neither created nor modified. Based on security fixes by Oskar Lindh ([@Olindh](https://github.com/Olindh)).
- `Host` and any supplied `Origin` are checked on `/mcp` and `/health` in both token modes. Only `127.0.0.1` / `localhost` on the server's port are accepted (`403` otherwise); comparisons ignore case, repeated headers and comma-separated values are refused, and port 80 also accepts addresses without `:80`. No CORS headers are sent. Request bodies are limited to 5 MB (`413`), with oversized `Content-Length` rejected before reading. Based on security fixes by Oskar Lindh ([@Olindh](https://github.com/Olindh)).
- Claude Desktop auto-configuration pins `mcp-remote@0.14.3`, targets `127.0.0.1`, and adds `--header-file` only when authentication is enabled, keeping the token out of client configuration and process arguments. Based on security fixes by Oskar Lindh ([@Olindh](https://github.com/Olindh)); see [#7](../../issues/7).

### Added

- `sashPreset` (`single_left`, `single_right`, `double`, `none`) and explicit `sashes` (`xAxis`, `yAxis`, `width`, `startAngle`, `endAngle`; angles in degrees) on `place_door_or_window` and `modify_furniture`. Leaves open toward the piece's front, hinged on the frame's front face; `mirrored` swaps the hinge side. Invalid sash values or sashes on ordinary furniture are rejected before changes. Furniture results and `get_state` report the sash count. By Daniel Whitston ([@danwhitston](https://github.com/danwhitston)), [PR #5](../../pull/5).
- `modify_dimension_line` edits endpoints or offset by ID and recalculates length; `delete_dimension_line` removes a line by ID. Both work on any level. Non-finite values and geometry overflow are rejected before changes. By Rolfy N. Montufar ([@rolfymontufar](https://github.com/rolfymontufar)), [PR #6](../../pull/6).
- `modify_label` edits text, position, angle, colors, font properties, alignment, elevation and pitch by ID; `delete_label` removes a label by ID. Only supplied properties change and the label keeps its level. Font edits start from the default label style when needed; validation happens before changes. Responses include the label's `level`. By [@SharonSx](https://github.com/SharonSx), [PR #14](../../pull/14).
- Token controls in **Tools → MCP Server...**: require-token checkbox, masked token, copy and rotate buttons, translated into all 18 supported languages. Based on security fixes by Oskar Lindh ([@Olindh](https://github.com/Olindh)).

### Changed

- `list_levels` returns a stable string `id` accepted by `set_selected_level` and `delete_level`, plus a separate zero-based numeric `index`. Reported by [@ikeoffiah](https://github.com/ikeoffiah), [#9](../../issues/9).
- Checkpoint IDs increase throughout the manager's lifetime and are never reused after eviction, branching or clearing history. `cursor` and `restoredTo` use these IDs; `restore_checkpoint` rejects fractional, negative and invalid IDs.
- `generate_shape` rejects a string `color` that is not a valid color; such strings were previously silently ignored.
- `add_dimension_line` responses include `level`; `add_label` rounds `angle` consistently with `get_state`. Shared response formatting also reports finite values beyond about 9.2e16 without clipping them. By Rolfy N. Montufar ([@rolfymontufar](https://github.com/rolfymontufar)), [PR #6](../../pull/6).
- `save_home` writes `Home.xml` so its output can be loaded by the hardened `load_home`. Based on security fixes by Oskar Lindh ([@Olindh](https://github.com/Olindh)).
- Plugin code now targets Java 8 language features, APIs and bytecode, including Sweet Home 3D's bundled Java 8; building and running tests require JDK 11+ and Maven 3.9+.
- Command descriptions now match behavior: `render_photo` uses the current camera by default and positive downward pitch; `export_svg` recommends `filePath` for larger scenes; `set_environment` defers default ground colors to Sweet Home 3D; `add_level` includes floor thickness in level spacing while `create_wall` uses the independent home default; stairs use `height` and `depth`.

### Fixed

- `restore_checkpoint` and `load_home` keep all objects on their saved levels and restore the observer camera at its saved height, even when the selected level is not the topmost. Checkpoints remain immutable after restoration because each restore applies a copy. Reported by [@ikeoffiah](https://github.com/ikeoffiah), [#10](../../issues/10).
- Overwriting a JPEG with `render_photo`, including angle-series files, truncates the old file without leaving trailing bytes. Reported by [@ikeoffiah](https://github.com/ikeoffiah), [#11](../../issues/11).
- Catalog lights are placed as `HomeLight`, preserving light sources, emitting materials and initial power through saving and loading. Shelf units preserve shelf elevations and boxes. Reported by [@jacktwilliams](https://github.com/jacktwilliams), [#13](../../issues/13).
- Catalog doors and windows are created as `HomeDoorOrWindow`, preserving sashes, cut-outs and frame metadata. `place_door_or_window` fits the frame depth and position into straight walls and binds the piece to the wall; on arc walls it follows the arc and tangent. Plan footprints follow model depth. Non-resizable items can be placed, while unsupported mirroring returns a clear error. By Daniel Whitston ([@danwhitston](https://github.com/danwhitston)), [PR #4](../../pull/4).
- `add_dimension_line` and `modify_dimension_line` reject coincident endpoints and overflowing geometry before changes; elevation dimension lines with distinct endpoint elevations remain editable. Positive offset is documented as the right-hand side of start → end (below a left-to-right line, left of a top-to-bottom line). Based on Rolfy N. Montufar's ([@rolfymontufar](https://github.com/rolfymontufar)) [PR #6](../../pull/6).
- `add_label` and `modify_label` assign the default label style when a numeric `pitch` is set on an unstyled label, making it visible in 3D. The response includes the assigned `style`. Follow-up to [@SharonSx](https://github.com/SharonSx)'s [PR #14](../../pull/14).
- `modify_furniture` validates all parameters, resize permissions and color-change permissions (including reset) before editing, rejects non-positive dimensions, and updates the plan dimensions along with the model, avoiding partial edits and corrupt group resizing.
- Scene commands reject non-finite inputs before changes, including camera coordinates, `lookAt`, nested geometry and shape operands. `set_camera` rejects a `lookAt` object missing X or Y before changing the camera.
- Catalog search, enum parsing, numeric output and `.ZIP` extension handling are independent of the JVM's locale, including Turkish casing; uppercase ZIP filenames no longer gain an extra suffix.
- Failed checkpoint scene replacement leaves the timeline cursor unchanged. `export_plan_image` restores the selected level and selection, including an empty selected level. `add_level` assigns existing objects in a home without levels to the requested ground level or creates a ground level from preferences first.
- `save_home` validates the generated `Home.xml` in a temporary `.tmp` file in the system temporary directory, without applying the 64 MB load limit. XML errors, including invalid characters, return the parser's reason without changing the previous file, home name or unsaved state. It checks usable space on the existing destination file or, for a new file, its directory, and skips the check when space is unknown or the query fails. Saving uses classic file streams to finish the destination copy even if the thread is interrupted, writes through symbolic and hard links and preserves the destination inode, permissions, owner and ACL; new files use normal umask permissions. An existing writable file can be saved even when its directory is not writable. Temporary saves are removed on success and failure.

### Documentation

- Installation documents double-clicking `.sh3p` files and the correct Linux plugins folder; Claude Desktop examples use the local `mcp-remote` bridge. Thanks to [@arkansas-coder](https://github.com/arkansas-coder) for the detailed report, [#7](../../issues/7).
- The macOS Mac App Store build cannot open the MCP listening port because its sandbox lacks the server entitlement; use a standalone build. See [#2](../../issues/2).
- Development examples use the current command API; runtime/build requirements, authentication, package layout and e2e options are documented for this release. See [PR #14](../../pull/14).

### Upgrade notes

- Files saved by plugin **1.1.0 or earlier**, and files saved by **Sweet Home 3D versions before 5.3**, are no longer accepted by `load_home` because they lack `Home.xml`. Open and save them again in Sweet Home 3D before loading them through the plugin. See [#10](../../issues/10), based on security fixes by Oskar Lindh ([@Olindh](https://github.com/Olindh)).
- Use the stable string `id` from `list_levels` for level commands; `index` is only the level's current list position. Reported by [@ikeoffiah](https://github.com/ikeoffiah), [#9](../../issues/9).
- Treat checkpoint IDs as stable numbers rather than list positions; discarded IDs are never reused during the manager's lifetime.
- Token authentication is **off by default**. Enable **Tools → MCP Server... → Require a token**, then configure the client with `Authorization: Bearer <token>` (`headersHelper` for Claude Code, `mcp-remote --header-file` for Claude Desktop). The token and headers files are in the plugins folder. See [Authentication](README.md#authentication), [#7](../../issues/7), based on security fixes by Oskar Lindh ([@Olindh](https://github.com/Olindh)).

## [1.1.0] - 2026-03-13

### Added
- **Multilingual UI** — plugin settings dialog and menu items are now localized for 18 languages: French, German, Spanish, Italian, Russian, Simplified Chinese, Traditional Chinese, Japanese, Portuguese, Brazilian Portuguese, Dutch, Swedish, Czech, Polish, Hungarian, Greek, Bulgarian, Vietnamese
- Plugin name and description in SH3D Plugin Manager are also localized

### Changed
- All UI strings externalized to Java ResourceBundle (`McpPlugin.properties`)
- `McpSettingsDialog` accepts `ResourceBundle` via constructor (dependency injection)
- Dynamic strings use `java.text.MessageFormat` for proper placeholder handling
- Version bumped to 1.1.0

## [1.0.0] - 2026-02-27

### Initial public release

Full-featured MCP server embedded directly into Sweet Home 3D as a plugin.
Exposes 42 tools over Streamable HTTP (JSON-RPC 2.0) on `http://localhost:9877/mcp`,
compatible with the MCP protocol version `2025-03-26`.

### Added

**Scene state**
- `get_state` — full scene snapshot: walls, furniture, rooms, levels, camera
- `clear_scene` — remove all objects from the scene
- `save_home` / `load_home` — persist and restore `.sh3d` files

**Walls**
- `create_wall` — single wall by two endpoints
- `create_walls` — four-wall rectangular room in one call
- `modify_wall` — update height, thickness, color, shininess, arc
- `delete_wall` — remove wall by ID
- `connect_walls` — join two walls for correct corner rendering

**Rooms**
- `create_room_polygon` — room from an arbitrary point polygon
- `modify_room` — update name, floor/ceiling color, shininess, visibility
- `delete_room` — remove room by ID

**Furniture**
- `list_furniture_catalog` — browse the built-in catalog with filtering
- `list_categories` — catalog categories with item counts
- `place_furniture` — place a catalog item at given coordinates
- `modify_furniture` — update position, rotation, size, color, visibility
- `delete_furniture` — remove furniture by ID

**Doors and windows**
- `place_door_or_window` — insert a catalog door/window into a wall by wall ID and position

**Textures**
- `list_textures_catalog` — browse texture catalog with filtering
- `apply_texture` — apply a catalog texture to a wall side, floor, or ceiling

**Levels (floors)**
- `add_level` — create a new floor with elevation and slab thickness
- `list_levels` — list all levels with the currently selected one
- `set_selected_level` — switch the active level
- `delete_level` — remove a level and all its objects

**Cameras and rendering**
- `set_camera` — switch between top-view and observer camera; set position and angles
- `store_camera` — save a named viewpoint
- `get_cameras` — list all saved viewpoints
- `render_photo` — 3D photo-realistic render (Sunflow); optionally save to file
- `set_environment` — configure sky, ground, light intensity, wall transparency, drawing mode

**Export**
- `export_plan_image` — fast 2D floor plan export to PNG
- `export_svg` — 2D floor plan export to SVG
- `export_to_obj` — 3D scene export to Wavefront OBJ (ZIP with OBJ + MTL + textures)

**Annotations**
- `add_label` — text label on the 2D plan
- `add_dimension_line` — measurement annotation on the 2D plan

**Checkpoints (undo timeline)**
- `checkpoint` — take an in-memory snapshot of the scene
- `restore_checkpoint` — restore a snapshot (undo/redo by index or ID)
- `list_checkpoints` — list all snapshots with the current cursor position

**3D shape generation**
- `generate_shape` — create arbitrary 3D geometry: extrude (polygon + height) or mesh (vertices + triangles)

**Utility**
- `batch_commands` — execute multiple commands in a single call

[Unreleased]: https://github.com/grimashevich/sweethome3d-mcp-server/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/grimashevich/sweethome3d-mcp-server/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/grimashevich/sweethome3d-mcp-server/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/grimashevich/sweethome3d-mcp-server/releases/tag/v1.0.0
