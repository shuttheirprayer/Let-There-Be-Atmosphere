package com.misanthropy.linggango.let_there_be_atmosphere.client.fx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ShaderWiringCheck {
    private static final Path SHADERS =
            Path.of("src/main/resources/assets/let_there_be_atmosphere/shaders");
    private static final Path FX_SOURCE =
            Path.of("src/main/java/com/misanthropy/linggango/let_there_be_atmosphere/client/fx");

    private static final Set<String> ENGINE_PROVIDED = Set.of(
            "ModelViewMat", "ProjMat", "InSize", "OutSize", "ScreenSize", "Time",
            "ColorModulator", "FogStart", "FogEnd", "FogColor", "GameTime");

    public static void main(String[] args) throws IOException {
        if (!Files.isDirectory(SHADERS)) {
            System.out.println("No shaders to check");
            return;
        }

        Set<String> declared = new TreeSet<>();
        for (Path json : list(SHADERS, ".json")) {
            declared.addAll(namesIn(Files.readString(json)));
            checkProgramMatchesGlsl(json);
        }

        Set<String> referenced = new TreeSet<>();
        for (Path java : list(FX_SOURCE, ".java")) {
            String source = Files.readString(java);
            collect(referenced, source, "(?:safeGetUniform|getUniform|setSampler|set)\\s*\\(\\s*\"([A-Za-z0-9_]+)\"");
        }

        referenced.removeAll(declared);
        check(referenced.isEmpty(), "Java pushes uniforms no shader JSON declares: " + referenced);

        System.out.println("Shader wiring OK");
    }

    private static void checkProgramMatchesGlsl(Path json) throws IOException {
        String base = json.getFileName().toString().replace(".json", "");
        Set<String> declared = namesIn(Files.readString(json));

        for (String extension : new String[]{".fsh", ".vsh"}) {
            Path glsl = json.resolveSibling(base + extension);
            if (!Files.exists(glsl)) continue;

            Set<String> used = new HashSet<>();
            collect(used, Files.readString(glsl), "uniform\\s+\\w+\\s+([A-Za-z0-9_]+)\\s*;");
            used.removeAll(declared);
            used.removeAll(ENGINE_PROVIDED);
            check(used.isEmpty(), glsl.getFileName() + " declares uniforms missing from "
                    + json.getFileName() + ": " + new TreeSet<>(used));
        }
    }

    private static Set<String> namesIn(String json) {
        Set<String> names = new HashSet<>();
        collect(names, json, "\"name\"\\s*:\\s*\"([A-Za-z0-9_]+)\"");
        return names;
    }

    private static void collect(Set<String> into, String text, String pattern) {
        Matcher matcher = Pattern.compile(pattern).matcher(text);
        while (matcher.find()) into.add(matcher.group(1));
    }

    private static List<Path> list(Path root, String suffix) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(suffix)).toList();
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
