#version 150

uniform sampler2D DiffuseSampler;
uniform vec4 Grade;
uniform vec4 SkyGrade;
uniform vec3 Tint;
uniform vec3 Vignette;
uniform sampler2D BloomSampler;
uniform float BloomStrength;
uniform sampler2D RaySampler;
uniform vec3 Rays;
uniform sampler2D SunSampler;
uniform sampler2D DepthSampler;
uniform float Shadows;
uniform sampler2D NoiseSampler;
uniform vec4 Haze;
uniform vec4 MistColor;
uniform vec4 PuddleColor;
uniform vec4 View;
uniform vec4 ViewTan;
uniform vec4 Flare;

in vec2 texCoord;
flat in float seen;

out vec4 fragColor;

void main() {
    vec4 sun = texture(SunSampler, texCoord);
    float sky = step(0.99, sun.b);
    bool hazy = Haze.x + Haze.y + Haze.z > 0.0;
    bool hand = (Shadows > 0.5 || hazy || BloomStrength > 0.0) && texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r < 1.0;
    vec3 color;
    float heat = 0.0;
    float frost = 0.0;
    if (hazy && !hand) {
        heat = Haze.x * smoothstep(0.15, 0.7, sun.a) * (1.0 - sky) + Haze.y * (1.0 - smoothstep(0.0, 0.6, sun.a));
        frost = smoothstep(0.3, 0.8, length((texCoord - 0.5) * vec2(Vignette.y, 1.0)));
    }
    if (heat + frost * Haze.z > 0.00005) {
        vec2 base = texCoord * vec2(Vignette.y, 1.0);
        vec2 slow = texture(NoiseSampler, base * 3.0 + vec2(0.0, -Haze.w * 0.12)).rg - 0.5;
        vec2 fast = texture(NoiseSampler, base * 9.0 + vec2(Haze.w * 0.03, -Haze.w * 0.55)).rg - 0.5;
        vec2 shift = (fast + slow * 0.5) * heat + slow * Haze.z * frost;
        shift *= vec2(2.0 / Vignette.y, 2.0);
        if (texture(DepthSampler, texCoord + shift).r < 1.0) shift = vec2(0.0);
        color = texture(DiffuseSampler, texCoord + shift).rgb;
        color = mix(color, color * vec3(0.9, 0.97, 1.08) + vec3(0.035, 0.045, 0.06), frost * clamp(Haze.z * 160.0, 0.0, 1.0) * (0.6 + 0.4 * fast.x));
    } else {
        color = texelFetch(DiffuseSampler, ivec2(gl_FragCoord.xy), 0).rgb;
    }
    if (Shadows > 0.5 && !hand) {
        ivec2 pixel = ivec2(gl_FragCoord.xy);
        ivec2 base = pixel / 2;
        ivec2 lean = (pixel & 1) * 2 - 1;
        ivec2 edge = textureSize(SunSampler, 0) - 1;
        float near = texelFetch(SunSampler, clamp(base, ivec2(0), edge), 0).a;
        float lit = 0.0;
        float weight = 0.0;
        for (int i = 0; i < 4; i++) {
            vec4 s = texelFetch(SunSampler, clamp(base + ivec2(i & 1, i >> 1) * lean, ivec2(0), edge), 0);
            float w = 1.0 / (1.0 + abs(s.a - near) * 400.0);
            lit += s.g * w;
            weight += w;
        }
        color *= lit / weight;
    }
    if (PuddleColor.w > 0.5 && !hand && sun.r * (1.0 - sky) > 0.0) {
        float wet = sun.r * (1.0 - sky);
        vec2 ndc = texCoord * 2.0 - 1.0;
        float rise = View.x + View.y * ndc.x + View.z * ndc.y;
        float span = sqrt(1.0 + ndc.x * ndc.x * View.w * View.w + ndc.y * ndc.y * ViewTan.x * ViewTan.x);
        float facing = clamp(-rise / span, 0.0, 1.0);
        float fresnel = 0.45 + 0.55 * pow(1.0 - facing, 5.0);
        vec3 reflection = PuddleColor.rgb;
        if (abs(View.z) > 0.001) {
            float horizon = -(View.x + View.y * ndc.x) / View.z;
            float wobble = sin(texCoord.y * 340.0 + ViewTan.y * 9.0) * 0.0018 * ViewTan.z;
            vec2 mirror = vec2(texCoord.x + wobble, (2.0 * horizon - ndc.y) * 0.5 + 0.5);
            if (mirror.y > 0.0 && mirror.y < 1.0 && texture(DepthSampler, mirror).r >= 1.0) {
                reflection = mix(texture(DiffuseSampler, mirror).rgb, PuddleColor.rgb, 0.2);
            }
        }
        color *= 1.0 - 0.45 * wet;
        color = mix(color, reflection, wet * fresnel);
    }
    if (MistColor.w > 0.5 && !hand) color = mix(color, MistColor.rgb, sun.b * (1.0 - sky));
    float noise = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    vec3 glow = texture(BloomSampler, texCoord).rgb * (hand ? 0.35 : 1.0);
    glow = max(mix(vec3(dot(glow, vec3(0.2126, 0.7152, 0.0722))), glow, 1.4) * BloomStrength + noise * BloomStrength / 255.0, 0.0);
    color = 1.0 - (1.0 - color) * (1.0 - min(glow, 1.0));
    color = 1.0 - (1.0 - color) * (1.0 - min(Rays * texture(RaySampler, texCoord).r * (1.0 - 0.4 * sky), 1.0));
    if (Flare.z > 0.0) {
        vec2 aspect = vec2(Vignette.y, 1.0);
        vec2 axis = vec2(0.5) - Flare.xy;
        float at[5] = float[](0.45, 0.8, 1.25, 1.6, 2.1);
        float size[5] = float[](0.045, 0.022, 0.08, 0.035, 0.13);
        vec3 tint[5] = vec3[](vec3(1.0, 0.78, 0.5), vec3(0.65, 1.0, 0.75), vec3(0.55, 0.72, 1.0), vec3(1.0, 0.62, 0.86), vec3(0.72, 0.92, 1.0));
        vec3 flare = vec3(0.0);
        for (int i = 0; i < 5; i++) {
            float d = length((texCoord - Flare.xy - axis * at[i]) * aspect) / size[i];
            flare += tint[i] * (1.0 - smoothstep(0.8, 1.0, d)) * (0.5 + 0.5 * smoothstep(0.3, 0.95, d));
        }
        color = 1.0 - (1.0 - color) * (1.0 - min(flare * Flare.z * seen * 0.08, 1.0));
    }
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float saturation = mix(Grade.x, SkyGrade.x, sky) * (1.0 - Grade.z * (1.0 - sky) * (1.0 - smoothstep(0.04, 0.3, luma)));
    float chroma = max(color.r, max(color.g, color.b)) - min(color.r, min(color.g, color.b));
    if (saturation > 1.0) saturation = 1.0 + (saturation - 1.0) * (1.0 - chroma) * (1.0 - chroma);
    float peak = max(color.r, max(color.g, color.b));
    saturation *= 1.0 + Grade.w * (1.0 - chroma / max(peak, 0.0001)) * smoothstep(0.02, 0.2, luma);
    color = max(mix(vec3(luma), color, saturation), 0.0);
    color = mix(color, color * color * (3.0 - 2.0 * color), Grade.y);
    color *= Tint;
    vec2 offset = (texCoord - 0.5) * vec2(Vignette.y, 1.0);
    color *= 1.0 - Vignette.x * smoothstep(mix(0.35, 0.0, Vignette.z), mix(1.0, 0.7, Vignette.z), length(offset) * 1.25);
    fragColor = vec4(color, 1.0);
}
