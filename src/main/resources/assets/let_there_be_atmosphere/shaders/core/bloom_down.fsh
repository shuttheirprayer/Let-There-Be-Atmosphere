#version 150

uniform sampler2D DiffuseSampler;
uniform vec3 Threshold;

in vec2 texCoord;
flat in vec2 exposure;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(DiffuseSampler, 0));
    vec4 color = texture(DiffuseSampler, texCoord + vec2(-texel.x, -texel.y));
    color += texture(DiffuseSampler, texCoord + vec2( texel.x, -texel.y));
    color += texture(DiffuseSampler, texCoord + vec2(-texel.x,  texel.y));
    color += texture(DiffuseSampler, texCoord + vec2( texel.x,  texel.y));
    color *= 0.25;
    if (Threshold.z > 0.5) {
        float peak = max(color.r, max(color.g, color.b));
        float soft = clamp(peak - exposure.x + Threshold.y, 0.0, 2.0 * Threshold.y);
        soft = soft * soft / (4.0 * Threshold.y + 0.0001);
        float keep = max(soft, peak - exposure.x) / max(peak, 0.0001) * exposure.y;
        color = vec4(color.rgb * keep, dot(color.rgb, vec3(0.2126, 0.7152, 0.0722)));
    }
    fragColor = color;
}
