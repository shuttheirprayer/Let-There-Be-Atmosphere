package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.NonNull;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ToggleRow extends AbstractButton {
    private final String label;
    private final BooleanSupplier get;
    private final Consumer<Boolean> set;

    public ToggleRow(int x, int y, int width, int height, String label,
                     BooleanSupplier get, Consumer<Boolean> set) {
        super(x, y, width, height, Component.literal(label));
        this.label = label;
        this.get = get;
        this.set = set;
    }

    @Override
    public void onPress() {
        boolean next = !get.getAsBoolean();
        set.accept(next);
        ColorPickerScreen.playUi(SoundEvents.NOTE_BLOCK_HAT, next ? 1.6f : 1.1f);
    }

    @Override
    protected void renderWidget(@NonNull GuiGraphics g, int mx, int my, float pt) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean on = get.getAsBoolean();
        AtmosphereUi.widget(g, x, y, w, h, isHovered);
        if (isFocused()) AtmosphereUi.focusRing(g, x, y, w, h);

        Minecraft mc = Minecraft.getInstance();
        AtmosphereUi.pair(g, mc.font, x + w / 2, y + (h - 8) / 2,
                AtmosphereUi.fit(mc.font, label, w - 50), on ? "On" : "Off",
                isHovered ? AtmosphereUi.TEXT : AtmosphereUi.TEXT_DIM,
                on ? AtmosphereUi.ON : AtmosphereUi.OFF);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.literal(label + ": " + (get.getAsBoolean() ? "on" : "off")));
    }
}
