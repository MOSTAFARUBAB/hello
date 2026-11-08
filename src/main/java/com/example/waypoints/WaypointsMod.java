package com.example.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/**
 * Simple client-side waypoints for Minecraft 26.3 (Fabric, Mojang names).
 *
 * Waypoints are saved to config/simple-waypoints.json. The world marker is only the native
 * 3D beacon beam with lightweight screen-projected name and distance labels; no logo markers or ground rings.
 */
public class WaypointsMod implements ClientModInitializer {
    public static final String MOD_ID = "simplewaypoints";

    static final int CHROMA = -1;
    private static final int[] COLORS = {0xFF0000, 0x00FF00, 0x3366FF, 0xFFFF00, 0xFF55FF, 0x00FFFF, 0xFF9900, 0xFFFFFF, CHROMA};
    private static final String[] COLOR_NAMES = {"red", "green", "blue", "yellow", "pink", "cyan", "orange", "white", "chroma"};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Waypoint>>() {}.getType();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("simple-waypoints.json");
    private static final Path SETTINGS_FILE = FabricLoader.getInstance().getConfigDir().resolve("simple-waypoints-settings.properties");
    private static final Path EXPORT_FILE = FabricLoader.getInstance().getConfigDir().resolve("simple-waypoints-export.json");
    private static final Path IMPORT_FILE = FabricLoader.getInstance().getConfigDir().resolve("simple-waypoints-import.json");

    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static KeyMapping addKey;
    private static KeyMapping menuKey;
    private static boolean openMenuRequested = false;
    private static boolean openNewRequested = false;

    /** True when the real 3D beam is running; then the on-screen beam is not drawn. */
    public static volatile boolean beam3dActive = false;
    public static volatile boolean showWaypointLabels = true;
    public static volatile boolean showWaypointBeams = true;
    /** 0=thin, 1=normal (vanilla-sized), 2=thick. */
    public static volatile int beamThickness = 1;
    /** Menu-only dimension browser: CURRENT, OVERWORLD, NETHER, END, or ALL. */
    public static volatile int menuDimensionMode = 4;
    /** Optional label plate padding preset; text size itself stays constant. */
    public static volatile int markerSizeMode = 1; // 0 small, 1 medium, 2 large
    public static volatile boolean showWaypointDistances = true;
    public static volatile boolean showWaypointCoordinates = false;
    public static volatile boolean matchWaypointColor = true;
    public static volatile boolean currentDimensionOnly = true;
    public static volatile boolean hideHudWhenMenuOpen = true;
    public static volatile boolean smoothUiEdges = true;
    /** User-selectable accent color for the custom waypoint screens (ARGB). */
    public static volatile int uiAccentColor = 0xFFB7F36B;
    /** 0 = unlimited; otherwise maximum world blocks from the player. */
    public static volatile int maxDisplayDistance = 0;
    /** 0-100; applied as beam color intensity on the vanilla beam renderer. */
    public static volatile int beamOpacity = 75;

    /** The waypoint currently selected for live action-bar navigation. */
    private static volatile Waypoint navigationTarget;



    @Override
    public void onInitializeClient() {
        load();
        loadPreferences();
        // Initialize the native in-world 3D beacon renderer. Beam3D is part of the
        // normal client source set and is included in every installable mod JAR.
        beam3dActive = false;
        try {
            Class.forName("com.example.waypoints.beam.Beam3D").getMethod("init").invoke(null);
        } catch (Throwable error) {
            beam3dActive = false;
            error.printStackTrace();
        }

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        addKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.simplewaypoints.add", InputConstants.KEY_B, category));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.simplewaypoints.menu", InputConstants.KEY_J, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            purgeExpired();
            updateNavigationHud(client);
            while (addKey.consumeClick()) {
                if (client.player == null) return;
                openNewRequested = true; // B opens the "New Waypoint" menu (name, X/Y/Z, dimension, color) instead of adding one instantly
            }
            while (menuKey.consumeClick()) {
                openMenuRequested = true;
            }
            if (openNewRequested) {
                openNewRequested = false;
                if (client.player != null) WaypointEditScreen.openNew();
            } else if (openMenuRequested) {
                openMenuRequested = false;
                if (client.player != null) WaypointScreen.openMenu();
            }
        });

        registerCommands();

        // Waypoint labels are submitted by Beam3D as native world-space text.
        // Normal depth testing makes mobs, items and blocks cover the exact pixels
        // in front of them; there is no screen-space HUD label overlay.
    }

    /** Begin guiding the player toward a saved waypoint. */
    public static void navigateTo(Waypoint waypoint) {
        if (waypoint == null) return;
        navigationTarget = waypoint;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.gui.chatListener().handleSystemMessage(Component.literal("Navigation started: " + waypoint.name()), true);
        }
    }

    /** Stop the live waypoint guide. */
    public static void stopNavigation() {
        navigationTarget = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.chatListener().handleSystemMessage(Component.literal("Waypoint navigation stopped."), true);
    }

    public static Waypoint navigationTarget() { return navigationTarget; }

    private static void updateNavigationHud(Minecraft client) {
        Waypoint target = navigationTarget;
        if (target == null || client.player == null || client.level == null) return;
        if (!WAYPOINTS.contains(target)) {
            navigationTarget = null;
            return;
        }
        if (!java.util.Objects.equals(target.worldId(), currentWorldId())) {
            client.gui.chatListener().handleSystemMessage(Component.literal("Navigation: " + target.name() + " belongs to another world."), true);
            return;
        }
        if (!normalizeDimension(target.dimension()).equals(normalizeDimension(currentDimension()))) {
            client.gui.chatListener().handleSystemMessage(Component.literal("Navigation: " + target.name() + " is in " + dimensionShortName(target.dimension()) + ". Travel there to continue."), true);
            return;
        }
        double dx = target.x() + 0.5 - client.player.getX();
        double dy = target.y() - client.player.getY();
        double dz = target.z() + 0.5 - client.player.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double bearing = Math.toDegrees(Math.atan2(dx, dz));
        double relative = bearing - client.player.getYRot();
        relative = ((relative + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int sector = Math.floorMod((int) Math.round(relative / 45.0), 8);
        String message = arrows[sector] + "  " + target.name() + "  •  " + formatDistance(distance)
                + "  •  X " + target.x() + "  Y " + target.y() + "  Z " + target.z();
        client.gui.chatListener().handleSystemMessage(Component.literal(message), true);
    }

    // ---------------- commands ----------------
    private static CompletableFuture<Suggestions> suggestNames(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder b) {
        for (Waypoint w : WAYPOINTS) b.suggest(w.name());
        return b.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestColors(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder b) {
        for (String n : COLOR_NAMES) b.suggest(n);
        return b.buildFuture();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("wp")
                        .then(ClientCommands.literal("menu")
                                .executes(ctx -> {
                                    openMenuRequested = true;
                                    return 1;
                                }))
                        .then(ClientCommands.literal("stop")
                                .executes(ctx -> {
                                    stopNavigation();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            BlockPos p = ctx.getSource().getPlayer().blockPosition();
                                            add(StringArgumentType.getString(ctx, "name"), p.getX(), p.getY(), p.getZ());
                                            feedback(ctx.getSource(), "Waypoint added at your position.");
                                            return 1;
                                        })
                                        .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                                .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                                        .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                                                .executes(ctx -> {
                                                                    add(StringArgumentType.getString(ctx, "name"),
                                                                            IntegerArgumentType.getInteger(ctx, "x"),
                                                                            IntegerArgumentType.getInteger(ctx, "y"),
                                                                            IntegerArgumentType.getInteger(ctx, "z"));
                                                                    feedback(ctx.getSource(), "Waypoint added.");
                                                                    return 1;
                                                                }))))))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests(WaypointsMod::suggestNames)
                                        .executes(ctx -> {
                                            String n = StringArgumentType.getString(ctx, "name");
                                            boolean removed = WAYPOINTS.removeIf(w -> w.name().equalsIgnoreCase(n));
                                            save();
                                            feedback(ctx.getSource(), removed ? "Removed." : "No waypoint with that name.");
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("color")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests(WaypointsMod::suggestNames)
                                        .then(ClientCommands.argument("color", StringArgumentType.word())
                                                .suggests(WaypointsMod::suggestColors)
                                                .executes(ctx -> {
                                                    String n = StringArgumentType.getString(ctx, "name");
                                                    Integer c = parseColor(StringArgumentType.getString(ctx, "color"));
                                                    if (c == null) {
                                                        feedback(ctx.getSource(), "Unknown color. Use red, green, blue, yellow, pink, cyan, orange, white, a hex code like ff8800, or chroma.");
                                                        return 0;
                                                    }
                                                    boolean ok = update(n, w -> new Waypoint(w.name(), w.x(), w.y(), w.z(), c, w.dimension(), w.expiresAt(), w.worldId(), w.safeMarkerText(), w.isLabelEnabled(), w.isBeamEnabled()));
                                                    feedback(ctx.getSource(), ok ? "Color changed." : "No waypoint with that name.");
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("expire")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests(WaypointsMod::suggestNames)
                                        .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    String n = StringArgumentType.getString(ctx, "name");
                                                    int secs = IntegerArgumentType.getInteger(ctx, "seconds");
                                                    long at = secs == 0 ? 0L : System.currentTimeMillis() + secs * 1000L;
                                                    boolean ok = update(n, w -> new Waypoint(w.name(), w.x(), w.y(), w.z(), w.color(), w.dimension(), at, w.worldId(), w.safeMarkerText(), w.isLabelEnabled(), w.isBeamEnabled()));
                                                    feedback(ctx.getSource(), ok ? (secs == 0 ? "Waypoint is now permanent." : "Waypoint will disappear in " + secs + "s.") : "No waypoint with that name.");
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("list")
                                .executes(ctx -> {
                                    if (WAYPOINTS.isEmpty()) feedback(ctx.getSource(), "No waypoints.");
                                    for (Waypoint w : WAYPOINTS) {
                                        String extra = w.color() == CHROMA ? " chroma" : String.format(" #%06X", w.color() & 0xFFFFFF);
                                        if (w.expiresAt() > 0) extra += " (" + Math.max(0, (w.expiresAt() - System.currentTimeMillis()) / 1000) + "s left)";
                                        feedback(ctx.getSource(), w.name() + "  " + w.x() + " " + w.y() + " " + w.z() + extra);
                                    }
                                    return 1;
                                }))
                        .then(ClientCommands.literal("clear")
                                .executes(ctx -> {
                                    WAYPOINTS.clear();
                                    save();
                                    feedback(ctx.getSource(), "All waypoints cleared.");
                                    return 1;
                                }))));
    }

    private static void feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message));
    }

    // ---------------- data ----------------
    public static List<Waypoint> waypoints() {
        return WAYPOINTS;
    }

    /** Waypoints for the active save/server and the player's actual current dimension. Used by world rendering. */
    public static List<Waypoint> currentContextWaypoints() {
        return currentDimensionOnly ? waypointsForDimension(currentDimension()) : waypointsForActiveWorld();
    }

    private static List<Waypoint> waypointsForActiveWorld() {
        String worldId = currentWorldId();
        migrateLegacyWorldIds(worldId);
        return WAYPOINTS.stream().filter(w -> java.util.Objects.equals(w.worldId(), worldId)).toList();
    }

    /** Menu list: lets the user browse dimensions without changing where the player currently is. */
    public static List<Waypoint> menuWaypoints() {
        String worldId = currentWorldId();
        migrateLegacyWorldIds(worldId);
        String mode = menuDimensionId();
        return WAYPOINTS.stream()
                .filter(w -> java.util.Objects.equals(w.worldId(), worldId))
                .filter(w -> dimensionMatches(w, mode))
                .toList();
    }

    private static List<Waypoint> waypointsForDimension(String dimension) {
        String worldId = currentWorldId();
        migrateLegacyWorldIds(worldId);
        return WAYPOINTS.stream()
                .filter(w -> java.util.Objects.equals(w.worldId(), worldId))
                .filter(w -> dimensionMatches(w, dimension))
                .toList();
    }

    /** True if the waypoint belongs in this dimension. A waypoint set to "all" shows in every dimension. */
    static boolean dimensionMatches(Waypoint w, String dimension) {
        String d = normalizeDimension(w.dimension());
        return "all".equals(d) || "all".equals(dimension) || java.util.Objects.equals(d, dimension);
    }

    /** Assigns pre-worldId saves to the first active save/server that opens the menu.
     * Older files did not record ownership, so their original world cannot be reconstructed.
     */
    private static void migrateLegacyWorldIds(String activeWorldId) {
        if (activeWorldId == null || activeWorldId.equals("unknown")) return;
        String oldSingleplayerId = null;
        Minecraft mc = Minecraft.getInstance();
        // Earlier builds used the displayed save name as the ID. Migrate that old ID once
        // to the real save-folder ID, so existing waypoints don't remain globally shared.
        if (mc.getCurrentServer() == null && mc.getSingleplayerServer() != null) {
            try {
                String levelName = mc.getSingleplayerServer().getWorldData().getLevelName();
                if (levelName != null && !levelName.isBlank()) oldSingleplayerId = "singleplayer:" + levelName.trim();
            } catch (Throwable ignored) { }
        }
        boolean changed = false;
        for (int i = 0; i < WAYPOINTS.size(); i++) {
            Waypoint w = WAYPOINTS.get(i);
            String existingId = w.worldId();
            boolean unassigned = existingId == null || existingId.isBlank() || existingId.equals("unknown");
            boolean oldNameBasedId = oldSingleplayerId != null
                    && existingId != null
                    && (existingId.equals(oldSingleplayerId) || existingId.startsWith("singleplayer-name:"));
            if (unassigned || oldNameBasedId) {
                WAYPOINTS.set(i, new Waypoint(w.name(), w.x(), w.y(), w.z(), w.color(),
                        normalizeDimension(w.dimension()), w.expiresAt(), activeWorldId, w.safeMarkerText(),
                        w.isLabelEnabled(), w.isBeamEnabled()));
                changed = true;
            }
        }
        if (changed) save();
    }

    public static String menuDimensionLabel() {
        return switch (menuDimensionMode) {
            case 1 -> "Overworld";
            case 2 -> "Nether";
            case 3 -> "End";
            case 4 -> "All Dimensions";
            default -> "Current: " + dimensionShortName(currentDimension());
        };
    }

    public static void cycleMenuDimension() { menuDimensionMode = (menuDimensionMode + 1) % 5; }

    private static String menuDimensionId() {
        return switch (menuDimensionMode) {
            case 1 -> "minecraft:overworld";
            case 2 -> "minecraft:the_nether";
            case 3 -> "minecraft:the_end";
            case 4 -> "all";
            default -> currentDimension();
        };
    }

    private static String dimensionShortName(String id) {
        if (id.endsWith(":overworld")) return "Overworld";
        if (id.endsWith(":the_nether")) return "Nether";
        if (id.endsWith(":the_end")) return "End";
        return id;
    }

    public static String markerSizeLabel() { return switch (markerSizeMode) { case 0 -> "Small"; case 2 -> "Large"; default -> "Medium"; }; }


    /** Canonical dimension ID. Older saved files may contain ResourceKey[...] wrappers. */
    public static String normalizeDimension(String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        // Minecraft 26.x ResourceKey.toString() can wrap the identifier in descriptive text.
        if (value.contains("minecraft:overworld")) return "minecraft:overworld";
        if (value.contains("minecraft:the_nether") || value.contains("minecraft:nether")) return "minecraft:the_nether";
        if (value.contains("minecraft:the_end") || value.contains("minecraft:end")) return "minecraft:the_end";
        // Support namespaced modded dimensions as well as older wrapper formats.
        int colon = value.indexOf(':');
        if (colon >= 1) {
            int start = colon - 1;
            while (start >= 0 && (Character.isLetterOrDigit(value.charAt(start)) || "_.-".indexOf(value.charAt(start)) >= 0)) start--;
            int end = colon + 1;
            while (end < value.length() && (Character.isLetterOrDigit(value.charAt(end)) || "_./-".indexOf(value.charAt(end)) >= 0)) end++;
            if (start + 1 < colon && end > colon + 1) return value.substring(start + 1, end);
        }
        return value.replace("resourcekey[", "").replace("]", "").trim();
    }

    public static String currentDimension() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "unknown";
        // Prefer the dimension key's identifier where available; fall back to its string form for mapping compatibility.
        try {
            Object key = mc.level.dimension();
            for (String methodName : new String[]{"identifier", "location"}) {
                try {
                    Object id = key.getClass().getMethod(methodName).invoke(key);
                    if (id != null) return normalizeDimension(id.toString());
                } catch (ReflectiveOperationException ignored) { }
            }
            return normalizeDimension(key.toString());
        } catch (Throwable ignored) {
            return normalizeDimension(mc.level.dimension().toString());
        }
    }

    /** Pick the next free default name only within this save/server and dimension. */
    public static String nextWaypointName() {
        String worldId = currentWorldId();
        String dimension = currentDimension();
        java.util.Set<Integer> used = new java.util.HashSet<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)^Waypoint\\s*(\\d+)$");
        for (Waypoint w : WAYPOINTS) {
            if (!java.util.Objects.equals(w.worldId(), worldId)
                    || !java.util.Objects.equals(normalizeDimension(w.dimension()), dimension)) continue;
            java.util.regex.Matcher matcher = pattern.matcher(w.name());
            if (matcher.matches()) {
                try { used.add(Integer.parseInt(matcher.group(1))); } catch (NumberFormatException ignored) { }
            }
        }
        int n = 1;
        while (used.contains(n)) n++;
        return "Waypoint" + n;
    }

    /** Stable scope for a waypoint: separate local save folders and multiplayer servers.
     * A world's display name is NOT unique (two saves can both be named "New World"),
     * so singleplayer identity is taken from the save-folder ID via reflection to avoid
     * hard-coding version-sensitive Minecraft storage API names.
     */
    public static String currentWorldId() {
        Minecraft mc = Minecraft.getInstance();
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "server:" + normalizeServerAddress(server.ip);
        }
        Object integratedServer = mc.getSingleplayerServer();
        if (integratedServer != null) {
            String saveId = findSingleplayerSaveId(integratedServer);
            if (saveId != null && !saveId.isBlank()) {
                return "singleplayer-save:" + saveId;
            }
            // Last-resort fallback only; display names can collide, but this is better than
            // returning unknown and mixing every local save together.
            try {
                String levelName = mc.getSingleplayerServer().getWorldData().getLevelName();
                if (levelName != null && !levelName.isBlank()) {
                    return "singleplayer-name:" + levelName.trim();
                }
            } catch (Throwable ignored) { }
        }
        return "unknown";
    }

    private static String normalizeServerAddress(String address) {
        return address.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
    }

    /** Discover the actual singleplayer save directory/ID without relying on unstable mapped names. */
    private static String findSingleplayerSaveId(Object integratedServer) {
        try {
            Object storage = invokeNoArg(integratedServer, "getStorageSource", "getStorageAccess", "getLevelStorage");
            if (storage == null) {
                for (Class<?> c = integratedServer.getClass(); c != null && storage == null; c = c.getSuperclass()) {
                    for (java.lang.reflect.Field field : c.getDeclaredFields()) {
                        String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                        if (!name.contains("storage") && !name.contains("levelsource")) continue;
                        try {
                            field.setAccessible(true);
                            Object candidate = field.get(integratedServer);
                            if (candidate != null) { storage = candidate; break; }
                        } catch (Throwable ignored) { }
                    }
                }
            }
            if (storage != null) {
                Object id = invokeNoArg(storage, "getLevelId", "getLevelDirectory", "getLevelPath", "getWorldPath");
                if (id instanceof Path path) return path.toAbsolutePath().normalize().toString();
                if (id != null && !id.toString().isBlank()) return id.toString();
                for (Class<?> c = storage.getClass(); c != null; c = c.getSuperclass()) {
                    for (java.lang.reflect.Field field : c.getDeclaredFields()) {
                        String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                        if (!name.contains("levelid") && !name.contains("leveldirectory") && !name.contains("path")) continue;
                        try {
                            field.setAccessible(true);
                            Object value = field.get(storage);
                            if (value instanceof Path path) return path.toAbsolutePath().normalize().toString();
                            if (value != null && !value.toString().isBlank()) return value.toString();
                        } catch (Throwable ignored) { }
                    }
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static Object invokeNoArg(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Method method = c.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (Throwable ignored) { }
            }
            try {
                java.lang.reflect.Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (Throwable ignored) { }
        }
        return null;
    }

    static void add(String name, int x, int y, int z) {
        int color = COLORS[WAYPOINTS.size() % 8]; // red, green, blue, ... (skips chroma)
        WAYPOINTS.add(new Waypoint(name, x, y, z, color, currentDimension(), 0L, currentWorldId(), "W"));
        save();
    }

    static void addHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos p = mc.player.blockPosition();
        add(nextWaypointName(), p.getX(), p.getY(), p.getZ());
    }

    static void remove(Waypoint w) {
        WAYPOINTS.remove(w);
        save();
    }

    /** Write all saved waypoints to a portable JSON export in the config directory. */
    public static String exportWaypointsToFile() {
        try {
            List<Waypoint> activeWorldWaypoints = waypointsForActiveWorld();
            Files.createDirectories(EXPORT_FILE.getParent());
            Files.writeString(EXPORT_FILE, GSON.toJson(activeWorldWaypoints, LIST_TYPE));
            return "Exported " + activeWorldWaypoints.size() + " waypoints to " + EXPORT_FILE.toAbsolutePath();
        } catch (IOException e) {
            return "Waypoint export failed: " + e.getMessage();
        }
    }

    /**
     * Import waypoints from config/simple-waypoints-import.json into the active world.
     * Matching names in the same dimension are updated; other entries are appended.
     */
    public static String importWaypointsFromFile() {
        if (!Files.exists(IMPORT_FILE)) {
            return "Import file not found. Place a waypoint JSON file at " + IMPORT_FILE.toAbsolutePath();
        }
        try {
            List<Waypoint> imported = GSON.fromJson(Files.readString(IMPORT_FILE), LIST_TYPE);
            if (imported == null || imported.isEmpty()) return "The import file contains no waypoints.";
            String activeWorld = currentWorldId();
            int added = 0;
            int updated = 0;
            for (Waypoint source : imported) {
                if (source == null || source.name() == null || source.name().isBlank()) continue;
                String dimension = normalizeDimension(source.dimension());
                Waypoint mapped = new Waypoint(source.name().trim(), source.x(), source.y(), source.z(),
                        source.color(), dimension, source.expiresAt(), activeWorld, source.safeMarkerText(),
                        source.isLabelEnabled(), source.isBeamEnabled());
                int existing = -1;
                for (int i = 0; i < WAYPOINTS.size(); i++) {
                    Waypoint current = WAYPOINTS.get(i);
                    if (java.util.Objects.equals(current.worldId(), activeWorld)
                            && current.name().equalsIgnoreCase(mapped.name())
                            && normalizeDimension(current.dimension()).equals(dimension)) {
                        existing = i;
                        break;
                    }
                }
                if (existing >= 0) { WAYPOINTS.set(existing, mapped); updated++; }
                else { WAYPOINTS.add(mapped); added++; }
            }
            if (added + updated > 0) save();
            return "Imported " + added + " new and updated " + updated + " waypoints into this world.";
        } catch (Exception e) {
            return "Waypoint import failed: " + e.getMessage();
        }
    }

    /** Adds a new waypoint (old == null) or replaces an existing one. */
    static void upsert(Waypoint old, Waypoint neu) {
        if (old == null) {
            WAYPOINTS.add(neu);
            save();
        } else {
            replace(old, neu);
        }
    }

    /** Given text from a color box, returns the name of the next color in the cycle. */
    static String nextColorName(String current) {
        Integer c = parseColor(current.trim());
        int idx = -1;
        for (int i = 0; i < COLORS.length; i++) {
            if (c != null && COLORS[i] == c) idx = i;
        }
        return COLOR_NAMES[(idx + 1) % COLOR_NAMES.length];
    }

    private static void replace(Waypoint old, Waypoint neu) {
        int i = WAYPOINTS.indexOf(old);
        if (i >= 0) {
            WAYPOINTS.set(i, neu);
            save();
        }
    }

    /** Next color in the cycle: red > green > blue > yellow > pink > cyan > orange > white > rainbow. */
    static void cycleColor(Waypoint w) {
        int idx = 0;
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i] == w.color()) idx = i;
        }
        int next = COLORS[(idx + 1) % COLORS.length];
        replace(w, new Waypoint(w.name(), w.x(), w.y(), w.z(), next, w.dimension(), w.expiresAt(), w.worldId(), w.safeMarkerText(), w.isLabelEnabled(), w.isBeamEnabled()));
    }

    /** Timer cycle: permanent > 60s > 5 min > permanent. */
    static void cycleTimer(Waypoint w) {
        long now = System.currentTimeMillis();
        long next;
        if (w.expiresAt() == 0) next = now + 60_000L;
        else if (w.expiresAt() - now < 120_000L) next = now + 300_000L;
        else next = 0L;
        replace(w, new Waypoint(w.name(), w.x(), w.y(), w.z(), w.color(), w.dimension(), next, w.worldId(), w.safeMarkerText(), w.isLabelEnabled(), w.isBeamEnabled()));
    }

    private static boolean update(String name, UnaryOperator<Waypoint> fn) {
        boolean found = false;
        for (int i = 0; i < WAYPOINTS.size(); i++) {
            Waypoint w = WAYPOINTS.get(i);
            if (w.name().equalsIgnoreCase(name)) {
                WAYPOINTS.set(i, fn.apply(w));
                found = true;
            }
        }
        if (found) save();
        return found;
    }

    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        if (WAYPOINTS.removeIf(w -> w.expiresAt() > 0 && now > w.expiresAt())) save();
    }

    static Integer parseColor(String s) {
        switch (s.toLowerCase()) {
            case "red": return 0xFF0000;
            case "green": return 0x00FF00;
            case "blue": return 0x3366FF;
            case "yellow": return 0xFFFF00;
            case "pink": return 0xFF55FF;
            case "cyan": return 0x00FFFF;
            case "orange": return 0xFF9900;
            case "white": return 0xFFFFFF;
            case "chroma": case "rainbow": return CHROMA;
            default:
                String hex = s.startsWith("#") ? s.substring(1) : s;
                if (hex.length() != 6) return null;
                try {
                    return Integer.parseInt(hex, 16);
                } catch (NumberFormatException e) {
                    return null;
                }
        }
    }

    /** Actual 0xRRGGBB to draw this frame (handles the rainbow effect). */
    public static int resolveColor(Waypoint w) {
        if (w.color() != CHROMA) return w.color() & 0xFFFFFF;
        float h = (System.currentTimeMillis() % 4000L) / 4000f * 6f;
        int i = (int) h;
        float f = h - i;
        float q = 1f - f;
        float r, g, b;
        switch (i % 6) {
            case 0: r = 1; g = f; b = 0; break;
            case 1: r = q; g = 1; b = 0; break;
            case 2: r = 0; g = 1; b = f; break;
            case 3: r = 0; g = q; b = 1; break;
            case 4: r = f; g = 0; b = 1; break;
            default: r = 1; g = 0; b = q; break;
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static void load() {
        try {
            if (Files.exists(FILE)) {
                List<Waypoint> list = GSON.fromJson(Files.readString(FILE), LIST_TYPE);
                if (list != null) WAYPOINTS.addAll(list);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(WAYPOINTS, LIST_TYPE));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadPreferences() {
        Properties p = new Properties();
        try {
            if (Files.exists(SETTINGS_FILE)) {
                try (var in = Files.newInputStream(SETTINGS_FILE)) { p.load(in); }
            }
            showWaypointLabels = Boolean.parseBoolean(p.getProperty("showLabels", "true"));
            showWaypointBeams = Boolean.parseBoolean(p.getProperty("showBeams", "true"));
            showWaypointDistances = Boolean.parseBoolean(p.getProperty("showDistances", "true"));
            showWaypointCoordinates = Boolean.parseBoolean(p.getProperty("showCoordinates", "false"));
            matchWaypointColor = Boolean.parseBoolean(p.getProperty("matchWaypointColor", "true"));
            currentDimensionOnly = Boolean.parseBoolean(p.getProperty("currentDimensionOnly", "true"));
            hideHudWhenMenuOpen = Boolean.parseBoolean(p.getProperty("hideHudWhenMenuOpen", "true"));
            smoothUiEdges = Boolean.parseBoolean(p.getProperty("smoothUiEdges", "true"));
            String accent = p.getProperty("uiAccentColor", "B7F36B").replace("#", "").trim();
            uiAccentColor = 0xFF000000 | ((int) Long.parseLong(accent, 16) & 0xFFFFFF);
            markerSizeMode = Math.max(0, Math.min(2, Integer.parseInt(p.getProperty("labelSize", "1"))));
            beamOpacity = Math.max(0, Math.min(100, Integer.parseInt(p.getProperty("beamOpacity", "75"))));
            maxDisplayDistance = Math.max(0, Integer.parseInt(p.getProperty("maxDisplayDistance", "0")));
        } catch (Exception e) {
            System.err.println("[Simple Waypoints] Could not read settings; using defaults.");
        }
    }

    public static void savePreferences() {
        Properties p = new Properties();
        p.setProperty("showLabels", Boolean.toString(showWaypointLabels));
        p.setProperty("showBeams", Boolean.toString(showWaypointBeams));
        p.setProperty("showDistances", Boolean.toString(showWaypointDistances));
        p.setProperty("showCoordinates", Boolean.toString(showWaypointCoordinates));
        p.setProperty("matchWaypointColor", Boolean.toString(matchWaypointColor));
        p.setProperty("currentDimensionOnly", Boolean.toString(currentDimensionOnly));
        p.setProperty("hideHudWhenMenuOpen", Boolean.toString(hideHudWhenMenuOpen));
        p.setProperty("smoothUiEdges", Boolean.toString(smoothUiEdges));
        p.setProperty("uiAccentColor", String.format(java.util.Locale.ROOT, "%06X", uiAccentColor & 0xFFFFFF));
        p.setProperty("labelSize", Integer.toString(markerSizeMode));
        p.setProperty("beamOpacity", Integer.toString(beamOpacity));
        p.setProperty("maxDisplayDistance", Integer.toString(maxDisplayDistance));
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (var out = Files.newOutputStream(SETTINGS_FILE)) { p.store(out, "Simple Waypoints preferences"); }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---------------- HUD rendering (beam + label) ----------------
    private static final double BEAM_HEIGHT = 320.0;
    private static final double CORE_HALF_WIDTH_BLOCKS = 0.22;
    private static final double GLOW_HALF_WIDTH_BLOCKS = 0.6;

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!showWaypointLabels || mc.gui.screen() != null || mc.player == null || mc.level == null || WAYPOINTS.isEmpty()) return;

        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        Vec3 playerEye = mc.player.getEyePosition(partial);
        Vec3 eye = playerEye;
        double yawDeg = mc.player.getViewYRot(partial);
        double pitchDeg = mc.player.getViewXRot(partial);
        // Use the real render camera when we can (Freecam, F5 and similar), so the label lines up with the beam.
        double[] cam = cameraState(mc);
        if (cam != null) {
            eye = new Vec3(cam[0], cam[1], cam[2]);
            yawDeg = cam[3];
            pitchDeg = cam[4];
        }
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);

        // Camera basis vectors (Minecraft: yaw 0 looks toward +Z).
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double ry = 0;
        double rz = -Math.sin(yaw);
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;
        double[] f = {fx, fy, fz};
        double[] r = {rx, ry, rz};
        double[] u = {ux, uy, uz};

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double aspect = (double) sw / (double) sh;
        double tanHalfFov = Math.tan(Math.toRadians(effectiveRenderFov(mc, partial)) / 2.0);

        Font font = mc.font;
        long gameTick = mc.level.getGameTime();
        if (gameTick != hudScopeTick) {
            hudWorldId = currentWorldId();
            hudDimension = currentDimension();
            hudScopeTick = gameTick;
        }
        String dim = hudDimension;
        String worldId = hudWorldId;

        // Keep the HUD work bounded. The label collision pass uses fixed arrays so it
        // does not allocate a rectangle object for every waypoint on every frame.
        final int maxLabels = 16;
        int[] occupiedLeft = new int[maxLabels];
        int[] occupiedTop = new int[maxLabels];
        int[] occupiedRight = new int[maxLabels];
        int[] occupiedBottom = new int[maxLabels];
        int renderedLabels = 0;

        // Nearest waypoints first, so a close label always wins a crowded spot.
        java.util.List<Waypoint> ordered = new java.util.ArrayList<>(WAYPOINTS);
        final Vec3 sortEye = playerEye;
        ordered.sort(java.util.Comparator.comparingDouble(o -> {
            double ox = o.x() + 0.5 - sortEye.x;
            double oy = o.y() + 0.5 - sortEye.y;
            double oz = o.z() + 0.5 - sortEye.z;
            return ox * ox + oy * oy + oz * oz;
        }));

        long nowMs = System.nanoTime() / 1_000_000L;
        double frameSeconds = Math.max(0.0, Math.min(0.1, (nowMs - lastLabelFrameMs) / 1000.0));
        lastLabelFrameMs = nowMs;
        double easeFactor = 1.0 - Math.exp(-frameSeconds * 16.0);
        pruneLabelAnims(nowMs);

        for (Waypoint w : ordered) {
            if (renderedLabels >= maxLabels) break;
            if (!java.util.Objects.equals(w.worldId(), worldId)) continue;
            if (!dimensionMatches(w, dim)) continue;

            double bx = w.x() + 0.5 - eye.x;
            double by = w.y() - eye.y;
            double bz = w.z() + 0.5 - eye.z;
            int rgb = resolveColor(w);

            // Waypoint beams are rendered only as 3D BeaconRenderer geometry.
            // Never fall back to the old screen-space billboard: it looked like a flat
            // rectangle and changed shape/disappeared as the camera rotated.

            // Labels can be disabled from the waypoint menu. Hide the label only when you are standing
            // almost inside the waypoint.
            if (!showWaypointLabels) continue;
            double pdx = w.x() + 0.5 - playerEye.x;
            double pdy = w.y() + 0.5 - playerEye.y;
            double pdz = w.z() + 0.5 - playerEye.z;
            double dist = Math.sqrt(pdx * pdx + pdy * pdy + pdz * pdz);
            if (dist < 1.0) continue;

            // Name + distance label. Constant size, floats above the waypoint, never grows.
            double dx = bx;
            double dy = by + 2.5;
            double dz = bz;
            double[] p = project(dx, dy, dz, f, r, u, sw, sh, tanHalfFov, aspect);
            if (p == null) continue;
            int sx = (int) p[0];
            int sy = (int) p[1];
            if (sx < -120 || sx > sw + 120 || sy < -80 || sy > sh + 80) continue;

            String distanceLine = formatDistance(dist) + " away";
            LabelLook look = currentLook();
            int labelWidth = pillWidth(font, w.name(), distanceLine, look);
            int labelHeight = look.height;
            int step = labelHeight + 3;

            // Pick the first spot that does not overlap a label that is already placed.
            int targetOffset = 0;
            boolean found = false;
            for (int order : LABEL_OFFSET_ORDER) {
                int offset = order * step;
                int left = sx - labelWidth / 2;
                int top = sy + offset - labelHeight / 2;
                int right = left + labelWidth;
                int bottom = top + labelHeight;

                boolean overlaps = false;
                for (int i = 0; i < renderedLabels; i++) {
                    if (left < occupiedRight[i] + 3 && right + 3 > occupiedLeft[i]
                            && top < occupiedBottom[i] + 3 && bottom + 3 > occupiedTop[i]) {
                        overlaps = true;
                        break;
                    }
                }
                if (overlaps) continue;

                occupiedLeft[renderedLabels] = left;
                occupiedTop[renderedLabels] = top;
                occupiedRight[renderedLabels] = right;
                occupiedBottom[renderedLabels] = bottom;
                targetOffset = offset;
                found = true;
                break;
            }
            if (!found) continue; // too crowded: hide this one instead of overlapping

            // Smooth motion: ease to the chosen spot, fade in, and bob gently so the label floats.
            LabelAnim anim = LABEL_ANIMS.computeIfAbsent(w.hashCode(), k -> new LabelAnim(nowMs));
            anim.lastSeen = nowMs;
            if (!anim.placed) {
                anim.offset = targetOffset;
                anim.placed = true;
            }
            anim.offset += (targetOffset - anim.offset) * easeFactor;
            double phase = (w.hashCode() & 0xFF) / 255.0 * Math.PI * 2.0;
            double bob = Math.sin(nowMs / 1000.0 * 1.8 + phase) * 1.6;
            int labelCenterY = (int) Math.round(sy + anim.offset + bob);
            float appear = (float) Math.min(1.0, (nowMs - anim.firstSeen) / 250.0);

            // Fade the label when the world is in front of it so it does not look painted over blocks or mobs.
            Vec3 labelWorldPos = new Vec3(w.x() + 0.5, w.y() + 2.5, w.z() + 0.5);
            float depthOpacity = labelDepthOpacity(mc, eye, labelWorldPos);
            drawPill(graphics, font, w.name(), distanceLine, rgb, sx, labelCenterY, labelWidth, look, depthOpacity * appear);
            renderedLabels++;
        }
    }

    // ---------------- real camera lookup (reflection, falls back to the player's view) ----------------
    private static java.lang.reflect.Method CAM_GET, CAM_POS, CAM_YROT, CAM_XROT;
    private static boolean camBroken = false;
    private static java.lang.reflect.Method CAM_FOV;
    private static boolean fovMethodChecked = false;
    private static long hudScopeTick = Long.MIN_VALUE;
    private static String hudWorldId = "unknown";
    private static String hudDimension = "unknown";

    private static java.lang.reflect.Method findMethod(Class<?> c, String... names) {
        for (String n : names) {
            try {
                return c.getMethod(n);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    /**
     * Ask Minecraft's renderer for the effective FOV, rather than reading the user option directly.
     * This includes vanilla camera effects and is a better match for the projection used to draw the world.
     * Reflection keeps this compatible with small mapping/signature changes across game versions.
     */
    private static double effectiveRenderFov(Minecraft mc, float partialTick) {
        try {
            Object renderer = mc.gameRenderer;
            Object camera = null;
            if (CAM_GET == null) CAM_GET = findMethod(renderer.getClass(), "getMainCamera", "mainCamera");
            if (CAM_GET != null) camera = CAM_GET.invoke(renderer);
            if (camera != null) {
                if (!fovMethodChecked) {
                    fovMethodChecked = true;
                    for (java.lang.reflect.Method method : renderer.getClass().getMethods()) {
                        if (!method.getName().equals("getFov") || method.getParameterCount() != 3) continue;
                        Class<?>[] p = method.getParameterTypes();
                        if (p[0].isInstance(camera) && (p[1] == float.class || p[1] == Float.class)
                                && (p[2] == boolean.class || p[2] == Boolean.class)) {
                            CAM_FOV = method;
                            break;
                        }
                    }
                }
                if (CAM_FOV != null) {
                    Object value = CAM_FOV.invoke(renderer, camera, partialTick, true);
                    if (value instanceof Number n && n.doubleValue() > 1.0 && n.doubleValue() < 179.0) {
                        return n.doubleValue();
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the configured FOV if the game renderer API differs.
        }
        return mc.options.fov().get();
    }

    /** Returns {x, y, z, yawDegrees, pitchDegrees} of the camera that is actually used to draw the world, or null. */
    public static double[] cameraState(Minecraft mc) {
        if (camBroken) return null;
        try {
            Object gr = mc.gameRenderer;
            if (CAM_GET == null) {
                CAM_GET = findMethod(gr.getClass(), "getMainCamera", "mainCamera");
                if (CAM_GET == null) {
                    camBroken = true;
                    return null;
                }
            }
            Object cam = CAM_GET.invoke(gr);
            if (cam == null) return null;
            if (CAM_POS == null) {
                CAM_POS = findMethod(cam.getClass(), "position", "getPosition");
                CAM_YROT = findMethod(cam.getClass(), "yRot", "getYRot");
                CAM_XROT = findMethod(cam.getClass(), "xRot", "getXRot");
                if (CAM_POS == null || CAM_YROT == null || CAM_XROT == null) {
                    camBroken = true;
                    return null;
                }
            }
            Vec3 pos = (Vec3) CAM_POS.invoke(cam);
            float yRot = ((Number) CAM_YROT.invoke(cam)).floatValue();
            float xRot = ((Number) CAM_XROT.invoke(cam)).floatValue();
            if (pos == null) return null;
            return new double[]{pos.x, pos.y, pos.z, yRot, xRot};
        } catch (Throwable t) {
            camBroken = true;
            return null;
        }
    }

    /**
     * Returns the opacity for a label based on what is between the camera and its
     * world anchor. Since this label is a HUD element, it cannot use the scene depth
     * buffer directly. A short world ray gives it useful depth-aware behaviour:
     * blocks soften it, while mobs and dropped items soften it more strongly.
     * The label is never removed completely, so distant waypoints remain findable.
     */
    private static float labelDepthOpacity(Minecraft mc, Vec3 from, Vec3 to) {
        float opacity = 1.0f;
        double distance = from.distanceTo(to);
        if (distance < 0.05) return opacity;

        try {
            var blockHit = mc.level.clip(new ClipContext(
                    from, to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    mc.player
            ));
            if (blockHit.getType() != HitResult.Type.MISS) {
                opacity = 0.72f;
            }
        } catch (Throwable ignored) {
            // Keep labels readable if a future game version changes clip behaviour.
        }

        // Avoid a huge broad-phase entity query for very distant waypoints. The
        // nearest 48 blocks are where foreground mobs/items most often cross labels.
        try {
            Vec3 probeEnd = distance > 48.0 ? from.lerp(to, 48.0 / distance) : to;
            AABB rayBounds = new AABB(from, probeEnd).inflate(0.18);
            List<Entity> foreground = mc.level.getEntities(mc.player, rayBounds,
                    entity -> entity.isAlive() && !entity.isSpectator());
            double probeDistanceSquared = from.distanceToSqr(probeEnd);
            for (Entity entity : foreground) {
                var hit = entity.getBoundingBox().inflate(0.08).clip(from, probeEnd);
                if (hit.isPresent() && from.distanceToSqr(hit.get()) < probeDistanceSquared - 0.04) {
                    return 0.48f;
                }
            }
        } catch (Throwable ignored) {
            // Entity fading is a visual enhancement; a failure must not hide waypoints.
        }
        return opacity;
    }

    // ---------------- Crisp waypoint marker ----------------
    // Draw the label using Minecraft's standard UI font and simple GUI geometry.
    // Avoid matrix scaling here: scaling extracted GUI text can cause glyph clipping/jitter.

    private static String formatDistance(double d) {
        if (d < 10.0) return (Math.round(d * 10.0) / 10.0) + " m";
        if (d < 1000.0) return Math.round(d) + " m";
        if (d < 10_000.0) return (Math.round(d / 100.0) / 10.0) + " km";
        return Math.round(d / 1000.0) + " km";
    }

    // ---------------- pill label ----------------
    // ---------------- waypoint label: constant size, connected outline, floats above the beam ----------------
    /** Layout of the label for the three "Logo" sizes. Constant sizes only, no matrix scaling, so edges stay sharp. */
    private static final class LabelLook {
        final int padX;
        final int height;
        final int nameY;
        final int metaY;

        LabelLook(int padX, int height, int nameY, int metaY) {
            this.padX = padX;
            this.height = height;
            this.nameY = nameY;
            this.metaY = metaY;
        }
    }

    private static final LabelLook LOOK_SMALL = new LabelLook(5, 22, 3, 12);
    private static final LabelLook LOOK_MEDIUM = new LabelLook(7, 25, 4, 13);
    private static final LabelLook LOOK_LARGE = new LabelLook(9, 29, 5, 15);

    private static LabelLook currentLook() {
        return markerSizeMode == 0 ? LOOK_SMALL : markerSizeMode == 2 ? LOOK_LARGE : LOOK_MEDIUM;
    }

    /** Per-label animation state: eases to its spot (no popping), fades in, and bobs gently. */
    private static final class LabelAnim {
        final long firstSeen;
        long lastSeen;
        double offset;
        boolean placed;

        LabelAnim(long now) {
            this.firstSeen = now;
            this.lastSeen = now;
        }
    }

    private static final java.util.HashMap<Integer, LabelAnim> LABEL_ANIMS = new java.util.HashMap<>();
    private static long labelFrame = 0;
    /** Try the anchor first, then spots above and below, in label-heights (nearest labels are placed first). */
    private static final int[] LABEL_OFFSET_ORDER = {0, -1, 1, -2, 2, -3, 3, -4, 4};

    private static void pruneLabelAnims(long nowMs) {
        if ((++labelFrame % 120) != 0) return;
        LABEL_ANIMS.values().removeIf(a -> nowMs - a.lastSeen > 1500);
    }

    private static String fitPillName(Font font, String name) {
        String displayName = name == null || name.isBlank() ? "Waypoint" : name.trim();
        final int maxTextWidth = 110;
        if (font.width(displayName) > maxTextWidth) {
            while (displayName.length() > 1 && font.width(displayName + "...") > maxTextWidth) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            displayName += "...";
        }
        return displayName;
    }

    private static int pillWidth(Font font, String name, String distanceLine, LabelLook look) {
        int nameWidth = font.width(fitPillName(font, name));
        int metaWidth = font.width(distanceLine);
        return Math.max(46, Math.max(nameWidth, metaWidth) + look.padX * 2);
    }

    private static int applyOpacity(int argb, float opacity) {
        int originalAlpha = (argb >>> 24) & 0xFF;
        int alpha = Math.max(0, Math.min(255, Math.round(originalAlpha * opacity)));
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * Two-line label: name, then distance. Dark body with a 1 px outline in the waypoint color.
     * Everything is drawn at whole-pixel positions with no scaling, so the outline is one continuous
     * connected line (corners step diagonally) and never breaks into pieces.
     */
    private static void drawPill(GuiGraphicsExtractor g, Font font, String name, String distanceLine,
                                 int rgb, int cx, int cy, int width, LabelLook look, float opacity) {
        if (opacity <= 0.01f) return;
        String displayName = fitPillName(font, name);
        int nameWidth = font.width(displayName);
        int metaWidth = font.width(distanceLine);
        int w = width;
        int h = look.height;
        int x = cx - w / 2;
        int y = cy - h / 2;

        int body = applyOpacity(0xD00B0D10, opacity);
        int outline = applyOpacity(0xFF000000 | (rgb & 0xFFFFFF), opacity);
        int shadow = applyOpacity(0x55000000, opacity);

        // soft shadow under the label
        g.fill(x + 2, y + h, x + w - 2, y + h + 1, shadow);

        // body (a plus shape, so the four corner pixels stay empty)
        g.fill(x + 2, y + 1, x + w - 2, y + h - 1, body);
        g.fill(x + 1, y + 2, x + w - 1, y + h - 2, body);

        // outline: four edges plus one pixel at each corner, so the line is closed all the way round
        g.fill(x + 2, y, x + w - 2, y + 1, outline);                 // top
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, outline);         // bottom
        g.fill(x, y + 2, x + 1, y + h - 2, outline);                 // left
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, outline);         // right
        g.fill(x + 1, y + 1, x + 2, y + 2, outline);                 // top-left corner
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, outline);         // top-right corner
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, outline);         // bottom-left corner
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, outline); // bottom-right corner

        // text, centered
        float textOpacity = Math.min(1.0f, opacity + 0.20f);
        int nameX = x + (w - nameWidth) / 2;
        int metaX = x + (w - metaWidth) / 2;
        g.text(font, displayName, nameX, y + look.nameY, applyOpacity(0xFFF3F4F6, textOpacity), false);
        g.text(font, distanceLine, metaX, y + look.metaY, applyOpacity(0xFFAEB6C2, textOpacity), false);
    }

    private static long lastLabelFrameMs = 0L;

    /** Projects a world offset (relative to the eye) to {screenX, screenY, depth}, or null if behind the camera. */
    private static double[] project(double dx, double dy, double dz, double[] f, double[] r, double[] u,
                                    int sw, int sh, double tanHalfFov, double aspect) {
        double depth = dx * f[0] + dy * f[1] + dz * f[2];
        if (depth < 0.1) return null;
        double side = dx * r[0] + dy * r[1] + dz * r[2];
        double up = dx * u[0] + dy * u[1] + dz * u[2];
        double ndcX = side / (depth * tanHalfFov * aspect);
        double ndcY = up / (depth * tanHalfFov);
        return new double[]{sw / 2.0 + ndcX * sw / 2.0, sh / 2.0 - ndcY * sh / 2.0, depth};
    }

    /**
     * Draws a tall beam: a bright core plus a soft glow. A straight line in 3D is a straight line on screen,
     * so it is drawn row by row. Edges are anti-aliased with partly transparent pixels so the width
     * changes smoothly (no popping or flicker) while you walk.
     */
    private static void drawBeam(GuiGraphicsExtractor g, double bx, double by, double bz,
                                 double[] f, double[] r, double[] u, int sw, int sh,
                                 double tanHalfFov, double aspect, int rgb, double fade) {
        double near = 0.1;
        double d0 = bx * f[0] + by * f[1] + bz * f[2];
        double d1 = d0 + BEAM_HEIGHT * f[1];
        if (d0 < near && d1 < near) return;

        double t0 = 0.0;
        double t1 = 1.0;
        if (d0 < near) t0 = (near - d0) / (d1 - d0);
        else if (d1 < near) t1 = (near - d0) / (d1 - d0);

        double[] a = project(bx, by + t0 * BEAM_HEIGHT, bz, f, r, u, sw, sh, tanHalfFov, aspect);
        double[] b = project(bx, by + t1 * BEAM_HEIGHT, bz, f, r, u, sw, sh, tanHalfFov, aspect);
        if (a == null || b == null) return;

        double pxPerInvDepth = (sh / 2.0) / tanHalfFov;
        double invA = 1.0 / a[2];
        double invB = 1.0 / b[2];
        double yA = a[1];
        double yB = b[1];

        if (Math.abs(yB - yA) < 1.0) {
            double hw = Math.max(3.0, CORE_HALF_WIDTH_BLOCKS * pxPerInvDepth * invA);
            int cx = (int) a[0];
            int cy = (int) yA;
            g.fill(cx - (int) hw, cy - (int) hw, cx + (int) hw, cy + (int) hw, argb(1.0 * fade, rgb));
            return;
        }

        int rowStart = (int) Math.max(0, Math.floor(Math.min(yA, yB)));
        int rowEnd = (int) Math.min(sh, Math.ceil(Math.max(yA, yB)));
        final int step = 3;
        for (int sy = rowStart; sy < rowEnd; sy += step) {
            double frac = (sy + step / 2.0 - yA) / (yB - yA);
            if (frac < 0.0 || frac > 1.0) continue;
            double sx = a[0] + frac * (b[0] - a[0]);
            double inv = invA + frac * (invB - invA);

            // soft glow first, bright core on top
            double bw = 1.0;
            double coreHalf = Math.max(3.0 * bw, CORE_HALF_WIDTH_BLOCKS * bw * pxPerInvDepth * inv);
            double glowHalf = Math.max(7.0 * bw, GLOW_HALF_WIDTH_BLOCKS * bw * pxPerInvDepth * inv);
            drawRowSpan(g, sx, sy, step, glowHalf, 0.18 * fade, rgb, sw);
            drawRowSpan(g, sx, sy, step, coreHalf, 1.0 * fade, rgb, sw);
        }
    }

    /** One horizontal slice of the beam with smooth (anti-aliased) left and right edges. */
    private static void drawRowSpan(GuiGraphicsExtractor g, double centerX, int sy, int rows, double halfWidth,
                                    double alpha, int rgb, int sw) {
        double left = centerX - halfWidth;
        double right = centerX + halfWidth;
        if (right < 0 || left > sw) return;

        int li = (int) Math.ceil(left);
        int ri = (int) Math.floor(right);
        if (ri > li) {
            g.fill(li, sy, ri, sy + rows, argb(alpha, rgb));
        }
        double leftCover = li - left;   // part of the pixel left of the span that is covered
        if (leftCover > 0.02) {
            g.fill(li - 1, sy, li, sy + rows, argb(alpha * Math.min(1.0, leftCover), rgb));
        }
        double rightCover = right - Math.max(ri, li);
        if (rightCover > 0.02) {
            int rx = Math.max(ri, li);
            g.fill(rx, sy, rx + 1, sy + rows, argb(alpha * Math.min(1.0, rightCover), rgb));
        }
    }

    private static int argb(double alpha, int rgb) {
        int a = (int) Math.round(255.0 * Math.max(0.0, Math.min(1.0, alpha)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
