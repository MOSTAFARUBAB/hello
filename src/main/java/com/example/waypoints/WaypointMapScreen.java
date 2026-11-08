package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Dedicated map workspace with waypoint markers and the shared neon sidebar. */
public final class WaypointMapScreen extends Screen {
    private static final int BG = 0xFF0B0C0E;
    private static final int PANEL = 0xFF131719;
    private static final int EDGE = 0xFF29312B;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFFA3ABB0;
    private static final int ACCENT = 0xFFB7F36B;
    private static final int RAIL = 176;
    private List<Waypoint> waypoints = List.of();
    private int mapX, mapY, mapW, mapH;

    public WaypointMapScreen() { super(Component.literal("Waypoint Map")); }

    @Override protected void init() { waypoints = WaypointsMod.menuWaypoints(); }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawSidebar(g, mx, my);
        int x = RAIL + 24;
        WaypointUiText.drawTitle(g, font, "Surface Map", x, 28, TEXT);
        WaypointUiText.draw(g, font, "Explore your saved locations", x, 56, MUTED, false);
        mapX = x;
        mapY = 94;
        mapW = Math.max(280, width - mapX - 26);
        mapH = Math.max(190, height - mapY - 48);
        drawMap(g);
        super.extractRenderState(g, mx, my, delta);
    }

    private void drawMap(GuiGraphicsExtractor g) {
        int top = mapY, bottom = mapY + mapH;
        rounded(g, mapX, top, mapX + mapW, bottom, 0xFF13251B, 11);
        border(g, mapX, top, mapX + mapW, bottom, EDGE);
        // A stylized overhead biome surface, built from broad terrain regions and contour paths.
        for (int band = 0; band < 9; band++) {
            int bx = mapX + 1 + band * (mapW - 2) / 9;
            int bw = Math.max(1, (mapW - 2) / 9);
            int tint = (band % 3 == 0) ? 0xFF244B35 : (band % 3 == 1) ? 0xFF28563A : 0xFF20442F;
            g.fill(bx, top + 1, Math.min(mapX + mapW - 1, bx + bw), bottom - 1, tint);
        }
        int seed = 31415;
        for (int i = 0; i < 22; i++) {
            int px = mapX + 18 + Math.abs((seed * (i + 3) + i * 911) % Math.max(1, mapW - 36));
            int py = top + 18 + Math.abs((seed / (i + 2) + i * 73) % Math.max(1, mapH - 36));
            int size = 8 + (i % 4) * 4;
            g.fill(px, py, Math.min(mapX + mapW - 4, px + size), Math.min(bottom - 4, py + size / 2), i % 4 == 0 ? 0xFF326A45 : 0xFF2B5C3C);
        }
        // Rivers / paths.
        for (int step = 0; step < mapW - 20; step++) {
            double t = step / (double)Math.max(1, mapW - 20);
            int py = top + mapH / 2 + (int)(Math.sin(t * Math.PI * 3.0) * mapH * 0.16);
            g.fill(mapX + 10 + step, py, mapX + 11 + step, py + 5, 0xFF245C7D);
            if (step % 9 == 0) g.fill(mapX + 10 + step, py + 5, mapX + 15 + step, py + 6, 0xFF347A9A);
        }
        for (int layer = -4; layer <= 4; layer++) {
            for (int px = mapX + 4; px < mapX + mapW - 4; px++) {
                double t = (px - mapX) / (double)Math.max(1, mapW);
                int py = (int)(top + mapH * .5 + layer * mapH * .085 + Math.sin(t * Math.PI * 2 + layer * .5) * mapH * .07);
                if (py > top + 4 && py < bottom - 4) g.fill(px, py, px + 1, py + 1, 0x554F8D5B);
            }
        }

        if (!waypoints.isEmpty()) {
            int minX = waypoints.stream().mapToInt(Waypoint::x).min().orElse(0);
            int maxX = waypoints.stream().mapToInt(Waypoint::x).max().orElse(0);
            int minZ = waypoints.stream().mapToInt(Waypoint::z).min().orElse(0);
            int maxZ = waypoints.stream().mapToInt(Waypoint::z).max().orElse(0);
            int rangeX = Math.max(1, maxX - minX), rangeZ = Math.max(1, maxZ - minZ);
            for (Waypoint w : waypoints) {
                int px = mapX + 36 + (int)((w.x() - minX) * (mapW - 72.0) / rangeX);
                int py = top + 36 + (int)((w.z() - minZ) * (mapH - 72.0) / rangeZ);
                int color = 0xFF000000 | WaypointsMod.resolveColor(w);
                g.fill(px - 2, py + 9, px + 3, py + 17, 0x99000000);
                rounded(g, px - 13, py - 13, px + 14, py + 14, 0xFF0D160F, 12);
                border(g, px - 13, py - 13, px + 14, py + 14, color);
                g.fill(px - 2, py - 6, px + 3, py + 1, color);
                g.fill(px - 6, py - 2, px + 7, py + 4, color);
                if (mapW > 500) {
                    String label = w.name().length() > 16 ? w.name().substring(0, 15) + "…" : w.name();
                    rounded(g, px + 17, py - 9, px + 25 + WaypointUiText.width(font, label), py + 13, 0xE8090C0A, 5);
                    WaypointUiText.draw(g, font, label, px + 21, py - 2, TEXT, false);
                }
            }
        }
        // Legend panel.
        int legendW = Math.min(240, Math.max(170, mapW / 4));
        int count = Math.min(7, waypoints.size());
        int lx = mapX + 14, ly = top + 14;
        rounded(g, lx, ly, lx + legendW, ly + 34 + count * 22, 0xE8090C0A, 8);
        WaypointUiText.draw(g, font, "WAYPOINTS  ·  " + waypoints.size(), lx + 11, ly + 10, ACCENT, true);
        for (int i = 0; i < count; i++) {
            Waypoint w = waypoints.get(i);
            int yy = ly + 35 + i * 22;
            g.fill(lx + 11, yy + 3, lx + 19, yy + 11, 0xFF000000 | WaypointsMod.resolveColor(w));
            String label = w.name().length() > 24 ? w.name().substring(0, 23) + "…" : w.name();
            WaypointUiText.draw(g, font, label, lx + 27, yy + 2, TEXT, false);
        }
        WaypointUiText.draw(g, font, "SCHEMATIC MAP PREVIEW  ·  Exact coordinates are retained for navigation",
                mapX + 12, bottom - 20, 0xFFB7C3B7, false);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, RAIL, height, 0xFF101113);
        g.fill(RAIL - 1, 0, RAIL, height, EDGE);
        rounded(g, 18, 20, 53, 55, ACCENT, 10);
        WaypointUiText.draw(g, font, "●", 28, 28, 0xFF101410, true);
        WaypointUiText.draw(g, font, "Simple", 69, 22, TEXT, true);
        WaypointUiText.draw(g, font, "Waypoints", 69, 36, TEXT, true);
        nav(g, mx, my, 10, 112, "Waypoints", false);
        nav(g, mx, my, 10, 157, "Map", true);
        nav(g, mx, my, 10, 202, "Settings", false);
        nav(g, mx, my, 10, 247, "About", false);
        g.fill(14, height - 96, 42, height - 77, 0xFF79502E);
        g.fill(14, height - 99, 42, height - 92, 0xFF4F9C35);
        WaypointUiText.draw(g, font, "v1.4.4", 54, height - 91, ACCENT, true);
    }

    private void nav(GuiGraphicsExtractor g, int mx, int my, int x, int y, String label, boolean active) {
        int w = RAIL - 20;
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + 36;
        if (active || hover) rounded(g, x, y, x + w, y + 36, active ? 0xFF283B1C : PANEL, 7);
        int c = active ? ACCENT : MUTED;
        if (label.equals("Waypoints")) drawPinGlyph(g, x + 12, y + 10, c);
        else if (label.equals("Map")) drawCubeGlyph(g, x + 11, y + 10, c);
        else if (label.equals("Settings")) drawSlidersGlyph(g, x + 10, y + 10, c);
        else drawInfoGlyph(g, x + 10, y + 10, c);
        WaypointUiText.draw(g, font, label, x + 38, y + 11, active ? ACCENT : MUTED, false);
    }

    private void drawPinGlyph(GuiGraphicsExtractor g, int x, int y, int c) {
        g.fill(x + 4, y, x + 8, y + 2, c); g.fill(x + 2, y + 2, x + 10, y + 6, c);
        g.fill(x + 3, y + 6, x + 9, y + 9, c); g.fill(x + 5, y + 9, x + 7, y + 12, c);
        g.fill(x + 4, y + 3, x + 8, y + 6, RAIL);
    }
    private void drawCubeGlyph(GuiGraphicsExtractor g, int x, int y, int c) {
        g.fill(x + 5, y, x + 6, y + 1, c); g.fill(x + 2, y + 2, x + 9, y + 3, c);
        g.fill(x, y + 4, x + 2, y + 6, c); g.fill(x + 9, y + 4, x + 11, y + 6, c);
        g.fill(x + 2, y + 7, x + 9, y + 8, c); g.fill(x + 5, y + 3, x + 6, y + 8, c);
    }
    private void drawSlidersGlyph(GuiGraphicsExtractor g, int x, int y, int c) {
        g.fill(x, y + 2, x + 13, y + 3, c); g.fill(x, y + 7, x + 13, y + 8, c);
        g.fill(x, y + 12, x + 13, y + 13, c); g.fill(x + 3, y, x + 6, y + 5, c); g.fill(x + 8, y + 5, x + 11, y + 10, c);
    }
    private void drawInfoGlyph(GuiGraphicsExtractor g, int x, int y, int c) {
        g.fill(x + 2, y, x + 11, y + 1, c); g.fill(x, y + 2, x + 1, y + 11, c);
        g.fill(x + 12, y + 2, x + 13, y + 11, c); g.fill(x + 2, y + 12, x + 11, y + 13, c);
        g.fill(x + 6, y + 3, x + 7, y + 4, c); g.fill(x + 6, y + 6, x + 7, y + 10, c);
    }

    private static void border(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color) {
        g.fill(x, y, x2, y + 1, color); g.fill(x, y2 - 1, x2, y2, color);
        g.fill(x, y, x + 1, y2, color); g.fill(x2 - 1, y, x2, y2, color);
    }
    private static void rounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int radius) {
        int w=x2-x, h=y2-y; if(w<=0||h<=0)return; int r=Math.min(WaypointsMod.smoothUiEdges?Math.max(radius,8):Math.min(radius,3),Math.min(w/2,h/2));
        g.fill(x+r,y,x2-r,y2,color); g.fill(x,y+r,x2,y2-r,color);
        for(int row=0;row<r;row++){int inset=(int)Math.ceil(r-Math.sqrt(Math.max(0,r*r-Math.pow(r-row-.5,2))));g.fill(x+inset,y+row,x2-inset,y+row+1,color);g.fill(x+inset,y2-row-1,x2-inset,y2-row,color);}
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            if (event.x() <= RAIL) {
                if (event.y() >= 112 && event.y() < 148) { Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)); return true; }
                if (event.y() >= 202 && event.y() < 238) { Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()); return true; }
                if (event.y() >= 247 && event.y() < 283) { Minecraft.getInstance().gui.setScreen(new WaypointAboutScreen()); return true; }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
