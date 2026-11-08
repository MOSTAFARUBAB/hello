# Simple Waypoints 1.3.3

Updated Waypoint Manager UI: screenshot-matched dark workspace layout, invisible hit targets instead of vanilla placeholder buttons, responsive editor/color-picker spacing, colored initial avatars, and a top-down surface-map preview with a waypoint callout. The preview samples only already-loaded client chunks and uses the world-surface heightmap, so it shows surface terrain and builds without loading chunks or revealing underground rooms.

# Simple Waypoints — Waypoint Manager UI

Client-side Fabric waypoint manager for Minecraft 26.3 (Java 25).

## Menu

- **J** opens the full-screen Waypoint Manager dashboard.
- **B** opens the Create Waypoint editor prefilled with the player's coordinates.
- The dashboard has dimension filters, search, recently-added/name sorting, grid/list layouts, cards, pagination, and JSON import/export.
- Export writes the active world's waypoints to `config/simple-waypoints-export.json`. Import reads `config/simple-waypoints-import.json`, maps entries into the active world, and updates matching name/dimension pairs.
- The editor has location details, dimension selection, HEX and visual color selection, Chroma, recent colors, a live surface-map preview, and per-waypoint label/beam switches. Existing temporary-waypoint expiry data is preserved when editing.
- Settings contains global label, distance, coordinate, label-size, beacon-beam, beam-color, beam-intensity, dimension-scope, HUD-visibility, and maximum display-distance preferences.

## Storage

Waypoints remain in `config/simple-waypoints.json`. Per-waypoint visibility fields are backward-compatible: waypoints from earlier versions default to showing both labels and beams.

Global display preferences are saved in `config/simple-waypoints-settings.properties` and survive restarts.

A maximum display distance of `0` means unlimited. This preserves the long-range direction labels for waypoints tens of millions of blocks away unless the user chooses a finite limit. Beacon columns remain subject to Minecraft's native chunk/render-distance limits.

## Rendering

- Labels retain camera-relative proxy rendering for extreme waypoint distances.
- The label plate has a fully opaque matte-black inset and a continuous waypoint-colored border.
- Labels remain depth-tested against mobs, players, items, and nearby terrain according to the existing renderer's visibility behavior.
- Distance and coordinate lines are controlled by the Settings page.
- The beam opacity control adjusts beam color intensity; color matching can be disabled independently.

## Build

Use Java 25 and Gradle 9.8 or compatible Loom tooling:

```bash
gradle clean build --no-daemon -PwithBeam=true
```

The installable mod JAR is produced in `build/libs/`.


## High-density UI rendering (1.3.1)

- Rebuilt the UI type atlas from vector outlines at 4x source density, with separate regular, bold, and compact resources.
- Uses native logical GUI coordinates so Minecraft's GUI Scale setting controls physical sizing across display resolutions.
- Raised color-picker backing textures for high-DPI displays and changed the contour preview to continuous pixel sampling.

## Display scaling

The screens are laid out in Minecraft GUI coordinates and follow the selected Minecraft GUI Scale (including Auto). Use the in-game GUI Scale setting to choose the preferred physical size on 1080p, 1440p, and 4K displays; the mod does not force a window resolution.


## Version 1.3.3 navigation and location improvements

- The waypoint editor includes **Use current position** to fill X, Y, and Z from the player's live position.
- **Use block you're looking at** copies the targeted block's coordinates into the form.
- Waypoint cards include a **GO** action. Selecting it starts live navigation in the action bar with a direction arrow, distance, and destination coordinates.
- Use `/wp stop` or the **Stop guide** button in the dashboard to end navigation.
- Navigation warns when a waypoint belongs to another world or dimension; it does not teleport the player.
- The existing RGB/HEX color picker and Chroma option are retained.

Build requirements: Java 25 and Gradle (Fabric Loom downloads the required Minecraft/Fabric dependencies).


## 1.3.3 UI refresh

- Replaces the old compact waypoint menu with the dark, lime-accented workspace dashboard.
- Adds compact topographic waypoint thumbnails to dashboard cards on larger screens.
- Keeps the redesigned waypoint editor, color/Chroma picker, surface preview, and settings workspace.
- Uses the Minecraft 26.3 chat listener overlay path for navigation notices instead of removed player message methods.


## Version 1.4.0 UI refresh

- Added a dedicated waypoint map overview and sidebar navigation to it.
- Kept the HSV saturation/value picker, hue strip, HEX field, recent colors, and Chroma mode in the waypoint editor.
- Minecraft target: 26.3; Java toolchain: 25; Fabric Loom is configured separately using the compatible `1.18-SNAPSHOT` plugin line.
- The map overview is a schematic preview and waypoint list, not a live terrain tile map.
