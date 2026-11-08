package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full workspace dashboard replacing the former compact modal waypoint menu. */
public final class WaypointScreen extends Screen {
    private static final int BG = 0xFF0B0C0E;
    private static final int RAIL = 0xFF101113;
    private static final int PANEL = 0xFF131518;
    private static final int PANEL_ALT = 0xFF0F1113;
    private static final int EDGE = 0xFF292D31;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFF9BA4A8;
    private static final int QUIET = 0xFF687278;
    private static final Identifier THUMB_HOME = Identifier.fromNamespaceAndPath("simplewaypoints", "textures/gui/waypoint_home.png");
    private static final Identifier THUMB_NETHER = Identifier.fromNamespaceAndPath("simplewaypoints", "textures/gui/waypoint_nether.png");
    private static final Identifier THUMB_TOWER = Identifier.fromNamespaceAndPath("simplewaypoints", "textures/gui/waypoint_tower.png");
    private static final Identifier THUMB_DUNGEON = Identifier.fromNamespaceAndPath("simplewaypoints", "textures/gui/waypoint_dungeon.png");
    private static final Identifier THUMB_STRONGHOLD = Identifier.fromNamespaceAndPath("simplewaypoints", "textures/gui/waypoint_stronghold.png");
    private static final Identifier THUMB_END = Identifier.fromNamespaceAndPath("simplewaypoints", "textures/gui/waypoint_end.png");

    private final int requestedPage;
    private int page;
    private String searchQuery;
    private int railWidth, contentX, contentRight, contentWidth;
    private int headingY, transferY;
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
        transferY = height - 39;
        toolbarY = 76;
        filtersY = toolbarY;
        gridY = 124;
        footerY = height - 82;
        // The reference dashboard uses a compact 3-column / 2-row card grid.
        // The old 900px threshold forced most Minecraft GUI resolutions to 2 columns.
        columns = listLayout ? 1 : contentWidth >= 540 ? 3 : contentWidth >= 350 ? 2 : 1;
        rows = listLayout ? Math.max(1, Math.min(6, filtered.size())) : 2;
        cardWidth = (contentWidth - (columns - 1) * 12) / columns;
        cardHeight = Math.max(132, Math.min(180, (footerY - gridY - 12 * (rows - 1) - 2) / rows));
        pageSize = columns * rows;

        entries = new ArrayList<>(WaypointsMod.menuWaypoints());
        if (sortByName) entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        refreshFiltered();
        // Keep the reference's compact 3-column / 2-row grid even when there
        // is only one saved waypoint. A single waypoint must never become a
        // full-width legacy list card.
        int preferredColumns = listLayout ? 1 : contentWidth >= 540 ? 3 : contentWidth >= 350 ? 2 : 1;
        columns = preferredColumns;
        rows = listLayout ? Math.max(1, Math.min(6, filtered.size())) : 2;
        cardWidth = (contentWidth - (columns - 1) * 12) / columns;
        cardHeight = Math.max(132, Math.min(180, (footerY - gridY - 12 * (rows - 1) - 2) / rows));
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
        int searchWidth = Math.max(150, Math.min(240, contentWidth * 2 / 5));
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
        int[] widths = {56, 100, 84, 62};
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
            cardHitTargets.add(new HitTarget(x, y, cardWidth, cardHeight, () -> editSlot(visibleSlot)));
            cardHitTargets.add(new HitTarget(x + cardWidth - 48, y + 7, 20, 20, () -> editSlot(visibleSlot)));
            cardHitTargets.add(new HitTarget(x + cardWidth - 26, y + 7, 18, 20, () -> editSlot(visibleSlot)));
            cardHitTargets.add(new HitTarget(x + 10, y + cardHeight - 27, 92, 22, () -> navigateSlot(visibleSlot)));
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
            WaypointUiText.draw(g, font, "v1.4.6", 54, base - 3, WaypointsMod.uiAccentColor, true);
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
        // Smooth, compact lime location pin with a dark circular center.
        rounded(g, x + 7, y, x + 28, y + 27, WaypointsMod.uiAccentColor, 11);
        g.fill(x + 9, y + 20, x + 26, y + 30, WaypointsMod.uiAccentColor);
        g.fill(x + 12, y + 28, x + 23, y + 36, WaypointsMod.uiAccentColor);
        g.fill(x + 16, y + 34, x + 20, y + 42, WaypointsMod.uiAccentColor);
        rounded(g, x + 13, y + 6, x + 23, y + 17, 0xFF101113, 6);
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
        if (active || hover) rounded(g, x, y, x + w, y + 32, active ? WaypointsMod.uiAccentColor : PANEL, 7);
        int tint = active ? 0xFF101410 : MUTED;
        if ("Waypoints".equals(label) || label.isEmpty()) drawPinGlyph(g, x + 11, y + 11, tint);
        else if ("Map".equals(label)) drawCubeGlyph(g, x + 10, y + 10, tint);
        else if ("Settings".equals(label)) drawSlidersGlyph(g, x + 10, y + 10, tint);
        else {
            rounded(g, x + 10, y + 10, x + 24, y + 24, tint, 7);
            WaypointUiText.draw(g, font, "i", x + 15, y + 12, RAIL, true);
        }
        if (!label.isEmpty()) WaypointUiText.draw(g, font, label, x + 34, y + 12, active ? 0xFF101410 : MUTED, active);
    }

    private void drawSearch(GuiGraphicsExtractor g, int mx, int my) {
        int w = Math.max(150, Math.min(240, contentWidth * 2 / 5));
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
        int x = contentX + Math.max(150, Math.min(240, contentWidth * 2 / 5)) + 12;
        int[] modes = {4, 1, 2, 3};
        int[] widths = {56, 100, 84, 62};
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
            rounded(g, x, y, x + cardWidth, y + cardHeight, hover ? 0xFF1B211E : PANEL, 9);
            border(g, x, y, x + cardWidth, y + cardHeight, EDGE);
            int color = 0xFF000000 | WaypointsMod.resolveColor(w);
            int thumbH = Math.max(60, Math.min(90, cardHeight * 53 / 100));
            drawSceneThumbnail(g, x + 1, y + 1, cardWidth - 44, thumbH, w);
            // Overlay star and overflow affordances, as in the supplied card design.
            WaypointUiText.draw(g, font, "★", x + cardWidth - 31, y + 7, WaypointsMod.uiAccentColor, true);
            WaypointUiText.draw(g, font, "...", x + cardWidth - 15, y + 7, 0xFFFFFFFF, true);
            // Marker icon and compact title row.
            drawWaypointGlyph(g, x + 12, y + thumbH + 10, color, w);
            WaypointUiText.draw(g, font, fit(w.name(), Math.max(45, cardWidth - 66)), x + 37, y + thumbH + 10, TEXT, true);
            String coords = "X: " + w.x() + "  Y: " + w.y() + "  Z: " + w.z();
            WaypointUiText.draw(g, font, fit(coords, cardWidth - 50), x + 37, y + thumbH + 25, MUTED, false);
            int metaY = y + cardHeight - 24;
            int badgeW = Math.min(cardWidth - 24, Math.max(58, WaypointUiText.width(font, dimension(w.dimension())) + 18));
            rounded(g, x + 10, metaY, x + 10 + badgeW, metaY + 18,
                    dimension(w.dimension()).equals("Nether") ? 0xFF3B244B : dimension(w.dimension()).equals("The End") ? 0xFF45405B : 0xFF203D1A, 7);
            WaypointUiText.draw(g, font, dimension(w.dimension()), x + 18, metaY + 5, WaypointsMod.uiAccentColor, false);
        }
    }

    private void drawWaypointGlyph(GuiGraphicsExtractor g, int x, int y, int color, Waypoint waypoint) {
        rounded(g, x, y, x + 20, y + 20, color, 6);
        WaypointUiGlyphs.draw(g, font, x + 2, y + 2, 16, waypoint.safeMarkerText(), contrastText(color));
    }

    private void drawSceneThumbnail(GuiGraphicsExtractor g, int x, int y, int w, int h, Waypoint waypoint) {
        String name = waypoint.name() == null ? "" : waypoint.name().toLowerCase(Locale.ROOT);
        String dimension = WaypointsMod.normalizeDimension(waypoint.dimension());
        Identifier texture;
        if (dimension.endsWith(":the_nether") || name.contains("nether")) texture = THUMB_NETHER;
        else if (dimension.endsWith(":the_end") || name.contains("end portal")) texture = THUMB_END;
        else if (name.contains("tower")) texture = THUMB_TOWER;
        else if (name.contains("dungeon")) texture = THUMB_DUNGEON;
        else if (name.contains("stronghold")) texture = THUMB_STRONGHOLD;
        else if (name.contains("portal")) texture = THUMB_NETHER;
        else texture = THUMB_HOME;
        // These are actual bundled image textures rather than the old flat
        // rectangle scene generator, which was the main reason cards looked
        // like the obsolete placeholder menu.
        g.blit(texture, x, y, x + w, y + h, 0.0f, 1.0f, 0.0f, 1.0f);
        g.fill(x, y + h - 3, x + w, y + h, 0x66080B0A);
    }

    private int contrastText(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return luminance > 145 ? 0xFF101410 : 0xFFFFFFFF;
    }

    private void drawFooter(GuiGraphicsExtractor g, int mx, int my) {
        int start = currentPage() * pageSize;
        int shown = filtered.isEmpty() ? 0 : Math.min(pageSize, filtered.size() - start);
        WaypointUiText.draw(g, font, "Showing " + (filtered.isEmpty() ? 0 : start + 1) + "-" + (start + shown),
                contentX, footerY + 7, MUTED, false);
        g.fill(contentRight - 102, transferY + 10, contentRight - 94, transferY + 18, WaypointsMod.uiAccentColor);
        WaypointUiText.draw(g, font, filtered.size() + " Waypoints", contentRight - 88, transferY + 9, TEXT, false);
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
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + (h - WaypointUiText.LINE_HEIGHT) / 2, selected ? WaypointsMod.uiAccentColor : MUTED, selected);
    }
    private void actionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = inside(mx, my, x, y, w, h);
        rounded(g, x, y, x + w, y + h, primary ? (hover ? 0xFFBDE39A : WaypointsMod.uiAccentColor) : (hover ? 0xFF242B2F : PANEL), 8);
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
