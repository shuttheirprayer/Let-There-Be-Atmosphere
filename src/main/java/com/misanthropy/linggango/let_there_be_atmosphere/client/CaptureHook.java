package com.misanthropy.linggango.let_there_be_atmosphere.client;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT)
public final class CaptureHook {
    private CaptureHook() {}

    private static final Path SCRIPT = Path.of("lta_capture.txt");
    private static List<String> steps;
    private static int index;
    private static int waiting;
    private static String pin;

    private static int burst;
    private static int burstIndex;
    private static String burstName;
    private static long ticks;
    private static long seenTicks;

    @SubscribeEvent
    public static void onCaptureHookFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || burst <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        boolean ticked = ticks != seenTicks;
        seenTicks = ticks;
        Screenshot.grab(mc.gameDirectory, String.format("%s_%03d.png", burstName, burstIndex), mc.getMainRenderTarget(), c -> {});
        float[] w = com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindSway.WIND;
        float[] g = com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindSway.GUST;
        Diagnostics.LOG.info("[capture] frame {} {} ticked {} pt {} wind {} {} {} {} gust {} {} flow {} storm {} uploads {} tagged {}", burstName, burstIndex, ticked, String.format("%.3f", event.renderTickTime),
                w[0], w[1], w[2], w[3], g[0], g[3], com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindSway.FLOW[0],
                com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindSway.CAMERA[3], com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindSway.uploads, com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindSway.tagged);
        com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindSway.uploads = 0;
        burstIndex++;
        burst--;
    }

    private static boolean skyTexture;
    private static com.mojang.blaze3d.pipeline.TextureTarget skyTarget;
    private static int skyTick = Integer.MIN_VALUE;

    public static void onCaptureHookStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (!skyTexture || event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.tickCount == skyTick) return;
        skyTick = mc.player.tickCount;
        if (skyTarget == null) skyTarget = new com.mojang.blaze3d.pipeline.TextureTarget(mc.getWindow().getWidth(), mc.getWindow().getHeight(), true, Minecraft.ON_OSX);
        skyTarget.bindWrite(true);
        com.mojang.blaze3d.systems.RenderSystem.clear(16640, Minecraft.ON_OSX);
        net.minecraft.world.phys.Vec3 camera = event.getCamera().getPosition();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        mc.levelRenderer.renderClouds(event.getPoseStack(), event.getProjectionMatrix(), event.getPartialTick(), camera.x, camera.y, camera.z);
        mc.getMainRenderTarget().bindWrite(true);
    }

    public static void onCaptureHookTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (steps == null) {
            try {
                steps = Files.exists(SCRIPT) ? Files.readAllLines(SCRIPT) : new ArrayList<>();
            } catch (Exception e) {
                steps = new ArrayList<>();
            }
        }
        if (steps.isEmpty() || mc.level == null || mc.player == null) return;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        mc.options.hideGui = true;
        if (pin != null) run(server, pin);
        if (waiting > 0) { waiting--; return; }
        while (index < steps.size()) {
            String line = steps.get(index++).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int sp = line.indexOf(' ');
            String op = sp < 0 ? line : line.substring(0, sp);
            String arg = sp < 0 ? "" : line.substring(sp + 1).trim();
            switch (op) {
                case "once" -> run(server, arg);
                case "log" -> server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), arg));
                case "pin" -> pin = arg.equals("-") ? null : arg;
                case "wait" -> { waiting = Integer.parseInt(arg); return; }
                case "set" -> set(arg);
                case "grab" -> {
                    Screenshot.grab(mc.gameDirectory, arg + ".png", mc.getMainRenderTarget(), c -> {});
                    Diagnostics.LOG.info("[capture] grabbed {}", arg);
                }
                case "fps" -> Diagnostics.LOG.info("[capture] fps {} {}", arg, mc.getFps());
                case "fpscap" -> {
                    mc.options.enableVsync().set(false);
                    mc.options.framerateLimit().set(Integer.parseInt(arg));
                }
                case "skytex" -> skyTexture = arg.equals("on");
                case "burst" -> {
                    String[] parts = arg.split(" ");
                    burstName = parts[1];
                    burstIndex = 0;
                    burst = Integer.parseInt(parts[0]);
                }
                case "birds" -> com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Birds.capture(arg);
                case "ripple" -> {
                    String[] r = arg.split(" ");
                    com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Ripples.emit(Double.parseDouble(r[0]), Double.parseDouble(r[1]), Double.parseDouble(r[2]), Float.parseFloat(r[3]), 1F);
                }
                case "splash" -> {
                    String[] r = arg.split(" ");
                    com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Wake.splash(Double.parseDouble(r[0]), Double.parseDouble(r[1]), Double.parseDouble(r[2]), Float.parseFloat(r[3]), 1F);
                }
                case "storm" -> com.misanthropy.linggango.let_there_be_atmosphere.client.fx.NetherDust.startStorm();
                case "lava" -> com.misanthropy.linggango.let_there_be_atmosphere.client.fx.NetherFx.capture(arg);
                case "wind" -> com.misanthropy.linggango.let_there_be_atmosphere.client.fx.Wind.pin(arg.equals("off") ? Float.NaN : Float.parseFloat(arg));
                case "close" -> mc.setScreen(null);
                case "freeze" -> com.misanthropy.linggango.let_there_be_atmosphere.client.fx.WindSway.frozen = arg.equals("on");
                case "quit" -> { mc.stop(); return; }
                default -> Diagnostics.LOG.warn("[capture] unknown step {}", line);
            }
        }
    }

    private static final java.util.Map<String, long[]> PROBES = new java.util.HashMap<>();

    public static void probeBegin(String name) {
        if (steps == null || steps.isEmpty()) return;
        long[] p = PROBES.computeIfAbsent(name, k -> {
            long[] a = new long[4 + 3];
            for (int i = 0; i < 4; i++) a[i] = org.lwjgl.opengl.GL15.glGenQueries();
            return a;
        });
        int slot = (int) (p[4] % 4);
        if (p[4] >= 4) {
            long ns = org.lwjgl.opengl.GL33.glGetQueryObjecti64((int) p[slot], org.lwjgl.opengl.GL15.GL_QUERY_RESULT);
            p[5] += ns;
            p[6]++;
            if (p[6] == 600) {
                Diagnostics.LOG.info("[capture] probe {} avg {} us", name, String.format("%.1f", p[5] / 600.0 / 1000.0));
                p[5] = 0;
                p[6] = 0;
            }
        }
        org.lwjgl.opengl.GL15.glBeginQuery(org.lwjgl.opengl.GL33.GL_TIME_ELAPSED, (int) p[slot]);
    }

    public static void probeEnd(String name) {
        long[] p = PROBES.get(name);
        if (p == null) return;
        org.lwjgl.opengl.GL15.glEndQuery(org.lwjgl.opengl.GL33.GL_TIME_ELAPSED);
        p[4]++;
    }

    private static void run(MinecraftServer server, String command) {
        server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), command));
    }

    private static void set(String kv) {
        try {
            Path path = Path.of("config", "let_there_be_atmosphere", "settings.properties");
            String key = kv.substring(0, kv.indexOf('='));
            List<String> lines = new ArrayList<>(Files.readAllLines(path));
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(key + "=")) { lines.set(i, kv); found = true; }
            }
            if (!found) lines.add(kv);
            Files.write(path, lines);
            DynamicFogHandler.load();
            Diagnostics.LOG.info("[capture] set {}", kv);
        } catch (Exception e) {
            Diagnostics.LOG.error("[capture] set failed {}", kv, e);
        }
    }
}
