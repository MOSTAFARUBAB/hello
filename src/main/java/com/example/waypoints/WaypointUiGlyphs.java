package com.example.waypoints;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small, consistent waypoint symbols rendered from shapes instead of bitmap text glyphs. */
final class WaypointUiGlyphs {
    private WaypointUiGlyphs() {}

    static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int size, String key, int color) {
        int s = Math.max(14, size);
        int cx = x + s / 2;
        switch (key == null ? "H" : key) {
            case "S" -> WaypointUiText.draw(g, font, "★", x + (s - WaypointUiText.width(font, "★")) / 2, y + 2, color, false);
            case "K" -> {
                rounded(g, x + s / 4, y + 2, x + s * 3 / 4, y + s * 3 / 4, color, Math.max(2, s / 7));
                g.fill(x + s / 3, y + s / 3, x + s / 3 + 2, y + s / 3 + 2, 0xFF111315);
                g.fill(x + s * 2 / 3 - 2, y + s / 3, x + s * 2 / 3, y + s / 3 + 2, 0xFF111315);
                g.fill(x + s / 2 - 3, y + s * 2 / 3, x + s / 2 + 3, y + s * 2 / 3 + 2, 0xFF111315);
                g.fill(x + s / 3, y + s * 3 / 4, x + s * 2 / 3, y + s * 3 / 4 + 2, color);
            }
            case "W" -> {
                for (int i = 0; i < s / 2; i++) g.fill(x + s / 2 + i - 1, y + 2 + i, x + s / 2 + i + 2, y + 5 + i, color);
                g.fill(x + 3, y + s - 5, x + s / 2 + 5, y + s - 3, color);
                g.fill(x + 5, y + s - 8, x + 8, y + s - 1, color);
            }
            case "T" -> {
                g.fill(x + s / 3, y + 2, x + s * 2 / 3, y + 5, color);
                g.fill(x + s / 4, y + 5, x + s * 3 / 4, y + 8, color);
                g.fill(x + s / 3, y + 8, x + s * 2 / 3, y + s - 1, color);
                g.fill(x + s / 2 - 2, y + s / 3, x + s / 2 + 2, y + s / 2, 0xFF111315);
                g.fill(x + s / 2 - 2, y + s * 2 / 3, x + s / 2 + 2, y + s - 2, 0xFF111315);
            }
            case "D" -> {
                int mid = s / 2;
                for (int row = 0; row < 4; row++) {
                    int half = row < 2 ? row + 2 : 5 - row;
                    g.fill(cx - half, y + 2 + row * 3, cx + half, y + 5 + row * 3, color);
                }
            }
            case "M" -> {
                int r = Math.max(2, s / 9);
                for (int i = 0; i < 3; i++) rounded(g, x + s / 2 - r + (i - 1) * (r * 3), y + s / 2 - r,
                        x + s / 2 + r + (i - 1) * (r * 3), y + s / 2 + r, color, r);
            }
            default -> { // Home pin/house symbol
                g.fill(x + s / 2 - 1, y + 2, x + s / 2 + 1, y + 5, color);
                for (int i = 0; i < s / 2 - 2; i++) {
                    g.fill(x + s / 2 - i - 2, y + 7 - i, x + s / 2 - i, y + 10 - i, color);
                    g.fill(x + s / 2 + i, y + 7 - i, x + s / 2 + i + 2, y + 10 - i, color);
                }
                g.fill(x + s / 4, y + 8, x + s * 3 / 4, y + s - 2, color);
                g.fill(x + s / 2 - 2, y + s * 2 / 3, x + s / 2 + 2, y + s - 2, 0xFF111315);
            }
        }
    }

    private static void rounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int radius) {
        int r = Math.max(1, Math.min(radius, Math.min((x2 - x) / 2, (y2 - y) / 2)));
        g.fill(x + r, y, x2 - r, y2, color);
        for (int i = 0; i < r; i++) {
            int inset = r - 1 - i;
            g.fill(x + inset, y + i, x2 - inset, y + i + 1, color);
            g.fill(x + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }
}
