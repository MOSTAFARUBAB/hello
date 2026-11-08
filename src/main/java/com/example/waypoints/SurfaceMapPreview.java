package com.example.waypoints;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Clean contour-map preview shared by the editor and preferences screen. */
final class SurfaceMapPreview {
    private SurfaceMapPreview() { }

    static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height,
                     int centerX, int centerZ, String requestedDimension, int markerColor) {
        draw(g, font, x, y, width, height, centerX, centerZ, requestedDimension, markerColor,
                "Waypoint", centerX + ", " + centerZ);
    }

    static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height,
                     int centerX, int centerZ, String requestedDimension, int markerColor,
                     String markerLabel, String markerDetails) {
        if (width < 40 || height < 50) return;
        rounded(g, x, y, x + width, y + height, 0xFF0F1113, 8);
        border(g, x, y, x + width, y + height, 0xFF292D31);

        int left = x + 1, right = x + width - 1;
        int top = y + 1, bottom = y + height - 1;
        // Quiet topographic contours: layered waves, never a high-contrast grid.
        for (int layer = -4; layer <= 4; layer++) {
            for (int px = left; px < right; px++) {
                double t = (px - left) / (double)Math.max(1, width - 2);
                double wave = Math.sin(t * Math.PI * 2.0 + layer * 0.36) * height * 0.095
                        + Math.sin(t * Math.PI * 4.0 - layer * 0.22) * height * 0.025;
                int py = (int)Math.round(y + height * 0.49 + layer * height * 0.105 + wave);
                if (py > top + 2 && py < bottom - 2) g.fill(px, py, Math.min(px + 1, right), py + 1, 0x26364036);
            }
        }

        int accent = markerColor | 0xFF000000;
        int markerX = x + width / 2;
        int markerY = y + Math.round(height * 0.37f);
        g.fill(markerX, top, markerX + 1, markerY - 17, 0xFF000000 | ((accent & 0x00FEFEFE) >> 1));

        int size = 34;
        rounded(g, markerX - size / 2, markerY - size / 2, markerX + size / 2, markerY + size / 2, 0xFF131A12, 8);
        border(g, markerX - size / 2, markerY - size / 2, markerX + size / 2, markerY + size / 2, accent);
        // Minimal map-pin symbol centered in the marker tile.
        int pin = accent;
        g.fill(markerX - 1, markerY - 8, markerX + 2, markerY - 5, pin);
        g.fill(markerX - 4, markerY - 5, markerX + 5, markerY - 2, pin);
        g.fill(markerX - 5, markerY - 2, markerX + 6, markerY + 2, pin);
        g.fill(markerX - 3, markerY + 2, markerX + 4, markerY + 5, pin);
        g.fill(markerX - 1, markerY + 5, markerX + 2, markerY + 8, pin);
        g.fill(markerX - 2, markerY - 3, markerX + 3, markerY + 2, 0xFF131A12);

        String name = markerLabel == null || markerLabel.isBlank() ? "Waypoint" : markerLabel.trim();
        String details = markerDetails == null ? "" : markerDetails.trim();
        int badgeW = Math.min(width - 24, Math.max(116, Math.max(WaypointUiText.width(font, name), WaypointUiText.compactWidth(font, details)) + 24));
        int badgeH = 34;
        int badgeX = x + (width - badgeW) / 2;
        int badgeY = Math.min(y + height - badgeH - 8, markerY + 19);
        rounded(g, badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xF20A0D0E, 7);
        border(g, badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xFF1D2520);
        String shownName = trim(font, name, badgeW - 16);
        String shownDetails = trimCompact(font, details, badgeW - 16);
        WaypointUiText.draw(g, font, shownName, badgeX + (badgeW - WaypointUiText.width(font, shownName)) / 2, badgeY + 6, accent, false);
        WaypointUiText.drawCompact(g, font, shownDetails, badgeX + (badgeW - WaypointUiText.compactWidth(font, shownDetails)) / 2, badgeY + 20, 0xFF9BA4A8);
    }

    private static String trim(Font font, String value, int maxWidth) {
        String out = value == null ? "" : value;
        while (!out.isEmpty() && WaypointUiText.width(font, out) > maxWidth) out = out.substring(0, out.length() - 1);
        return out.length() < (value == null ? 0 : value.length()) ? out + "..." : out;
    }
    private static String trimCompact(Font font, String value, int maxWidth) {
        String out = value == null ? "" : value;
        while (!out.isEmpty() && WaypointUiText.compactWidth(font, out) > maxWidth) out = out.substring(0, out.length() - 1);
        return out.length() < (value == null ? 0 : value.length()) ? out + "..." : out;
    }

    private static void border(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color) {
        g.fill(x, y, x2, y + 1, color); g.fill(x, y2 - 1, x2, y2, color);
        g.fill(x, y, x + 1, y2, color); g.fill(x2 - 1, y, x2, y2, color);
    }
    private static void rounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int radius) {
        int w = x2 - x, h = y2 - y; if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w / 2, h / 2));
        g.fill(x + r, y, x2 - r, y2, color); g.fill(x, y + r, x2, y2 - r, color);
        for (int row = 0; row < r; row++) {
            int inset = (int)Math.ceil(r - Math.sqrt(Math.max(0.0, r * r - Math.pow(r - row - 0.5, 2))));
            g.fill(x + inset, y + row, x2 - inset, y + row + 1, color);
            g.fill(x + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
    }
}
