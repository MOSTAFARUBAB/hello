package com.example.waypoints.beam;

import com.example.waypoints.Waypoint;
import com.example.waypoints.WaypointsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders waypoint beams through Minecraft's native BeaconRenderer submit path.
 *
 * Each beam is centered at the waypoint's X/Z and runs through the full world
 * column. It uses the vanilla beacon texture, animation, inner/outer radii and
 * 2048-block segment height. Beam range, width scaling, and animation timing
 * follow BeaconRenderer's Minecraft 26.3 implementation.
 */
public final class Beam3D {
    private static final int MAX_RENDERED_BEAMS = 12;
    private static volatile List<BeamState> beams = List.of();
    private static volatile List<LabelState> labels = List.of();
    private static volatile boolean failed = false;
    private static final int MAX_RENDERED_LABELS = 16;
    private static final int FULL_BRIGHT = 0x00F000F0;
    // Fully opaque ARGB black: the world must never show through the label plate.
    private static final int LABEL_BACKGROUND = 0xFF000000;
    // Keep the billboard inside the normal world projection range even when its waypoint
    // is millions of blocks away. Its direction is preserved, so its screen position is
    // still the direction of the real waypoint.
    private static final double LABEL_PROXY_DISTANCE = 16.0;
    // Long-distance ray casts are both expensive and unreliable through unloaded chunks.
    // Distant labels deliberately use SEE_THROUGH mode instead.
    private static final double MAX_LABEL_OCCLUSION_CHECK_DISTANCE = 256.0;

    private Beam3D() {
    }

    public static void init() {
        LevelExtractionEvents.END_EXTRACTION.register(Beam3D::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(Beam3D::renderAndDraw);
        WaypointsMod.beam3dActive = true;
    }

    private static void fail(Throwable t) {
        if (!failed) {
            failed = true;
            WaypointsMod.beam3dActive = false;
            System.err.println("[Simple Waypoints] Native beacon beam renderer failed:");
            t.printStackTrace();
        }
        beams = List.of();
        labels = List.of();
    }

    private static void extract(LevelExtractionContext context) {
        if (failed) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null
                    || (WaypointsMod.hideHudWhenMenuOpen && mc.gui.screen() != null)) {
                beams = List.of();
                labels = List.of();
                return;
            }

            boolean renderBeams = WaypointsMod.showWaypointBeams;
            boolean renderLabels = WaypointsMod.showWaypointLabels;
            if (!renderBeams && !renderLabels) {
                beams = List.of();
                labels = List.of();
                return;
            }

            Vec3 cameraPosition = mc.player.getEyePosition();
            double[] camera = WaypointsMod.cameraState(mc);
            if (camera != null) {
                cameraPosition = new Vec3(camera[0], camera[1], camera[2]);
            }
            Vec3 playerPosition = mc.player.position();

            List<BeamState> beamCandidates = new ArrayList<>();
            List<LabelState> labelCandidates = new ArrayList<>();
            double maxRenderDistance = mc.options.getEffectiveRenderDistance() * 16.0;
            double maxRenderDistanceSquared = maxRenderDistance * maxRenderDistance;

            for (Waypoint waypoint : WaypointsMod.currentContextWaypoints()) {
                double wx = waypoint.x() + 0.5;
                double wy = waypoint.y() + 0.5;
                double wz = waypoint.z() + 0.5;
                double dx = wx - cameraPosition.x;
                double dy = wy - cameraPosition.y;
                double dz = wz - cameraPosition.z;
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                double horizontalDistanceSquared = dx * dx + dz * dz;
                double playerDistance = Math.sqrt(playerPosition.distanceToSqr(wx, wy, wz));
                boolean withinDisplayRange = WaypointsMod.maxDisplayDistance <= 0
                        || playerDistance <= WaypointsMod.maxDisplayDistance;
                if (!withinDisplayRange) continue;
                if (renderLabels && waypoint.isLabelEnabled()) {
                    double cameraDistance = Math.sqrt(distanceSquared);
                    // Do not tie label visibility to chunk render distance. For long-range
                    // waypoints, skip the potentially huge ray cast and intentionally draw
                    // through terrain so the marker remains a reliable direction indicator.
                    Vec3 labelTarget = new Vec3(wx, waypoint.y() + 2.5, wz);
                    boolean blocked = cameraDistance > MAX_LABEL_OCCLUSION_CHECK_DISTANCE
                            || isBlockedByTerrain(mc, cameraPosition, labelTarget);
                    // The label is drawn on a camera-relative proxy for extreme distances.
                    // Check loaded mobs against the real waypoint ray so the proxy cannot
                    // paint a label over a mob between the player and the waypoint.
                    boolean mobOverlap = isBlockedByMob(mc, cameraPosition, labelTarget, maxRenderDistance);
                    if (!mobOverlap) {
                        labelCandidates.add(new LabelState(waypoint, playerDistance, distanceSquared, blocked));
                    }
                }

                // Beacon columns remain constrained to normal world render distance. Labels
                // have their own camera-relative path and are not discarded by this limit.
                if (renderBeams && waypoint.isBeamEnabled() && distanceSquared <= maxRenderDistanceSquared
                        && horizontalDistanceSquared <= maxRenderDistanceSquared) {
                    double horizontalDistance = Math.sqrt(horizontalDistanceSquared);
                    // Match BeaconRenderer.extract(): scoped players use normal width;
                    // otherwise the beam widens past 96 horizontal blocks.
                    float radiusScale = mc.player.isScoping()
                            ? 1.0f
                            : (float) Math.max(1.0, horizontalDistance / 96.0);
                    int rawColor = WaypointsMod.matchWaypointColor ? WaypointsMod.resolveColor(waypoint) : 0xFFFFFF;
                    int intensity = Math.min(100, Math.round(WaypointsMod.beamOpacity * 1.25f));
                    int red = ((rawColor >> 16) & 255) * intensity / 100;
                    int green = ((rawColor >> 8) & 255) * intensity / 100;
                    int blue = (rawColor & 255) * intensity / 100;
                    int color = 0xFF000000 | (red << 16) | (green << 8) | blue;
                    beamCandidates.add(new BeamState(
                            waypoint.x(),
                            waypoint.z(),
                            color,
                            radiusScale,
                            horizontalDistanceSquared
                    ));
                }
            }

            beamCandidates.sort(java.util.Comparator.comparingDouble(BeamState::distanceSquared));
            if (beamCandidates.size() > MAX_RENDERED_BEAMS) {
                beamCandidates.subList(MAX_RENDERED_BEAMS, beamCandidates.size()).clear();
            }
            labelCandidates.sort(java.util.Comparator.comparingDouble(LabelState::distanceSquared));
            if (labelCandidates.size() > MAX_RENDERED_LABELS) {
                labelCandidates.subList(MAX_RENDERED_LABELS, labelCandidates.size()).clear();
            }
            beams = List.copyOf(beamCandidates);
            labels = List.copyOf(labelCandidates);
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Hide the label whenever a loaded mob intersects the camera-to-waypoint sightline. */
    private static boolean isBlockedByMob(Minecraft mc, Vec3 from, Vec3 to, double renderDistance) {
        try {
            double searchRadius = Math.max(32.0, renderDistance);
            AABB searchArea = mc.player.getBoundingBox().inflate(searchRadius);
            return mc.level.getEntitiesOfClass(
                    Mob.class,
                    searchArea,
                    Mob::isAlive
            ).stream().anyMatch(mob -> mob.getBoundingBox().inflate(0.75).clip(from, to).isPresent());
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when a solid block lies between the two points (checked every frame, no delay). */
    private static boolean isBlockedByTerrain(Minecraft mc, Vec3 from, Vec3 to) {
        try {
            var hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            return hit.getType() != HitResult.Type.MISS;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void renderAndDraw(LevelRenderContext context) {
        if (failed) return;

        try {
            List<BeamState> beamStates = beams;
            List<LabelState> labelStates = labels;
            if (beamStates.isEmpty() && labelStates.isEmpty()) return;

            var cameraState = context.levelState().cameraRenderState;
            Vec3 camera = cameraState.pos;
            if (camera == null) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            PoseStack poseStack = context.poseStack();
            var collector = context.submitNodeCollector();
            int minBuildY = mc.level.getMinY();

            if (WaypointsMod.showWaypointBeams) {
                // Copy BeaconRenderer.extract() timing exactly. Vanilla wraps at 40 ticks
                // and adds the current partial tick; passing a huge game-time float loses
                // precision in the rotating core and scrolling texture.
                float animationTime = Math.floorMod(mc.level.getGameTime(), 40L)
                        + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

                for (BeamState beam : beamStates) {
                    poseStack.pushPose();
                    // BeaconRenderer itself adds +0.5 X/Z to center the column.
                    poseStack.translate(
                            beam.x() - camera.x,
                            minBuildY - camera.y,
                            beam.z() - camera.z
                    );
                    BeaconRenderer.submitBeaconBeam(
                            poseStack,
                            collector,
                            BeaconRenderer.BEAM_LOCATION,
                            1.0f,
                            animationTime,
                            0,
                            BeaconRenderer.MAX_RENDER_Y,
                            beam.rgb(),
                            BeaconRenderer.SOLID_BEAM_RADIUS * beam.radiusScale() * 1.10f,
                            BeaconRenderer.BEAM_GLOW_RADIUS * beam.radiusScale() * 1.65f
                    );
                    poseStack.popPose();
                }
            }

            if (WaypointsMod.showWaypointLabels) {
                if (!loggedLabels && !labelStates.isEmpty()) {
                    loggedLabels = true;
                    System.out.println("[Simple Waypoints] submitting " + labelStates.size() + " world-space label(s)");
                }
                for (LabelState label : labelStates) {
                    submitDepthTestedLabel(poseStack, collector, cameraState, mc.font, label);
                }
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static boolean loggedLabels = false;

    /**
     * World-space label, drawn like a vanilla name tag.
     *  - Clear view: everything is depth-tested, so mobs, items, players and blocks in front hide it.
     *  - Terrain in the way: it is drawn through the terrain, so it can always guide you back.
     *  - Size: constant on screen, with a optional small/medium/large plate-padding presets.
     *  - Outline: a border in the same color as the waypoint's beam (rainbow follows the beam).
     */
    private static void submitDepthTestedLabel(PoseStack poseStack,
                                                net.minecraft.client.renderer.SubmitNodeCollector collector,
                                                net.minecraft.client.renderer.state.level.CameraRenderState camera,
                                                Font font,
                                                LabelState label) {
        Waypoint waypoint = label.waypoint();
        Vec3 cameraPos = camera.pos;
        if (cameraPos == null || camera.orientation == null) return;

        String name = fitLabelName(font, waypoint.name());
        String distanceLine = WaypointsMod.showWaypointDistances
                ? formatDistance(label.playerDistance()) + " away" : "";
        String coordinateLine = WaypointsMod.showWaypointCoordinates
                ? waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z() : "";
        // Size presets change the plate padding, not the billboard zoom.
        int horizontalPadding = switch (WaypointsMod.markerSizeMode) { case 0 -> 3; case 2 -> 7; default -> 5; };
        String nameLine = name;
        int nameWidth = font.width(nameLine);
        int distWidth = distanceLine.isEmpty() ? 0 : font.width(distanceLine);
        int coordWidth = coordinateLine.isEmpty() ? 0 : font.width(coordinateLine);
        int plateWidth = Math.max(nameWidth, Math.max(distWidth, coordWidth));
        int lineCount = 1 + (distanceLine.isEmpty() ? 0 : 1) + (coordinateLine.isEmpty() ? 0 : 1);

        double lx = waypoint.x() + 0.5;
        double ly = waypoint.y() + 2.5;
        double lz = waypoint.z() + 0.5;
        // The beam runs through the middle of the waypoint, so move the label toward you so it is
        // in front of its own beam instead of half-hidden inside it.
        double toX = cameraPos.x - lx;
        double toZ = cameraPos.z - lz;
        double len = Math.sqrt(toX * toX + toZ * toZ);
        if (len > 0.001) {
            double push = Math.min(0.55, len * 0.5);
            lx += toX / len * push;
            lz += toZ / len * push;
        }

        // Preserve the real waypoint direction, but draw the billboard at a safe camera-
        // relative distance. Minecraft's world projection clips far-away geometry, even
        // when the waypoint data itself is still available. The proxy makes a marker at
        // 60,000,000 blocks render just like the same marker at 16 blocks, without changing
        // its horizontal/vertical screen direction or reported distance.
        double realDx = lx - cameraPos.x;
        double realDy = ly - cameraPos.y;
        double realDz = lz - cameraPos.z;
        double realDistance = Math.sqrt(realDx * realDx + realDy * realDy + realDz * realDz);
        if (realDistance < 1.0e-6) return;
        double drawDistance = Math.min(realDistance, LABEL_PROXY_DISTANCE);
        double proxyRatio = drawDistance / realDistance;
        double dx = realDx * proxyRatio;
        double dy = realDy * proxyRatio;
        double dz = realDz * proxyRatio;

        Minecraft mc = Minecraft.getInstance();
        double screenH = Math.max(1.0, mc.getWindow().getHeight());
        double tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        // Keep labels nearly the same size, with only a gentle reduction at distance.
        // Even at millions of blocks away the scale bottoms out at 90% of near size.
        double distanceScale = Math.max(0.90, 1.0 - 0.015 * Math.log10(1.0 + label.playerDistance()));
        final double linePx = 13.0 * distanceScale;
        float scale = (float) ((linePx / 9.0) * (2.0 * drawDistance * tanHalfFov) / screenH);

        Font.DisplayMode mode = label.blocked() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        int outline = 0xFF000000 | (WaypointsMod.resolveColor(waypoint) & 0x00FFFFFF);

        var nameSeq = Component.literal(nameLine).getVisualOrderText();

        // Draw one complete outer rectangle, then one inset body rectangle. A single
        // border quad avoids the hairline corner gaps caused by four separately rounded
        // edge strips at different distances and display scales.
        float half = plateWidth * 0.5f + horizontalPadding;
        float verticalPadding = switch (WaypointsMod.markerSizeMode) { case 0 -> 0.0f; case 2 -> 2.0f; default -> 1.0f; };
        final float top = -11.0f - verticalPadding;
        final float bottom = (lineCount == 3 ? 16.0f : lineCount == 2 ? 7.0f : -2.0f) + verticalPadding;
        final float th = 1.0f;

        // Camera orientation already makes the billboard face the viewer, so one copy
        // is enough. The previous mirrored duplicate occupied the exact same depth plane
        // and could flicker against itself in the depth buffer.
        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        poseStack.mulPose(new Matrix4f().rotation(camera.orientation));
        poseStack.scale(scale, -scale, 1.0f);

        // Keep the full-color frame, opaque black inset and text on distinct, tiny depth
        // offsets. This prevents coplanar background quads from fighting while preserving
        // the single continuous rectangular outline.
        poseStack.pushPose();
        poseStack.translate(0.0, 0.0, -0.01);
        collector.submitTextBackground(poseStack,
                -half - th, top - th, half + th, bottom + th,
                outline, mode, FULL_BRIGHT);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.0, 0.0, 0.0);
        collector.submitTextBackground(poseStack,
                -half, top, half, bottom,
                LABEL_BACKGROUND, mode, FULL_BRIGHT);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.0, 0.0, 0.01);
        collector.submitText(poseStack, -nameWidth * 0.5f, -10.0f, nameSeq, false, mode,
                FULL_BRIGHT, 0xFFFFFFFF, 0x00000000, 0);
        if (!distanceLine.isEmpty()) {
            collector.submitText(poseStack, -distWidth * 0.5f, -1.0f, Component.literal(distanceLine).getVisualOrderText(), false, mode,
                    FULL_BRIGHT, 0xFFD0D6DC, 0x00000000, 0);
        }
        if (!coordinateLine.isEmpty()) {
            float y = distanceLine.isEmpty() ? -1.0f : 8.0f;
            collector.submitText(poseStack, -coordWidth * 0.5f, y, Component.literal(coordinateLine).getVisualOrderText(), false, mode,
                    FULL_BRIGHT, 0xFFB8C0C8, 0x00000000, 0);
        }
        poseStack.popPose();
        poseStack.popPose();
    }

    private static String fitLabelName(Font font, String rawName) {
        String name = rawName == null || rawName.isBlank() ? "Waypoint" : rawName.trim();
        final int maxWidth = 110;
        if (font.width(name) <= maxWidth) return name;
        while (name.length() > 1 && font.width(name + "…") > maxWidth) {
            name = name.substring(0, name.length() - 1);
        }
        return name + "…";
    }

    private static String formatDistance(double distance) {
        if (distance < 10.0) return (Math.round(distance * 10.0) / 10.0) + " m";
        if (distance < 1000.0) return Math.round(distance) + " m";
        if (distance < 10_000.0) return (Math.round(distance / 100.0) / 10.0) + " km";
        return Math.round(distance / 1000.0) + " km";
    }

    private record BeamState(double x, double z, int rgb, float radiusScale, double distanceSquared) {
    }

    private record LabelState(Waypoint waypoint, double playerDistance, double distanceSquared, boolean blocked) {
    }
}
