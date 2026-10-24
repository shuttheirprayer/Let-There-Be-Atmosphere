package com.misanthropy.linggango.let_there_be_atmosphere.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class AtmosphereMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOG = LogManager.getLogger("let_there_be_atmosphere");

    private static final String EMBEDDIUM_MARKER = "embeddium";
    private static final String CELESTIAL_MIXIN = "CelestialSizeMixin";
    private static final String[] CELESTIAL_BLOCKERS = {"oculus", "iris", "linggango_tweaks"};
    private static final String STORY_CLOUD_MIXIN = "StoryCloudRenderMixin";
    private static final String[] STORY_CLOUD_BLOCKERS = {};

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName == null) return true;
        boolean apply = true;
        if (mixinClassName.toLowerCase().contains(EMBEDDIUM_MARKER)) {
            apply = isModLoaded(EMBEDDIUM_MARKER);
        } else if (mixinClassName.endsWith(CELESTIAL_MIXIN)) {
            apply = noneLoaded(CELESTIAL_BLOCKERS);
        } else if (mixinClassName.endsWith(STORY_CLOUD_MIXIN)) {
            apply = noneLoaded(STORY_CLOUD_BLOCKERS);
        }
        if (!apply) LOG.info("[atmosphere] mixin skipped, another mod owns it: {}", mixinClassName);
        return apply;
    }

    private static boolean noneLoaded(String[] modIds) {
        for (String modId : modIds) {
            if (isModLoaded(modId)) return false;
        }
        return true;
    }

    private static boolean isModLoaded(String modId) {
        LoadingModList modList = LoadingModList.get();
        return modList != null && modList.getModFileById(modId) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        LOG.info("[atmosphere] mixin applied: {} -> {}", mixinClassName, targetClassName);
    }
}
