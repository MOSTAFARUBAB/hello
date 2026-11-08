package com.example.waypoints;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Shared, antialiased text renderer and sizing helpers for the custom waypoint UI. */
final class WaypointUiText {
    static final int LINE_HEIGHT = 13;

    private static final int ATLAS_WIDTH = 512;
    private static final int ATLAS_HEIGHT = 128;
    private static final int CELL_WIDTH = 20;
    private static final int CELL_HEIGHT = 20;
    private static final int COLUMNS = 24;
    private static final String EXTRA_GLYPHS = "★←→↑↓✓✕×−…·◆◇⌂☠⚔♜▸⌄◉●■≋≤≥%°";
    private static final Map<AtlasKey, GlyphAtlas> ATLASES = new LinkedHashMap<>();
    private static final Map<String, Integer> WIDTH_CACHE = new LinkedHashMap<>();

    private static final java.awt.Font METRIC_FONT = new java.awt.Font("Dialog", java.awt.Font.PLAIN, 11);
    private static final BufferedImage METRIC_IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private static final Graphics2D METRIC_GRAPHICS = METRIC_IMAGE.createGraphics();
    private static final FontMetrics METRICS;

    static {
        METRIC_GRAPHICS.setFont(METRIC_FONT);
        METRIC_GRAPHICS.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        METRIC_GRAPHICS.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        METRICS = METRIC_GRAPHICS.getFontMetrics();
    }

    private WaypointUiText() {}

    static MutableComponent component(String text) {
        // Keep vanilla components available for descriptions that use Font.split.
        return Component.literal(text == null ? "" : text);
    }

    static int width(Font font, String text) {
        if (text == null || text.isEmpty()) return 0;
        synchronized (WIDTH_CACHE) {
            Integer cached = WIDTH_CACHE.get(text);
            if (cached != null) return cached;
            int measured = METRICS.stringWidth(text);
            if (WIDTH_CACHE.size() > 2048) WIDTH_CACHE.clear();
            WIDTH_CACHE.put(text, measured);
            return measured;
        }
    }

    static void draw(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean shadow) {
        drawText(g, text, x, y, color, shadow, false, 11);
    }

    static void draw(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        // Used only for rich vanilla description lines; the main menu labels use the antialiased atlas.
        g.text(font, text, x, y, color, shadow);
    }

    static int compactWidth(Font font, String text) {
        return width(font, text);
    }

    static void drawCompact(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        drawText(g, text, x, y, color, false, false, 10);
    }

    static void drawStrong(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        drawText(g, text, x, y, color, false, true, 12);
    }

    static void drawTitle(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        drawText(g, text, x, y, color, false, true, 16);
    }

    /** The native EditBox remains responsible for input; its visible text uses the same smooth UI glyphs. */
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
        drawText(g, shown, x, y, 0xFFF2F4F5, false, false, 11);
        if (box.isFocused() && ((System.currentTimeMillis() / 500L) & 1L) == 0L) {
            int caretX = x + width(font, shown.substring(0, Math.min(shownCursor, shown.length())));
            g.fill(caretX, y - 1, caretX + 1, y + 12, 0xFFF2F4F5);
        }
    }

    private static void drawText(GuiGraphicsExtractor g, String text, int x, int y, int color,
                                 boolean shadow, boolean bold, int size) {
        if (text == null || text.isEmpty()) return;
        if (shadow) drawGlyphRun(g, text, x + 1, y + 1, 0x88000000, bold, size);
        drawGlyphRun(g, text, x, y, color, bold, size);
    }

    private static void drawGlyphRun(GuiGraphicsExtractor g, String text, int x, int y, int color,
                                     boolean bold, int size) {
        GlyphAtlas atlas = atlas(color, bold, size);
        int drawX = x;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            Glyph glyph = atlas.glyphs.get(codePoint);
            if (glyph == null) glyph = atlas.glyphs.get((int) '?');
            if (glyph == null) continue;
            if (!Character.isWhitespace(codePoint)) {
                float u0 = glyph.x / (float) ATLAS_WIDTH;
                float u1 = (glyph.x + glyph.sourceWidth) / (float) ATLAS_WIDTH;
                float v0 = (glyph.y + 1) / (float) ATLAS_HEIGHT;
                float v1 = (glyph.y + 1 + atlas.lineHeight) / (float) ATLAS_HEIGHT;
                g.blit(atlas.id, drawX, y, drawX + glyph.advance, y + atlas.lineHeight, u0, u1, v0, v1);
            }
            drawX += glyph.advance;
        }
    }

    private static synchronized GlyphAtlas atlas(int color, boolean bold, int size) {
        AtlasKey key = new AtlasKey(color, bold, size);
        GlyphAtlas found = ATLASES.get(key);
        if (found != null) return found;

        java.awt.Font awtFont = new java.awt.Font("Dialog", bold ? java.awt.Font.BOLD : java.awt.Font.PLAIN, size);
        BufferedImage image = new BufferedImage(ATLAS_WIDTH, ATLAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setFont(awtFont);
        graphics.setColor(new Color(color, true));
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontMetrics metrics = graphics.getFontMetrics();
        int lineHeight = Math.max(11, Math.min(CELL_HEIGHT - 2, metrics.getHeight()));

        Set<Integer> codePoints = new LinkedHashSet<>();
        for (int cp = 32; cp <= 126; cp++) codePoints.add(cp);
        EXTRA_GLYPHS.codePoints().forEach(codePoints::add);
        Map<Integer, Glyph> glyphs = new LinkedHashMap<>();
        int index = 0;
        for (int cp : codePoints) {
            int cellX = (index % COLUMNS) * CELL_WIDTH;
            int cellY = (index / COLUMNS) * CELL_HEIGHT;
            String glyphText = new String(Character.toChars(cp));
            int advance = Math.max(1, Math.min(CELL_WIDTH - 2, metrics.stringWidth(glyphText)));
            graphics.drawString(glyphText, cellX + 1, cellY + 1 + metrics.getAscent());
            glyphs.put(cp, new Glyph(cellX, cellY, Math.min(CELL_WIDTH, advance + 2), advance));
            index++;
        }
        graphics.dispose();

        Identifier id = Identifier.fromNamespaceAndPath("simplewaypoints",
                "ui_text_" + Integer.toHexString(color) + (bold ? "_b" : "_r") + "_" + size);
        DynamicTexture texture = new DynamicTexture("simple-waypoints-ui-text", ATLAS_WIDTH, ATLAS_HEIGHT, true);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        NativeImage pixels = texture.getPixels();
        for (int py = 0; py < ATLAS_HEIGHT; py++) {
            for (int px = 0; px < ATLAS_WIDTH; px++) {
                pixels.setPixel(px, py, image.getRGB(px, py));
            }
        }
        texture.upload();
        GlyphAtlas created = new GlyphAtlas(id, texture, glyphs, lineHeight);
        ATLASES.put(key, created);
        return created;
    }

    private record AtlasKey(int color, boolean bold, int size) {}
    private record Glyph(int x, int y, int sourceWidth, int advance) {}
    private record GlyphAtlas(Identifier id, DynamicTexture texture, Map<Integer, Glyph> glyphs, int lineHeight) {}
}
