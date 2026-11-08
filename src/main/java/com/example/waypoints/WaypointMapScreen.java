package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Dedicated waypoint overview map. The background is a stylized local preview, not a live world-map renderer. */
public final class WaypointMapScreen extends Screen {
    private static final int BG = 0xFF0B0C0E;
    private static final int PANEL = 0xFF131719;
    private static final int EDGE = 0xFF29312B;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFFA3ABB0;
    private static final int ACCENT = 0xFFB7F36B;
    private List<Waypoint> waypoints = List.of();

    public WaypointMapScreen() { super(Component.literal("Waypoint Map")); }

    @Override protected void init() { waypoints = WaypointsMod.menuWaypoints(); }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int rail = width >= 1000 ? 180 : 62;
        g.fill(0, 0, rail, height, 0xFF101315);
        g.fill(rail - 1, 0, rail, height, EDGE);
        g.fill(18, 18, 48, 48, ACCENT);
        WaypointUiText.draw(g, font, "SW", 24, 28, 0xFF101410, true);
        nav(g, rail, 86, "Waypoints", false);
        nav(g, rail, 130, "Map", true);
        nav(g, rail, 174, "Settings", false);
        int x = rail + 28;
        int right = width - 28;
        WaypointUiText.drawTitle(g, font, "Surface Map", x, 28, TEXT);
        WaypointUiText.draw(g, font, "Explore your saved locations", x, 58, MUTED, false);
        button(g, right - 116, 26, 116, 32, "Back to Waypoints", mx, my);
        int mapY = 100;
        int mapH = Math.max(180, height - 145);
        SurfaceMapPreview.draw(g, font, x, mapY, Math.max(260, right - x), mapH,
                waypoints.isEmpty() ? 0 : waypoints.get(0).x(),
                waypoints.isEmpty() ? 0 : waypoints.get(0).z(),
                WaypointsMod.currentDimension(), 0xFFB7F36B,
                waypoints.isEmpty() ? "No waypoints yet" : waypoints.get(0).name(),
                waypoints.isEmpty() ? "Create a waypoint to see it here" : waypoints.get(0).x() + ", " + waypoints.get(0).z());
        int count = Math.min(waypoints.size(), 8);
        int legendW = Math.min(230, Math.max(150, (right - x) / 3));
        int lx = x + 14, ly = mapY + 14;
        g.fill(lx, ly, lx + legendW, ly + 28 + count * 24, 0xE80A0D0E);
        WaypointUiText.draw(g, font, "WAYPOINTS  ·  " + waypoints.size(), lx + 10, ly + 9, ACCENT, false);
        for (int i = 0; i < count; i++) {
            Waypoint w = waypoints.get(i);
            int yy = ly + 31 + i * 24;
            int color = WaypointsMod.resolveColor(w) | 0xFF000000;
            g.fill(lx + 10, yy + 3, lx + 19, yy + 12, color);
            WaypointUiText.draw(g, font, w.name(), lx + 27, yy + 2, TEXT, false);
        }
        WaypointUiText.draw(g, font, "Map preview is schematic; waypoint coordinates and navigation remain exact.", x, height - 26, MUTED, false);
        super.extractRenderState(g, mx, my, delta);
    }

    private void nav(GuiGraphicsExtractor g, int rail, int y, String label, boolean active) {
        int x = 10, w = rail - 20;
        if (active) g.fill(x, y, x + w, y + 32, 0xFF26371A);
        WaypointUiText.draw(g, font, label, x + 10, y + 10, active ? ACCENT : MUTED, false);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my) {
        g.fill(x, y, x + w, y + h, mx >= x && mx <= x + w && my >= y && my <= y + h ? 0xFF29361F : PANEL);
        WaypointUiText.draw(g, font, label, x + 9, y + 11, TEXT, false);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            int rail = width >= 1000 ? 180 : 62;
            if (event.x() >= rail && event.y() >= 18 && event.y() <= 70) {
                Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)); return true;
            }
            if (event.x() >= 10 && event.x() <= rail - 10) {
                if (event.y() >= 86 && event.y() <= 118) { Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)); return true; }
                if (event.y() >= 174 && event.y() <= 206) { Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()); return true; }
            }
            if (event.x() >= width - 150 && event.y() >= 20 && event.y() <= 65) {
                Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)); return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
