package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.Consumer;

public class SettingSlider extends AbstractWidget {
    private static final int HANDLE_W = 8;

    private final String label;
    private final String suffix;
    private final float min;
    private final float range;
    private final boolean percent;
    private final Consumer<Float> onChange;
    private final DecimalFormat format;
    private final double startValue;

    private double value;
    private boolean dragging;

    public SettingSlider(int x, int y, int width, int height, String label, float rawInit,
                         float min, float max, int decimals, String suffix, Consumer<Float> onChange) {
        super(x, y, width, height, Component.literal(label));
        this.label = label;
        this.min = min;
        this.range = max - min;
        this.suffix = suffix;
        this.percent = "%".equals(suffix);
        this.onChange = onChange;
        this.format = new DecimalFormat("0" + (decimals > 0 ? "." + "0".repeat(decimals) : ""),
                new DecimalFormatSymbols(Locale.ROOT));
        this.value = Mth.clamp((rawInit - min) / this.range, 0f, 1f);
        this.startValue = this.value;
    }

    private int trackSpan() { return Math.max(1, getWidth() - HANDLE_W); }

    private float raw() { return min + (float) value * range; }

    private String readout() {
        return format.format(percent ? raw() * 100f : raw()) + suffix;
    }

    private void setValue(double next) {
        double clamped = Mth.clamp(next, 0.0D, 1.0D);
        if (clamped == value) return;
        value = clamped;
        onChange.accept(raw());
    }

    private void setFromMouse(double mouseX) {
        setValue((mouseX - (getX() + HANDLE_W / 2.0D)) / trackSpan());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!active || !visible || !isMouseOver(mx, my)) return false;
        if (button == 1) {
            setValue(startValue);
            return true;
        }
        if (button != 0) return false;
        dragging = true;
        setFromMouse(mx);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return false;
        setFromMouse(mx);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!dragging) return false;
        dragging = false;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        boolean left = key == 263;
        if (!left && key != 262) return false;
        setValue(value + (left ? -1.0D : 1.0D) / trackSpan());
        return true;
    }

    @Override
    public void playDownSound(@NonNull SoundManager sounds) {}

    @Override
    protected void renderWidget(@NonNull GuiGraphics g, int mx, int my, float pt) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = isHovered || dragging;

        AtmosphereUi.sliderTrack(g, x, y, w, h);
        AtmosphereUi.sliderHandle(g, x + (int) (value * trackSpan()), y, h, hot);
        if (isFocused()) AtmosphereUi.focusRing(g, x, y, w, h);

        Minecraft mc = Minecraft.getInstance();
        AtmosphereUi.pair(g, mc.font, x + w / 2, y + (h - 8) / 2,
                AtmosphereUi.fit(mc.font, label, w - 90), readout(),
                hot ? AtmosphereUi.TEXT : AtmosphereUi.TEXT_DIM, AtmosphereUi.VALUE);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.literal(label + " " + readout()));
    }
}
