#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec3 Threshold;

in vec2 texCoord;
flat in vec3 exposure;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(DiffuseSampler, 0));
    vec2 taps[4] = vec2[](vec2(-texel.x, -texel.y), vec2(texel.x, -texel.y), vec2(-texel.x, texel.y), vec2(texel.x, texel.y));
    vec4 color = vec4(0.0);
    float scene = 0.0;
    for (int i = 0; i < 4; i++) {
        vec4 tap = texture(DiffuseSampler, texCoord + taps[i]);
        if (Threshold.z > 0.5) {
            scene += dot(tap.rgb, vec3(0.2126, 0.7152, 0.0722));
        }
        color += tap;
    }
    color *= Threshold.z > 0.5 ? 0.25 * step(0.99999, texture(DepthSampler, texCoord).r) : 0.25;
    if (Threshold.z > 0.5) {
        float peak = max(color.r, max(color.g, color.b));
        float tint = smoothstep(0.12, 0.45, (peak - min(color.r, min(color.g, color.b))) / max(peak, 0.0001));
        float soft = clamp(peak - exposure.x + Threshold.y, 0.0, 2.0 * Threshold.y);
        soft = soft * soft / (4.0 * Threshold.y + 0.0001);
        float keep = max(soft, peak - exposure.x) / max(peak, 0.0001) * exposure.y * mix(1.0, mix(0.2, 1.25, tint), exposure.z);
        color = vec4(color.rgb * keep, scene * 0.25);
    }
    fragColor = color;
}
