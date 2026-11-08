package com.example.waypoints;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/** Shared smooth sans-serif text renderer for all Waypoint Manager screens. */
final class WaypointUiText {
    private static final int TEXT = 0xFFF2F4F5;
    static final int LINE_HEIGHT = 10;

    private WaypointUiText() {}

    static MutableComponent component(String text) {
        // Use Minecraft's native UI font. The previous custom bitmap atlas had
        // incorrect glyph advances and made labels look widely spaced/blocky.
        return Component.literal(text == null ? "" : text);
    }

    static int width(Font font, String text) {
        return font.width(component(text));
    }

    static void draw(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean shadow) {
        g.text(font, component(text), x, y, color, shadow);
    }

    static void draw(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        g.text(font, text, x, y, color, shadow);
    }

    static int compactWidth(Font font, String text) {
        return font.width(smallComponent(text));
    }

    private static MutableComponent smallComponent(String text) {
        return Component.literal(text == null ? "" : text);
    }

    private static MutableComponent boldComponent(String text) {
        return Component.literal(text == null ? "" : text).withStyle(style -> style.withBold(true));
    }

    static void drawCompact(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        g.text(font, smallComponent(text), x, y, color, false);
    }

    static void drawStrong(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        g.text(font, boldComponent(text), x, y, color, false);
    }

    static void drawTitle(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        Component c = boldComponent(text);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(1.35f, 1.35f);
        g.text(font, c, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** The native EditBox remains responsible for keyboard/mouse input; render its text in the shared UI font. */
    static void drawInput(GuiGraphicsExtractor g, Font font, EditBox box, int x, int y, int maxWidth) {
        if (box == null || maxWidth <= 0) return;
        String value = box.getValue();
        int cursor = Math.max(0, Math.min(value.length(), box.getCursorPosition()));
        int start = 0;
        while (start < cursor && width(font, value.substring(start)) > maxWidth) start++;
        String shown = value.substring(start);
        int shownCursor = Math.max(0, cursor - start);
        if (width(font, shown) > maxWidth) {
            int end = shown.length();
            while (end > shownCursor && width(font, shown.substring(0, end)) > maxWidth) end--;
            shown = shown.substring(0, Math.max(shownCursor, end));
        }
        draw(g, font, shown, x, y, TEXT, false);
        if (box.isFocused() && ((System.currentTimeMillis() / 500L) & 1L) == 0L) {
            int caretX = x + width(font, shown.substring(0, Math.min(shownCursor, shown.length())));
            g.fill(caretX, y - 1, caretX + 1, y + 10, TEXT);
        }
    }
}
