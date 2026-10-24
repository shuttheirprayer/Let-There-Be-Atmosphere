package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.function.Supplier;

public class ActionRow extends AbstractButton {
    private final Supplier<String> label;
    private final Supplier<String> value;
    private final Runnable onPress;

    public ActionRow(int x, int y, int width, int height, Supplier<String> label, Supplier<String> value,
                     Runnable onPress) {
        super(x, y, width, height, Component.literal(label.get()));
        this.label = label;
        this.value = value;
        this.onPress = onPress;
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(@NonNull GuiGraphics g, int mx, int my, float pt) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        AtmosphereUi.widget(g, x, y, w, h, isHovered);
        if (isFocused()) AtmosphereUi.focusRing(g, x, y, w, h);

        Minecraft mc = Minecraft.getInstance();
        AtmosphereUi.pair(g, mc.font, x + w / 2, y + (h - 8) / 2,
                AtmosphereUi.fit(mc.font, label.get(), w - 60),
                value == null ? null : value.get(),
                isHovered ? AtmosphereUi.TEXT : AtmosphereUi.TEXT_DIM, AtmosphereUi.VALUE);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.literal(label.get()));
    }
}
