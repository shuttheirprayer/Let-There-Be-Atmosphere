package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.NonNull;

import java.util.function.IntConsumer;

@OnlyIn(Dist.CLIENT)
public class ColorPickerScreen extends Screen {
    private static final int PANEL_W = 326, PANEL_H = 248;
    private static final int SV_SIZE = 140, HUE_W = 14, CELL = 21, COLS = 6;

    private static final int[] PRESETS = {
            0xFFFFFF, 0xC8C8C8, 0x8C8C8C, 0x505050, 0x1E1E1E, 0x000000,
            0xFF5555, 0xFF8A3C, 0xFFE45C, 0x7FE05C, 0x3FD9C9, 0x5599FF,
            0x8855FF, 0xFF66CC, 0xB86A3C, 0x6D8C4A, 0x3F76E4, 0x99BBFF,
            0x88BFEA, 0xE8A060, 0x1A1E36, 0x7FB238, 0x59AE30, 0x445566
    };

    private final Screen parent;
    private final IntConsumer onChange;
    private final int initial;
    private final int fallback;
    private final boolean allowAuto;
    private final float[] hsvBuf = new float[3];

    private float hue, sat, val;
    private boolean auto;
    private int drag;
    private boolean syncing;
    private int left, top;

    private EditBox hexBox, rBox, gBox, bBox;
    private Button autoButton;

    public ColorPickerScreen(Screen parent, Component title, int value, int fallback,
                             boolean allowAuto, IntConsumer onChange) {
        super(title);
        this.parent = parent;
        this.onChange = onChange;
        this.initial = value;
        this.fallback = fallback;
        this.allowAuto = allowAuto;
        this.auto = allowAuto && value < 0;
        setFromRgb(value >= 0 ? value : fallback);
    }

    public static void playUi(Holder<SoundEvent> sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;

        int fy = top + 186;
        hexBox = addRenderableWidget(new EditBox(font, left + 14, fy, 74, 18, Component.empty()));
        hexBox.setMaxLength(7);
        hexBox.setFilter(s -> s.matches("#?[0-9a-fA-F]{0,6}"));
        hexBox.setResponder(s -> {
            if (syncing) return;
            int rgb = ColorMath.parseHex(s);
            if (rgb >= 0) { setFromRgb(rgb); push(); }
        });

        rBox = channelBox(left + 96, fy, 0);
        gBox = channelBox(left + 144, fy, 1);
        bBox = channelBox(left + 192, fy, 2);

        int by = top + PANEL_H - 26;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            playUi(SoundEvents.NOTE_BLOCK_BELL, 1.4f);
            close();
        }).bounds(left + 14, by, 94, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            onChange.accept(initial);
            close();
        }).bounds(left + 112, by, 94, 20).build());

        if (allowAuto) {
            autoButton = addRenderableWidget(Button.builder(Component.literal(autoLabel()), b -> toggleAuto())
                    .bounds(left + 210, by, 102, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Undo"), b -> {
                setFromRgb(initial);
                push();
            }).bounds(left + 210, by, 102, 20).build());
        }

        syncFields();
    }

    private EditBox channelBox(int x, int y, int index) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, 42, 18, Component.empty()));
        box.setMaxLength(3);
        box.setFilter(s -> s.matches("[0-9]{0,3}"));
        box.setResponder(s -> {
            if (syncing || s.isEmpty()) return;
            try {
                setFromRgb(ColorMath.withChannel(currentRgb(), index, Integer.parseInt(s)));
                push();
            } catch (NumberFormatException ignored) {}
        });
        return box;
    }

    private int currentRgb() { return ColorMath.hsvToRgb(hue, sat, val); }

    private void setFromRgb(int rgb) {
        ColorMath.rgbToHsv(rgb, hsvBuf);
        if (hsvBuf[1] > 0f) hue = hsvBuf[0];
        if (hsvBuf[2] > 0f) sat = hsvBuf[1];
        val = hsvBuf[2];
    }

    private void push() {
        auto = false;
        if (autoButton != null) autoButton.setMessage(Component.literal(autoLabel()));
        onChange.accept(currentRgb());
        syncFields();
    }

    private void toggleAuto() {
        auto = !auto;
        if (auto) setFromRgb(fallback);
        autoButton.setMessage(Component.literal(autoLabel()));
        onChange.accept(auto ? -1 : currentRgb());
        syncFields();
    }

    private String autoLabel() { return auto ? "Default: on" : "Use Default"; }

    private void syncFields() {
        int c = currentRgb();
        syncing = true;
        hexBox.setValue(ColorMath.hex(c));
        rBox.setValue(String.valueOf(ColorMath.channel(c, 0)));
        gBox.setValue(String.valueOf(ColorMath.channel(c, 1)));
        bBox.setValue(String.valueOf(ColorMath.channel(c, 2)));
        syncing = false;
    }

    private void close() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() { close(); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(@NonNull GuiGraphics g, int mx, int my, float pt) {
        int c = currentRgb();

        AtmosphereUi.surface(g, left, top, PANEL_W, PANEL_H, AtmosphereUi.PANEL);
        AtmosphereUi.bevel(g, left, top, PANEL_W, PANEL_H);
        AtmosphereUi.separator(g, left + 1, top + 24, PANEL_W - 2);
        g.drawString(font, title, left + 14, top + 8, AtmosphereUi.TEXT, true);

        int sx = left + 14, sy = top + 34;
        for (int i = 0; i < SV_SIZE; i++) {
            int col = ColorMath.hsvToRgb(hue, i / (float) (SV_SIZE - 1), 1f);
            g.fillGradient(sx + i, sy, sx + i + 1, sy + SV_SIZE, 0xFF000000 | col, 0xFF000000);
        }
        AtmosphereUi.inset(g, sx - 1, sy - 1, SV_SIZE + 2, SV_SIZE + 2);

        int px = sx + (int) (sat * (SV_SIZE - 1));
        int py = sy + (int) ((1f - val) * (SV_SIZE - 1));
        g.renderOutline(px - 4, py - 4, 9, 9, 0xFF000000);
        g.renderOutline(px - 3, py - 3, 7, 7, 0xFFFFFFFF);

        int hx = sx + SV_SIZE + 8;
        for (int i = 0; i < SV_SIZE; i++) {
            g.fill(hx, sy + i, hx + HUE_W, sy + i + 1,
                    0xFF000000 | ColorMath.hsvToRgb(i / (float) (SV_SIZE - 1), 1f, 1f));
        }
        AtmosphereUi.inset(g, hx - 1, sy - 1, HUE_W + 2, SV_SIZE + 2);
        int hy = sy + (int) (hue * (SV_SIZE - 1));
        g.fill(hx - 3, hy - 1, hx + HUE_W + 3, hy + 2, 0xFFFFFFFF);

        int gx = hx + HUE_W + 12, gy = sy;
        for (int i = 0; i < PRESETS.length; i++) {
            int cx = gx + (i % COLS) * CELL, cy = gy + (i / COLS) * CELL;
            boolean hover = mx >= cx && mx < cx + CELL - 3 && my >= cy && my < cy + CELL - 3;
            g.fill(cx, cy, cx + CELL - 3, cy + CELL - 3, 0xFF000000 | PRESETS[i]);
            g.renderOutline(cx, cy, CELL - 3, CELL - 3, hover ? 0xFFFFFFFF : 0xFF181818);
        }

        int vy = gy + ((PRESETS.length + COLS - 1) / COLS) * CELL + 6;
        int vw = COLS * CELL - 3;
        g.drawString(font, "Before", gx, vy, AtmosphereUi.TEXT_SOFT, true);
        g.drawString(font, "After", gx + vw / 2 + 4, vy, AtmosphereUi.TEXT_SOFT, true);
        AtmosphereUi.swatch(g, gx, vy + 10, vw / 2 - 2, 26, initial);
        AtmosphereUi.swatch(g, gx + vw / 2 + 2, vy + 10, vw / 2 - 2, 26, auto ? -1 : c);

        int fy = top + 176;
        g.drawString(font, "Hex", left + 14, fy, AtmosphereUi.TEXT_DIM, true);
        g.drawString(font, "Red", left + 96, fy, 0xFFFF9A9A, true);
        g.drawString(font, "Green", left + 144, fy, 0xFF9AFF9A, true);
        g.drawString(font, "Blue", left + 192, fy, 0xFF9AAAFF, true);

        if (auto) {
            g.drawString(font, "Using the game default for this one.",
                    left + 14, top + PANEL_H - 38, AtmosphereUi.TEXT_SOFT, true);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        int sx = left + 14, sy = top + 34;
        if (inside(mx, my, sx, sy, SV_SIZE, SV_SIZE)) { drag = 1; applySv(mx, my); return true; }

        int hx = sx + SV_SIZE + 8;
        if (inside(mx, my, hx, sy, HUE_W, SV_SIZE)) { drag = 2; applyHue(my); return true; }

        int gx = hx + HUE_W + 12;
        for (int i = 0; i < PRESETS.length; i++) {
            int cx = gx + (i % COLS) * CELL, cy = sy + (i / COLS) * CELL;
            if (inside(mx, my, cx, cy, CELL - 3, CELL - 3)) {
                setFromRgb(PRESETS[i]);
                push();
                playUi(SoundEvents.NOTE_BLOCK_HAT, 1.6f);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag == 1) { applySv(mx, my); return true; }
        if (drag == 2) { applyHue(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drag != 0) {
            drag = 0;
            playUi(SoundEvents.NOTE_BLOCK_HAT, 0.8f + hue);
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void applySv(double mx, double my) {
        int sx = left + 14, sy = top + 34;
        sat = clamp01((float) (mx - sx) / (SV_SIZE - 1));
        val = 1f - clamp01((float) (my - sy) / (SV_SIZE - 1));
        push();
    }

    private void applyHue(double my) {
        hue = clamp01((float) (my - (top + 34)) / (SV_SIZE - 1));
        push();
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }
}
