package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class AtmosphereCommands {
    private AtmosphereCommands() {}

    @SubscribeEvent
    public static void onAtmosphereCommandsRegister(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("atmosphere");
        root.then(Commands.literal("netherstorm")
                .then(Commands.literal("start").executes(c -> storm(1)))
                .then(Commands.literal("stop").executes(c -> storm(0)))
                .then(Commands.literal("toggle").executes(c -> storm(2))));
        dispatcher.register(root);
    }

    private static int storm(int mode) {
        Minecraft mc = Minecraft.getInstance();
        if (!NetherAir.here(mc.level)) return say("Nether storms only blow in the Nether and other ultra warm dimensions.");
        if (!FxConfig.netherStorms) return say("Nether Storms are switched off. Turn them on in the Nether page of the settings first.");
        boolean start = mode == 1 || (mode == 2 && !NetherDust.stormActive());
        if (start) {
            NetherDust.startStorm();
            return say("The Nether storm rolls in.");
        }
        NetherDust.stopStorm();
        return say("The Nether storm is dying down.");
    }

    private static int say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
        return 1;
    }
}
