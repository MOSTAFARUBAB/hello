package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Create / edit one waypoint in the Field Atlas editor: a wide location form above
 * a color workstation, live preview, dimension scope, and expiry controls.
 *
 * Text inputs remain native Minecraft edit boxes; custom controls use separate
 * hit targets so vanilla button backgrounds never cover the designed interface.
 */
public class WaypointEditScreen extends Screen {
    // ---- theme ----
    private static final int PANEL = 0xFF131518;
    private static final int PANEL_ALT = 0xFF0F1113;
    private static final int CARD = 0xFF131518;
    private static final int CARD_HOVER = 0xFF1A1D21;
    private static final int CARD_BORDER = 0xFF292D31;
    private static final int TEXT = 0xFFF2F4F5;
    private static final int MUTED = 0xFFA3ABB0;
    private static final int QUIET = 0xFF727B82;
    private static final int CYAN = 0xFFE0E0E0;
    private static final int CYAN_HOVER = 0xFFFFFFFF;
    private static final int CYAN_DARK = 0xFF303030;

    // ---- color picker geometry ----
    // GUI-space dimensions stay compact; the backing texture is 2x resolution
    // so the gradient stays smooth on scaled/high-DPI displays.
    private static final int PICKER_MAX_HEIGHT = 126;
    private static final int PICKER_TEXTURE_WIDTH = 1024;
    private static final int PICKER_TEXTURE_HEIGHT = 512;
    private static final Identifier PICKER_TEXTURE_ID = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "gui/waypoint_color_picker");
    private static DynamicTexture pickerTexture;
    private static float pickerTextureHue = Float.NaN;
    private static long lastPickerUploadNanos = 0L;

    // The hue strip is static, so generate it once and draw it as one texture.
    private static final int HUE_DISPLAY_HEIGHT = 8;
    private static final int[] PRESET_COLORS = {0xFF70E000, 0xFF1877F2, 0xFF00C8D7, 0xFF8A2BE2,
            0xFFE72C91, 0xFFFF8A00, 0xFFFFD21C, 0xFFFFFFFF, 0xFFFF3030};
    private static final int HUE_TEXTURE_HEIGHT = 32;
    private static final int HUE_TEXTURE_WIDTH = 1024;
    private static final Identifier EDITOR_SCENE_TEXTURE = Identifier.fromNamespaceAndPath("simplewaypoints", "textures/gui/waypoint_preview.png");
    private static final Identifier HUE_TEXTURE_ID = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "gui/waypoint_hue_strip");
    private static DynamicTexture hueTexture;

    // ---- dimensions ----
    private static final String[] DIM_LABELS = {"Overworld", "Nether", "The End"};
    private static final String[] DIM_IDS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};

    /** Recently saved colors (kept until you quit the game). */
    private static final List<Integer> RECENT = new ArrayList<>(List.of(0xB7F36B, 0x59B7FF, 0xA98AFF, 0xFFB65C, 0x55D6B5, 0xF17A91, 0xE48BCB));

    private record HitTarget(int x, int y, int width, int height, Runnable action) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    private final List<HitTarget> hitTargets = new ArrayList<>();
    private final List<HitTarget> scrollHitTargets = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;
    private int scrollTop;
    private int scrollBottom;

    private final Waypoint original; // null = creating a new one
    private final int returnPage;

    // starting values (kept so the screen survives being re-initialised, e.g. on window resize)
    private final String initName;
    private final String initX;
    private final String initY;
    private final String initZ;
    private String keepDimension;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox hexBox;

    private float hue = 0f;
    private float sat = 1f;
    private float val = 1f;
    private boolean draggingSvPicker = false;
    private boolean draggingHuePicker = false;
    // Track the selection while the cursor moves; GUI-scaled absolute coordinates
    // are used so high-DPI window scaling cannot cause drag drift.
    private double pickerPointerX = 0.0;
    private double pickerPointerY = 0.0;
    private boolean chroma = false;
    private int selectedIconIndex = 0;
    private static final String[] ICON_GLYPHS = {"H", "★", "K", "W", "T", "D", "+"};
    private static final String[] ICON_MARKERS = {"H", "S", "K", "W", "T", "D", "M"};
    private boolean dimensionMenuOpen;
    private int dimMode;            // 0 Overworld, 1 Nether, 2 End, -1 = keep the original (modded) dimension
    private String lastHex = "";
    private String error = "";

    private int cx;
    private int oy;
    private int cardTop;
    private int locationCardHeight;
    private int visibilityY;
    private int visibilityHeight;
    private int colorCardHeight;
    private int actionY;
    private int breadcrumbY;
    private int railWidth;
    private int contentLeft;
    private int contentRight;
    private int formWidth;
    private int colorX;
    private int colorWidth;
    private int coordinateWidth;
    private boolean labelEnabled = true;
    private boolean beamEnabled = true;

    // Full-page editor geometry: location form on the left, color workstation on the right.
    private int formLeft() { return contentLeft + 18; }
    private int locationOffset(int base) { return Math.round(base * 0.60f); }
    private int nameFieldY() { return cardTop + 24; }
    private int coordinateFieldsY() { return cardTop + 77; }
    private int dimensionFieldY() { return cardTop + 129; }
    private int pickerX() { return contentLeft + 112; }
    private int pickerY() { return cardTop + 227; }
    private int pickerHeight() { return 64; }
    private int pickerWidth() { return Math.max(130, Math.min(210, formWidth - 130)); }
    private int hueX() { return pickerX(); }
    private int hueY() { return pickerY() + pickerHeight() + 2; }
    private int hexRowY() { return cardTop + 315; }
    private int chromaY() { return hexRowY(); }
    private int recentLabelY() { return chromaY() + 32; }
    private int recentColorsY() { return recentLabelY() + 15; }
    private int previewX() { return contentLeft + 18; }
    private int presetX(int index) { return contentLeft + 17 + (index % 3) * 24; }
    private int presetY(int index) { return cardTop + 227 + (index / 3) * 22; }
    private int iconRowY() { return cardTop + 164; }

    public WaypointEditScreen(Waypoint original, int returnPage) {
        super(Component.literal(original == null ? "New Waypoint" : "Edit Waypoint"));
        this.original = original;
        this.returnPage = returnPage;

        if (original != null) {
            labelEnabled = original.isLabelEnabled();
            beamEnabled = original.isBeamEnabled();
            boolean matchedIcon = false;
            for (int i = 0; i < ICON_MARKERS.length; i++) {
                if (ICON_MARKERS[i].equalsIgnoreCase(original.safeMarkerText())) {
                    selectedIconIndex = i;
                    matchedIcon = true;
                    break;
                }
            }
            if (!matchedIcon && "W".equalsIgnoreCase(original.safeMarkerText())) selectedIconIndex = 3;
            initName = original.name();
            initX = String.valueOf(original.x());
            initY = String.valueOf(original.y());
            initZ = String.valueOf(original.z());
            keepDimension = original.dimension();
            if (original.color() == WaypointsMod.CHROMA) {
                chroma = true;
            } else {
                setFromRgb(original.color() & 0xFFFFFF);
            }
        } else {
            Minecraft mc = Minecraft.getInstance();
            BlockPos p = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
            initName = WaypointsMod.nextWaypointName();
            initX = String.valueOf(p.getX());
            initY = String.valueOf(p.getY());
            initZ = String.valueOf(p.getZ());
            keepDimension = WaypointsMod.currentDimension();
            setFromRgb(0xB7F36B);
        }

        String normalized = WaypointsMod.normalizeDimension(keepDimension);
        dimMode = -1;
        for (int i = 0; i < DIM_IDS.length; i++) {
            if (DIM_IDS[i].equals(normalized)) dimMode = i;
        }
    }

    /** Opened by the B key: a blank "New Waypoint" menu, already filled with your position and dimension. */
    static void openNew() {
        Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, -1));
    }

    /** Plain dark overlay instead of Minecraft's blurred world, so the menu looks sharp and clear. */
    public void extractBlurredBackground(GuiGraphicsExtractor g) {
    }

    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0xFF0B0C0E);
    }

    // ------------------------------------------------------------------ layout / widgets
    @Override
    protected void init() {
        clearWidgets();
        hitTargets.clear();
        scrollHitTargets.clear();
        railWidth = width >= 760 ? 176 : 0;
        int leftInset = railWidth > 0 ? 24 : 18;
        int rightInset = width >= 1120 ? 28 : 18;
        contentLeft = railWidth > 0 ? railWidth + leftInset : leftInset;
        contentRight = width - rightInset;
        int available = Math.max(500, contentRight - contentLeft);
        colorWidth = Math.max(220, Math.min(270, Math.round(available * 0.41f)));
        formWidth = Math.max(300, available - colorWidth - 18);
        colorX = contentLeft + formWidth + 18;
        coordinateWidth = Math.max(72, (formWidth - 52) / 3);

        cardTop = Math.max(60, Math.min(78, Math.round(height * 0.14f)));
        oy = cardTop - 10;
        breadcrumbY = 22;
        locationCardHeight = Math.max(280, height - cardTop - 52);
        visibilityY = cardTop + 188;
        visibilityHeight = Math.max(130, height - visibilityY - 55);
        colorCardHeight = 178;
        actionY = height - 43;
        scrollTop = cardTop;
        scrollBottom = height - 49;
        maxScroll = 0;
        scrollOffset = 0;

        int left = formLeft();
        int nameY = nameFieldY();
        int coordsY = coordinateFieldsY();
        int dimY = dimensionFieldY();
        int nameWidth = formWidth - 36;
        int coordGap = 8;

        if (railWidth > 0) {
            int navY = 112;
            int sideInset = 10;
            int navWidth = railWidth - 20;
            click(sideInset, navY, navWidth, 36,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(returnPage < 0 ? 0 : returnPage)));
            click(sideInset, navY + 45, navWidth, 36,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointMapScreen()));
            click(sideInset, navY + 90, navWidth, 36,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
            click(sideInset, navY + 135, navWidth, 36,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointAboutScreen()));
        }
        click(contentLeft, breadcrumbY - 5, 38, 36, this::back);

        nameBox = makeBox(nameBox, left + 42, nameY + 7, nameWidth - 52, 20, initName, 32, "Waypoint name");
        xBox = makeBox(xBox, left + 10, coordsY + 7, coordinateWidth - 20, 20, initX, 9, "X");
        yBox = makeBox(yBox, left + coordinateWidth + coordGap + 10, coordsY + 7, coordinateWidth - 20, 20, initY, 9, "Y");
        zBox = makeBox(zBox, left + (coordinateWidth + coordGap) * 2 + 10, coordsY + 7, coordinateWidth - 20, 20, initZ, 9, "Z");
        clickContent(left, dimY, nameWidth, 34, () -> dimensionMenuOpen = !dimensionMenuOpen);

        // Compact 3x3 swatches sit beside the saturation/value picker, as in the reference.
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            final int preset = PRESET_COLORS[i];
            int px = presetX(i), py = presetY(i);
            clickContent(px - 2, py - 2, 21, 21, () -> {
                chroma = false;
                setFromRgb(preset);
                syncHex();
            });
        }
        clickContent(pickerX(), pickerY(), pickerWidth(), pickerHeight(), () -> { });
        clickContent(hueX(), hueY() - 2, pickerWidth(), HUE_DISPLAY_HEIGHT + 4, () -> { });
        clickContent(contentLeft + 98, hexRowY(), 27, 27, this::pickBlockColor);
        clickContent(contentLeft + 130, chromaY(), formWidth - 148, 28, () -> chroma = !chroma);
        hexBox = makeBox(hexBox, contentLeft + 22, hexRowY() + 4, 66, 20,
                String.format(Locale.ROOT, "#%06X", currentRgb()), 7, "HEX");
        lastHex = hexBox.getValue();

        // Seven selectable waypoint symbols.
        int iconStart = contentLeft + 16;
        for (int i = 0; i < ICON_GLYPHS.length; i++) {
            final int selected = i;
            clickContent(iconStart + i * 42, iconRowY() + 17, 34, 29, () -> selectedIconIndex = selected);
        }

        // The three right-side switches control real waypoint display settings.
        int toggleX = colorX + colorWidth - 48;
        clickContent(toggleX - 1, cardTop + 204, 36, 20, () -> beamEnabled = !beamEnabled);
        clickContent(toggleX - 1, cardTop + 248, 36, 20, () -> labelEnabled = !labelEnabled);
        clickContent(toggleX - 1, cardTop + 292, 36, 20, () -> {
            WaypointsMod.matchWaypointColor = !WaypointsMod.matchWaypointColor;
            WaypointsMod.savePreferences();
        });

        refreshPickerTexture();
        ensureHueTexture();

        int formRight = contentLeft + formWidth;
        if (original == null) {
            click(contentRight - 180, actionY, 62, 31, this::back);
            click(contentRight - 118, actionY, 118, 31, this::save);
        } else {
            click(contentRight - 236, actionY, 112, 31, this::back);
            click(contentRight - 116, actionY, 116, 31, this::save);
        }
    }

    private void click(int x, int y, int w, int h, Runnable action) {
        hitTargets.add(new HitTarget(x, y, w, h, action));
    }
    private void clickContent(int x, int y, int w, int h, Runnable action) {
        scrollHitTargets.add(new HitTarget(x, y, w, h, action));
    }

    private EditBox makeBox(EditBox old, int x, int y, int w, int h, String initial, int maxLength, String label) {
        EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(old != null ? old.getValue() : initial);
        box.setBordered(false);
        box.setTextColor(0x00000000);
        this.addRenderableWidget(box);
        return box;
    }

    private void useCurrentPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos pos = mc.player.blockPosition();
        xBox.setValue(Integer.toString(pos.getX()));
        yBox.setValue(Integer.toString(pos.getY()));
        zBox.setValue(Integer.toString(pos.getZ()));
        dimMode = -1;
        keepDimension = WaypointsMod.currentDimension();
        error = "Current position added.";
    }

    private void useLookedAtBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.hitResult == null || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            error = "Look at a block first, then choose this button.";
            return;
        }
        BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();
        xBox.setValue(Integer.toString(pos.getX()));
        yBox.setValue(Integer.toString(pos.getY()));
        zBox.setValue(Integer.toString(pos.getZ()));
        dimMode = -1;
        keepDimension = WaypointsMod.currentDimension();
        error = "Block position added.";
    }

    private void back() {
        if (returnPage < 0) {
            this.onClose(); // opened with B: go straight back to the game
        } else {
            WaypointScreen.open(returnPage);
        }
    }

    // SDL3 left mouse button is 1 in Minecraft 26.3.
    // Keep a drag capture so the picker follows the cursor continuously, even
    // after the pointer moves beyond the edge of the color square or hue strip.
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && event.y() >= scrollTop && event.y() <= scrollBottom
                && event.x() >= contentLeft && event.x() <= width - 12) {
            double mouseX = event.x();
            double mouseY = event.y();

            int gx = pickerX();
            int gy = pickerY();
            int pickerW = pickerWidth();
            int pickerH = pickerHeight();

            if (mouseX >= gx && mouseX < gx + pickerW
                    && mouseY >= gy - scrollOffset && mouseY < gy + pickerH - scrollOffset) {
                draggingSvPicker = true;
                draggingHuePicker = false;
                this.setDragging(true);
                pickerPointerX = mouseX;
                pickerPointerY = mouseY;
                updateSvPicker(pickerPointerX, pickerPointerY + scrollOffset);
                return true;
            }

            int hueX = hueX();
            int hueY = hueY();
            int hueW = pickerWidth();
            if (mouseX >= hueX && mouseX < hueX + hueW
                    && mouseY >= hueY - scrollOffset && mouseY < hueY + HUE_DISPLAY_HEIGHT + 4 - scrollOffset) {
                draggingHuePicker = true;
                draggingSvPicker = false;
                this.setDragging(true);
                pickerPointerX = mouseX;
                pickerPointerY = mouseY;
                updateHuePicker(pickerPointerX);
                return true;
            }
        }

        if (event.button() == 1) {
            if (event.y() >= scrollTop && event.y() <= scrollBottom
                    && event.x() >= contentLeft && event.x() <= width - 12) {
            if (dimensionMenuOpen) {
                int left = formLeft();
                int optionWidth = formWidth - 36;
                if (inside(event.x(), event.y() + scrollOffset, left, dimensionFieldY(), optionWidth, 34)) {
                    dimensionMenuOpen = false;
                    return true;
                }
                int top = dimensionFieldY() + 35;
                int optionHeight = 23;
                for (int i = 0; i < DIM_LABELS.length; i++) {
                    int optionY = top + i * optionHeight;
                    if (inside(event.x(), event.y() + scrollOffset, left, optionY, optionWidth, optionHeight)) {
                        dimMode = i;
                        dimensionMenuOpen = false;
                        return true;
                    }
                }
                dimensionMenuOpen = false;
            }
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
        if (event.button() == 1 && event.x() >= contentLeft && event.x() <= width - 12
                && (event.y() < scrollTop || event.y() > scrollBottom)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY < scrollTop || mouseY > scrollBottom || mouseX < contentLeft || mouseX > width - 12) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int next = Math.max(0, Math.min(maxScroll, scrollOffset - (int)Math.round(scrollY * 30.0)));
        if (next == scrollOffset) return false;
        scrollOffset = next;
        if (nameBox != null) nameBox.setY(nameFieldY() + 7 - scrollOffset);
        if (xBox != null) xBox.setY(coordinateFieldsY() + 7 - scrollOffset);
        if (yBox != null) yBox.setY(coordinateFieldsY() + 7 - scrollOffset);
        if (zBox != null) zBox.setY(coordinateFieldsY() + 7 - scrollOffset);
        if (hexBox != null) hexBox.setY(hexRowY() + 6 - scrollOffset);
        unfocusIfOutside(nameBox);
        unfocusIfOutside(xBox);
        unfocusIfOutside(yBox);
        unfocusIfOutside(zBox);
        unfocusIfOutside(hexBox);
        return true;
    }

    private void unfocusIfOutside(EditBox box) {
        if (box != null && (box.getY() + box.getHeight() <= scrollTop || box.getY() >= scrollBottom)) {
            box.setFocused(false);
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingSvPicker || draggingHuePicker) {
            // Prefer the live cursor position maintained by Minecraft's SDL-backed
            // MouseHandler. Its scaled coordinates are the same coordinate space
            // used by Screen widgets, including non-default GUI scales.
            updatePickerFromLiveMouse();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (draggingSvPicker || draggingHuePicker) {
            // Keep event-driven updates for immediate response, but use the exact
            // same coordinate conversion as vanilla GUI elements.
            updatePickerFromLiveMouse();
        }
        super.mouseMoved(mouseX, mouseY);
    }

    /** Read the current pointer directly from Minecraft instead of relying on a
     * possibly coalesced MouseButtonEvent. This keeps the picker marker attached
     * to the cursor while dragging quickly or when Windows sends sparse events.
     */
    private void updatePickerFromLiveMouse() {
        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double mouseY = mc.mouseHandler.getScaledYPos(mc.getWindow());
        pickerPointerX = mouseX;
        pickerPointerY = mouseY;
        if (draggingSvPicker) updateSvPicker(mouseX, mouseY + scrollOffset);
        else if (draggingHuePicker) updateHuePicker(mouseX);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSvPicker || draggingHuePicker) {
            // Apply the exact release coordinate before ending the drag. The
            // picker capture itself is initiated only by a left-button press.
            if (draggingSvPicker) updateSvPicker(event.x(), event.y() + scrollOffset);
            else updateHuePicker(event.x());
            draggingSvPicker = false;
            draggingHuePicker = false;
            this.setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    private void updateSvPicker(double mouseX, double mouseY) {
        int gx = pickerX();
        int gy = pickerY();

        sat = clamp01((float) ((mouseX - gx) / (pickerWidth() - 1.0)));
        val = 1.0f - clamp01((float) ((mouseY - gy) / (pickerHeight() - 1.0)));
        chroma = false;
        syncHex();
    }

    private void updateHuePicker(double mouseX) {
        int gx = hueX();
        int hueW = pickerWidth();

        hue = clamp01((float) ((mouseX - gx) / (hueW - 1.0)));
        chroma = false;
        syncHex();
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /**
     * Builds a high-density saturation/value square once for the current hue and uploads
     * it only when the hue changes. The old implementation queued 8,192 fill calls every frame,
     * which made the edit screen noticeably laggy.
     */
    private void refreshPickerTexture() {
        if (pickerTexture == null) {
            pickerTexture = new DynamicTexture("simple-waypoints-color-picker",
                    PICKER_TEXTURE_WIDTH, PICKER_TEXTURE_HEIGHT, true);
            Minecraft.getInstance().getTextureManager().register(PICKER_TEXTURE_ID, pickerTexture);
            pickerTextureHue = Float.NaN;
        }

        if (!Float.isNaN(pickerTextureHue) && Math.abs(pickerTextureHue - hue) < 0.0001f) {
            return;
        }

        // When dragging the hue strip, cap texture uploads at roughly 60 per second.
        // The selection hex/preview still follows the cursor every input event.
        long now = System.nanoTime();
        if (!Float.isNaN(pickerTextureHue)
                && now - lastPickerUploadNanos < 16_000_000L) {
            return;
        }

        NativeImage pixels = pickerTexture.getPixels();
        for (int y = 0; y < PICKER_TEXTURE_HEIGHT; y++) {
            float brightness = 1.0f - y / (float) (PICKER_TEXTURE_HEIGHT - 1);
            for (int x = 0; x < PICKER_TEXTURE_WIDTH; x++) {
                float saturation = x / (float) (PICKER_TEXTURE_WIDTH - 1);
                pixels.setPixel(x, y, 0xFF000000 | hsvToRgb(hue, saturation, brightness));
            }
        }
        pickerTexture.upload();
        pickerTextureHue = hue;
        lastPickerUploadNanos = now;
    }

    /** Creates the vertical hue spectrum once and reuses it for every menu frame. */
    private static void ensureHueTexture() {
        if (hueTexture != null) return;

        hueTexture = new DynamicTexture("simple-waypoints-hue-strip",
                HUE_TEXTURE_WIDTH, HUE_TEXTURE_HEIGHT, true);
        Minecraft.getInstance().getTextureManager().register(HUE_TEXTURE_ID, hueTexture);

        NativeImage pixels = hueTexture.getPixels();
        for (int x = 0; x < HUE_TEXTURE_WIDTH; x++) {
            int rgb = 0xFF000000 | hsvToRgb(
                    x / (float) (HUE_TEXTURE_WIDTH - 1), 1.0f, 1.0f);
            for (int y = 0; y < HUE_TEXTURE_HEIGHT; y++) pixels.setPixel(x, y, rgb);
        }
        hueTexture.upload();
    }

    // ------------------------------------------------------------------ color logic

    private void pickRecent(int idx) {
        if (idx < 0 || idx >= RECENT.size()) return;
        setFromRgb(RECENT.get(idx));
        chroma = false;
        syncHex();
    }

    private void resetColor() {
        hue = 0f;
        sat = 1f;
        val = 1f;
        chroma = false;
        syncHex();
    }

    private void syncHex() {
        if (hexBox == null) return;
        String hex = String.format(Locale.ROOT, "#%06X", currentRgb());
        if (!hex.equals(lastHex)) {
            hexBox.setValue(hex);
            lastHex = hex;
        }
    }

    private void applyHex(String text) {
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) return;
        try {
            setFromRgb(Integer.parseInt(t, 16));
            chroma = false;
        } catch (NumberFormatException ignored) {
        }
    }

    private int currentRgb() {
        return hsvToRgb(hue, sat, val);
    }

    private void setFromRgb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = (((g - b) / d) % 6f) / 6f;
        else if (max == g) h = (((b - r) / d) + 2f) / 6f;
        else h = (((r - g) / d) + 4f) / 6f;
        if (h < 0f) h += 1f;
        hue = h;
        sat = max == 0f ? 0f : d / max;
        val = max;
    }

    private static int hsvToRgb(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    // ------------------------------------------------------------------ saving
    private void pickBlockColor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null
                || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            error = "Look at a block to sample its map color.";
            return;
        }
        BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();
        int sampled = mc.level.getBlockState(pos).getMapColor(mc.level, pos).col;
        setFromRgb(sampled);
        chroma = false;
        syncHex();
        error = "Block color sampled.";
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            error = "Please type a name.";
            return;
        }

        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(xBox.getValue().trim());
            y = Integer.parseInt(yBox.getValue().trim());
            z = Integer.parseInt(zBox.getValue().trim());
        } catch (NumberFormatException e) {
            error = "X, Y and Z must be whole numbers.";
            return;
        }

        int color = chroma ? WaypointsMod.CHROMA : currentRgb();
        // Keep the legacy JSON marker field for backwards compatibility; it is not rendered.
        String marker = ICON_MARKERS[Math.max(0, Math.min(ICON_MARKERS.length - 1, selectedIconIndex))];

        // Keep an existing temporary waypoint's expiry intact; newly created waypoints are permanent.
        long expires = original != null ? original.expiresAt() : 0L;
        String dim = dimMode >= 0 ? DIM_IDS[dimMode] : keepDimension;
        String worldId = original != null ? original.worldId() : WaypointsMod.currentWorldId();

        WaypointsMod.upsert(original, new Waypoint(name, x, y, z, color, dim, expires, worldId, marker, labelEnabled, beamEnabled));

        if (!chroma) {
            Integer rgb = color;
            RECENT.remove(rgb);
            RECENT.add(0, rgb);
            while (RECENT.size() > 7) RECENT.remove(RECENT.size() - 1);
        }
        back();
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (draggingSvPicker || draggingHuePicker) updatePickerFromLiveMouse();
        String curHex = hexBox.getValue();
        if (!curHex.equals(lastHex)) {
            lastHex = curHex;
            applyHex(curHex);
        }

        int left = formLeft();
        int coordsY = coordinateFieldsY();
        int dimY = dimensionFieldY();
        int nameWidth = formWidth - 36;
        int formRight = contentLeft + formWidth;

        drawSidebar(g, mouseX, mouseY);
        rounded(g, contentLeft, breadcrumbY - 5, contentLeft + 38, breadcrumbY + 31, CARD, 8);
        border(g, contentLeft, breadcrumbY - 5, contentLeft + 38, breadcrumbY + 31, CARD_BORDER);
        WaypointUiText.draw(g, font, "←", contentLeft + 12, breadcrumbY + 7, TEXT, true);
        WaypointUiText.drawTitle(g, font, original == null ? "Create Waypoint" : "Edit Waypoint", contentLeft + 54, breadcrumbY + 2, TEXT);

        // Main left panel: name, coordinates, dimension, icon family and color picker.
        card(g, contentLeft, cardTop, formWidth, locationCardHeight);
        WaypointUiText.draw(g, font, "Waypoint name", left, cardTop + 20, TEXT, false);
        drawPinGlyph(g, left + 4, nameFieldY() + 10, WaypointsMod.uiAccentColor);
        drawBox(g, nameBox, left + 32, nameFieldY(), nameWidth - 32, 34, "Waypoint name");
        WaypointUiText.draw(g, font, "Coordinates", left, coordsY - 16, TEXT, false);
        int cw = coordinateWidth, gap = 8;
        WaypointUiText.draw(g, font, "X", left, coordsY - 4, WaypointsMod.uiAccentColor, true);
        WaypointUiText.draw(g, font, "Y", left + cw + gap, coordsY - 4, WaypointsMod.uiAccentColor, true);
        WaypointUiText.draw(g, font, "Z", left + (cw + gap) * 2, coordsY - 4, WaypointsMod.uiAccentColor, true);
        drawBox(g, xBox, left, coordsY, cw, 34, "X");
        drawBox(g, yBox, left + cw + gap, coordsY, cw, 34, "Y");
        drawBox(g, zBox, left + (cw + gap) * 2, coordsY, cw, 34, "Z");
        WaypointUiText.draw(g, font, "Dimension", left, dimY - 16, TEXT, false);
        rounded(g, left, dimY, left + nameWidth, dimY + 34, PANEL_ALT, 6);
        border(g, left, dimY, left + nameWidth, dimY + 34, CARD_BORDER);
        String selectedDimension = dimMode >= 0 ? DIM_LABELS[dimMode] : dimensionName(keepDimension);
        g.fill(left + 9, dimY + 10, left + 24, dimY + 19, 0xFF765239);
        g.fill(left + 9, dimY + 7, left + 24, dimY + 12, 0xFF4E9A38);
        WaypointUiText.draw(g, font, "■", left + 13, dimY + 8, 0xFFB7F36B, true);
        WaypointUiText.draw(g, font, fitText(selectedDimension, nameWidth - 55), left + 32, dimY + 11, TEXT, false);
        WaypointUiText.draw(g, font, "v", left + nameWidth - 18, dimY + 10, MUTED, false);
        if (dimensionMenuOpen) {
            int menuY = dimY + 35;
            int optionHeight = 23;
            rounded(g, left, menuY, left + nameWidth, menuY + DIM_LABELS.length * optionHeight, 0xFF070809, 7);
            border(g, left, menuY, left + nameWidth, menuY + DIM_LABELS.length * optionHeight, CARD_BORDER);
            for (int i = 0; i < DIM_LABELS.length; i++) {
                int optionY = menuY + i * optionHeight;
                if (i == dimMode) rounded(g, left + 2, optionY + 1, left + nameWidth - 2, optionY + optionHeight - 1, 0xFF202B1B, 5);
                WaypointUiText.draw(g, font, DIM_LABELS[i], left + 11, optionY + 7, i == dimMode ? WaypointsMod.uiAccentColor : TEXT, false);
            }
        }

        WaypointUiText.draw(g, font, "Icon", left, iconRowY(), TEXT, false);
        for (int i = 0; i < ICON_GLYPHS.length; i++) {
            int bx = contentLeft + 16 + i * 42;
            int by = iconRowY() + 16;
            boolean selected = selectedIconIndex == i;
            rounded(g, bx, by, bx + 34, by + 29, selected ? WaypointsMod.uiAccentColor : PANEL_ALT, 6);
            border(g, bx, by, bx + 34, by + 29, selected ? WaypointsMod.uiAccentColor : CARD_BORDER);
            WaypointUiGlyphs.draw(g, font, bx + 7, by + 6, 20, ICON_MARKERS[i], selected ? 0xFF101410 : TEXT);
        }

        WaypointUiText.draw(g, font, "Color", left, cardTop + 210, TEXT, false);
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int px = presetX(i), py = presetY(i);
            rrect(g, px, py, px + 17, py + 17, PRESET_COLORS[i]);
            if (!chroma && currentRgb() == (PRESET_COLORS[i] & 0xFFFFFF)) border(g, px - 2, py - 2, px + 19, py + 19, WaypointsMod.uiAccentColor);
        }
        refreshPickerTexture();
        g.blit(PICKER_TEXTURE_ID, pickerX(), pickerY(), pickerX() + pickerWidth(), pickerY() + pickerHeight(),
                0.0f, 1.0f, 0.0f, 1.0f);
        border(g, pickerX(), pickerY(), pickerX() + pickerWidth(), pickerY() + pickerHeight(), CARD_BORDER);
        int markX = pickerX() + Math.round(sat * (pickerWidth() - 1));
        int markY = pickerY() + Math.round((1f - val) * (pickerHeight() - 1));
        ring(g, markX, markY);
        ensureHueTexture();
        g.blit(HUE_TEXTURE_ID, hueX(), hueY(), hueX() + pickerWidth(), hueY() + HUE_DISPLAY_HEIGHT,
                0.0f, 1.0f, 0.0f, 1.0f);
        int hueMarkerX = hueX() + Math.round(hue * (pickerWidth() - 1));
        g.fill(hueMarkerX - 1, hueY() - 2, hueMarkerX + 2, hueY() + HUE_DISPLAY_HEIGHT + 2, 0xFFFFFFFF);
        drawBox(g, hexBox, contentLeft + 16, hexRowY(), 76, 27, "HEX");
        WaypointUiText.draw(g, font, "HEX", contentLeft + 22, hexRowY() + 9, QUIET, false);
        rounded(g, contentLeft + 98, hexRowY(), contentLeft + 125, hexRowY() + 27, PANEL_ALT, 6);
        border(g, contentLeft + 98, hexRowY(), contentLeft + 125, hexRowY() + 27, CARD_BORDER);
        drawDropper(g, contentLeft + 108, hexRowY() + 8, TEXT);
        rounded(g, contentLeft + 130, chromaY(), contentLeft + formWidth - 16, chromaY() + 28,
                chroma ? 0xFF293E1B : PANEL_ALT, 7);
        border(g, contentLeft + 130, chromaY(), contentLeft + formWidth - 16, chromaY() + 28, chroma ? WaypointsMod.uiAccentColor : CARD_BORDER);
        String rainbow = "O";
        WaypointUiText.draw(g, font, rainbow + "  Chroma", contentLeft + 145, chromaY() + 9, chroma ? WaypointsMod.uiAccentColor : TEXT, true);

        // Right side: scene preview and the three switches in the supplied mockup.
        card(g, colorX, cardTop, colorWidth, 178);
        drawEditorScene(g, colorX + 8, cardTop + 8, colorWidth - 16, 162,
                chroma ? WaypointsMod.resolveColor(new Waypoint("", 0, 0, 0, WaypointsMod.CHROMA, "", 0L, "", "W")) : currentRgb());
        int toggleTop = cardTop + 190;
        card(g, colorX, toggleTop, colorWidth, Math.max(150, height - toggleTop - 56));
        int[] rowY = {toggleTop + 12, toggleTop + 56, toggleTop + 100};
        String[] rowLabels = {"Show Beacon", "Show Label", "Match Color"};
        boolean[] rowStates = {beamEnabled, labelEnabled, WaypointsMod.matchWaypointColor};
        String[] rowIcons = {"◉", "Aa", "●"};
        for (int i = 0; i < rowY.length; i++) {
            rounded(g, colorX + 8, rowY[i], colorX + colorWidth - 8, rowY[i] + 38, PANEL_ALT, 7);
            WaypointUiText.draw(g, font, rowIcons[i], colorX + 18, rowY[i] + 12, WaypointsMod.uiAccentColor, true);
            WaypointUiText.draw(g, font, rowLabels[i], colorX + 49, rowY[i] + 14, TEXT, false);
            toggle(g, colorX + colorWidth - 47, rowY[i] + 10, rowStates[i]);
        }

        if (original != null) {
            actionButton(g, mouseX, mouseY, contentRight - 236, actionY, 112, 31, "Cancel", false, false);
            actionButton(g, mouseX, mouseY, contentRight - 116, actionY, 116, 31, "Save Changes", true, false);
        } else {
            actionButton(g, mouseX, mouseY, contentRight - 180, actionY, 62, 31, "Cancel", false, false);
            actionButton(g, mouseX, mouseY, contentRight - 118, actionY, 118, 31, "Create Waypoint", true, false);
        }
        if (!error.isEmpty()) WaypointUiText.draw(g, font, error, contentLeft, actionY - 18, 0xFFFF8585, false);

        super.extractRenderState(g, mouseX, mouseY, delta);
        WaypointUiText.drawInput(g, font, nameBox, left + 44, nameFieldY() + 11, nameWidth - 56);
        WaypointUiText.drawInput(g, font, xBox, left + 12, coordsY + 11, cw - 28);
        WaypointUiText.drawInput(g, font, yBox, left + cw + gap + 12, coordsY + 11, cw - 28);
        WaypointUiText.drawInput(g, font, zBox, left + (cw + gap) * 2 + 12, coordsY + 11, cw - 28);
        WaypointUiText.drawInput(g, font, hexBox, contentLeft + 42, hexRowY() + 9, 44);
    }

    private void drawEditorScene(GuiGraphicsExtractor g, int x, int y, int w, int h, int marker) {
        g.blit(EDITOR_SCENE_TEXTURE, x, y, x + w, y + h, 0.0f, 1.0f, 0.0f, 1.0f);
        int cx = x + w / 2;
        // Keep the beacon and label responsive to the current chosen color.
        g.fill(cx - 1, y + 5, cx + 2, y + h - 12, (marker & 0x00FFFFFF) | 0x88000000);
        rounded(g, cx - 28, y + 18, cx + 29, y + 46, 0xDD080B0A, 7);
        WaypointUiText.draw(g, font, "⌂  " + (nameBox == null || nameBox.getValue().isBlank() ? "Home" : fitText(nameBox.getValue(), 12)),
                cx - 21, y + 27, (marker & 0x00FFFFFF) | 0xFF000000, true);
        border(g, x, y, x + w, y + h, CARD_BORDER);
    }

    private void drawDropper(GuiGraphicsExtractor g, int x, int y, int color) {
        // Small eyedropper silhouette, drawn from crisp one-pixel strokes.
        g.fill(x + 5, y, x + 8, y + 3, color);
        g.fill(x + 3, y + 2, x + 6, y + 5, color);
        g.fill(x + 1, y + 4, x + 4, y + 7, color);
        g.fill(x, y + 7, x + 2, y + 9, color);
        g.fill(x - 1, y + 9, x + 1, y + 11, color);
    }

    private String fitText(String value, int maxWidth) {
        if (value == null) return "";
        String result = value;
        while (!result.isEmpty() && WaypointUiText.width(font, result) > maxWidth) result = result.substring(0, result.length() - 1);
        return result.equals(value) ? result : result + "...";
    }

    private String dimensionName(String raw) {
        String dimension = WaypointsMod.normalizeDimension(raw);
        if (dimension.endsWith(":the_nether")) return "Nether";
        if (dimension.endsWith(":the_end")) return "The End";
        if (dimension.endsWith(":overworld")) return "Overworld";
        return "Current dimension";
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        if (railWidth <= 0) return;
        g.fill(0, 0, railWidth, height, 0xFF101113);
        g.fill(railWidth - 1, 0, railWidth, height, CARD_BORDER);
        int sideInset = 10;
        drawBrandMark(g, sideInset + 8, 20);
        WaypointUiText.draw(g, font, "Simple", sideInset + 51, 22, TEXT, true);
        WaypointUiText.draw(g, font, "Waypoints", sideInset + 51, 36, TEXT, true);
        int navWidth = railWidth - 20;
        nav(g, mx, my, sideInset, 112, navWidth, "", "Waypoints", true);
        nav(g, mx, my, sideInset, 157, navWidth, "", "Map", false);
        nav(g, mx, my, sideInset, 202, navWidth, "", "Settings", false);
        nav(g, mx, my, sideInset, 247, navWidth, "", "About", false);
        int base = height - 72;
        g.fill(14, base - 25, 42, base - 7, 0xFF79502E);
        g.fill(14, base - 28, 42, base - 22, 0xFF4F9C35);
        g.fill(18, base - 26, 24, base - 24, 0xFF82C94B);
        WaypointUiText.draw(g, font, "v1.4.6", 54, base - 3, WaypointsMod.uiAccentColor, true);
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

    private void drawSlidersGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y + 2, x + 13, y + 3, color); g.fill(x, y + 7, x + 13, y + 8, color); g.fill(x, y + 12, x + 13, y + 13, color);
        g.fill(x + 3, y, x + 6, y + 5, color); g.fill(x + 8, y + 5, x + 11, y + 10, color);
    }

    private void drawCubeGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        drawPixelLine(g, x + 5, y, x + 10, y + 3, color); drawPixelLine(g, x + 10, y + 3, x + 5, y + 6, color);
        drawPixelLine(g, x + 5, y + 6, x, y + 3, color); drawPixelLine(g, x, y + 3, x + 5, y, color);
        g.fill(x + 5, y + 1, x + 6, y + 6, color); drawPixelLine(g, x, y + 3, x, y + 8, color);
        drawPixelLine(g, x + 5, y + 6, x + 5, y + 10, color); drawPixelLine(g, x + 10, y + 3, x + 10, y + 8, color);
        drawPixelLine(g, x, y + 8, x + 5, y + 10, color); drawPixelLine(g, x + 5, y + 10, x + 10, y + 8, color);
    }

    private void drawPinGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 4, y, x + 8, y + 2, color);
        g.fill(x + 2, y + 2, x + 10, y + 6, color);
        g.fill(x + 3, y + 6, x + 9, y + 9, color);
        g.fill(x + 5, y + 9, x + 7, y + 12, color);
        g.fill(x + 4, y + 3, x + 8, y + 6, CARD);
    }

    private void nav(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String icon, String label, boolean active) {
        if (active || inside(mx, my, x, y, w, 36)) rrect(g, x, y, x + w, y + 36, active ? WaypointsMod.uiAccentColor : CARD);
        int tint = active ? 0xFF101410 : MUTED;
        if ("Waypoints".equals(label) || label.isEmpty()) drawPinGlyph(g, x + 11, y + 12, tint);
        else if ("Map".equals(label)) drawCubeGlyph(g, x + 10, y + 12, tint);
        else if ("Settings".equals(label)) drawSlidersGlyph(g, x + 10, y + 12, tint);
        else { rrect(g, x + 10, y + 11, x + 24, y + 25, tint); WaypointUiText.draw(g, font, "i", x + 15, y + 13, 0xFF101113, true); }
        if (!label.isEmpty()) WaypointUiText.draw(g, font, label, x + 34, y + 12, active ? 0xFF101410 : MUTED, active);
    }

    private void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        rrect(g, x, y, x + w, y + h, CARD);
        g.fill(x, y, x + w, y + 1, CARD_BORDER); g.fill(x, y + h - 1, x + w, y + h, CARD_BORDER);
        g.fill(x, y, x + 1, y + h, CARD_BORDER); g.fill(x + w - 1, y, x + w, y + h, CARD_BORDER);
    }

    private void settingLine(GuiGraphicsExtractor g, int mx, int x, int y, int w,
                             String title, String description, boolean enabled) {
        WaypointUiText.draw(g, font, title, x, y, TEXT, false);
        WaypointUiText.draw(g, font, description, x, y + 12, MUTED, false);
        toggle(g, x + w - 26, y + 1, enabled);
    }

    private void toggle(GuiGraphicsExtractor g, int x, int y, boolean enabled) {
        rrect(g, x, y, x + 26, y + 15, enabled ? 0xFF45652A : 0xFF3B4247);
        rrect(g, enabled ? x + 13 : x + 2, y + 2, enabled ? x + 24 : x + 13, y + 13,
                enabled ? WaypointsMod.uiAccentColor : 0xFF9BA4A8);
    }

    private void actionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h,
                              String label, boolean primary, boolean danger) {
        boolean hover = inside(mx, my, x, y, w, h);
        int fill = primary ? (hover ? 0xFFBDE39A : WaypointsMod.uiAccentColor)
                : danger ? (hover ? 0xFF4D2225 : 0xFF281719)
                : (hover ? 0xFF242B2F : CARD);
        rrect(g, x, y, x + w, y + h, fill);
        if (!primary) {
            g.fill(x, y, x + w, y + 1, danger ? 0xFF85464A : CARD_BORDER);
            g.fill(x, y + h - 1, x + w, y + h, danger ? 0xFF85464A : CARD_BORDER);
        }
        int color = primary ? 0xFF101410 : danger ? 0xFFFF8C92 : TEXT;
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + (h - WaypointUiText.LINE_HEIGHT) / 2, color, false);
    }

    private int parseCoordinate(EditBox box, int fallback) {
        try { return Integer.parseInt(box.getValue().trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private String fitPreview(String value, int max) {
        if (value == null) return "Waypoint";
        String s = value;
        while (!s.isEmpty() && WaypointUiText.width(font, s) > max) s = s.substring(0, s.length() - 1);
        return s.isEmpty() ? "Waypoint" : s.equals(value) ? s : s + "...";
    }

    private String worldTitle() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.getCurrentServer() != null) return mc.getCurrentServer().name;
            if (mc.getSingleplayerServer() != null) return mc.getSingleplayerServer().getWorldData().getLevelName();
        } catch (Throwable ignored) { }
        return "Current World";
    }

    private void drawBox(GuiGraphicsExtractor g, EditBox box, int x, int y, int w, int h, String placeholder) {
        boolean focused = box.isFocused();
        rrect(g, x, y, x + w, y + h, focused ? 0xFF536044 : CARD_BORDER);
        rrect(g, x + 1, y + 1, x + w - 1, y + h - 1, 0xFF08090A);
    }

    private static void border(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color) {
        g.fill(x, y, x2, y + 1, color);
        g.fill(x, y2 - 1, x2, y2, color);
        g.fill(x, y, x + 1, y2, color);
        g.fill(x2 - 1, y, x2, y2, color);
    }

    private static void rounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int radius) {
        int w = x2 - x;
        int h = y2 - y;
        if (w <= 0 || h <= 0) return;
        int r = Math.min(WaypointsMod.smoothUiEdges ? Math.max(radius, 8) : Math.min(radius, 3), Math.min(w / 2, h / 2));
        g.fill(x + r, y, x2 - r, y2, color);
        g.fill(x, y + r, x2, y2 - r, color);
        for (int row = 0; row < r; row++) {
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0.0, r * r - Math.pow(r - row - 0.5, 2))));
            g.fill(x + inset, y + row, x2 - inset, y + row + 1, color);
            g.fill(x + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
    }

    /** Efficient rounded rectangle: draw the body once and rasterize only the corner rows. */
    private static void rrect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int width = x2 - x1;
        int height = y2 - y1;
        if (width <= 0 || height <= 0) return;

        int radius = Math.min(WaypointsMod.smoothUiEdges ? 8 : 3, Math.min(width / 2, height / 2));
        if (radius <= 0) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }

        // Central body: two rectangles instead of one draw call per vertical pixel.
        g.fill(x1 + radius, y1, x2 - radius, y2, color);
        g.fill(x1, y1 + radius, x2, y2 - radius, color);

        // Only the rounded top and bottom corner rows need separate fills.
        for (int row = 0; row < radius; row++) {
            double dy = radius - row - 0.5;
            int inset = (int) Math.ceil(radius - Math.sqrt(Math.max(0.0, radius * radius - dy * dy)));
            g.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color);
            g.fill(x1 + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
    }

    /** Small white ring used as the picker marker. */
    private static void ring(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 3, y - 3, x + 4, y - 2, 0xFF000000);
        g.fill(x - 3, y + 3, x + 4, y + 4, 0xFF000000);
        g.fill(x - 3, y - 2, x - 2, y + 3, 0xFF000000);
        g.fill(x + 3, y - 2, x + 4, y + 3, 0xFF000000);
        g.fill(x - 2, y - 2, x + 3, y - 1, 0xFFFFFFFF);
        g.fill(x - 2, y + 2, x + 3, y + 3, 0xFFFFFFFF);
        g.fill(x - 2, y - 1, x - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + 2, y - 1, x + 3, y + 2, 0xFFFFFFFF);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
