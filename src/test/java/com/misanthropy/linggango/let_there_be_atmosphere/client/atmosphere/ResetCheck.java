package com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere;

import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.FxConfig;
import com.misanthropy.linggango.let_there_be_atmosphere.client.fx.StoryCloudConfig;
import com.misanthropy.linggango.let_there_be_atmosphere.tweaks.dynamic_fog.DynamicFogHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ResetCheck {
    private static final Class<?>[] HOLDERS = {DynamicFogHandler.class, FxConfig.class, StoryCloudConfig.class};

    public static void main(String[] args) throws Exception {
        Class<?> screen = Class.forName(
                "com.misanthropy.linggango.let_there_be_atmosphere.client.atmosphere.AtmosphereScreen");

        Field tabsField = screen.getDeclaredField("WORLD_TABS");
        tabsField.setAccessible(true);
        String[] tabs = (String[]) tabsField.get(null);

        Field tableField = screen.getDeclaredField("TAB_FIELDS");
        tableField.setAccessible(true);
        String[][] table = (String[][]) tableField.get(null);

        require(tabs.length == table.length, "every tab has a reset list");

        Set<String> seen = new HashSet<>();
        for (int tab = 0; tab < table.length; tab++) {
            require(table[tab].length > 0, tabs[tab] + " resets at least one setting");
            require(table[tab].length <= 16,
                    tabs[tab] + " carries " + table[tab].length + " settings; split the page");
            for (String name : table[tab]) {
                require(resolve(name) != null, name + " names a real setting");
                require(seen.add(name), name + " belongs to a single tab");
            }
        }

        Set<String> orphans = new TreeSet<>();
        for (Class<?> holder : HOLDERS) {
            for (Field field : holder.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (field.isSynthetic() || !Modifier.isPublic(modifiers)
                        || !Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) continue;
                String name = holder.getSimpleName() + "." + field.getName();
                if (!seen.contains(name)) orphans.add(name);
            }
        }
        require(orphans.isEmpty(), "settings that no tab can reset: " + orphans);

        DynamicFogHandler.resetDefaults();
        double defaultDensity = DynamicFogHandler.fogDensity;
        boolean defaultComets = FxConfig.cometsEnabled;

        DynamicFogHandler.fogDensity = 3.5;
        FxConfig.cometsEnabled = !defaultComets;
        StoryCloudConfig.coverage = 0.91;

        Method defaults = screen.getDeclaredMethod("defaults");
        defaults.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) defaults.invoke(null);

        require(DynamicFogHandler.fogDensity == 3.5, "looking up defaults leaves tuned values alone");
        require(FxConfig.cometsEnabled != defaultComets, "looking up defaults leaves toggles alone");
        require(StoryCloudConfig.coverage == 0.91, "looking up defaults leaves cloud settings alone");

        require(values.get("DynamicFogHandler.fogDensity").equals(defaultDensity), "the default density is reported");
        require(values.get("FxConfig.cometsEnabled").equals(defaultComets), "the default toggle is reported");

        Method write = screen.getDeclaredMethod("write", String.class, Object.class);
        write.setAccessible(true);
        write.invoke(null, "DynamicFogHandler.fogDensity", values.get("DynamicFogHandler.fogDensity"));
        require(DynamicFogHandler.fogDensity == defaultDensity, "a single setting can be put back");

        DynamicFogHandler.resetDefaults();
        System.out.println("Reset tables OK");
    }

    private static Field resolve(String name) {
        int dot = name.indexOf('.');
        if (dot < 0) return null;
        String holderName = name.substring(0, dot);
        String fieldName = name.substring(dot + 1);
        for (Class<?> holder : HOLDERS) {
            if (!holder.getSimpleName().equals(holderName)) continue;
            try {
                Field field = holder.getDeclaredField(fieldName);
                int modifiers = field.getModifiers();
                boolean usable = Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)
                        && !Modifier.isFinal(modifiers);
                return usable ? field : null;
            } catch (NoSuchFieldException e) {
                return null;
            }
        }
        return null;
    }

    private static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
