package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ColorRowButton extends AbstractButton {
    private final Screen parent;
    private final String name;
    private final IntSupplier get;
    private final IntConsumer set;
    private final boolean allowAuto;
    private final int fallback;

    public ColorRowButton(Screen parent, int x, int y, int width, int height, String name,
                          IntSupplier get, IntConsumer set, boolean allowAuto, int fallback) {
        super(x, y, width, height, Component.literal(name));
        this.parent = parent;
        this.name = name;
        this.get = get;
        this.set = set;
        this.allowAuto = allowAuto;
        this.fallback = fallback;
    }

    @Override
    public void onPress() {
        Minecraft.getInstance().setScreen(new ColorPickerScreen(
                parent, Component.literal(name), get.getAsInt(), fallback, allowAuto, set));
    }

    @Override
    protected void renderWidget(@NonNull GuiGraphics g, int mx, int my, float pt) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int color = get.getAsInt();
        AtmosphereUi.widget(g, x, y, w, h, isHovered);
        if (isFocused()) AtmosphereUi.focusRing(g, x, y, w, h);

        int size = h - 8;
        AtmosphereUi.swatch(g, x + w - size - 6, y + 4, size, color);

        Minecraft mc = Minecraft.getInstance();
        AtmosphereUi.pair(g, mc.font, x + (w - size) / 2, y + (h - 8) / 2,
                AtmosphereUi.fit(mc.font, name, w - 90),
                color < 0 ? "Default" : ColorMath.hex(color),
                isHovered ? AtmosphereUi.TEXT : AtmosphereUi.TEXT_DIM,
                color < 0 ? AtmosphereUi.TEXT_SOFT : AtmosphereUi.VALUE);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.literal(name));
    }
}
