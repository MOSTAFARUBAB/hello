# Smooth world-space waypoint label

The active label renderer is `beam/Beam3D.java`. Labels are submitted through Minecraft's world-text renderer, not drawn as a 2D HUD overlay.

- **Long-range visibility:** labels are no longer culled by the chunk render-distance setting. A distant waypoint is projected onto a camera-relative point 16 blocks away, preserving its real direction on screen while avoiding Minecraft's far-world clipping. This supports waypoint coordinates tens of millions of blocks away, as long as the waypoint is saved in the active world and dimension.
- **Occlusion:** nearby labels keep normal terrain depth testing, with a ray check limited to 256 blocks. Beyond that range, labels deliberately use see-through rendering; this avoids enormous ray casts through unloaded chunks and keeps the direction marker visible.
- **Outline:** one complete rectangle uses the waypoint/beam color.
- **Plate:** the inset is fully opaque matte black (`0xFF000000`), so terrain and sky cannot bleed through it.
- **No billboard self-flicker:** only one camera-facing billboard is submitted. The previous mirrored duplicate sat on the same depth plane. The colored frame, black inset, and text now use tiny separate depth offsets.
- **Size:** text remains about 13 screen pixels high per line at every distance. Size presets change only plate padding.
- **Position:** the label floats 2.5 blocks above the waypoint and is offset slightly toward the camera so it does not sit inside its own beam.
- **Name length:** long waypoint names are shortened with an ellipsis at about 110 px so the plate cannot grow without limit.

The vertical beacon beam itself remains subject to Minecraft's normal world/chunk render distance; the long-range guarantee in this update applies to waypoint labels.
