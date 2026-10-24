package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import com.misanthropy.linggango.let_there_be_atmosphere.Let_there_be_atmosphere;
import com.misanthropy.linggango.let_there_be_atmosphere.client.Diagnostics;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public final class StormBolt extends EntityRenderer<LightningBolt> {
    private static final int SEGMENTS = 18;
    private static final float HEIGHT = 150F;
    private static final float[] PATH = new float[(SEGMENTS + 1) * 3];
    private static final float[] FORK = new float[10 * 3];
    private static final Vector3f SIDE = new Vector3f();
    private static final Vector3f VIEW = new Vector3f();

    private final LightningBoltRenderer vanilla;

    private StormBolt(EntityRendererProvider.Context context) {
        super(context);
        vanilla = new LightningBoltRenderer(context);
    }

    @Override
    public void render(LightningBolt bolt, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!FxConfig.stormBolts || !DynamicFogHandler.active()) {
            vanilla.render(bolt, yaw, partialTick, pose, buffers, light);
            return;
        }
        Diagnostics.once("bright bolts replaced a vanilla lightning bolt");
        RandomSource random = RandomSource.create(bolt.seed);
        float x = 0F, z = 0F;
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = i / (float) SEGMENTS;
            PATH[i * 3] = x;
            PATH[i * 3 + 1] = t * t * HEIGHT * 0.35F + t * HEIGHT * 0.65F;
            PATH[i * 3 + 2] = z;
            float step = 1.2F + 3.4F * t;
            x += (random.nextFloat() - 0.5F) * 2F * step;
            z += (random.nextFloat() - 0.5F) * 2F * step;
        }
        Vec3 eye = entityRenderDispatcher.camera.getPosition();
        Vec3 at = bolt.getPosition(partialTick);
        VIEW.set((float) (eye.x - at.x), (float) (eye.y - at.y), (float) (eye.z - at.z));
        float distance = VIEW.length();
        float grow = Math.max(1F, distance / 70F);
        float pulse = Mth.clamp(0.45F + Lightning.flashOrZero() * 0.9F, 0.45F, 1.3F);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer out = buffers.getBuffer(Glow.BOLT);
        chain(out, matrix, PATH, SEGMENTS + 1, grow, pulse);
        int forks = 2 + random.nextInt(3);
        for (int f = 0; f < forks; f++) {
            int from = 4 + random.nextInt(SEGMENTS - 6);
            int length = 3 + random.nextInt(6);
            float fx = PATH[from * 3], fy = PATH[from * 3 + 1], fz = PATH[from * 3 + 2];
            float angle = random.nextFloat() * Mth.TWO_PI;
            float dx = Mth.cos(angle), dz = Mth.sin(angle);
            for (int i = 0; i <= length; i++) {
                FORK[i * 3] = fx;
                FORK[i * 3 + 1] = fy;
                FORK[i * 3 + 2] = fz;
                fx += dx * (2.5F + random.nextFloat() * 3F) + (random.nextFloat() - 0.5F) * 3F;
                fy -= 3F + random.nextFloat() * 5F;
                fz += dz * (2.5F + random.nextFloat() * 3F) + (random.nextFloat() - 0.5F) * 3F;
            }
            chain(out, matrix, FORK, length + 1, grow * 0.6F, pulse * 0.75F);
        }
    }

    private static void chain(VertexConsumer out, Matrix4f matrix, float[] points, int count, float grow, float pulse) {
        for (int i = 0; i + 1 < count; i++) {
            float ax = points[i * 3], ay = points[i * 3 + 1], az = points[i * 3 + 2];
            float bx = points[i * 3 + 3], by = points[i * 3 + 4], bz = points[i * 3 + 5];
            SIDE.set(bx - ax, by - ay, bz - az).cross(VIEW.x - ax, VIEW.y - ay, VIEW.z - az);
            if (SIDE.lengthSquared() < 1.0E-6F) continue;
            SIDE.normalize();
            ribbon(out, matrix, ax, ay, az, bx, by, bz, 2.4F * grow, 0.42F, 0.5F, 1F, 0.32F * pulse);
            ribbon(out, matrix, ax, ay, az, bx, by, bz, 0.8F * grow, 0.72F, 0.8F, 1F, 0.7F * pulse);
            ribbon(out, matrix, ax, ay, az, bx, by, bz, 0.28F * grow, 1F, 1F, 1F, pulse);
        }
    }

    private static void ribbon(VertexConsumer out, Matrix4f matrix, float ax, float ay, float az, float bx, float by, float bz,
                               float width, float r, float g, float b, float alpha) {
        float sx = SIDE.x * width * 0.5F, sy = SIDE.y * width * 0.5F, sz = SIDE.z * width * 0.5F;
        float a = Math.min(alpha, 1F);
        out.vertex(matrix, ax - sx, ay - sy, az - sz).color(r, g, b, 0F).endVertex();
        out.vertex(matrix, ax, ay, az).color(r, g, b, a).endVertex();
        out.vertex(matrix, bx, by, bz).color(r, g, b, a).endVertex();
        out.vertex(matrix, bx - sx, by - sy, bz - sz).color(r, g, b, 0F).endVertex();
        out.vertex(matrix, ax, ay, az).color(r, g, b, a).endVertex();
        out.vertex(matrix, ax + sx, ay + sy, az + sz).color(r, g, b, 0F).endVertex();
        out.vertex(matrix, bx + sx, by + sy, bz + sz).color(r, g, b, 0F).endVertex();
        out.vertex(matrix, bx, by, bz).color(r, g, b, a).endVertex();
    }

    @Override
    public boolean shouldRender(LightningBolt bolt, Frustum frustum, double x, double y, double z) {
        if (!FxConfig.stormBolts || !DynamicFogHandler.active()) return super.shouldRender(bolt, frustum, x, y, z);
        double dx = bolt.getX() - x, dz = bolt.getZ() - z;
        return dx * dx + dz * dz < 256.0 * 256.0 && frustum.isVisible(new AABB(bolt.getX() - 24.0, bolt.getY(), bolt.getZ() - 24.0,
                bolt.getX() + 24.0, bolt.getY() + HEIGHT, bolt.getZ() + 24.0));
    }

    @Override
    public ResourceLocation getTextureLocation(LightningBolt bolt) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private abstract static class Glow extends RenderType {
        private static final float[] FOG = new float[2];

        static final RenderType BOLT = create("let_there_be_atmosphere_bolt", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                256, false, true, CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER).setWriteMaskState(COLOR_WRITE)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY).setOutputState(WEATHER_TARGET)
                        .setTexturingState(new TexturingStateShard("let_there_be_atmosphere_bolt_fog", () -> {
                            FOG[0] = RenderSystem.getShaderFogStart();
                            FOG[1] = RenderSystem.getShaderFogEnd();
                            RenderSystem.setShaderFogStart(FOG[0] * 4F);
                            RenderSystem.setShaderFogEnd(FOG[1] * 4F);
                        }, () -> {
                            RenderSystem.setShaderFogStart(FOG[0]);
                            RenderSystem.setShaderFogEnd(FOG[1]);
                        }))
                        .createCompositeState(false));

        private Glow() {
            super("", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    @Mod.EventBusSubscriber(modid = Let_there_be_atmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class StormBoltRenderers {
        private StormBoltRenderers() {}

        @SubscribeEvent
        public static void onRegisterStormBoltRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityType.LIGHTNING_BOLT, StormBolt::new);
        }
    }
}
