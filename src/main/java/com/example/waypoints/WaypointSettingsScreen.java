package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Preferences workspace matching the dark, lime-accented Waypoint Manager design. */
public final class WaypointSettingsScreen extends Screen {
    private static final int BG = 0xFF0B0C0E;
    private static final int RAIL = 0xFF101113;
    private static final int PANEL = 0xFF131518;
    private static final int PANEL_ALT = 0xFF0F1113;
    private static final int EDGE = 0xFF292D31;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFFA3ABB0;
    private static final int QUIET = 0xFF727B82;
    private static final int ACCENT = 0xFFB7F36B;

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
        hitTargets.clear();
        scrollHitTargets.clear();
        railWidth = width >= 800 ? 176 : 54;
        int inset = width >= 800 ? 24 : 12;
        contentX = railWidth + inset;
        contentRight = width - inset;
        contentWidth = Math.max(220, contentRight - contentX);
        headingY = 28;
        cardsY = 98;
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
        scrollButton(generalToggleX, cardsY + 43, 28, 18, this::toggleLabels);
        scrollButton(generalToggleX, cardsY + 66, 28, 18, this::toggleDistances);
        scrollButton(generalToggleX, cardsY + 89, 28, 18, this::toggleCoordinates);
        int sizeW = Math.max(40, Math.min(66, (leftW - 82) / 3));
        int sizeStart = contentX + leftW - (sizeW * 3 + 28);
        for (int i = 0; i < 3; i++) {
            final int size = i;
            scrollButton(sizeStart + i * (sizeW + 4), cardsY + labelCardHeight - 30, sizeW, 20, () -> {
                WaypointsMod.markerSizeMode = size;
                save();
            });
        }

        // Map Settings card: beacon visibility, color matching and beam intensity.
        int mapToggleX = dataX + dataW - 42;
        scrollButton(mapToggleX, beamY + 49, 28, 18, this::toggleBeams);
        scrollButton(mapToggleX, beamY + 78, 28, 18, this::toggleBeamColor);
        beamTrackX = dataX + 16;
        beamTrackWidth = Math.max(60, dataW - 32);
        beamTrackY = beamY + beamCardHeight - 24;

        // Appearance card: dimension filtering, HUD visibility and distance cap.
        int appearanceToggleX = contentX + leftW - 42;
        scrollButton(appearanceToggleX, displayY + 43, 28, 18, () -> {
            WaypointsMod.currentDimensionOnly = !WaypointsMod.currentDimensionOnly;
            save();
        });
        scrollButton(appearanceToggleX, displayY + 66, 28, 18, () -> {
            WaypointsMod.hideHudWhenMenuOpen = !WaypointsMod.hideHudWhenMenuOpen;
            save();
        });
        scrollButton(appearanceToggleX, displayY + 89, 28, 18, () -> {
            WaypointsMod.smoothUiEdges = !WaypointsMod.smoothUiEdges;
            save();
        });
        distanceBox = new EditBox(font, contentX + leftW - 102, displayY + 113, 84, 20,
                Component.literal("Maximum display distance"));
        distanceBox.setMaxLength(9);
        distanceBox.setValue(Integer.toString(WaypointsMod.maxDisplayDistance));
        distanceBox.setBordered(false);
        distanceBox.setTextColor(0x00000000);
        distanceBox.setResponder(value -> {
            try {
                WaypointsMod.maxDisplayDistance = Math.max(0, Integer.parseInt(value));
                save();
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(distanceBox);

        // Data actions are real import/export operations, not decorative buttons.
        button(dataX + 14, displayY + 50, Math.max(90, (dataW - 38) / 2), 30, () -> reportTransfer(WaypointsMod.exportWaypointsToFile()));
        button(dataX + 22 + Math.max(90, (dataW - 38) / 2), displayY + 50,
                Math.max(90, (dataW - 38) / 2), 30, () -> reportTransfer(WaypointsMod.importWaypointsFromFile()));
        button(dataX + 14, displayY + 96, Math.max(90, dataW - 28), 27, this::resetDefaults);
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
        WaypointsMod.maxDisplayDistance = 0;
        save();
        Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawSidebar(g, mx, my);
        WaypointUiText.drawTitle(g, font, "Settings", contentX, headingY, TEXT);
        WaypointUiText.draw(g, font, "Tune labels, map markers and waypoint data.", contentX, headingY + 27, MUTED, false);
        rounded(g, contentRight - 104, headingY + 9, contentRight, headingY + 34, 0xFF172016, 7);
        WaypointUiText.draw(g, font, "●  Saved", contentRight - 91, headingY + 17, ACCENT, false);

        // General — matches the top-left settings panel in the reference.
        card(g, contentX, cardsY, leftW, labelCardHeight);
        drawLabelsGlyph(g, contentX + 15, cardsY + 14, ACCENT);
        WaypointUiText.draw(g, font, "General", contentX + 36, cardsY + 16, ACCENT, true);
        compactSetting(g, mx, contentX + 16, cardsY + 43, leftW - 32, "Show Labels", WaypointsMod.showWaypointLabels);
        compactSetting(g, mx, contentX + 16, cardsY + 66, leftW - 32, "Show Distance", WaypointsMod.showWaypointDistances);
        compactSetting(g, mx, contentX + 16, cardsY + 89, leftW - 32, "Show Coordinates", WaypointsMod.showWaypointCoordinates);
        WaypointUiText.draw(g, font, "Size", contentX + 16, cardsY + labelCardHeight - 21, MUTED, false);
        String[] sizeLabels = {"S", "M", "L"};
        int sizeW = Math.max(40, Math.min(66, (leftW - 82) / 3));
        int sizeX = contentX + leftW - (sizeW * 3 + 28);
        for (int i = 0; i < 3; i++) {
            boolean active = WaypointsMod.markerSizeMode == i;
            rounded(g, sizeX, cardsY + labelCardHeight - 30, sizeX + sizeW, cardsY + labelCardHeight - 10,
                    active ? 0xFF2B411D : PANEL_ALT, 6);
            if (active) border(g, sizeX, cardsY + labelCardHeight - 30, sizeX + sizeW, cardsY + labelCardHeight - 10, ACCENT);
            WaypointUiText.draw(g, font, sizeLabels[i], sizeX + (sizeW - WaypointUiText.width(font, sizeLabels[i])) / 2,
                    cardsY + labelCardHeight - 24, active ? ACCENT : TEXT, true);
            sizeX += sizeW + 4;
        }

        // Map Settings — top-right panel.
        card(g, dataX, beamY, dataW, beamCardHeight);
        drawBeaconGlyph(g, dataX + 15, beamY + 14, ACCENT);
        WaypointUiText.draw(g, font, "Map Settings", dataX + 36, beamY + 16, ACCENT, true);
        compactSetting(g, mx, dataX + 16, beamY + 49, dataW - 32, "Show Beacons", WaypointsMod.showWaypointBeams);
        compactSetting(g, mx, dataX + 16, beamY + 78, dataW - 32, "Match Waypoint Color", WaypointsMod.matchWaypointColor);
        WaypointUiText.draw(g, font, "Beam Opacity  " + WaypointsMod.beamOpacity + "%", dataX + 16, beamY + beamCardHeight - 39, MUTED, false);
        rounded(g, beamTrackX, beamTrackY, beamTrackX + beamTrackWidth, beamTrackY + 5, EDGE, 3);
        rounded(g, beamTrackX, beamTrackY, beamTrackX + beamTrackWidth * WaypointsMod.beamOpacity / 100, beamTrackY + 5, ACCENT, 3);
        int knob = beamTrackX + beamTrackWidth * WaypointsMod.beamOpacity / 100;
        rounded(g, knob - 4, beamTrackY - 3, knob + 4, beamTrackY + 8, ACCENT, 5);

        // Appearance — bottom-left panel.
        card(g, contentX, displayY, leftW, displayCardHeight);
        WaypointUiText.draw(g, font, "Appearance", contentX + 16, displayY + 16, ACCENT, true);
        compactSetting(g, mx, contentX + 16, displayY + 43, leftW - 32, "Current Dimension Only", WaypointsMod.currentDimensionOnly);
        compactSetting(g, mx, contentX + 16, displayY + 66, leftW - 32, "Hide HUD In Menus", WaypointsMod.hideHudWhenMenuOpen);
        compactSetting(g, mx, contentX + 16, displayY + 89, leftW - 32, "Smooth UI Edges", WaypointsMod.smoothUiEdges);
        WaypointUiText.draw(g, font, "Max Distance", contentX + 16, displayY + 115, MUTED, false);
        rounded(g, contentX + leftW - 108, displayY + 108, contentX + leftW - 16, displayY + 134, PANEL_ALT, 5);
        border(g, contentX + leftW - 108, displayY + 108, contentX + leftW - 16, displayY + 134, EDGE);

        // Data — bottom-right panel.
        card(g, dataX, displayY, dataW, displayCardHeight);
        WaypointUiText.draw(g, font, "Data", dataX + 16, displayY + 16, ACCENT, true);
        WaypointUiText.draw(g, font, "Import and export your saved waypoint list.", dataX + 16, displayY + 36, MUTED, false);
        int transferW = Math.max(90, (dataW - 38) / 2);
        buttonLabel(g, mx, my, dataX + 14, displayY + 50, transferW, 30, "Export Waypoints", false);
        buttonLabel(g, mx, my, dataX + 22 + transferW, displayY + 50, transferW, 30, "Import Waypoints", false);
        buttonLabel(g, mx, my, dataX + 14, displayY + 96, Math.max(90, dataW - 28), 27, "Reset to Default", false);
        WaypointUiText.draw(g, font, "Preferences save automatically.", dataX + 16, displayY + displayCardHeight - 15, QUIET, false);

        super.extractRenderState(g, mx, my, delta);
        WaypointUiText.drawInput(g, font, distanceBox, contentX + leftW - 101, displayY + 114, 76);
    }

    private void compactSetting(GuiGraphicsExtractor g, int mx, int x, int y, int w, String label, boolean enabled) {
        WaypointUiText.draw(g, font, label, x, y + 3, TEXT, false);
        toggle(g, x + w - 26, y + 1, enabled);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, railWidth, height, RAIL);
        g.fill(railWidth - 1, 0, railWidth, height, EDGE);
        int sideInset = 10;
        rounded(g, sideInset + 8, 20, sideInset + 43, 55, ACCENT, 10);
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
            WaypointUiText.draw(g, font, "v1.4.4", 54, base - 3, ACCENT, true);
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
        int c = 0xFF101410;
        drawPixelLine(g, x + 6, y + 14, x + 20, y + 6, c);
        drawPixelLine(g, x + 20, y + 6, x + 15, y + 21, c);
        drawPixelLine(g, x + 15, y + 21, x + 12, y + 14, c);
        drawPixelLine(g, x + 12, y + 14, x + 6, y + 14, c);
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
        if (active || inside(mx, my, x, y, w, 32)) rounded(g, x, y, x + w, y + 32, active ? 0xFF202629 : PANEL, 7);
        int tint = active ? ACCENT : MUTED;
        if ("Waypoints".equals(label) || label.isEmpty()) drawPinGlyph(g, x + 11, y + 9, tint);
        else if ("Map".equals(label)) drawCubeGlyph(g, x + 10, y + 10, tint);
        else if ("Settings".equals(label)) drawSlidersGlyph(g, x + 10, y + 10, tint);
        else drawInfoGlyph(g, x + 10, y + 10);
        if (!label.isEmpty()) WaypointUiText.draw(g, font, label, x + 30, y + 10, active ? TEXT : MUTED, false);
    }
    private void settingRow(GuiGraphicsExtractor g, int mx, int x, int y, int w, String title, String desc, boolean enabled) {
        WaypointUiText.draw(g, font, fit(title, w - 54), x, y, TEXT, false);
        int textWidth = Math.max(70, w - 58);
        var lines = font.split(WaypointUiText.component(desc), textWidth);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            WaypointUiText.draw(g, font, lines.get(i), x, y + 13 + i * 12, MUTED, false);
        }
        int sx = x + w - 26;
        toggle(g, sx, y + 1, enabled);
    }
    private void toggle(GuiGraphicsExtractor g, int x, int y, boolean enabled) {
        rounded(g, x, y, x + 26, y + 15, enabled ? 0xFF45652A : 0xFF3B4247, 8);
        rounded(g, enabled ? x + 13 : x + 2, y + 2, enabled ? x + 24 : x + 13, y + 13, enabled ? ACCENT : 0xFF9BA4A8, 6);
    }
    private void card(GuiGraphicsExtractor g, int x, int y, int w, int h) { rounded(g, x, y, x + w, y + h, PANEL, 9); border(g, x, y, x + w, y + h, EDGE); }
    private void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        List<Waypoint> saved = WaypointsMod.menuWaypoints();
        String currentDimension = WaypointsMod.normalizeDimension(WaypointsMod.currentDimension());
        Waypoint featured = saved.stream()
                .filter(waypoint -> {
                    String d = WaypointsMod.normalizeDimension(waypoint.dimension());
                    return d.equals(currentDimension) || d.equals("all");
                })
                .findFirst()
                .orElse(saved.isEmpty() ? null : saved.get(0));
        int centerX = featured != null ? featured.x() : mc.player == null ? 0 : mc.player.blockPosition().getX();
        int centerZ = featured != null ? featured.z() : mc.player == null ? 0 : mc.player.blockPosition().getZ();
        int centerY = featured != null ? featured.y() : mc.player == null ? 64 : mc.player.blockPosition().getY();
        int markerColor = featured != null ? 0xFF000000 | WaypointsMod.resolveColor(featured) : ACCENT;
        String label = featured != null ? featured.name() : "Your position";
        int distance = mc.player == null || featured == null ? 256 : (int)Math.round(Math.sqrt(
                mc.player.distanceToSqr(centerX + 0.5, centerY + 0.5, centerZ + 0.5)));
        String details = distance + "m";
        SurfaceMapPreview.draw(g, font, x, y, w, h, centerX, centerZ,
                featured != null ? featured.dimension() : WaypointsMod.currentDimension(),
                markerColor, label, details);
    }
    private void buttonLabel(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = inside(mx, my, x, y, w, h);
        rounded(g, x, y, x + w, y + h, primary ? ACCENT : hover ? 0xFF242B2F : PANEL, 6);
        if (!primary) border(g, x, y, x + w, y + h, EDGE);
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + (h - WaypointUiText.LINE_HEIGHT) / 2, primary ? BG : TEXT, false);
    }
    private String fit(String value, int max) { String s = value; while (!s.isEmpty() && WaypointUiText.width(font, s) > max) s = s.substring(0, s.length() - 1); return s.equals(value) ? s : s + "..."; }
    private String worldTitle() {
        Minecraft mc = Minecraft.getInstance();
        try { if (mc.getCurrentServer() != null) return mc.getCurrentServer().name;
            if (mc.getSingleplayerServer() != null) return mc.getSingleplayerServer().getWorldData().getLevelName(); }
        catch (Throwable ignored) { }
        return "Current World";
    }
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
