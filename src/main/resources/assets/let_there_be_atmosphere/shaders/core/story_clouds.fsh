#version 150

uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform vec2 FadeRange;
uniform float FogTint;
uniform vec3 SunDir;
uniform vec4 SkyTint;
uniform vec4 Light;
uniform float DepthPass;
uniform vec4 Pastel;
uniform sampler2D DepthSampler;

in vec4 vertexColor;
in vec3 relative;
in vec2 cellUv;

out vec4 fragColor;

void main() {
    if (Pastel.w > 0.5) {
        ivec2 last = textureSize(DepthSampler, 0) - 1;
        ivec2 p = ivec2(gl_FragCoord.xy) * 2;
        ivec2 q = min(p + 1, last);
        float scene = max(max(texelFetch(DepthSampler, p, 0).r, texelFetch(DepthSampler, ivec2(q.x, p.y), 0).r),
                          max(texelFetch(DepthSampler, ivec2(p.x, q.y), 0).r, texelFetch(DepthSampler, q, 0).r));
        if (gl_FragCoord.z > scene) discard;
    }
    vec3 tint = mix(ColorModulator.rgb, mix(SkyTint.rgb, vec3(1.0), 0.55) * max(max(ColorModulator.r, ColorModulator.g), ColorModulator.b), SkyTint.a * (1.0 - Pastel.x));
    float alpha = vertexColor.a * ColorModulator.a;
    float distance = length(relative.xz);
    vec3 color = tint;
    float paint = 1.0;
    if (Pastel.x > 0.5) {
        int face = int(vertexColor.r * 5.0 + 0.5);
        int edges = int(vertexColor.b * 15.0 + 0.5);
        vec2 f = fract(cellUv);
        float ex = min((edges & 1) != 0 ? f.x : 1.0, (edges & 2) != 0 ? 1.0 - f.x : 1.0);
        float ez = min((edges & 4) != 0 ? f.y : 1.0, (edges & 8) != 0 ? 1.0 - f.y : 1.0);
        if (face <= 1) {
            paint = mix(0.6, 1.0, smoothstep(0.0, 0.16, min(ex, ez)));
            color = face == 1 ? min(tint * 1.04, vec3(1.0)) : tint;
        } else {
            paint = mix(0.9, 0.12, smoothstep(0.0, 1.0, clamp(vertexColor.g, 0.0, 1.0)));
            color = tint * (1.0 - Pastel.z);
        }
        paint *= Pastel.y;
    } else if (Light.x > 0.5) {
        int face = int(vertexColor.r * 5.0 + 0.5);
        float height = clamp(vertexColor.g, 0.0, 1.0);
        vec3 normal = face == 0 ? vec3(0.0, -1.0, 0.0) : face == 1 ? vec3(0.0, 1.0, 0.0)
                : face == 2 ? vec3(-1.0, 0.0, 0.0) : face == 3 ? vec3(1.0, 0.0, 0.0)
                : face == 4 ? vec3(0.0, 0.0, -1.0) : vec3(0.0, 0.0, 1.0);
        vec3 under = tint * vec3(0.95, 0.96, 0.99) + SkyTint.rgb * 0.02;
        vec3 upper = mix(tint * 1.03, SkyTint.rgb, 0.04);
        float ramp = smoothstep(0.0, 1.0, height);
        int edges = int(vertexColor.b * 15.0 + 0.5);
        vec2 f = fract(cellUv);
        float ex = min((edges & 1) != 0 ? f.x : 1.0, (edges & 2) != 0 ? 1.0 - f.x : 1.0);
        float ez = min((edges & 4) != 0 ? f.y : 1.0, (edges & 8) != 0 ? 1.0 - f.y : 1.0);
        float feather = (face <= 1) ? mix(0.75, 1.0, smoothstep(0.0, 0.12, min(ex, ez))) : 1.0;
        float rim = 1.0 - feather;
        vec3 glow = mix(vec3(1.0), vec3(1.35, 0.9, 0.72), Light.z);
        under = mix(under, under * glow, rim * Light.z);
        under += vec3(1.0, 0.55, 0.35) * Light.z * Light.y * 0.18;
        color = face == 0 ? under : face == 1 ? upper : mix(under, upper, ramp);
        if (face <= 1) alpha *= feather;
        float facing = max(dot(normal, SunDir), 0.0);
        vec3 warm = mix(vec3(1.0), vec3(1.28, 0.96, 0.74), Light.z);
        color *= 1.0 + Light.y * Light.w * (0.2 * facing - 0.05);
        color = mix(color, color * warm, facing * 0.85);
        float toward = max(dot(normalize(relative), SunDir), 0.0);
        float edge = face == 1 ? 1.0 : face == 0 ? 0.25 : smoothstep(0.35, 1.0, height);
        color += pow(toward, 10.0) * Light.y * edge * 0.35 * warm;
        if (face >= 2) alpha *= mix(1.0, 0.6, ramp);
        color = mix(color, upper, smoothstep(FadeRange.x * 0.3, FadeRange.y, distance) * 0.3);
    }
    float fade = smoothstep(FadeRange.x, FadeRange.y, distance);
    color = mix(color, FogColor.rgb, fade * FogTint);
    alpha *= 1.0 - fade;
    if (DepthPass < 0.5) alpha *= paint;
    if (alpha < (DepthPass > 0.5 ? 0.5 : 0.004)) {
        discard;
    }
    fragColor = Pastel.w > 0.5 ? vec4(color * alpha, alpha) : vec4(color, alpha);
}
