package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


/** Preferences workspace matching the dark, lime-accented Waypoint Manager design. */
public final class WaypointSettingsScreen extends Screen {
    private static final int BG = 0xFF0B0C0E;
    private static final int RAIL = 0xFF101113;
    private static final int[] ACCENT_SWATCHES = {
            0xFFB7F36B, 0xFF3184F6, 0xFF10C8D7, 0xFF9C36E8,
            0xFFE43C83, 0xFFF59A20, 0xFFF4D21E, 0xFFF4F4F4
    };
    private static final int PANEL = 0xFF131518;
    private static final int PANEL_ALT = 0xFF0F1113;
    private static final int EDGE = 0xFF292D31;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFFA3ABB0;
    private static final int QUIET = 0xFF727B82;

    private record HitTarget(int x, int y, int width, int height, Runnable action) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    private final java.util.List<HitTarget> hitTargets = new java.util.ArrayList<>();
    private final java.util.List<HitTarget> scrollHitTargets = new java.util.ArrayList<>();
    private int scrollOffset;
    private int maxScroll;
    private int scrollTop;
    private int scrollBottom;

    private int railWidth, contentX, contentRight, contentWidth, leftW, dataX, dataW;
    private int headingY, cardsY, labelCardHeight, beamY, beamCardHeight, displayY, displayCardHeight;
    private int beamTrackX, beamTrackWidth, beamTrackY;
    private boolean draggingBeamOpacity;
    private EditBox distanceBox;

    public WaypointSettingsScreen() { super(Component.literal("Waypoint Settings")); }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override
    protected void init() {
        clearWidgets();
        distanceBox = null;
        hitTargets.clear();
        scrollHitTargets.clear();
        railWidth = width >= 800 ? 176 : 54;
        int inset = width >= 800 ? 24 : 12;
        contentX = railWidth + inset;
        contentRight = width - inset;
        contentWidth = Math.max(220, contentRight - contentX);
        headingY = 28;
        cardsY = 60;
        leftW = Math.max(150, (contentWidth - 12) / 2);
        dataX = contentX + leftW + 12;
        dataW = Math.max(150, contentRight - dataX);
        labelCardHeight = Math.max(136, (height - cardsY - 66 - 12) / 2);
        beamY = cardsY;
        beamCardHeight = labelCardHeight;
        displayY = cardsY + labelCardHeight + 12;
        displayCardHeight = labelCardHeight;
        scrollTop = cardsY;
        scrollBottom = height - 54;
        maxScroll = 0;
        scrollOffset = 0;

        if (railWidth > 70) {
            int navWidth = railWidth - 20;
            button(10, 112, navWidth, 36, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            button(10, 157, navWidth, 36, () -> Minecraft.getInstance().gui.setScreen(new WaypointMapScreen()));
            button(10, 202, navWidth, 36, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
            button(10, 247, navWidth, 36, () -> Minecraft.getInstance().gui.setScreen(new WaypointAboutScreen()));
        } else {
            button(8, 112, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            button(8, 156, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointMapScreen()));
            button(8, 200, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
            button(8, 244, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointAboutScreen()));
        }

        // General card: labels, distance and coordinates.
        int generalToggleX = contentX + leftW - 42;
        scrollButton(generalToggleX, cardsY + 43, 28, 18, this::toggleBeams);
        scrollButton(generalToggleX, cardsY + 72, 28, 18, this::toggleLabels);
        scrollButton(generalToggleX, cardsY + 101, 28, 18, this::toggleCoordinates);
        scrollButton(generalToggleX, cardsY + 130, 28, 18, this::toggleDistances);

        // Map Settings card: beacon visibility, color matching and beam intensity.
        int mapToggleX = dataX + dataW - 42;
        scrollButton(mapToggleX, beamY + 49, 28, 18, this::toggleBeamColor);
        scrollButton(mapToggleX, beamY + 78, 28, 18, () -> {
            WaypointsMod.currentDimensionOnly = !WaypointsMod.currentDimensionOnly;
            save();
        });
        beamTrackX = dataX + 16;
        beamTrackWidth = Math.max(60, dataW - 32);
        beamTrackY = beamY + beamCardHeight - 24;

        // Appearance card: smooth corners plus a persisted accent palette.
        int appearanceToggleX = contentX + leftW - 42;
        scrollButton(appearanceToggleX, displayY + 43, 28, 18, () -> {
            WaypointsMod.smoothUiEdges = !WaypointsMod.smoothUiEdges;
            save();
        });
        int accentStartX = contentX + 16;
        int accentY = displayY + 128;
        for (int i = 0; i < ACCENT_SWATCHES.length; i++) {
            final int color = ACCENT_SWATCHES[i];
            button(accentStartX + i * 25, accentY, 19, 19, () -> {
                WaypointsMod.uiAccentColor = color;
                save();
            });
        }

        // Data actions are real import/export operations, not decorative buttons.
        button(dataX + 14, displayY + 50, Math.max(90, dataW - 28), 30, () -> reportTransfer(WaypointsMod.exportWaypointsToFile()));
        button(dataX + 14, displayY + 91, Math.max(90, dataW - 28), 30, () -> reportTransfer(WaypointsMod.importWaypointsFromFile()));
        button(dataX + 14, displayY + 132, Math.max(90, dataW - 28), 27, this::resetDefaults);
        button(contentRight - 126, height - 38, 112, 27, this::save);
    }

    private void reportTransfer(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) mc.gui.chatListener().handleSystemMessage(Component.literal(message), false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && event.x() >= beamTrackX - 5 && event.x() <= beamTrackX + beamTrackWidth + 5
                && event.y() >= beamTrackY - 5 && event.y() <= beamTrackY + 12) {
            draggingBeamOpacity = true;
            setDragging(true);
            updateBeamOpacity(event.x());
            return true;
        }
        if (event.button() == 1) {
            for (int i = scrollHitTargets.size() - 1; i >= 0; i--) {
                HitTarget target = scrollHitTargets.get(i);
                if (target.contains(event.x(), event.y())) {
                    target.action().run();
                    return true;
                }
            }
            for (int i = hitTargets.size() - 1; i >= 0; i--) {
                HitTarget target = hitTargets.get(i);
                if (target.contains(event.x(), event.y())) {
                    target.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingBeamOpacity) {
            double mouseX = Minecraft.getInstance().mouseHandler.getScaledXPos(Minecraft.getInstance().getWindow());
            updateBeamOpacity(mouseX);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingBeamOpacity) {
            updateBeamOpacity(event.x());
            draggingBeamOpacity = false;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < contentX || mouseX > contentX + leftW || mouseY < scrollTop || mouseY > scrollBottom) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int next = Math.max(0, Math.min(maxScroll, scrollOffset - (int)Math.round(scrollY * 28.0)));
        if (next == scrollOffset) return false;
        scrollOffset = next;
        if (distanceBox != null) {
            distanceBox.setY(displayY + 143 - scrollOffset);
            if (distanceBox.getY() + distanceBox.getHeight() <= scrollTop || distanceBox.getY() >= scrollBottom) {
                distanceBox.setFocused(false);
            }
        }
        return true;
    }

    private void updateBeamOpacity(double mouseX) {
        double ratio = Math.max(0.0, Math.min(1.0, (mouseX - beamTrackX) / Math.max(1.0, beamTrackWidth)));
        WaypointsMod.beamOpacity = (int) Math.round(ratio * 100.0);
        save();
    }

    private void button(int x, int y, int w, int h, Runnable action) {
        hitTargets.add(new HitTarget(x, y, w, h, action));
    }
    private void scrollButton(int x, int y, int w, int h, Runnable action) {
        scrollHitTargets.add(new HitTarget(x, y, w, h, action));
    }
    private void toggleLabels() { WaypointsMod.showWaypointLabels = !WaypointsMod.showWaypointLabels; save(); }
    private void toggleDistances() { WaypointsMod.showWaypointDistances = !WaypointsMod.showWaypointDistances; save(); }
    private void toggleCoordinates() { WaypointsMod.showWaypointCoordinates = !WaypointsMod.showWaypointCoordinates; save(); }
    private void toggleBeams() { WaypointsMod.showWaypointBeams = !WaypointsMod.showWaypointBeams; save(); }
    private void toggleBeamColor() { WaypointsMod.matchWaypointColor = !WaypointsMod.matchWaypointColor; save(); }
    private void save() { WaypointsMod.savePreferences(); }

    private void resetDefaults() {
        WaypointsMod.showWaypointLabels = true;
        WaypointsMod.showWaypointDistances = true;
        WaypointsMod.showWaypointCoordinates = false;
        WaypointsMod.markerSizeMode = 1;
        WaypointsMod.showWaypointBeams = true;
        WaypointsMod.matchWaypointColor = true;
        WaypointsMod.beamOpacity = 75;
        WaypointsMod.currentDimensionOnly = true;
        WaypointsMod.hideHudWhenMenuOpen = true;
        WaypointsMod.smoothUiEdges = true;
        WaypointsMod.uiAccentColor = 0xFFB7F36B;
        WaypointsMod.maxDisplayDistance = 0;
        save();
        Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawSidebar(g, mx, my);
        WaypointUiText.drawTitle(g, font, "Settings", contentX, headingY, TEXT);

        // General panel — four compact switches.
        card(g, contentX, cardsY, leftW, labelCardHeight);
        drawLabelsGlyph(g, contentX + 15, cardsY + 14, WaypointsMod.uiAccentColor);
        WaypointUiText.draw(g, font, "General", contentX + 36, cardsY + 16, WaypointsMod.uiAccentColor, true);
        compactSetting(g, mx, contentX + 16, cardsY + 43, leftW - 32, "Show Beacons", WaypointsMod.showWaypointBeams);
        compactSetting(g, mx, contentX + 16, cardsY + 72, leftW - 32, "Show Labels", WaypointsMod.showWaypointLabels);
        compactSetting(g, mx, contentX + 16, cardsY + 101, leftW - 32, "Show Coordinates", WaypointsMod.showWaypointCoordinates);
        compactSetting(g, mx, contentX + 16, cardsY + 130, leftW - 32, "Show Distance", WaypointsMod.showWaypointDistances);

        // Map Settings panel — color matching and beam intensity.
        card(g, dataX, beamY, dataW, beamCardHeight);
        drawBeaconGlyph(g, dataX + 15, beamY + 14, WaypointsMod.uiAccentColor);
        WaypointUiText.draw(g, font, "Map Settings", dataX + 36, beamY + 16, WaypointsMod.uiAccentColor, true);
        compactSetting(g, mx, dataX + 16, beamY + 49, dataW - 32, "Match Waypoint Color", WaypointsMod.matchWaypointColor);
        compactSetting(g, mx, dataX + 16, beamY + 78, dataW - 32, "Current Dimension Only", WaypointsMod.currentDimensionOnly);
        WaypointUiText.draw(g, font, "Beam Opacity  " + WaypointsMod.beamOpacity + "%", dataX + 16, beamY + 111, MUTED, false);
        rounded(g, beamTrackX, beamTrackY, beamTrackX + beamTrackWidth, beamTrackY + 5, EDGE, 3);
        rounded(g, beamTrackX, beamTrackY, beamTrackX + beamTrackWidth * WaypointsMod.beamOpacity / 100, beamTrackY + 5, WaypointsMod.uiAccentColor, 3);
        int knob = beamTrackX + beamTrackWidth * WaypointsMod.beamOpacity / 100;
        rounded(g, knob - 4, beamTrackY - 3, knob + 4, beamTrackY + 8, WaypointsMod.uiAccentColor, 5);

        // Appearance panel — match the reference Theme / Accent controls and keep the requested smooth-edge switch.
        card(g, contentX, displayY, leftW, displayCardHeight);
        WaypointUiText.draw(g, font, "Appearance", contentX + 16, displayY + 16, WaypointsMod.uiAccentColor, true);
        compactSetting(g, mx, contentX + 16, displayY + 43, leftW - 32, "Smooth UI Edges", WaypointsMod.smoothUiEdges);
        WaypointUiText.draw(g, font, "Theme", contentX + 16, displayY + 79, MUTED, false);
        rounded(g, contentX + leftW - 132, displayY + 72, contentX + leftW - 16, displayY + 98, PANEL_ALT, 5);
        border(g, contentX + leftW - 132, displayY + 72, contentX + leftW - 16, displayY + 98, EDGE);
        WaypointUiText.draw(g, font, "Dark (Neon)", contentX + leftW - 119, displayY + 81, TEXT, false);
        WaypointUiText.draw(g, font, "Accent Color", contentX + 16, displayY + 108, MUTED, false);
        for (int i = 0; i < ACCENT_SWATCHES.length; i++) {
            int sx = contentX + 16 + i * 25;
            int sy = displayY + 128;
            rounded(g, sx, sy, sx + 19, sy + 19, ACCENT_SWATCHES[i], 8);
            if ((WaypointsMod.uiAccentColor & 0xFFFFFF) == (ACCENT_SWATCHES[i] & 0xFFFFFF))
                border(g, sx - 2, sy - 2, sx + 21, sy + 21, TEXT);
        }

        // Data panel — stacked full-width import/export actions.
        card(g, dataX, displayY, dataW, displayCardHeight);
        WaypointUiText.draw(g, font, "Data", dataX + 16, displayY + 16, WaypointsMod.uiAccentColor, true);
        buttonLabel(g, mx, my, dataX + 14, displayY + 50, dataW - 28, 30, "Export Waypoints", false);
        buttonLabel(g, mx, my, dataX + 14, displayY + 91, dataW - 28, 30, "Import Waypoints", false);
        buttonLabel(g, mx, my, dataX + 14, displayY + 132, dataW - 28, 27, "Reset to Default", false);

        buttonLabel(g, mx, my, contentRight - 126, height - 38, 112, 27, "Save Settings", true);
        super.extractRenderState(g, mx, my, delta);
    }

    private void compactSetting(GuiGraphicsExtractor g, int mx, int x, int y, int w, String label, boolean enabled) {
        WaypointUiText.draw(g, font, label, x, y + 3, TEXT, false);
        toggle(g, x + w - 26, y + 1, enabled);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, railWidth, height, RAIL);
        g.fill(railWidth - 1, 0, railWidth, height, EDGE);
        int sideInset = 10;
        drawBrandMark(g, sideInset + 8, 20);
        if (railWidth > 70) {
            WaypointUiText.draw(g, font, "Simple", sideInset + 51, 22, TEXT, true);
            WaypointUiText.draw(g, font, "Waypoints", sideInset + 51, 36, TEXT, true);
            int navWidth = railWidth - 20;
            nav(g, mx, my, sideInset, 112, navWidth, "", "Waypoints", false);
            nav(g, mx, my, sideInset, 157, navWidth, "", "Map", false);
            nav(g, mx, my, sideInset, 202, navWidth, "", "Settings", true);
            nav(g, mx, my, sideInset, 247, navWidth, "", "About", false);
            int base = height - 72;
            g.fill(14, base - 25, 42, base - 7, 0xFF79502E);
            g.fill(14, base - 28, 42, base - 22, 0xFF4F9C35);
            WaypointUiText.draw(g, font, "v1.4.6", 54, base - 3, WaypointsMod.uiAccentColor, true);
        } else {
            nav(g, mx, my, 8, 112, 38, "", "", false);
            nav(g, mx, my, 8, 156, 38, "", "", false);
            nav(g, mx, my, 8, 200, 38, "", "", true);
            nav(g, mx, my, 8, 244, 38, "", "", false);
        }
    }

    private void drawPinGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        // Compact waypoint pin glyph used by the sidebar navigation.
        g.fill(x + 4, y, x + 8, y + 2, color);
        g.fill(x + 2, y + 2, x + 10, y + 6, color);
        g.fill(x + 3, y + 6, x + 9, y + 9, color);
        g.fill(x + 5, y + 9, x + 7, y + 12, color);
        g.fill(x + 4, y + 3, x + 8, y + 6, RAIL);
    }

    private void drawBrandMark(GuiGraphicsExtractor g, int x, int y) {
        // Smooth, compact lime location pin with a dark circular center.
        rounded(g, x + 7, y, x + 28, y + 27, WaypointsMod.uiAccentColor, 11);
        g.fill(x + 9, y + 20, x + 26, y + 30, WaypointsMod.uiAccentColor);
        g.fill(x + 12, y + 28, x + 23, y + 36, WaypointsMod.uiAccentColor);
        g.fill(x + 16, y + 34, x + 20, y + 42, WaypointsMod.uiAccentColor);
        rounded(g, x + 13, y + 6, x + 23, y + 17, 0xFF101113, 6);
    }

    private void drawPixelLine(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1), sy = y1 < y2 ? 1 : -1;
        int err = dx + dy, x = x1, y = y1;
        while (true) { g.fill(x, y, x + 1, y + 1, color); if (x == x2 && y == y2) break; int e2 = 2 * err; if (e2 >= dy) { err += dy; x += sx; } if (e2 <= dx) { err += dx; y += sy; } }
    }

    private void drawCubeGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 5, y, x + 6, y + 1, color);
        g.fill(x + 2, y + 2, x + 9, y + 3, color);
        g.fill(x, y + 4, x + 2, y + 6, color);
        g.fill(x + 9, y + 4, x + 11, y + 6, color);
        g.fill(x + 2, y + 7, x + 9, y + 8, color);
        g.fill(x + 5, y + 3, x + 6, y + 8, color);
    }

    private void drawSlidersGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y + 2, x + 13, y + 3, color); g.fill(x, y + 7, x + 13, y + 8, color); g.fill(x, y + 12, x + 13, y + 13, color);
        g.fill(x + 3, y, x + 6, y + 5, color); g.fill(x + 8, y + 5, x + 11, y + 10, color);
    }
    private void drawLabelsGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 1, y + 2, x + 12, y + 3, color); g.fill(x + 1, y + 2, x + 2, y + 13, color);
        g.fill(x + 1, y + 12, x + 9, y + 13, color); g.fill(x + 8, y + 8, x + 12, y + 9, color);
        g.fill(x + 10, y + 5, x + 11, y + 8, color);
    }
    private void drawBeaconGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 6, y + 1, x + 7, y + 13, color); g.fill(x + 3, y + 5, x + 10, y + 6, color);
        g.fill(x + 1, y + 3, x + 2, y + 5, color); g.fill(x + 11, y + 3, x + 12, y + 5, color);
        g.fill(x + 3, y + 1, x + 4, y + 3, color); g.fill(x + 9, y + 1, x + 10, y + 3, color);
    }
    private void drawMonitorGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        border(g, x, y, x + 14, y + 10, color); g.fill(x + 6, y + 10, x + 8, y + 12, color); g.fill(x + 3, y + 12, x + 11, y + 13, color);
    }

    private void drawInfoGlyph(GuiGraphicsExtractor g, int x, int y) {
        // Thin outlined info circle with a hand-drawn stem and dot.
        border(g, x + 1, y, x + 12, y + 1, MUTED); border(g, x + 1, y + 11, x + 12, y + 12, MUTED);
        g.fill(x, y + 2, x + 1, y + 10, MUTED); g.fill(x + 12, y + 2, x + 13, y + 10, MUTED);
        g.fill(x + 6, y + 3, x + 7, y + 4, MUTED); g.fill(x + 6, y + 5, x + 7, y + 9, MUTED);
    }
    private void nav(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String icon, String label, boolean active) {
        if (active || inside(mx, my, x, y, w, 32)) rounded(g, x, y, x + w, y + 32, active ? WaypointsMod.uiAccentColor : PANEL, 7);
        int tint = active ? 0xFF101410 : MUTED;
        if ("Waypoints".equals(label) || label.isEmpty()) drawPinGlyph(g, x + 11, y + 9, tint);
        else if ("Map".equals(label)) drawCubeGlyph(g, x + 10, y + 10, tint);
        else if ("Settings".equals(label)) drawSlidersGlyph(g, x + 10, y + 10, tint);
        else drawInfoGlyph(g, x + 10, y + 10);
        if (!label.isEmpty()) WaypointUiText.draw(g, font, label, x + 30, y + 10, active ? 0xFF101410 : MUTED, active);
    }
    private void toggle(GuiGraphicsExtractor g, int x, int y, boolean enabled) {
        rounded(g, x, y, x + 26, y + 15, enabled ? 0xFF45652A : 0xFF3B4247, 8);
        rounded(g, enabled ? x + 13 : x + 2, y + 2, enabled ? x + 24 : x + 13, y + 13, enabled ? WaypointsMod.uiAccentColor : 0xFF9BA4A8, 6);
    }
    private void card(GuiGraphicsExtractor g, int x, int y, int w, int h) { rounded(g, x, y, x + w, y + h, PANEL, 9); border(g, x, y, x + w, y + h, EDGE); }
    private void buttonLabel(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = inside(mx, my, x, y, w, h);
        rounded(g, x, y, x + w, y + h, primary ? WaypointsMod.uiAccentColor : hover ? 0xFF242B2F : PANEL, 6);
        if (!primary) border(g, x, y, x + w, y + h, EDGE);
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + (h - WaypointUiText.LINE_HEIGHT) / 2, primary ? BG : TEXT, false);
    }
    private String fit(String value, int max) { String s = value; while (!s.isEmpty() && WaypointUiText.width(font, s) > max) s = s.substring(0, s.length() - 1); return s.equals(value) ? s : s + "..."; }
    private static void border(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color) {
        g.fill(x, y, x2, y + 1, color); g.fill(x, y2 - 1, x2, y2, color); g.fill(x, y, x + 1, y2, color); g.fill(x2 - 1, y, x2, y2, color);
    }
    private static void rounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int radius) {
        int w = x2 - x, h = y2 - y; if (w <= 0 || h <= 0) return; int r = Math.min(WaypointsMod.smoothUiEdges ? Math.max(radius, 8) : Math.min(radius, 3), Math.min(w / 2, h / 2));
        g.fill(x + r, y, x2 - r, y2, color); g.fill(x, y + r, x2, y2 - r, color);
        for (int row = 0; row < r; row++) { int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - Math.pow(r - row - .5, 2))));
            g.fill(x + inset, y + row, x2 - inset, y + row + 1, color); g.fill(x + inset, y2 - row - 1, x2 - inset, y2 - row, color); }
    }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
}
