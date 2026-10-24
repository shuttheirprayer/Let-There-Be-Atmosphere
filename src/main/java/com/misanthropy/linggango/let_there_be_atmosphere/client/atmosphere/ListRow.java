package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.function.BooleanSupplier;

public class ListRow extends AbstractButton {
    private final String label;
    private final BooleanSupplier picked;
    private final Runnable onPress;

    public ListRow(int x, int y, int width, int height, String label,
                   BooleanSupplier picked, Runnable onPress) {
        super(x, y, width, height, Component.literal(label));
        this.label = label;
        this.picked = picked;
        this.onPress = onPress;
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(@NonNull GuiGraphics g, int mx, int my, float pt) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean on = picked.getAsBoolean();
        AtmosphereUi.widget(g, x, y, w, h, isHovered);
        if (isFocused()) AtmosphereUi.focusRing(g, x, y, w, h);

        AtmosphereUi.checkbox(g, x + 3, y + (h - 20) / 2, on);

        Minecraft mc = Minecraft.getInstance();
        g.drawCenteredString(mc.font, AtmosphereUi.fit(mc.font, label, w - 52), x + w / 2 + 11,
                y + (h - 8) / 2, on || isHovered ? AtmosphereUi.TEXT : AtmosphereUi.TEXT_DIM);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.literal(label));
    }
}
