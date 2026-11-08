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
    private static final int RAIL = 176;
    private List<Waypoint> waypoints = List.of();
    private int mapX, mapY, mapW, mapH;
    private int zoom = 100;
    private boolean showContours = true;

    public WaypointMapScreen() { super(Component.literal("Waypoint Map")); }

    @Override protected void init() { waypoints = WaypointsMod.menuWaypoints(); }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawSidebar(g, mx, my);
        int x = RAIL + 24;
        WaypointUiText.drawTitle(g, font, "Surface Map", x, 28, TEXT);
        mapX = x;
        mapY = 60;
        mapW = Math.max(280, width - mapX - 26);
        mapH = Math.max(190, height - mapY - 14);
        drawMap(g);
        drawMapTools(g, mx, my);
        super.extractRenderState(g, mx, my, delta);
    }

    private void drawMap(GuiGraphicsExtractor g) {
        int top = mapY, bottom = mapY + mapH;
        rounded(g, mapX, top, mapX + mapW, bottom, 0xFF13251B, 11);
        border(g, mapX, top, mapX + mapW, bottom, EDGE);
        // A stylized overhead biome surface, built from broad terrain regions and contour paths.
        g.fill(mapX + 1, top + 1, mapX + mapW - 1, bottom - 1, 0xFF245037);
        // Varied forest and meadow patches give the preview a satellite-map feel.
        for (int i = 0; i < 190; i++) {
            int px = mapX + 5 + Math.abs((i * 977 + 31) % Math.max(1, mapW - 14));
            int py = top + 5 + Math.abs((i * 613 + 17) % Math.max(1, mapH - 14));
            int size = 3 + (i % 5) * 2;
            int c = switch (i % 5) { case 0 -> 0xFF326B43; case 1 -> 0xFF2B5D3B; case 2 -> 0xFF407947; case 3 -> 0xFF537D4E; default -> 0xFF294B36; };
            g.fill(px, py, Math.min(mapX + mapW - 2, px + size), Math.min(bottom - 2, py + Math.max(2, size / 2)), c);
        }
        // Snowy highlands in the north-east corner, plus pale rock outcrops.
        for (int i = 0; i < 38; i++) {
            int px = mapX + mapW * 3 / 4 + Math.abs((i * 37 + 5) % Math.max(1, mapW / 4 - 8));
            int py = top + 8 + Math.abs((i * 53 + 7) % Math.max(1, mapH / 3));
            g.fill(px, py, Math.min(mapX + mapW - 3, px + 4 + i % 6), Math.min(top + mapH / 2, py + 3 + i % 5), i % 3 == 0 ? 0xFFE1E7DC : 0xFF9DA99B);
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
        if (showContours) {
            for (int layer = -4; layer <= 4; layer++) {
                for (int px = mapX + 4; px < mapX + mapW - 4; px++) {
                    double t = (px - mapX) / (double)Math.max(1, mapW);
                    int py = (int)(top + mapH * .5 + layer * mapH * .085 + Math.sin(t * Math.PI * 2 + layer * .5) * mapH * .07);
                    if (py > top + 4 && py < bottom - 4) g.fill(px, py, px + 1, py + 1, 0x554F8D5B);
                }
            }
        }

        if (!waypoints.isEmpty()) {
            int minX = waypoints.stream().mapToInt(Waypoint::x).min().orElse(0);
            int maxX = waypoints.stream().mapToInt(Waypoint::x).max().orElse(0);
            int minZ = waypoints.stream().mapToInt(Waypoint::z).min().orElse(0);
            int maxZ = waypoints.stream().mapToInt(Waypoint::z).max().orElse(0);
            int rangeX = Math.max(1, maxX - minX), rangeZ = Math.max(1, maxZ - minZ);
            for (Waypoint w : waypoints) {
                double factor = zoom / 100.0;
                int baseX = mapX + 36 + (int)((w.x() - minX) * (mapW - 72.0) / rangeX);
                int baseY = top + 36 + (int)((w.z() - minZ) * (mapH - 72.0) / rangeZ);
                int px = mapX + mapW / 2 + (int)((baseX - (mapX + mapW / 2)) * factor);
                int py = top + mapH / 2 + (int)((baseY - (top + mapH / 2)) * factor);
                px = Math.max(mapX + 24, Math.min(mapX + mapW - 24, px));
                py = Math.max(top + 24, Math.min(bottom - 24, py));
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
        int lx = mapX + 14, ly = bottom - (34 + count * 22) - 14;
        rounded(g, lx, ly, lx + legendW, ly + 34 + count * 22, 0xE8090C0A, 8);
        WaypointUiText.draw(g, font, "WAYPOINTS  ·  " + waypoints.size(), lx + 11, ly + 10, WaypointsMod.uiAccentColor, true);
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

    private void drawMapTools(GuiGraphicsExtractor g, int mx, int my) {
        int x = mapX + 12, y = mapY + 12;
        mapTool(g, x, y, 28, 28, "+", zoom < 200, mx, my);
        mapTool(g, x, y + 35, 28, 28, "−", zoom > 40, mx, my);
        mapTool(g, x, y + 70, 28, 28, "≋", showContours, mx, my);
        int cx = mapX + mapW - 43, cy = mapY + 43;
        rounded(g, cx - 28, cy - 28, cx + 28, cy + 28, 0xE90C1010, 28);
        border(g, cx - 28, cy - 28, cx + 28, cy + 28, 0xFFBBC5C2);
        WaypointUiText.draw(g, font, "N", cx - 3, cy - 23, TEXT, true);
        WaypointUiText.draw(g, font, "S", cx - 3, cy + 13, MUTED, false);
        WaypointUiText.draw(g, font, "W", cx - 22, cy - 4, MUTED, false);
        WaypointUiText.draw(g, font, "E", cx + 15, cy - 4, MUTED, false);
        g.fill(cx - 2, cy - 18, cx + 3, cy + 18, 0xFFEEF2EE);
        g.fill(cx - 15, cy - 2, cx + 16, cy + 3, 0xFFEEF2EE);
        g.fill(cx - 2, cy - 18, cx + 3, cy - 2, WaypointsMod.uiAccentColor);
    }

    private void mapTool(GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
                         boolean enabled, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        rounded(g, x, y, x + w, y + h, hover ? 0xFF26312A : 0xE90C1010, 6);
        border(g, x, y, x + w, y + h, enabled ? 0xFF637E5C : EDGE);
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + 8,
                enabled ? WaypointsMod.uiAccentColor : MUTED, true);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, RAIL, height, 0xFF101113);
        g.fill(RAIL - 1, 0, RAIL, height, EDGE);
        drawBrandMark(g, 18, 20);
        WaypointUiText.draw(g, font, "Simple", 69, 22, TEXT, true);
        WaypointUiText.draw(g, font, "Waypoints", 69, 36, TEXT, true);
        nav(g, mx, my, 10, 112, "Waypoints", false);
        nav(g, mx, my, 10, 157, "Map", true);
        nav(g, mx, my, 10, 202, "Settings", false);
        nav(g, mx, my, 10, 247, "About", false);
        g.fill(14, height - 96, 42, height - 77, 0xFF79502E);
        g.fill(14, height - 99, 42, height - 92, 0xFF4F9C35);
        WaypointUiText.draw(g, font, "v1.4.5", 54, height - 91, WaypointsMod.uiAccentColor, true);
    }

    private void drawBrandMark(GuiGraphicsExtractor g, int x, int y) {
        rounded(g, x + 7, y, x + 28, y + 27, WaypointsMod.uiAccentColor, 11);
        g.fill(x + 9, y + 20, x + 26, y + 30, WaypointsMod.uiAccentColor);
        g.fill(x + 12, y + 28, x + 23, y + 36, WaypointsMod.uiAccentColor);
        g.fill(x + 16, y + 34, x + 20, y + 42, WaypointsMod.uiAccentColor);
        rounded(g, x + 13, y + 6, x + 23, y + 17, 0xFF101113, 6);
    }

    private void nav(GuiGraphicsExtractor g, int mx, int my, int x, int y, String label, boolean active) {
        int w = RAIL - 20;
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + 36;
        if (active || hover) rounded(g, x, y, x + w, y + 36, active ? WaypointsMod.uiAccentColor : PANEL, 7);
        int c = active ? 0xFF101410 : MUTED;
        if (label.equals("Waypoints")) drawPinGlyph(g, x + 12, y + 10, c);
        else if (label.equals("Map")) drawCubeGlyph(g, x + 11, y + 10, c);
        else if (label.equals("Settings")) drawSlidersGlyph(g, x + 10, y + 10, c);
        else drawInfoGlyph(g, x + 10, y + 10, c);
        WaypointUiText.draw(g, font, label, x + 38, y + 11, active ? 0xFF101410 : MUTED, active);
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
            int toolX = mapX + 12;
            int toolY = mapY + 12;
            if (event.x() >= toolX && event.x() < toolX + 28 && event.y() >= toolY && event.y() < toolY + 28) {
                zoom = Math.min(200, zoom + 20);
                return true;
            }
            if (event.x() >= toolX && event.x() < toolX + 28 && event.y() >= toolY + 35 && event.y() < toolY + 63) {
                zoom = Math.max(40, zoom - 20);
                return true;
            }
            if (event.x() >= toolX && event.x() < toolX + 28 && event.y() >= toolY + 70 && event.y() < toolY + 98) {
                showContours = !showContours;
                return true;
            }
            if (event.x() <= RAIL) {
                if (event.y() >= 112 && event.y() < 148) { Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)); return true; }
                if (event.y() >= 202 && event.y() < 238) { Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()); return true; }
                if (event.y() >= 247 && event.y() < 283) { Minecraft.getInstance().gui.setScreen(new WaypointAboutScreen()); return true; }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
