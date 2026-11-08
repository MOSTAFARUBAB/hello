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

    private int railWidth, contentX, contentRight, contentWidth, leftW;
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
        railWidth = width >= 1120 ? 190 : width >= 1000 ? 165 : width >= 800 ? Math.max(150, Math.min(190, Math.round(width * 0.16f))) : 54;
        int leftInset = width >= 1120 ? 35 : width >= 1000 ? 30 : 12;
        int rightInset = width >= 1120 ? 35 : width >= 1000 ? 32 : 12;
        contentX = railWidth + leftInset;
        contentRight = width - rightInset;
        contentWidth = Math.max(220, contentRight - contentX);
        headingY = Math.max(58, Math.round(height * 0.095f));
        cardsY = Math.max(112, Math.round(height * 0.168f));
        leftW = contentWidth < 520 ? contentWidth : Math.min(570, Math.max(330, Math.round(contentWidth * 0.678f)));
        labelCardHeight = Math.max(224, Math.round(height * 0.275f));
        beamY = cardsY + labelCardHeight + 15;
        beamCardHeight = Math.max(190, Math.round(height * 0.227f));
        displayY = beamY + beamCardHeight + 15;
        displayCardHeight = 188;
        scrollTop = cardsY;
        scrollBottom = height - 55;
        maxScroll = Math.max(0, displayY + displayCardHeight - scrollBottom);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        if (railWidth > 70) {
            int sideInset = railWidth > 170 ? 20 : 10;
            int navWidth = railWidth - sideInset - 14;
            button(sideInset, 153, navWidth, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            button(sideInset, 194, navWidth, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointMapScreen()));
        } else {
            button(8, 104, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            button(8, 150, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointMapScreen()));
        }

        // Waypoint label preferences.
        int toggleX = contentX + leftW - 42;
        scrollButton(toggleX, cardsY + 55, 28, 18, this::toggleLabels);
        scrollButton(toggleX, cardsY + 100, 28, 18, this::toggleDistances);
        scrollButton(toggleX, cardsY + 144, 28, 18, this::toggleCoordinates);
        int sizeW = Math.max(42, (leftW - 190) / 3);
        int sizeStart = contentX + leftW - (sizeW * 3 + 28);
        for (int i = 0; i < 3; i++) {
            final int size = i;
            scrollButton(sizeStart + i * (sizeW + 4), cardsY + 189, sizeW, 26, () -> {
                WaypointsMod.markerSizeMode = size;
                save();
            });
        }

        // Beacon beam preferences.
        scrollButton(toggleX, beamY + 55, 28, 18, this::toggleBeams);
        scrollButton(toggleX, beamY + 100, 28, 18, this::toggleBeamColor);
        beamTrackX = contentX + 17;
        beamTrackWidth = Math.max(60, leftW - 34);
        beamTrackY = beamY + 171;

        // Display preferences + editable maximum distance. 0 means unlimited.
        scrollButton(toggleX, displayY + 55, 28, 18, () -> {
            WaypointsMod.currentDimensionOnly = !WaypointsMod.currentDimensionOnly;
            save();
        });
        scrollButton(toggleX, displayY + 100, 28, 18, () -> {
            WaypointsMod.hideHudWhenMenuOpen = !WaypointsMod.hideHudWhenMenuOpen;
            save();
        });
        distanceBox = new EditBox(font, contentX + leftW - 88, displayY + 143 - scrollOffset, 72, 20, Component.literal("Maximum display distance"));
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

        button(contentX + leftW - 79, height - 49, 79, 25, this::resetDefaults);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && event.x() >= beamTrackX - 5 && event.x() <= beamTrackX + beamTrackWidth + 5
                && event.y() >= beamTrackY - scrollOffset - 5 && event.y() <= beamTrackY - scrollOffset + 12) {
            draggingBeamOpacity = true;
            setDragging(true);
            updateBeamOpacity(event.x());
            return true;
        }
        if (event.button() == 1) {
            if (event.y() >= scrollTop && event.y() <= scrollBottom
                    && event.x() >= contentX && event.x() <= contentX + leftW) {
            for (int i = scrollHitTargets.size() - 1; i >= 0; i--) {
                HitTarget target = scrollHitTargets.get(i);
                if (target.contains(event.x(), event.y() + scrollOffset)) {
                    target.action().run();
                    return true;
                }
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
        WaypointsMod.maxDisplayDistance = 0;
        save();
        Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawSidebar(g, mx, my);
        int topBarHeight = height >= 880 ? 55 : 44;
        g.fill(railWidth, 0, width, topBarHeight, BG);
        g.fill(railWidth, topBarHeight - 1, width, topBarHeight, EDGE);
        drawCubeGlyph(g, contentX, 12, MUTED);
        WaypointUiText.draw(g, font, fit(worldTitle(), 120) + "  /  Settings", contentX + 18, 15, MUTED, false);
        g.fill(width - 132, 17, width - 127, 22, ACCENT);
        WaypointUiText.draw(g, font, "Local workspace", width - 120, 15, MUTED, false);
        border(g, width - 49, 14, width - 37, 26, QUIET);
        WaypointUiText.draw(g, font, "?", width - 45, 16, MUTED, false);
        WaypointUiText.drawTitle(g, font, "Your world. Your view.", contentX, headingY, TEXT);
        WaypointUiText.draw(g, font, "Make waypoints work the way you explore.", contentX, headingY + 31, MUTED, false);
        g.fill(contentRight - 90, headingY + 19, contentRight - 84, headingY + 25, ACCENT);
        WaypointUiText.draw(g, font, "Changes saved", contentRight - 78, headingY + 16, MUTED, false);

        int rightX = contentX + leftW + 18;
        int rightW = Math.max(0, contentRight - rightX);
        g.enableScissor(contentX, scrollTop, contentX + leftW, scrollBottom);
        g.pose().pushMatrix();
        g.pose().translate(0.0f, -scrollOffset);
        int y = cardsY;

        // Waypoint labels card: four well-spaced rows, no overlapping descriptions.
        card(g, contentX, y, leftW, labelCardHeight);
        drawLabelsGlyph(g, contentX + 16, y + 17, ACCENT);
        WaypointUiText.draw(g, font, "Waypoint labels", contentX + 36, y + 18, TEXT, false);
        settingRow(g, mx, contentX + 16, y + 55, leftW - 32, "Show waypoint labels", "See waypoint names while exploring your world.", WaypointsMod.showWaypointLabels);
        settingRow(g, mx, contentX + 16, y + 100, leftW - 32, "Show distance", "Display your distance to each waypoint in blocks.", WaypointsMod.showWaypointDistances);
        settingRow(g, mx, contentX + 16, y + 144, leftW - 32, "Show coordinates", "Include X, Y and Z beneath each waypoint name.", WaypointsMod.showWaypointCoordinates);
        WaypointUiText.draw(g, font, "Label size", contentX + 16, y + 190, MUTED, false);
        String[] sizes = {"Small", "Medium", "Large"};
        int sizeW = Math.max(42, (leftW - 190) / 3);
        int sizeX = contentX + leftW - (sizeW * 3 + 28);
        for (int i = 0; i < 3; i++) {
            boolean active = WaypointsMod.markerSizeMode == i;
            rounded(g, sizeX, y + 189, sizeX + sizeW, y + 215, active ? 0xFF29361F : PANEL_ALT, 5);
            if (active) border(g, sizeX, y + 189, sizeX + sizeW, y + 215, 0xFF759D48);
            WaypointUiText.draw(g, font, sizes[i], sizeX + (sizeW - WaypointUiText.width(font, sizes[i])) / 2, y + 198, active ? ACCENT : MUTED, false);
            sizeX += sizeW + 4;
        }

        // Beacon beams card.
        card(g, contentX, beamY, leftW, beamCardHeight);
        drawBeaconGlyph(g, contentX + 16, beamY + 15, ACCENT);
        WaypointUiText.draw(g, font, "Beacon beams", contentX + 36, beamY + 18, TEXT, false);
        settingRow(g, mx, contentX + 16, beamY + 55, leftW - 32, "Show beacon beams", "Guide your way with a vertical beam at each waypoint.", WaypointsMod.showWaypointBeams);
        settingRow(g, mx, contentX + 16, beamY + 100, leftW - 32, "Match waypoint color", "Use each waypoint's color for its beacon beam.", WaypointsMod.matchWaypointColor);
        WaypointUiText.draw(g, font, "Beam opacity", contentX + 16, beamY + 151, MUTED, false);
        rounded(g, beamTrackX, beamTrackY, beamTrackX + beamTrackWidth, beamTrackY + 5, EDGE, 3);
        rounded(g, beamTrackX, beamTrackY, beamTrackX + beamTrackWidth * WaypointsMod.beamOpacity / 100, beamTrackY + 5, ACCENT, 3);
        int knob = beamTrackX + beamTrackWidth * WaypointsMod.beamOpacity / 100;
        rounded(g, knob - 4, beamTrackY - 3, knob + 4, beamTrackY + 8, ACCENT, 5);
        WaypointUiText.draw(g, font, WaypointsMod.beamOpacity + "%", contentX + leftW - 44, beamY + 149, MUTED, false);

        // Display preferences card.
        card(g, contentX, displayY, leftW, displayCardHeight);
        drawMonitorGlyph(g, contentX + 16, displayY + 17, ACCENT);
        WaypointUiText.draw(g, font, "Display preferences", contentX + 36, displayY + 18, TEXT, false);
        settingRow(g, mx, contentX + 16, displayY + 55, leftW - 32, "Current dimension only", "Hide waypoints from dimensions you're not in.", WaypointsMod.currentDimensionOnly);
        settingRow(g, mx, contentX + 16, displayY + 100, leftW - 32, "Hide HUD when menu is open", "Keep your view clean while browsing menus.", WaypointsMod.hideHudWhenMenuOpen);
        WaypointUiText.draw(g, font, "Maximum display distance", contentX + 16, displayY + 148, MUTED, false);
        rounded(g, contentX + leftW - 92, displayY + 143, contentX + leftW - 18, displayY + 170, PANEL_ALT, 5);
        border(g, contentX + leftW - 92, displayY + 143, contentX + leftW - 18, displayY + 170, EDGE);
        WaypointUiText.draw(g, font, "blocks", contentX + leftW - 48, displayY + 148, QUIET, false);

        g.pose().popMatrix();
        g.disableScissor();

        // Right preview and compact help card stay fixed while the settings column scrolls.
        if (rightW >= 160) {
            WaypointUiText.draw(g, font, "IN-GAME PREVIEW", rightX, y + 2, QUIET, false);
            drawPreview(g, rightX, y + 24, rightW, 170);
            int infoY = y + 24 + 170 + 53;
            card(g, rightX, infoY, rightW, 131);
            drawInfoGlyph(g, rightX + 16, infoY + 15);
            WaypointUiText.draw(g, font, "A starting point, not a limit.", rightX + 14, infoY + 38, TEXT, false);
            var infoLines = font.split(WaypointUiText.component(
                    "These are your world defaults. You can customize labels and beams for individual waypoints when creating or editing them."),
                    Math.max(1, rightW - 28));
            for (int i = 0; i < Math.min(4, infoLines.size()); i++) {
                WaypointUiText.draw(g, font, infoLines.get(i), rightX + 14, infoY + 58 + i * 13, MUTED, false);
            }
            WaypointUiText.draw(g, font, "Preferences save automatically", rightX + 14, infoY + 112, MUTED, false);
        }
        WaypointUiText.draw(g, font, "Preferences apply to all waypoints in this world.", contentX, height - 37, QUIET, false);
        buttonLabel(g, mx, my, contentX + leftW - 79, height - 49, 79, 25, "Reset defaults", false);
        if (maxScroll > 0) {
            int trackX = contentX + leftW - 4;
            int trackTop = scrollTop + 3;
            int trackHeight = scrollBottom - scrollTop - 6;
            int thumbHeight = Math.max(24, trackHeight * trackHeight / (trackHeight + maxScroll));
            int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
            rounded(g, trackX, trackTop, trackX + 2, trackTop + trackHeight, EDGE, 1);
            rounded(g, trackX, thumbY, trackX + 2, thumbY + thumbHeight, MUTED, 1);
        }

        // Native edit-box owns input; draw the shared UI glyphs over its transparent text.
        g.enableScissor(contentX, scrollTop, contentX + leftW, scrollBottom);
        super.extractRenderState(g, mx, my, delta);
        WaypointUiText.drawInput(g, font, distanceBox, contentX + leftW - 84, displayY + 147 - scrollOffset, 48);
        g.disableScissor();
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, railWidth, height, RAIL);
        g.fill(railWidth - 1, 0, railWidth, height, EDGE);
        int sideInset = railWidth > 170 ? 20 : 10;
        int labelX = sideInset + 10;
        int navWidth = railWidth - sideInset - 14;
        int brandX = railWidth > 170 ? 20 : 10;
        rounded(g, brandX, 15, brandX + 27, 42, ACCENT, 7);
        drawBrandMark(g, brandX, 15);
        if (railWidth > 70) {
            int brandTextX = railWidth > 170 ? 56 : 44;
            WaypointUiText.draw(g, font, "Waypoint", brandTextX, 19, TEXT, false);
            WaypointUiText.draw(g, font, "M A N A G E R", brandTextX, 31, MUTED, false);
            rounded(g, sideInset, 65, railWidth - 14, 115, PANEL, 8);
            WaypointUiText.draw(g, font, fit(worldTitle(), railWidth - 48), labelX, 76, TEXT, false);
            g.fill(labelX, 98, labelX + 4, 102, ACCENT);
            WaypointUiText.draw(g, font, "Java Edition | 26.3", labelX + 10, 96, MUTED, false);
            WaypointUiText.draw(g, font, "WORKSPACE", sideInset, 144, QUIET, false);
            nav(g, mx, my, sideInset, 153, navWidth, "", "Waypoints", false);
            nav(g, mx, my, sideInset, 194, navWidth, "", "Map", false);
            nav(g, mx, my, sideInset, 235, navWidth, "", "Settings", true);
            int footerTop = height >= 880 ? height - 186 : height >= 830 ? height - 159 : height - 162;
            int footerBottom = height >= 880 ? height - 75 : height >= 830 ? height - 59 : height - 62;
            rounded(g, sideInset, footerTop, railWidth - 14, footerBottom, PANEL_ALT, 8);
            WaypointUiText.draw(g, font, "Your world. Your waypoints.", labelX, footerTop + 31, TEXT, false);
            WaypointUiText.draw(g, font, "Stored locally, ready for", labelX, footerTop + 51, MUTED, false);
            WaypointUiText.draw(g, font, "your next adventure.", labelX, footerTop + 66, MUTED, false);
            g.fill(labelX, footerBottom - 21, labelX + 5, footerBottom - 16, ACCENT);
            WaypointUiText.draw(g, font, "All changes saved", labelX + 11, footerBottom - 22, MUTED, false);
            int profileTop = footerBottom + 25;
            rounded(g, sideInset, profileTop, sideInset + 28, profileTop + 28, 0xFF1B1E21, 6);
            WaypointUiText.draw(g, font, "AL", sideInset + 7, profileTop + 9, TEXT, false);
            WaypointUiText.draw(g, font, Minecraft.getInstance().player == null ? "Player" : Minecraft.getInstance().player.getName().getString(), labelX + 27, profileTop + 6, TEXT, false);
            WaypointUiText.draw(g, font, "In-game", labelX + 27, profileTop + 18, MUTED, false);
            for (int dot = 0; dot < 3; dot++) g.fill(railWidth - 27 + dot * 3, profileTop + 13, railWidth - 25 + dot * 3, profileTop + 15, MUTED);
        } else {
            nav(g, mx, my, 8, 104, 38, "", "", false);
            nav(g, mx, my, 8, 150, 38, "", "", false);
            nav(g, mx, my, 8, 196, 38, "", "", true);
        }
    }

    private void drawBrandMark(GuiGraphicsExtractor g, int x, int y) {
        int c = 0xFF101410;
        drawPixelLine(g, x + 6, y + 14, x + 20, y + 6, c); drawPixelLine(g, x + 20, y + 6, x + 15, y + 21, c);
        drawPixelLine(g, x + 15, y + 21, x + 12, y + 14, c); drawPixelLine(g, x + 12, y + 14, x + 6, y + 14, c);
    }
    private void drawPixelLine(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int dx=Math.abs(x2-x1), sx=x1<x2?1:-1, dy=-Math.abs(y2-y1), sy=y1<y2?1:-1, err=dx+dy, x=x1, y=y1;
        while (true) { g.fill(x,y,x+1,y+1,color); if (x==x2 && y==y2) break; int e2=2*err; if (e2>=dy) { err+=dy; x+=sx; } if (e2<=dx) { err+=dx; y+=sy; } }
    }
    private void drawCubeGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 5, y, x + 6, y + 1, color); g.fill(x + 2, y + 2, x + 9, y + 3, color);
        g.fill(x, y + 4, x + 2, y + 6, color); g.fill(x + 9, y + 4, x + 11, y + 6, color);
        g.fill(x + 2, y + 7, x + 9, y + 8, color); g.fill(x + 5, y + 3, x + 6, y + 8, color);
        g.fill(x + 1, y + 3, x + 2, y + 4, color); g.fill(x + 9, y + 3, x + 10, y + 4, color);
    }
    private void drawPinGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 4, y, x + 8, y + 2, color); g.fill(x + 2, y + 2, x + 10, y + 6, color);
        g.fill(x + 3, y + 6, x + 9, y + 9, color); g.fill(x + 5, y + 9, x + 7, y + 12, color);
        g.fill(x + 4, y + 3, x + 8, y + 6, PANEL);
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
        else drawSlidersGlyph(g, x + 10, y + 10, tint);
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
        int w = x2 - x, h = y2 - y; if (w <= 0 || h <= 0) return; int r = Math.min(radius, Math.min(w / 2, h / 2));
        g.fill(x + r, y, x2 - r, y2, color); g.fill(x, y + r, x2, y2 - r, color);
        for (int row = 0; row < r; row++) { int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - Math.pow(r - row - .5, 2))));
            g.fill(x + inset, y + row, x2 - inset, y + row + 1, color); g.fill(x + inset, y2 - row - 1, x2 - inset, y2 - row, color); }
    }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
}
