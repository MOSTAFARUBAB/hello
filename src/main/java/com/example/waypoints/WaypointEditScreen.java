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
    private static final int ACCENT = 0xFFB7F36B;

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
    private static final int HUE_TEXTURE_HEIGHT = 32;
    private static final int HUE_TEXTURE_WIDTH = 1024;
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
    private int locationOffset(int base) { return Math.round(base * (locationCardHeight / 308.0f)); }
    private int nameFieldY() { return cardTop + locationOffset(70); }
    private int coordinateFieldsY() { return cardTop + locationOffset(156); }
    private int dimensionFieldY() { return cardTop + locationOffset(243); }
    private int pickerX() { return colorX + 18; }
    private int pickerY() { return cardTop + 43 + Math.round((height - 784) * 0.03f); }
    private int pickerHeight() { return Math.max(108, Math.min(PICKER_MAX_HEIGHT, Math.round(height * 0.095f + 39))); }
    private int pickerWidth() { return Math.max(170, colorWidth - 36); }
    private int hueX() { return pickerX(); }
    private int hueY() { return pickerY() + pickerHeight() + 12; }
    private int hexRowY() { return hueY() + HUE_DISPLAY_HEIGHT + 12; }
    private int chromaY() { return hexRowY() + 42; }
    private int recentLabelY() { return chromaY() + 57; }
    private int recentColorsY() { return recentLabelY() + 15; }
    private int previewX() { return colorX + 18; }

    public WaypointEditScreen(Waypoint original, int returnPage) {
        super(Component.literal(original == null ? "New Waypoint" : "Edit Waypoint"));
        this.original = original;
        this.returnPage = returnPage;

        if (original != null) {
            labelEnabled = original.isLabelEnabled();
            beamEnabled = original.isBeamEnabled();
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
        railWidth = width >= 1120 ? 190 : width >= 1000 ? 165 : width >= 800 ? Math.max(150, Math.min(190, Math.round(width * 0.16f))) : 0;
        int leftInset = width >= 1120 ? 33 : width >= 1000 ? 30 : 18;
        int rightInset = width >= 1120 ? 35 : width >= 1000 ? 37 : 18;
        contentLeft = railWidth > 0 ? railWidth + leftInset : leftInset;
        contentRight = width - rightInset;
        int available = Math.max(500, contentRight - contentLeft);
        colorWidth = Math.max(250, Math.min(330, Math.round(available * 0.337f)));
        formWidth = Math.max(300, available - colorWidth - 18);
        colorX = contentLeft + formWidth + 18;
        coordinateWidth = Math.max(72, (formWidth - 52) / 3);

        cardTop = Math.max(140, Math.round(height * 0.19f + 21));
        oy = cardTop - 10;
        breadcrumbY = Math.round(height * 0.10f);
        locationCardHeight = Math.max(292, Math.min(344, Math.round(308 + (height - 784) * 0.266f)));
        visibilityY = cardTop + locationCardHeight + (height < 830 ? 17 : 16);
        visibilityHeight = Math.max(148, Math.round(156 - (height - 784) * 0.04f));
        colorCardHeight = Math.max(330, Math.round(340 + (height - 784) * 0.298f));
        actionY = original == null ? height - 67 : visibilityY + visibilityHeight + 17;
        scrollTop = cardTop;
        scrollBottom = height - 20;
        int previewYForScroll = cardTop + colorCardHeight + 42 + Math.round((height - 784) * 0.024f);
        int previewHForScroll = Math.max(96, Math.min(190, Math.round(170 + (height - 784) * 0.135f)));
        int deleteBottomForScroll = original == null ? 0 : actionY + 50 + 66;
        int leftBottomForScroll = original == null ? visibilityY + visibilityHeight + 48 : Math.max(deleteBottomForScroll, actionY + 31);
        int rightBottomForScroll = previewYForScroll + previewHForScroll + 40;
        maxScroll = Math.max(0, Math.max(leftBottomForScroll, rightBottomForScroll) - scrollBottom);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        int left = formLeft();
        int nameY = nameFieldY();
        int coordsY = coordinateFieldsY();
        int dimY = dimensionFieldY();
        int nameWidth = formWidth - 36;
        int coordGap = 8;

        if (railWidth > 0) {
            int navY = 153;
            int sideInset = railWidth > 170 ? 20 : 10;
            int navWidth = railWidth - sideInset - 14;
            click(sideInset, navY, navWidth, 32,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(returnPage < 0 ? 0 : returnPage)));
            click(sideInset, navY + 41, navWidth, 32,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointMapScreen()));
            click(sideInset, navY + 82, navWidth, 32,
                    () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));
        }
        // The breadcrumb is the only Back hit area; it never overlaps the page title.
        click(contentLeft, breadcrumbY - 4, 126, 18, this::back);

        nameBox = makeBox(nameBox, left + 10, nameY + 7, nameWidth - 20, 20, initName, 32, "Waypoint name");
        xBox = makeBox(xBox, left + 10, coordsY + 7, coordinateWidth - 20, 20, initX, 9, "X");
        yBox = makeBox(yBox, left + coordinateWidth + coordGap + 10, coordsY + 7, coordinateWidth - 20, 20, initY, 9, "Y");
        zBox = makeBox(zBox, left + (coordinateWidth + coordGap) * 2 + 10, coordsY + 7, coordinateWidth - 20, 20, initZ, 9, "Z");

        clickContent(left, coordsY + locationOffset(39), 142, 24, this::useCurrentPosition);
        clickContent(left + 150, coordsY + locationOffset(39), Math.max(112, formWidth - 186), 24, this::useLookedAtBlock);
        clickContent(left, dimY, nameWidth, 34, () -> dimensionMenuOpen = !dimensionMenuOpen);

        // Color picker interactions use a wide horizontal saturation/value field and hue strip.
        clickContent(colorX + colorWidth - 58, recentLabelY() - 1, 42, 18, this::resetColor);
        clickContent(previewX(), chromaY(), colorWidth - 20, 46, () -> chroma = !chroma);
        for (int i = 0; i < RECENT.size(); i++) {
            final int idx = i;
            clickContent(previewX() + i * 29, recentColorsY(), 23, 23, () -> pickRecent(idx));
        }
        int rowY = hexRowY();
        hexBox = makeBox(hexBox, previewX() + 124, rowY + 6 - scrollOffset, Math.max(52, colorWidth - 190), 20,
                String.format(Locale.ROOT, "#%06X", currentRgb()), 7, "HEX");
        lastHex = hexBox.getValue();
        nameBox.setY(nameY + 7 - scrollOffset);
        xBox.setY(coordsY + 7 - scrollOffset);
        yBox.setY(coordsY + 7 - scrollOffset);
        zBox.setY(coordsY + 7 - scrollOffset);

        // Per-waypoint visibility controls.
        clickContent(left + formWidth - 62, visibilityY + 55, 26, 15, () -> labelEnabled = !labelEnabled);
        clickContent(left + formWidth - 62, visibilityY + 100, 26, 15, () -> beamEnabled = !beamEnabled);

        refreshPickerTexture();
        ensureHueTexture();

        int formRight = contentLeft + formWidth;
        if (original == null) {
            click(formRight - 180, actionY, 62, 31, this::back);
            click(formRight - 118, actionY, 118, 31, this::save);
        } else {
            click(formRight - 236, actionY, 112, 31, this::back);
            click(formRight - 116, actionY, 116, 31, this::save);
            int deleteY = actionY + 50;
            clickContent(formRight - 96, deleteY + 16, 78, 34, () -> {
                WaypointsMod.remove(original);
                back();
            });
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
                int top = dimensionFieldY() + locationOffset(36);
                int optionHeight = locationOffset(24);
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
        String marker = original != null ? original.safeMarkerText() : "W";

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
        int preview = previewX();
        int pickerTop = pickerY();
        int nameY = nameFieldY();
        int coordsY = coordinateFieldsY();
        int dimY = dimensionFieldY();
        int nameWidth = formWidth - 36;
        int formRight = contentLeft + formWidth;

        drawSidebar(g, mouseX, mouseY);
        int topBarHeight = height >= 880 ? 55 : 44;
        g.fill(railWidth, 0, width, topBarHeight, 0xFF0B0C0E);
        g.fill(railWidth, topBarHeight - 1, width, topBarHeight, CARD_BORDER);
        drawCubeGlyph(g, contentLeft, 14, MUTED);
        String topWorld = fitText(worldTitle(), 112);
        int topWorldX = contentLeft + 18;
        WaypointUiText.draw(g, font, topWorld, topWorldX, 17, MUTED, false);
        int crumbX = topWorldX + WaypointUiText.width(font, topWorld) + 10;
        WaypointUiText.draw(g, font, "/", crumbX, 17, QUIET, false);
        WaypointUiText.draw(g, font, "Waypoints", crumbX + 14, 17, TEXT, false);
        g.fill(width - 132, 18, width - 127, 23, ACCENT);
        WaypointUiText.draw(g, font, "Local workspace", width - 120, 16, MUTED, false);
        border(g, width - 49, 15, width - 37, 27, QUIET);
        WaypointUiText.draw(g, font, "?", width - 45, 17, MUTED, false);
        WaypointUiText.draw(g, font, original == null ? "<  All waypoints" : "<  All waypoints  /  " + fitText(original.name(), 140), contentLeft, breadcrumbY, MUTED, false);
        int titleY = height >= 880 ? 127 : 109;
        WaypointUiText.drawTitle(g, font, original == null ? "Mark somewhere new." : "Edit waypoint", contentLeft, titleY, TEXT);
        WaypointUiText.draw(g, font, original == null ? "Save a location now. Find your way back whenever you need it." : "Fine-tune the details. Keep your favorite place within reach.",
                contentLeft, titleY + 32, MUTED, false);
        if (original != null) {
            rounded(g, contentRight - 101, titleY + 8, contentRight, titleY + 32, 0xFF151B14, 6);
            g.fill(contentRight - 94, titleY + 19, contentRight - 90, titleY + 23, ACCENT);
            WaypointUiText.draw(g, font, "Unsaved changes", contentRight - 85, titleY + 16, ACCENT, false);
        }

        // Scrollable form and color workspace. Footer actions remain fixed and readable.
        g.enableScissor(contentLeft, scrollTop, width - 12, scrollBottom);
        g.pose().pushMatrix();
        g.pose().translate(0.0f, -scrollOffset);

        // Left: location card and per-waypoint visibility, matching the reference layout.
        card(g, contentLeft, cardTop, formWidth, locationCardHeight);
        drawPinGlyph(g, contentLeft + 19, cardTop + 18, ACCENT);
        WaypointUiText.draw(g, font, "Location details", contentLeft + 39, cardTop + 21, TEXT, false);
        WaypointUiText.draw(g, font, "Waypoint name", left, cardTop + locationOffset(54), TEXT, false);
        drawBox(g, nameBox, left, nameY, nameWidth, 34, "Waypoint name");
        WaypointUiText.draw(g, font, "A memorable name for a memorable place.", left, nameY + locationOffset(42), QUIET, false);

        int cw = coordinateWidth;
        int gap = 8;
        WaypointUiText.draw(g, font, "X", left, coordsY - 16, TEXT, false);
        WaypointUiText.draw(g, font, "Y", left + cw + gap, coordsY - 16, TEXT, false);
        WaypointUiText.draw(g, font, "Z", left + (cw + gap) * 2, coordsY - 16, TEXT, false);
        drawBox(g, xBox, left, coordsY, cw, 34, "X");
        drawBox(g, yBox, left + cw + gap, coordsY, cw, 34, "Y");
        drawBox(g, zBox, left + (cw + gap) * 2, coordsY, cw, 34, "Z");
        int quickY = coordsY + locationOffset(39);
        rounded(g, left, quickY, left + 142, quickY + 24, 0xFF202820, 5);
        border(g, left, quickY, left + 142, quickY + 24, 0xFF49613A);
        WaypointUiText.draw(g, font, "Use current position", left + 9, quickY + 7, ACCENT, false);
        int lookX = left + 150;
        int lookW = Math.max(112, formWidth - 186);
        rounded(g, lookX, quickY, lookX + lookW, quickY + 24, PANEL_ALT, 5);
        border(g, lookX, quickY, lookX + lookW, quickY + 24, CARD_BORDER);
        WaypointUiText.draw(g, font, "Use block you're looking at", lookX + 8, quickY + 7, TEXT, false);

        WaypointUiText.draw(g, font, "Dimension", left, dimY - locationOffset(15), TEXT, false);
        rounded(g, left, dimY, left + nameWidth, dimY + 34, PANEL_ALT, 6);
        border(g, left, dimY, left + nameWidth, dimY + 34, CARD_BORDER);
        String selectedDimension = dimMode >= 0 ? DIM_LABELS[dimMode] : dimensionName(keepDimension);
        WaypointUiText.draw(g, font, fitText(selectedDimension, nameWidth - 42), left + 11, dimY + 11, TEXT, false);
        WaypointUiText.draw(g, font, "v", left + nameWidth - 19, dimY + 12, MUTED, false);
        WaypointUiText.draw(g, font, "Only visible when you're in this dimension.", left, dimY + locationOffset(44), QUIET, false);
        card(g, contentLeft, visibilityY, formWidth, visibilityHeight);
        WaypointUiText.draw(g, font, "Make it visible", contentLeft + 18, visibilityY + 20, TEXT, false);
        settingLine(g, mouseX, left, visibilityY + 58, formWidth - 36,
                "Show waypoint label", "Display the name and distance in your world.", labelEnabled);
        g.fill(left, visibilityY + 91, contentLeft + formWidth - 18, visibilityY + 92, CARD_BORDER);
        settingLine(g, mouseX, left, visibilityY + 104, formWidth - 36,
                "Show beacon beam", "A vertical beam to guide you from a distance.", beamEnabled);
        // Pop over the following card, as a native dropdown would, instead of
        // being clipped by the visibility panel when the dimension menu is open.
        if (dimensionMenuOpen) {
            int menuY = dimY + locationOffset(36);
            int optionHeight = locationOffset(24);
            rounded(g, left, menuY, left + nameWidth, menuY + DIM_LABELS.length * optionHeight, 0xFF070809, 7);
            border(g, left, menuY, left + nameWidth, menuY + DIM_LABELS.length * optionHeight, CARD_BORDER);
            for (int i = 0; i < DIM_LABELS.length; i++) {
                int optionY = menuY + i * optionHeight;
                if (i == dimMode) rounded(g, left + 2, optionY + 1, left + nameWidth - 2, optionY + optionHeight - 1, 0xFF202B1B, 5);
                WaypointUiText.draw(g, font, DIM_LABELS[i], left + 11, optionY + (optionHeight - WaypointUiText.LINE_HEIGHT) / 2, i == dimMode ? ACCENT : TEXT, false);
            }
        }

        // Right: color workstation with a broad square picker and horizontal hue strip.
        card(g, colorX, cardTop, colorWidth, colorCardHeight);
        int previewRgb = chroma
                ? WaypointsMod.resolveColor(new Waypoint("", 0, 0, 0, WaypointsMod.CHROMA, "", 0L, "", "W"))
                : currentRgb();
        WaypointUiText.draw(g, font, "Waypoint color", colorX + 16, cardTop + 21, TEXT, false);
        g.fill(colorX + colorWidth - 48, cardTop + 24, colorX + colorWidth - 43, cardTop + 29, ACCENT);
        String colorName = chroma ? "Chroma" : colorName(previewRgb);
        WaypointUiText.draw(g, font, colorName, colorX + colorWidth - WaypointUiText.width(font, colorName) - 18, cardTop + 21, MUTED, false);
        refreshPickerTexture();
        int pickerW = pickerWidth();
        g.blit(PICKER_TEXTURE_ID, pickerX(), pickerTop, pickerX() + pickerW, pickerTop + pickerHeight(),
                0.0f, 1.0f, 0.0f, 1.0f);
        border(g, pickerX(), pickerTop, pickerX() + pickerW, pickerTop + pickerHeight(), CARD_BORDER);
        int markX = pickerX() + Math.round(sat * (pickerW - 1));
        int markY = pickerTop + Math.round((1f - val) * (pickerHeight() - 1));
        ring(g, markX, markY);
        ensureHueTexture();
        g.blit(HUE_TEXTURE_ID, hueX(), hueY(), hueX() + pickerW, hueY() + HUE_DISPLAY_HEIGHT,
                0.0f, 1.0f, 0.0f, 1.0f);
        int hueMarkerX = hueX() + Math.round(hue * (pickerW - 1));
        g.fill(hueMarkerX - 1, hueY() - 2, hueMarkerX + 2, hueY() + HUE_DISPLAY_HEIGHT + 2, 0xFFFFFFFF);

        int rowY = hexRowY();
        rounded(g, preview, rowY, preview + 32, rowY + 34, 0xFF000000 | previewRgb, 6);
        int hexX = preview + 38;
        int hexW = Math.max(140, colorWidth - 58);
        drawBox(g, hexBox, hexX, rowY + 1, hexW, 32, "");
        WaypointUiText.draw(g, font, "HEX", hexX + 10, rowY + 12, QUIET, false);
        drawDropper(g, hexX + hexW - 18, rowY + 11, MUTED);

        int chromaRow = chromaY();
        rounded(g, preview, chromaRow, preview + colorWidth - 20, chromaRow + 46,
                chroma ? 0xFF1B2815 : PANEL_ALT, 7);
        if (chroma) border(g, preview, chromaRow, preview + colorWidth - 20, chromaRow + 46, ACCENT);
        int[] spectrum = {0xFFFF3030, 0xFFFFB52E, 0xFFFFFF38, 0xFF42E85A, 0xFF35C8FF, 0xFF765BFF, 0xFFE84DDB};
        for (int i = 0; i < spectrum.length; i++) {
            int sx = preview + 11 + i * 5;
            g.fill(sx, chromaRow + 9, sx + 4, chromaRow + 23, spectrum[i]);
        }
        WaypointUiText.draw(g, font, "CHROMA", preview + 52, chromaRow + 8, chroma ? ACCENT : TEXT, false);
        WaypointUiText.draw(g, font, "Cycle through the spectrum", preview + 11, chromaRow + 27, QUIET, false);
        toggle(g, preview + colorWidth - 70, chromaRow + 15, chroma);

        int recentY = recentLabelY();
        WaypointUiText.draw(g, font, "Recent colors", preview, recentY, MUTED, false);
        String resetLabel = "Reset";
        WaypointUiText.draw(g, font, resetLabel, colorX + colorWidth - 16 - WaypointUiText.width(font, resetLabel), recentY, MUTED, false);
        for (int i = 0; i < RECENT.size(); i++) {
            int sx = preview + i * 29;
            rrect(g, sx, recentColorsY(), sx + 23, recentColorsY() + 23, 0xFF000000 | RECENT.get(i));
            if (!chroma && RECENT.get(i) == currentRgb()) border(g, sx - 1, recentColorsY() - 1, sx + 24, recentColorsY() + 24, TEXT);
        }

        if (original != null) {
            int deleteY = actionY + 50;
            card(g, contentLeft, deleteY, formWidth, 66);
            WaypointUiText.draw(g, font, "Delete waypoint", contentLeft + 16, deleteY + 17, 0xFFE58A8A, false);
            WaypointUiText.draw(g, font, "Permanently remove this waypoint. This can't be undone.", contentLeft + 16, deleteY + 39, MUTED, false);
            actionButton(g, mouseX, mouseY + scrollOffset, formRight - 96, deleteY + 16, 78, 34, "Delete", false, true);
        }

        // Surface preview: sampled from loaded top-surface chunks, not underground blocks.
        int previewPanelY = cardTop + colorCardHeight + 42 + Math.round((height - 784) * 0.024f);
        WaypointUiText.draw(g, font, "IN-GAME PREVIEW", colorX, previewPanelY - 18, QUIET, false);
        int mapCenterX = parseCoordinate(xBox, Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.blockPosition().getX());
        int mapCenterZ = parseCoordinate(zBox, Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.blockPosition().getZ());
        String mapDimension = dimMode >= 0 && dimMode < DIM_IDS.length ? DIM_IDS[dimMode] : keepDimension;
        int mapHeight = Math.max(96, Math.min(190, Math.round(170 + (height - 784) * 0.135f)));
        int mapCenterY = parseCoordinate(yBox, Minecraft.getInstance().player == null ? 64 : Minecraft.getInstance().player.blockPosition().getY());
        String previewName = nameBox.getValue().isBlank() ? "Waypoint" : nameBox.getValue().trim();
        Minecraft previewMc = Minecraft.getInstance();
        int previewDistance = previewMc.player == null ? 0 : (int)Math.round(Math.sqrt(
                previewMc.player.distanceToSqr(mapCenterX + 0.5, mapCenterY + 0.5, mapCenterZ + 0.5)));
        String previewDetails = String.format(Locale.US, "%,d, %,d, %,d  ·  %dm",
                mapCenterX, mapCenterY, mapCenterZ, previewDistance);
        SurfaceMapPreview.draw(g, font, colorX, previewPanelY, colorWidth, mapHeight,
                mapCenterX, mapCenterZ, mapDimension, 0xFF000000 | previewRgb,
                previewName, previewDetails);
        var previewCaption = font.split(WaypointUiText.component("A clear marker, right where you need it. Your color is used for labels and beacon beams."), colorWidth);
        for (int i = 0; i < Math.min(2, previewCaption.size()); i++) {
            WaypointUiText.draw(g, font, previewCaption.get(i), colorX, previewPanelY + mapHeight + 8 + i * WaypointUiText.LINE_HEIGHT, QUIET, false);
        }
        if (original != null) WaypointUiText.draw(g, font, "CREATED IN THIS WORLD", colorX, previewPanelY + mapHeight + 62, QUIET, false);
        g.pose().popMatrix();
        g.disableScissor();

        int formActionY = actionY;
        if (original == null) {
            WaypointUiText.draw(g, font, "This waypoint is saved to Survival Realm and stored on this device. Your coordinates stay yours.",
                    contentLeft + 14, visibilityY + visibilityHeight + 29, QUIET, false);
            actionButton(g, mouseX, mouseY, formRight - 180, formActionY, 62, 31, "Cancel", false, false);
            actionButton(g, mouseX, mouseY, formRight - 118, formActionY, 118, 31, "+  Create waypoint", true, false);
        } else {
            actionButton(g, mouseX, mouseY, formRight - 236, formActionY, 112, 31, "Discard changes", false, false);
            actionButton(g, mouseX, mouseY, formRight - 116, formActionY, 116, 31, "Save changes", true, false);
        }
        if (!error.isEmpty()) WaypointUiText.draw(g, font, error, contentLeft, actionY - 18, 0xFFFF8585, false);

        if (maxScroll > 0) {
            int trackX = width - 8;
            int trackTop = scrollTop + 3;
            int trackHeight = scrollBottom - scrollTop - 6;
            int thumbHeight = Math.max(24, trackHeight * trackHeight / (trackHeight + maxScroll));
            int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
            rounded(g, trackX, trackTop, trackX + 2, trackTop + trackHeight, CARD_BORDER, 1);
            rounded(g, trackX, thumbY, trackX + 2, thumbY + thumbHeight, MUTED, 1);
        }

        // Native EditBoxes are rendered once and clipped to the scroll viewport.
        g.enableScissor(contentLeft, scrollTop, width - 12, scrollBottom);
        super.extractRenderState(g, mouseX, mouseY, delta);
        WaypointUiText.drawInput(g, font, nameBox, left + 12, nameY + 11 - scrollOffset, nameWidth - 28);
        WaypointUiText.drawInput(g, font, xBox, left + 12, coordsY + 11 - scrollOffset, coordinateWidth - 28);
        WaypointUiText.drawInput(g, font, yBox, left + coordinateWidth + 20, coordsY + 11 - scrollOffset, coordinateWidth - 28);
        WaypointUiText.drawInput(g, font, zBox, left + (coordinateWidth + 8) * 2 + 12, coordsY + 11 - scrollOffset, coordinateWidth - 28);
        WaypointUiText.drawInput(g, font, hexBox, preview + 127, hexRowY() + 10 - scrollOffset, Math.max(48, hexW - 145));
        g.disableScissor();
    }


    private String colorName(int rgb) {
        if (rgb == 0xB7F36B) return "Lime";
        return "Custom";
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
        int sideInset = railWidth > 170 ? 20 : 10;
        int labelX = sideInset + 10;
        int navWidth = railWidth - sideInset - 14;
        int brandX = railWidth > 170 ? 20 : 10;
        rrect(g, brandX, 15, brandX + 27, 42, ACCENT);
        drawBrandMark(g, brandX, 15);
        if (railWidth > 70) {
            int brandTextX = railWidth > 170 ? 56 : 44;
            WaypointUiText.draw(g, font, "Waypoint", brandTextX, 19, TEXT, false);
            WaypointUiText.draw(g, font, "M A N A G E R", brandTextX, 31, MUTED, false);
            rrect(g, sideInset, 65, railWidth - 14, 115, CARD);
            WaypointUiText.draw(g, font, fitText(worldTitle(), railWidth - 48), labelX, 76, TEXT, false);
            g.fill(labelX, 98, labelX + 4, 102, ACCENT);
            WaypointUiText.draw(g, font, "Java Edition | 26.3", labelX + 10, 96, MUTED, false);
            WaypointUiText.draw(g, font, "WORKSPACE", sideInset, 144, QUIET, false);
            nav(g, mx, my, sideInset, 153, navWidth, "", "Waypoints", true);
            nav(g, mx, my, sideInset, 194, navWidth, "", "Map", false);
            nav(g, mx, my, sideInset, 235, navWidth, "", "Settings", false);
            int footerTop = height >= 880 ? height - 186 : height >= 830 ? height - 159 : height - 162;
            int footerBottom = height >= 880 ? height - 75 : height >= 830 ? height - 59 : height - 62;
            rrect(g, sideInset, footerTop, railWidth - 14, footerBottom, PANEL_ALT);
            WaypointUiText.draw(g, font, "Your world. Your waypoints.", labelX, footerTop + 31, TEXT, false);
            WaypointUiText.draw(g, font, "Stored locally, ready for", labelX, footerTop + 51, MUTED, false);
            WaypointUiText.draw(g, font, "your next adventure.", labelX, footerTop + 66, MUTED, false);
            g.fill(labelX, footerBottom - 21, labelX + 5, footerBottom - 16, ACCENT);
            WaypointUiText.draw(g, font, "All changes saved", labelX + 11, footerBottom - 22, MUTED, false);
            int profileTop = footerBottom + 25;
            rrect(g, sideInset, profileTop, sideInset + 28, profileTop + 28, CARD_HOVER);
            WaypointUiText.draw(g, font, "AL", sideInset + 7, profileTop + 9, TEXT, false);
            WaypointUiText.draw(g, font, Minecraft.getInstance().player == null ? "Player" : Minecraft.getInstance().player.getName().getString(), labelX + 27, profileTop + 6, TEXT, false);
            WaypointUiText.draw(g, font, "In-game", labelX + 27, profileTop + 18, MUTED, false);
            for (int dot = 0; dot < 3; dot++) g.fill(railWidth - 27 + dot * 3, profileTop + 13, railWidth - 25 + dot * 3, profileTop + 15, MUTED);
        } else {
            nav(g, mx, my, 8, 104, 38, "", "", false);
            nav(g, mx, my, 8, 150, 38, "", "", false);
        }
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
        while (true) { g.fill(x, y, x + 1, y + 1, color); if (x == x2 && y == y2) break; int e2 = 2 * err; if (e2 >= dy) { err += dy; x += sx; } if (e2 <= dx) { err += dx; y += sy; } }
    }

    private void nav(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String icon, String label, boolean active) {
        if (active || inside(mx, my, x, y, w, 32)) rrect(g, x, y, x + w, y + 32, 0xFF202629);
        int tint = active ? ACCENT : MUTED;
        if ("Waypoints".equals(label) || label.isEmpty()) drawPinGlyph(g, x + 11, y + 9, tint);
        else if ("Map".equals(label)) drawPinGlyph(g, x + 11, y + 9, tint);
        else drawSlidersGlyph(g, x + 10, y + 10, tint);
        if (!label.isEmpty()) WaypointUiText.draw(g, font, label, x + 30, y + 10, active ? TEXT : MUTED, false);
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
                enabled ? ACCENT : 0xFF9BA4A8);
    }

    private void actionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h,
                              String label, boolean primary, boolean danger) {
        boolean hover = inside(mx, my, x, y, w, h);
        int fill = primary ? (hover ? 0xFFBDE39A : ACCENT)
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
        int r = Math.min(radius, Math.min(w / 2, h / 2));
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

        int radius = Math.min(7, Math.min(width / 2, height / 2));
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
