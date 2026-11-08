package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full workspace dashboard replacing the former compact modal waypoint menu. */
public final class WaypointScreen extends Screen {
    private static final String[] FILTER_LABELS = {"All dimensions", "Overworld", "The Nether", "The End"};
    private static final int BG = 0xFF0B0C0E;
    private static final int RAIL = 0xFF101113;
    private static final int PANEL = 0xFF131518;
    private static final int PANEL_ALT = 0xFF0F1113;
    private static final int EDGE = 0xFF292D31;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFF9BA4A8;
    private static final int QUIET = 0xFF687278;
    private static final int ACCENT = 0xFFB7F36B;
    private static final int RED = 0xFFE58A8A;

    private final int requestedPage;
    private int page;
    private String searchQuery;
    private int railWidth, contentX, contentRight, contentWidth;
    private int headingY, statsY, statsHeight, sectionY, transferY;
    private int toolbarY, filtersY, gridY, footerY, columns, rows, cardWidth, cardHeight, pageSize;
    private record HitTarget(int x, int y, int width, int height, Runnable action) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private final List<HitTarget> hitTargets = new ArrayList<>();
    private final List<HitTarget> cardHitTargets = new ArrayList<>();
    private final List<HitTarget> paginationTargets = new ArrayList<>();
    private List<Waypoint> entries = List.of();
    private List<Waypoint> filtered = List.of();
    private EditBox searchBox;
    private static boolean sortByName;
    private static boolean listLayout;

    public WaypointScreen(int page) { this(page, ""); }

    private WaypointScreen(int page, String search) {
        super(Component.literal("Waypoint Manager"));
        this.requestedPage = page;
        this.page = page;
        this.searchQuery = search == null ? "" : search;
    }

    static void openMenu() { open(0); }
    static void open(int page) { Minecraft.getInstance().gui.setScreen(new WaypointScreen(page)); }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override
    protected void init() {
        clearWidgets();
        hitTargets.clear();
        cardHitTargets.clear();
        paginationTargets.clear();
        railWidth = width >= 1120 ? 190 : width >= 1000 ? 165 : width >= 800 ? Math.max(150, Math.min(190, Math.round(width * 0.16f))) : 54;
        int leftInset = width >= 1120 ? 35 : width >= 1000 ? 30 : 12;
        int rightInset = width >= 1120 ? 35 : width >= 1000 ? 32 : 12;
        contentX = railWidth + leftInset;
        contentRight = width - rightInset;
        contentWidth = Math.max(220, contentRight - contentX);
        headingY = Math.max(58, Math.round(height * 0.098f));
        statsY = Math.max(110, Math.round(height * 0.184f));
        statsHeight = Math.max(80, Math.round(height * 0.118f));
        sectionY = Math.max(statsY + statsHeight + 20, Math.round(height * 0.337f));
        transferY = sectionY - 7;
        toolbarY = Math.max(sectionY + 36, Math.round(height * 0.385f));
        filtersY = toolbarY + 50;
        gridY = filtersY + 43;
        footerY = height - 92;
        columns = listLayout ? 1 : contentWidth >= 620 ? 3 : contentWidth >= 390 ? 2 : 1;
        rows = Math.max(1, Math.min(2, (footerY - gridY - 12) / 120));
        cardWidth = (contentWidth - (columns - 1) * 12) / columns;
        cardHeight = Math.max(96, (footerY - gridY - 12 * (rows - 1) - 12) / rows);
        pageSize = columns * rows;

        entries = new ArrayList<>(WaypointsMod.menuWaypoints());
        if (sortByName) entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        refreshFiltered();
        page = Math.max(0, Math.min(requestedPage, Math.max(0, (filtered.size() - 1) / pageSize)));

        // Sidebar navigation.
        int navY = railWidth > 70 ? 153 : 104;
        if (railWidth > 70) {
            int sideInset = railWidth > 170 ? 20 : 10;
            int navWidth = railWidth - sideInset - 14;
            button(sideInset, navY, navWidth, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            button(sideInset, navY + 41, navWidth, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointMapScreen()));
            button(sideInset, navY + 82, navWidth, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
        } else {
            button(8, navY, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            button(8, navY + 46, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointMapScreen()));
            button(8, navY + 92, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
        }
        // Create action and data transfer controls.
        button(contentRight - 132, headingY + 2, 132, 34,
                () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, currentPage())));
        if (WaypointsMod.navigationTarget() != null) {
            button(contentRight - 240, headingY + 2, 98, 34, WaypointsMod::stopNavigation);
        }
        button(contentRight - 190, transferY, 86, 27, () -> showTransferMessage(WaypointsMod.importWaypointsFromFile(), true));
        button(contentRight - 98, transferY, 86, 27, () -> showTransferMessage(WaypointsMod.exportWaypointsToFile(), false));
        int searchWidth = Math.max(70, contentWidth - 220);
        searchBox = new EditBox(font, contentX + 30, toolbarY + 7,
                Math.max(40, searchWidth - 42), 21, Component.literal("Search waypoints"));
        searchBox.setMaxLength(48);
        searchBox.setValue(searchQuery);
        searchBox.setBordered(false);
        searchBox.setTextColor(0x00000000);
        searchBox.setResponder(value -> {
            searchQuery = value;
            page = 0;
            refreshFiltered();
            rebuildCardTargets();
            rebuildPaginationTargets();
        });
        addRenderableWidget(searchBox);
        button(contentRight - 190, toolbarY, 120, 35, () -> {
            sortByName = !sortByName;
            rebuildDashboard();
        });
        button(contentRight - 62, toolbarY, 62, 35, () -> {
            listLayout = !listLayout;
            rebuildDashboard();
        });

        // Dimension filters: current, Overworld, Nether, End, All.
        int filterX = contentX;
        int[] modes = {4, 1, 2, 3};
        int[] widths = {106, 86, 86, 72};
        for (int i = 0; i < modes.length; i++) {
            final int mode = modes[i];
            button(filterX, filtersY, widths[i], 27, () -> {
                WaypointsMod.menuDimensionMode = mode;
                page = 0;
                rebuildDashboard();
            });
            filterX += widths[i] + 7;
        }
        button(contentRight - 78, filtersY, 78, 24, () -> {
            WaypointsMod.menuDimensionMode = 0;
            page = 0;
            rebuildDashboard();
        });
        rebuildCardTargets();
        rebuildPaginationTargets();
    }

    private void rebuildPaginationTargets() {
        paginationTargets.clear();
        if (pageCount() <= 1) return;
        int visiblePages = Math.min(width >= 800 ? 5 : 3, pageCount());
        int startPage = Math.max(0, Math.min(currentPage() - visiblePages / 2, pageCount() - visiblePages));
        int pageButton = 24;
        int gap = 4;
        int totalWidth = pageButton + 8 + visiblePages * pageButton + Math.max(0, visiblePages - 1) * gap + 8 + pageButton;
        int x = contentRight - totalWidth;
        int y = footerY;
        paginationTargets.add(new HitTarget(x, y, pageButton, 24, () -> changePage(-1)));
        int pagesX = x + pageButton + 8;
        for (int i = 0; i < visiblePages; i++) {
            final int pageIndex = startPage + i;
            paginationTargets.add(new HitTarget(pagesX + i * (pageButton + gap), y, pageButton, 24,
                    () -> {
                        if (pageIndex != currentPage()) {
                            Minecraft.getInstance().gui.setScreen(new WaypointScreen(pageIndex, searchQuery));
                        }
                    }));
        }
        int nextX = pagesX + visiblePages * (pageButton + gap) - gap + 8;
        paginationTargets.add(new HitTarget(nextX, y, pageButton, 24, () -> changePage(1)));
    }

    private void rebuildDashboard() {
        Minecraft.getInstance().gui.setScreen(new WaypointScreen(currentPage(), searchQuery));
    }

    private void rebuildCardTargets() {
        // One target set per visible slot. The slot is resolved at click time, so
        // search edits never leave stale buttons pointing to older waypoints.
        int visibleCount = Math.max(0, Math.min(pageSize, filtered.size() - currentPage() * pageSize));
        cardHitTargets.clear();
        for (int slot = 0; slot < visibleCount; slot++) {
            final int visibleSlot = slot;
            int col = slot % columns;
            int row = slot / columns;
            int x = contentX + col * (cardWidth + 12);
            int y = gridY + row * (cardHeight + 12);
            cardHitTargets.add(new HitTarget(x, y, Math.max(1, cardWidth - 54), cardHeight, () -> editSlot(visibleSlot)));
            cardHitTargets.add(new HitTarget(x + cardWidth - 50, y + 8, 20, 20, () -> editSlot(visibleSlot)));
            cardHitTargets.add(new HitTarget(x + cardWidth - 27, y + 8, 20, 20, () -> deleteSlot(visibleSlot)));
            cardHitTargets.add(new HitTarget(x + cardWidth - 76, y + cardHeight - 28, 30, 22, () -> navigateSlot(visibleSlot)));
        }
    }

    private Waypoint visibleSlot(int slot) {
        int index = currentPage() * pageSize + slot;
        return index >= 0 && index < filtered.size() ? filtered.get(index) : null;
    }

    private void editSlot(int slot) {
        Waypoint w = visibleSlot(slot);
        if (w != null) Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(w, currentPage()));
    }

    private void navigateSlot(int slot) {
        Waypoint w = visibleSlot(slot);
        if (w != null) {
            WaypointsMod.navigateTo(w);
            Minecraft.getInstance().gui.setScreen(null);
        }
    }

    private void deleteSlot(int slot) {
        Waypoint w = visibleSlot(slot);
        if (w == null) return;
        WaypointsMod.remove(w);
        Minecraft.getInstance().gui.setScreen(new WaypointScreen(currentPage(), searchQuery));
    }

    private void refreshFiltered() {
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        String current = WaypointsMod.normalizeDimension(WaypointsMod.currentDimension());
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint w : entries) {
            String dimension = WaypointsMod.normalizeDimension(w.dimension());
            boolean dimensionMatches = switch (WaypointsMod.menuDimensionMode) {
                case 0 -> dimension.equals(current);
                case 1 -> dimension.equals("minecraft:overworld");
                case 2 -> dimension.equals("minecraft:the_nether");
                case 3 -> dimension.equals("minecraft:the_end");
                default -> true; // 4 = all dimensions
            };
            if (!dimensionMatches) continue;
            String value = (w.name() + " " + w.dimension() + " " + w.x() + " " + w.y() + " " + w.z()).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || value.contains(q)) result.add(w);
        }
        filtered = List.copyOf(result);
    }

    private int currentPage() { return Math.max(0, Math.min(page, Math.max(0, (filtered.size() - 1) / Math.max(1, pageSize)))); }
    private int pageCount() { return Math.max(1, (filtered.size() + Math.max(1, pageSize) - 1) / Math.max(1, pageSize)); }
    private void changePage(int delta) {
        int next = Math.max(0, Math.min(pageCount() - 1, currentPage() + delta));
        if (next != page) Minecraft.getInstance().gui.setScreen(new WaypointScreen(next, searchQuery));
    }

    private void showTransferMessage(String message, boolean refresh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.chatListener().handleSystemMessage(Component.literal(message), false);
        if (refresh) Minecraft.getInstance().gui.setScreen(new WaypointScreen(currentPage(), searchQuery));
    }

    private void button(int x, int y, int w, int h, Runnable action) {
        // Pure hit target: the custom interface is drawn by extractRenderState.
        // A vanilla Button here would paint a grey rectangle over the custom card.
        hitTargets.add(new HitTarget(x, y, w, h, action));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            for (int i = hitTargets.size() - 1; i >= 0; i--) {
                HitTarget target = hitTargets.get(i);
                if (target.contains(event.x(), event.y())) {
                    target.action().run();
                    return true;
                }
            }
            for (int i = paginationTargets.size() - 1; i >= 0; i--) {
                HitTarget target = paginationTargets.get(i);
                if (target.contains(event.x(), event.y())) {
                    target.action().run();
                    return true;
                }
            }
            for (int i = cardHitTargets.size() - 1; i >= 0; i--) {
                HitTarget target = cardHitTargets.get(i);
                if (target.contains(event.x(), event.y())) {
                    target.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawSidebar(g, mx, my);
        g.fill(railWidth, 0, width, 44, BG);
        g.fill(railWidth, 43, width, 44, EDGE);
        drawCubeGlyph(g, contentX, 13, MUTED);
        String worldLabel = fit(worldTitle(), 112);
        WaypointUiText.draw(g, font, worldLabel, contentX + 18, 16, MUTED, false);
        int crumbX = contentX + 18 + WaypointUiText.width(font, worldLabel) + 10;
        WaypointUiText.draw(g, font, "/", crumbX, 16, QUIET, false);
        WaypointUiText.draw(g, font, "Waypoints", crumbX + 14, 16, TEXT, false);
        g.fill(width - 132, 18, width - 127, 23, ACCENT);
        WaypointUiText.draw(g, font, "Local workspace", width - 120, 16, MUTED, false);
        border(g, width - 49, 15, width - 37, 27, QUIET);
        WaypointUiText.draw(g, font, "?", width - 45, 17, MUTED, false);

        WaypointUiText.draw(g, font, "Every place, a waypoint.", contentX, headingY, TEXT, false);
        WaypointUiText.draw(g, font, "Keep your world organized. Never lose a place worth returning to.", contentX, headingY + 35, MUTED, false);
        primaryButton(g, mx, my, contentRight - 132, headingY + 2, 132, 34, "+  Create waypoint");
        if (WaypointsMod.navigationTarget() != null) {
            pill(g, contentRight - 240, headingY + 2, 98, 34, "Stop guide", false, mx, my);
        }

        int statGap = 12;
        int statW = (contentWidth - 2 * statGap) / 3;
        statCard(g, contentX, statsY, statW, statsHeight, "Total waypoints", String.valueOf(entries.size()), "Across this world", "");
        statCard(g, contentX + statW + statGap, statsY, statW, statsHeight, "Dimensions explored", String.valueOf(countDimensions()), dimensionsText(), "");
        Waypoint last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        statCard(g, contentX + (statW + statGap) * 2, statsY, statW, statsHeight, "Last added",
                last == null ? "-" : fit(last.name(), Math.max(40, statW - 18)), last == null ? "No waypoints yet" : "Saved in this world", "");

        WaypointUiText.draw(g, font, "Your waypoints", contentX, sectionY, TEXT, false);
        rounded(g, contentX + WaypointUiText.width(font, "Your waypoints") + 9, sectionY - 1,
                contentX + WaypointUiText.width(font, "Your waypoints") + 34, sectionY + 15, PANEL_ALT, 5);
        WaypointUiText.draw(g, font, String.valueOf(filtered.size()), contentX + WaypointUiText.width(font, "Your waypoints") + 16, sectionY + 3, MUTED, false);
        actionButton(g, mx, my, contentRight - 190, transferY, 86, 27, "Import", false);
        actionButton(g, mx, my, contentRight - 98, transferY, 86, 27, "Export", false);
        drawSearch(g, mx, my);
        drawFilters(g, mx, my);
        drawCards(g, mx, my);
        drawFooter(g, mx, my);
        // Native EditBox handles input; the shared UI font draws its visible text.
        super.extractRenderState(g, mx, my, delta);
        WaypointUiText.drawInput(g, font, searchBox, contentX + 31, toolbarY + 11, Math.max(36, searchBox.getWidth() - 8));
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
            navItem(g, mx, my, sideInset, 153, navWidth, "", "Waypoints", true);
            navItem(g, mx, my, sideInset, 194, navWidth, "", "Map", false);
            navItem(g, mx, my, sideInset, 235, navWidth, "", "Settings", false);
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
            navItem(g, mx, my, 8, 104, 38, "", "", true);
            navItem(g, mx, my, 8, 150, 38, "", "", false);
            navItem(g, mx, my, 8, 196, 38, "", "", false);
        }
    }

    private void drawBrandMark(GuiGraphicsExtractor g, int x, int y) {
        int c = 0xFF101410;
        drawPixelLine(g, x + 6, y + 14, x + 20, y + 6, c);
        drawPixelLine(g, x + 20, y + 6, x + 15, y + 21, c);
        drawPixelLine(g, x + 15, y + 21, x + 12, y + 14, c);
        drawPixelLine(g, x + 12, y + 14, x + 6, y + 14, c);
        drawPixelLine(g, x + 12, y + 14, x + 20, y + 6, c);
    }

    private void drawPixelLine(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1), sy = y1 < y2 ? 1 : -1;
        int err = dx + dy, x = x1, y = y1;
        while (true) {
            g.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x += sx; }
            if (e2 <= dx) { err += dx; y += sy; }
        }
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

    private void navItem(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String icon, String label, boolean active) {
        boolean hover = inside(mx, my, x, y, w, 32);
        if (active || hover) rounded(g, x, y, x + w, y + 32, active ? 0xFF202629 : PANEL, 7);
        int tint = active ? ACCENT : MUTED;
        if ("Waypoints".equals(label) || label.isEmpty()) drawPinGlyph(g, x + 11, y + 9, tint);
        else drawSlidersGlyph(g, x + 10, y + 10, tint);
        if (!label.isEmpty()) WaypointUiText.draw(g, font, label, x + 30, y + 10, active ? TEXT : MUTED, false);
    }

    private void statCard(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String value, String note, String icon) {
        rounded(g, x, y, x + w, y + h, PANEL, 9);
        border(g, x, y, x + w, y + h, EDGE);
        WaypointUiText.draw(g, font, label, x + 12, y + 9, MUTED, false);
        WaypointUiText.draw(g, font, fit(value, w - 24), x + 12, y + 25, TEXT, false);
        WaypointUiText.draw(g, font, fit(note, w - 24), x + 12, y + h - 14, QUIET, false);
        WaypointUiText.draw(g, font, icon, x + w - 24, y + 10, MUTED, false);
    }

    private void drawSearch(GuiGraphicsExtractor g, int mx, int my) {
        int y = toolbarY;
        int w = Math.max(60, contentWidth - 220);
        rounded(g, contentX, y, contentX + w, y + 35, PANEL_ALT, 7);
        border(g, contentX, y, contentX + w, y + 35, EDGE);
        WaypointUiText.draw(g, font, "", contentX + 11, y + 13, MUTED, false);
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            WaypointUiText.draw(g, font, "Search by name or coordinates...", contentX + 31, y + 11, QUIET, false);
        }
        if (sortByName) actionButton(g, mx, my, contentRight - 190, y, 120, 35, "A-Z", false);
        else actionButton(g, mx, my, contentRight - 190, y, 120, 35, "Recently added", false);
        actionButton(g, mx, my, contentRight - 62, y, 62, 35, listLayout ? "Grid" : "List", false);
    }

    private void drawFilters(GuiGraphicsExtractor g, int mx, int my) {
        int x = contentX;
        int y = filtersY;
        int[] modes = {4, 1, 2, 3};
        int[] widths = {106, 86, 86, 72};
        String[] labels = {"All dimensions", "Overworld", "The Nether", "The End"};
        for (int i = 0; i < modes.length; i++) {
            boolean selected = WaypointsMod.menuDimensionMode == modes[i];
            pill(g, x, y, widths[i], 27, labels[i], selected, mx, my);
            x += widths[i] + 7;
        }
        pill(g, contentRight - 78, y, 78, 24, "Current", WaypointsMod.menuDimensionMode == 0, mx, my);
    }

    private void drawCards(GuiGraphicsExtractor g, int mx, int my) {
        int start = currentPage() * pageSize;
        int end = Math.min(filtered.size(), start + pageSize);
        if (filtered.isEmpty()) {
            rounded(g, contentX, gridY, contentRight, gridY + 88, PANEL, 10);
            WaypointUiText.draw(g, font, searchQuery.isBlank() ? "No waypoints yet" : "No matching waypoints", contentX + 18, gridY + 23, TEXT, false);
            WaypointUiText.draw(g, font, "Create a waypoint to save a place in this world.", contentX + 18, gridY + 43, MUTED, false);
            return;
        }
        for (int i = start; i < end; i++) {
            Waypoint w = filtered.get(i);
            int slot = i - start;
            int col = slot % columns;
            int row = slot / columns;
            int x = contentX + col * (cardWidth + 12);
            int y = gridY + row * (cardHeight + 12);
            boolean hover = inside(mx, my, x, y, cardWidth, cardHeight);
            rounded(g, x, y, x + cardWidth, y + cardHeight, hover ? 0xFF1A2023 : PANEL, 9);
            border(g, x, y, x + cardWidth, y + cardHeight, EDGE);
            int color = 0xFF000000 | WaypointsMod.resolveColor(w);
            // The waypoint's own color is the avatar fill; its first letter is the mark.
            rounded(g, x + 12, y + 13, x + 36, y + 37, color, 7);
            String initial = firstInitial(w.name());
            int initialColor = contrastText(color);
            WaypointUiText.draw(g, font, initial, x + 24 - WaypointUiText.width(font, initial) / 2, y + 20, initialColor, false);
            WaypointUiText.draw(g, font, fit(w.name(), Math.max(45, cardWidth - 100)), x + 44, y + 14, TEXT, false);
            String sub = dimension(w.dimension()) + "  /  " + (w.expiresAt() > 0 ? "Temporary" : "Permanent");
            WaypointUiText.draw(g, font, fit(sub, cardWidth - 60), x + 44, y + 29, MUTED, false);
            // A compact topographic thumbnail gives each waypoint the visual card treatment
            // from the new reference UI while remaining lightweight and resolution-independent.
            boolean showMiniMap = cardHeight >= 145;
            if (showMiniMap) drawMiniMap(g, x + 12, y + 44, cardWidth - 24, 42, color, w);
            int coordsY = y + (showMiniMap ? 92 : 58);
            int coordW = (cardWidth - 42) / 3;
            drawCoordinate(g, x + 12, coordsY, coordW, "X", Integer.toString(w.x()));
            drawCoordinate(g, x + 18 + coordW, coordsY, coordW, "Y", Integer.toString(w.y()));
            drawCoordinate(g, x + 24 + coordW * 2, coordsY, coordW, "Z", Integer.toString(w.z()));
            int metaY = y + cardHeight - 15;
            g.fill(x + 12, metaY + 4, x + 16, metaY + 8, color);
            WaypointUiText.draw(g, font, dimension(w.dimension()), x + 22, metaY + 1, MUTED, false);
            rounded(g, x + cardWidth - 78, y + cardHeight - 29, x + cardWidth - 46, y + cardHeight - 7, 0xFF25351B, 5);
            WaypointUiText.draw(g, font, "GO", x + cardWidth - 70, y + cardHeight - 23, ACCENT, true);
            WaypointUiText.draw(g, font, "E", x + cardWidth - 44, metaY, MUTED, false);
            WaypointUiText.draw(g, font, "x", x + cardWidth - 21, metaY, MUTED, false);
        }
    }


    private void drawMiniMap(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, Waypoint waypoint) {
        rounded(g, x, y, x + w, y + h, 0xFF101A17, 6);
        // Soft contour strokes create a quiet map-like texture without loading external assets.
        for (int layer = -3; layer <= 3; layer++) {
            for (int px = x + 3; px < x + w - 3; px++) {
                double t = (px - x) / (double)Math.max(1, w);
                double wave = Math.sin(t * Math.PI * 2.0 + layer * 0.48) * h * 0.13
                        + Math.sin(t * Math.PI * 4.0 - layer * 0.2) * h * 0.05;
                int py = (int)Math.round(y + h * 0.5 + layer * h * 0.13 + wave);
                if (py > y + 2 && py < y + h - 2) g.fill(px, py, px + 1, py + 1, 0x554E755D);
            }
        }
        int cx = x + w / 2;
        int cy = y + h / 2;
        int marker = color | 0xFF000000;
        g.fill(cx - 1, y + 3, cx + 2, cy - 5, marker);
        rounded(g, cx - 7, cy - 7, cx + 8, cy + 8, 0xFF101410, 5);
        border(g, cx - 7, cy - 7, cx + 8, cy + 8, marker);
        g.fill(cx - 1, cy - 3, cx + 2, cy + 1, marker);
        g.fill(cx - 4, cy - 1, cx + 5, cy + 3, marker);
        g.fill(cx - 2, cy + 3, cx + 3, cy + 5, marker);
        g.fill(cx, cy + 5, cx + 1, cy + 7, marker);
    }

    private String firstInitial(String name) {
        if (name == null || name.isBlank()) return "?";
        return name.strip().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private int contrastText(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return luminance > 145 ? 0xFF101410 : 0xFFFFFFFF;
    }

    private void drawCoordinate(GuiGraphicsExtractor g, int x, int y, int w, String label, String value) {
        rounded(g, x, y, x + w, y + 28, PANEL_ALT, 5);
        WaypointUiText.draw(g, font, label, x + 7, y + 2, QUIET, false);
        WaypointUiText.draw(g, font, fit(value, w - 14), x + 7, y + 15, TEXT, false);
    }

    private void drawFooter(GuiGraphicsExtractor g, int mx, int my) {
        int start = currentPage() * pageSize;
        int shown = filtered.isEmpty() ? 0 : Math.min(pageSize, filtered.size() - start);
        WaypointUiText.draw(g, font, "Showing " + (filtered.isEmpty() ? 0 : start + 1) + "-" + (start + shown) + " of " + filtered.size() + " waypoints",
                contentX, footerY + 7, MUTED, false);
        if (pageCount() > 1) {
            int visiblePages = Math.min(width >= 800 ? 5 : 3, pageCount());
            int startPage = Math.max(0, Math.min(currentPage() - visiblePages / 2, pageCount() - visiblePages));
            int pageButton = 24;
            int gap = 4;
            int totalWidth = pageButton + 8 + visiblePages * pageButton + Math.max(0, visiblePages - 1) * gap + 8 + pageButton;
            int pagerX = contentRight - totalWidth;
            actionButton(g, mx, my, pagerX, footerY, pageButton, 24, "<", false);
            int pagesX = pagerX + pageButton + 8;
            for (int i = 0; i < visiblePages; i++) {
                int pageIndex = startPage + i;
                int px = pagesX + i * (pageButton + gap);
                boolean active = pageIndex == currentPage();
                rounded(g, px, footerY, px + pageButton, footerY + 24,
                        active ? 0xFF202629 : inside(mx, my, px, footerY, pageButton, 24) ? PANEL : PANEL_ALT, 5);
                if (active) border(g, px, footerY, px + pageButton, footerY + 24, EDGE);
                String pageText = Integer.toString(pageIndex + 1);
                WaypointUiText.draw(g, font, pageText, px + (pageButton - WaypointUiText.width(font, pageText)) / 2, footerY + 8,
                        active ? TEXT : MUTED, active);
            }
            int nextX = pagesX + visiblePages * (pageButton + gap) - gap + 8;
            actionButton(g, mx, my, nextX, footerY, pageButton, 24, ">", false);
        }
        WaypointUiText.draw(g, font, "A little less wandering. A lot more exploring.", contentX, height - 25, QUIET, false);
    }

    private int countDimensions() {
        boolean over = false, nether = false, end = false;
        for (Waypoint w : entries) {
            String d = WaypointsMod.normalizeDimension(w.dimension());
            over |= d.equals("minecraft:overworld");
            nether |= d.equals("minecraft:the_nether");
            end |= d.equals("minecraft:the_end");
        }
        return (over ? 1 : 0) + (nether ? 1 : 0) + (end ? 1 : 0);
    }
    private String dimensionsText() {
        List<String> names = new ArrayList<>();
        for (Waypoint w : entries) {
            String d = dimension(w.dimension());
            if (!names.contains(d)) names.add(d);
        }
        return names.isEmpty() ? "No dimensions yet" : String.join(", ", names);
    }
    private String worldTitle() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.getCurrentServer() != null) return mc.getCurrentServer().name;
            if (mc.getSingleplayerServer() != null) return mc.getSingleplayerServer().getWorldData().getLevelName();
        } catch (Throwable ignored) { }
        return "Current World";
    }
    private String dimension(String raw) {
        String d = WaypointsMod.normalizeDimension(raw);
        if (d.endsWith(":the_nether")) return "The Nether";
        if (d.endsWith(":the_end")) return "The End";
        if (d.endsWith(":overworld")) return "Overworld";
        return "Custom dimension";
    }
    private String fit(String value, int maxWidth) {
        if (value == null) return "";
        String s = value;
        while (!s.isEmpty() && WaypointUiText.width(font, s) > maxWidth) s = s.substring(0, s.length() - 1);
        return s.equals(value) ? s : s + "...";
    }
    private void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean selected, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, h);
        rounded(g, x, y, x + w, y + h, selected ? 0xFF26321F : hover ? PANEL : PANEL_ALT, 6);
        if (selected) border(g, x, y, x + w, y + h, 0xFF789D4C);
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + (h - WaypointUiText.LINE_HEIGHT) / 2, selected ? ACCENT : MUTED, selected);
    }
    private void actionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = inside(mx, my, x, y, w, h);
        rounded(g, x, y, x + w, y + h, primary ? (hover ? 0xFFBDE39A : ACCENT) : (hover ? 0xFF242B2F : PANEL), 6);
        if (!primary) border(g, x, y, x + w, y + h, EDGE);
        int color = primary ? 0xFF12170C : TEXT;
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + (h - WaypointUiText.LINE_HEIGHT) / 2, color, false);
    }
    private void primaryButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label) {
        actionButton(g, mx, my, x, y, w, h, label, true);
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
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - Math.pow(r - row - .5, 2))));
            g.fill(x + inset, y + row, x2 - inset, y + row + 1, color);
            g.fill(x + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
    }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
