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
        railWidth = width >= 800 ? 176 : 54;
        int leftInset = width >= 800 ? 24 : 12;
        int rightInset = width >= 800 ? 24 : 12;
        contentX = railWidth + leftInset;
        contentRight = width - rightInset;
        contentWidth = Math.max(220, contentRight - contentX);
        headingY = 28;
        statsY = 0;
        statsHeight = 0;
        sectionY = 0;
        transferY = height - 43;
        toolbarY = 76;
        filtersY = toolbarY;
        gridY = 124;
        footerY = height - 84;
        columns = listLayout ? 1 : contentWidth >= 900 ? 3 : contentWidth >= 520 ? 2 : 1;
        rows = Math.max(1, Math.min(2, (footerY - gridY - 12) / 150));
        cardWidth = (contentWidth - (columns - 1) * 12) / columns;
        cardHeight = Math.max(176, (footerY - gridY - 12 * (rows - 1) - 12) / rows);
        pageSize = columns * rows;

        entries = new ArrayList<>(WaypointsMod.menuWaypoints());
        if (sortByName) entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        refreshFiltered();
        // Avoid leaving most of the dashboard empty when only one or two
        // waypoints exist: let the visible cards use the available width.
        int preferredColumns = listLayout ? 1 : contentWidth >= 900 ? 3 : contentWidth >= 520 ? 2 : 1;
        columns = Math.max(1, Math.min(preferredColumns, Math.max(1, filtered.size())));
        rows = Math.max(1, Math.min(2, (footerY - gridY - 12) / 150));
        if (filtered.size() <= columns) rows = 1;
        cardWidth = (contentWidth - (columns - 1) * 12) / columns;
        cardHeight = Math.max(176, (footerY - gridY - 12 * (rows - 1) - 12) / rows);
        pageSize = columns * rows;
        page = Math.max(0, Math.min(requestedPage, Math.max(0, (filtered.size() - 1) / pageSize)));

        // Sidebar navigation mirrors the four sections in the neon reference design.
        int navY = 112;
        if (railWidth > 70) {
            int sideInset = 10;
            int navWidth = railWidth - 20;
            button(sideInset, navY, navWidth, 36, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            button(sideInset, navY + 45, navWidth, 36, () -> Minecraft.getInstance().gui.setScreen(new WaypointMapScreen()));
            button(sideInset, navY + 90, navWidth, 36, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
            button(sideInset, navY + 135, navWidth, 36, () -> Minecraft.getInstance().gui.setScreen(new WaypointAboutScreen()));
        } else {
            button(8, navY, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            button(8, navY + 44, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointMapScreen()));
            button(8, navY + 88, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
            button(8, navY + 132, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointAboutScreen()));
        }
        // Header and toolbar actions.
        button(contentRight - 138, headingY, 138, 36,
                () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, currentPage())));
        if (WaypointsMod.navigationTarget() != null) {
            button(contentRight - 250, headingY, 102, 34, WaypointsMod::stopNavigation);
        }
        button(contentX, transferY, 92, 27, () -> showTransferMessage(WaypointsMod.importWaypointsFromFile(), true));
        button(contentX + 100, transferY, 92, 27, () -> showTransferMessage(WaypointsMod.exportWaypointsToFile(), false));
        int searchWidth = Math.max(160, Math.min(330, contentWidth / 3));
        searchBox = new EditBox(font, contentX + 32, toolbarY + 7,
                Math.max(60, searchWidth - 42), 21, Component.literal("Search waypoints"));
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
        int filterX = contentX + searchWidth + 12;
        int[] modes = {4, 1, 2, 3};
        int[] widths = {56, 104, 86, 66};
        for (int i = 0; i < modes.length; i++) {
            final int mode = modes[i];
            int x = filterX;
            button(x, filtersY, widths[i], 35, () -> {
                WaypointsMod.menuDimensionMode = mode;
                page = 0;
                rebuildDashboard();
            });
            filterX += widths[i] + 7;
        }
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
            cardHitTargets.add(new HitTarget(x + cardWidth - 27, y + 8, 20, 20, () -> editSlot(visibleSlot)));
            cardHitTargets.add(new HitTarget(x + cardWidth - 76, y + cardHeight - 30, 64, 26, () -> navigateSlot(visibleSlot)));
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
        WaypointUiText.drawTitle(g, font, "Waypoints", contentX, headingY, TEXT);
        WaypointUiText.draw(g, font, "Your saved places across every dimension", contentX, headingY + 25, MUTED, false);
        primaryButton(g, mx, my, contentRight - 138, headingY, 138, 36, "+  Create Waypoint");
        if (WaypointsMod.navigationTarget() != null) {
            actionButton(g, mx, my, contentRight - 250, headingY, 102, 34, "Stop guide", false);
        }
        drawSearch(g, mx, my);
        drawFilters(g, mx, my);
        drawCards(g, mx, my);
        drawFooter(g, mx, my);
        actionButton(g, mx, my, contentX, transferY, 92, 27, "Import", false);
        actionButton(g, mx, my, contentX + 100, transferY, 92, 27, "Export", false);
        super.extractRenderState(g, mx, my, delta);
        WaypointUiText.drawInput(g, font, searchBox, contentX + 33, toolbarY + 11,
                Math.max(54, searchBox.getWidth() - 8));
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
            int navX = sideInset;
            int navW = railWidth - 20;
            navItem(g, mx, my, navX, 112, navW, "", "Waypoints", true);
            navItem(g, mx, my, navX, 157, navW, "", "Map", false);
            navItem(g, mx, my, navX, 202, navW, "", "Settings", false);
            navItem(g, mx, my, navX, 247, navW, "", "About", false);
            int base = height - 72;
            drawGrassBlock(g, 14, base - 27);
            WaypointUiText.draw(g, font, "v1.4.4", 54, base - 3, ACCENT, true);
        } else {
            navItem(g, mx, my, 8, 112, 38, "", "", true);
            navItem(g, mx, my, 8, 156, 38, "", "", false);
            navItem(g, mx, my, 8, 200, 38, "", "", false);
            navItem(g, mx, my, 8, 244, 38, "", "", false);
        }
    }

    private void drawGrassBlock(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x + 2, y + 3, x + 27, y + 19, 0xFF79502E);
        g.fill(x + 2, y, x + 27, y + 5, 0xFF4F9C35);
        g.fill(x, y + 4, x + 3, y + 18, 0xFF4D8C2E);
        g.fill(x + 27, y + 4, x + 30, y + 18, 0xFF6D4528);
        g.fill(x + 4, y + 2, x + 8, y + 4, 0xFF82C94B);
        g.fill(x + 14, y + 1, x + 18, y + 3, 0xFF82C94B);
        g.fill(x + 8, y + 8, x + 11, y + 11, 0xFF5B3823);
        g.fill(x + 18, y + 13, x + 21, y + 16, 0xFF5B3823);
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
        if ("Waypoints".equals(label) || label.isEmpty()) drawPinGlyph(g, x + 11, y + 11, tint);
        else if ("Map".equals(label)) drawCubeGlyph(g, x + 10, y + 10, tint);
        else if ("Settings".equals(label)) drawSlidersGlyph(g, x + 10, y + 10, tint);
        else {
            rounded(g, x + 10, y + 10, x + 24, y + 24, tint, 7);
            WaypointUiText.draw(g, font, "i", x + 15, y + 12, RAIL, true);
        }
        if (!label.isEmpty()) WaypointUiText.draw(g, font, label, x + 34, y + 12, active ? TEXT : MUTED, false);
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
        int w = Math.max(160, Math.min(330, contentWidth / 3));
        rounded(g, contentX, toolbarY, contentX + w, toolbarY + 35, PANEL_ALT, 8);
        border(g, contentX, toolbarY, contentX + w, toolbarY + 35, EDGE);
        g.fill(contentX + 12, toolbarY + 10, contentX + 22, toolbarY + 20, MUTED);
        g.fill(contentX + 14, toolbarY + 12, contentX + 20, toolbarY + 18, PANEL_ALT);
        g.fill(contentX + 21, toolbarY + 19, contentX + 26, toolbarY + 24, MUTED);
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            WaypointUiText.draw(g, font, "Search waypoints...", contentX + 33, toolbarY + 11, QUIET, false);
        }
    }

    private void drawFilters(GuiGraphicsExtractor g, int mx, int my) {
        int x = contentX + Math.max(160, Math.min(330, contentWidth / 3)) + 12;
        int[] modes = {4, 1, 2, 3};
        int[] widths = {56, 104, 86, 66};
        String[] labels = {"All", "Overworld", "Nether", "End"};
        for (int i = 0; i < modes.length; i++) {
            boolean selected = WaypointsMod.menuDimensionMode == modes[i];
            pill(g, x, filtersY, widths[i], 35, labels[i], selected, mx, my);
            x += widths[i] + 7;
        }
    }

    private void drawCards(GuiGraphicsExtractor g, int mx, int my) {
        int start = currentPage() * pageSize;
        int end = Math.min(filtered.size(), start + pageSize);
        if (filtered.isEmpty()) {
            rounded(g, contentX, gridY, contentRight, gridY + 100, PANEL, 10);
            WaypointUiText.draw(g, font, searchQuery.isBlank() ? "No waypoints yet" : "No matching waypoints", contentX + 18, gridY + 25, TEXT, true);
            WaypointUiText.draw(g, font, "Create your first waypoint to save a place in this world.", contentX + 18, gridY + 48, MUTED, false);
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
            rounded(g, x, y, x + cardWidth, y + cardHeight, hover ? 0xFF1B211E : PANEL, 10);
            border(g, x, y, x + cardWidth, y + cardHeight, EDGE);
            int color = 0xFF000000 | WaypointsMod.resolveColor(w);
            int thumbH = Math.max(55, Math.min(142, Math.min(cardHeight * 43 / 100, cardHeight - 108)));
            drawSceneThumbnail(g, x + 1, y + 1, cardWidth - 2, thumbH, w, color);
            // Small color marker and title beneath the landscape preview.
            rounded(g, x + 13, y + thumbH + 13, x + 37, y + thumbH + 37, color, 7);
            WaypointUiText.draw(g, font, firstInitial(w.name()), x + 21, y + thumbH + 20, contrastText(color), true);
            WaypointUiText.draw(g, font, fit(w.name(), Math.max(50, cardWidth - 130)), x + 48, y + thumbH + 15, TEXT, true);
            WaypointUiText.draw(g, font, dimension(w.dimension()), x + 48, y + thumbH + 31, MUTED, false);
            int coordsY = y + thumbH + 51;
            int coordW = (cardWidth - 42) / 3;
            drawCoordinate(g, x + 12, coordsY, coordW, "X", Integer.toString(w.x()));
            drawCoordinate(g, x + 18 + coordW, coordsY, coordW, "Y", Integer.toString(w.y()));
            drawCoordinate(g, x + 24 + coordW * 2, coordsY, coordW, "Z", Integer.toString(w.z()));
            int metaY = y + cardHeight - 27;
            rounded(g, x + 12, metaY, x + 98, metaY + 20, 0xFF20351A, 7);
            g.fill(x + 19, metaY + 8, x + 25, metaY + 14, color);
            WaypointUiText.draw(g, font, dimension(w.dimension()), x + 31, metaY + 6, ACCENT, false);
            WaypointUiText.draw(g, font, "★", x + cardWidth - 53, y + 8, ACCENT, true);
            WaypointUiText.draw(g, font, "⋮", x + cardWidth - 26, y + 8, MUTED, true);
            rounded(g, x + cardWidth - 72, metaY - 2, x + cardWidth - 16, metaY + 22, 0xFF25391A, 7);
            WaypointUiText.draw(g, font, "GO  →", x + cardWidth - 64, metaY + 5, ACCENT, true);
        }
    }

    private void drawSceneThumbnail(GuiGraphicsExtractor g, int x, int y, int w, int h, Waypoint waypoint, int marker) {
        String dim = WaypointsMod.normalizeDimension(waypoint.dimension());
        int sky = dim.endsWith(":the_nether") ? 0xFF342044 : dim.endsWith(":the_end") ? 0xFF25213D : 0xFF527A9B;
        int hill = dim.endsWith(":the_nether") ? 0xFF512D5E : dim.endsWith(":the_end") ? 0xFF57516B : 0xFF31533F;
        int ground = dim.endsWith(":the_nether") ? 0xFF42233E : dim.endsWith(":the_end") ? 0xFF50475F : 0xFF24462F;
        g.fill(x, y, x + w, y + h, sky);
        g.fill(x, y + h / 3, x + w, y + h / 3 + 1, 0x33FFFFFF);
        int seed = Math.abs(waypoint.x() * 31 + waypoint.z() * 17 + waypoint.name().hashCode());
        for (int i = 0; i < 7; i++) {
            int hillX = x + (w * i / 7);
            int hillY = y + h / 2 + Math.abs((seed >> (i % 16)) % Math.max(6, h / 5));
            g.fill(hillX, hillY, Math.min(x + w, hillX + w / 5 + 4), y + h, hill);
        }
        g.fill(x, y + h * 3 / 4, x + w, y + h, ground);
        // Blocky trees/buildings and a winding path make each card read as a Minecraft scene.
        for (int i = 0; i < 9; i++) {
            int tx = x + 12 + Math.abs((seed + i * 37) % Math.max(1, w - 24));
            int th = 8 + Math.abs((seed / (i + 1) + i * 13) % Math.max(8, h / 3));
            int ty = y + h - th - 4;
            g.fill(tx + 3, ty + th / 2, tx + 6, y + h - 2, 0xFF765239);
            g.fill(tx, ty, tx + 12, ty + th / 2 + 4, dim.endsWith(":the_nether") ? 0xFF8B3D66 : dim.endsWith(":the_end") ? 0xFF9483B4 : 0xFF34783D);
            g.fill(tx + 2, ty + 2, tx + 10, ty + 6, dim.endsWith(":the_nether") ? 0xFFB24C80 : 0xFF579B47);
        }
        // A bright waypoint beam is the visual focal point of each landscape card.
        int cx = x + w / 2;
        g.fill(cx - 1, y + 7, cx + 2, y + h - 16, 0x77FFFFFF);
        g.fill(cx - 2, y + 7, cx + 3, y + h - 16, marker & 0x66FFFFFF);
        rounded(g, cx - 8, y + h - 22, cx + 9, y + h - 5, 0xFF101410, 7);
        border(g, cx - 8, y + h - 22, cx + 9, y + h - 5, marker);
        g.fill(cx - 1, y + h - 17, cx + 2, y + h - 9, marker);
        // dark glass gradient strip at the lower edge
        g.fill(x, y + h - 2, x + w, y + h, 0xAA080B0A);
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
        WaypointUiText.draw(g, font, "", contentX, height - 25, QUIET, false);
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
        rounded(g, x, y, x + w, y + h, selected ? 0xFF26321F : hover ? PANEL : PANEL_ALT, 8);
        if (selected) border(g, x, y, x + w, y + h, 0xFF789D4C);
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + (h - WaypointUiText.LINE_HEIGHT) / 2, selected ? ACCENT : MUTED, selected);
    }
    private void actionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = inside(mx, my, x, y, w, h);
        rounded(g, x, y, x + w, y + h, primary ? (hover ? 0xFFBDE39A : ACCENT) : (hover ? 0xFF242B2F : PANEL), 8);
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
        int r = Math.min(WaypointsMod.smoothUiEdges ? Math.max(radius, 8) : Math.min(radius, 3), Math.min(w / 2, h / 2));
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
