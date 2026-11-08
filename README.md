# Simple Waypoints Manager 1.4.2 — Neon Interface Redesign

A client-side Fabric waypoint manager for Minecraft 26.3 with a redesigned dark interface, neon-green accents, a waypoint-card dashboard, a waypoint editor with color presets and Chroma mode, a surface-map preview, and settings.

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

Use Java 25 and Gradle 9.8.0 with Fabric Loom 1.17.21:

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


## Version 1.4.2 neon interface redesign

- Replaced the dashboard workspace-summary layout with a waypoint-first card grid, dimension filters, search, waypoint thumbnails, and Import / Export actions.
- Shared four-item sidebar navigation across Waypoints, Map, Settings, and About.
- Reworked Settings into four compact panels: General, Map Settings, Appearance, and Data. The switches, label-size controls, beam-opacity slider, import/export, and reset actions are interactive.
- Added nine preset color swatches to the editor, while retaining the HSV square, hue slider, HEX field, eyedropper, recent colors, and Chroma switch.
- Added a dedicated map overview with waypoint markers and a legend. It is a schematic preview, not a live tile map of the current world.
- Minecraft target: 26.3; Java toolchain: 25; Fabric Loader: 0.19.5; Fabric API: 0.163.0+26.3; Fabric Loom: 1.17.21.
- Build uses Gradle 9.8.0 and Java 25.


## Version 1.4.2 build notes

The Minecraft game version is **26.3**. Fabric Loom has its own independent version number (configured here as **1.17.21**); it is not set to 26.3. The GitHub Actions workflow uses Gradle 9.8.0 and Java 25.
