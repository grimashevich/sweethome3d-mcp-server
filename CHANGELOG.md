# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- V2 endpoint layout with Streamable HTTP MCP on `/mcp` and the legacy SSE-style transport on `/sse`
- Settings dialog now shows both endpoint URLs and separate copy-ready config snippets for Streamable HTTP clients and Claude Desktop

### Changed
- Claude Desktop auto-config now points `mcp-remote` at the `/mcp` endpoint
- README and architecture docs now document the two-endpoint transport layout

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

[Unreleased]: https://github.com/grimashevich/sweethome3d-mcp-server/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/grimashevich/sweethome3d-mcp-server/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/grimashevich/sweethome3d-mcp-server/releases/tag/v1.0.0
