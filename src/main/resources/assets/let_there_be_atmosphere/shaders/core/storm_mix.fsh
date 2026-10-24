#version 150

uniform sampler2D LayerSampler;
uniform sampler2D LowDepthSampler;
uniform sampler2D DepthSampler;
uniform vec4 Depth;

in vec2 texCoord;

out vec4 fragColor;

float distanceOf(float z) {
    return Depth.y / (Depth.x + z * 2.0 - 1.0);
}

void main() {
    float here = distanceOf(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r);
    ivec2 last = textureSize(LowDepthSampler, 0) - 1;
    vec2 low = gl_FragCoord.xy * 0.5 - 0.5;
    ivec2 base = ivec2(floor(low));
    ivec2 t0 = clamp(base, ivec2(0), last);
    ivec2 t1 = clamp(base + ivec2(1, 0), ivec2(0), last);
    ivec2 t2 = clamp(base + ivec2(0, 1), ivec2(0), last);
    ivec2 t3 = clamp(base + ivec2(1, 1), ivec2(0), last);
    float d0 = abs(distanceOf(texelFetch(LowDepthSampler, t0, 0).r) - here);
    float d1 = abs(distanceOf(texelFetch(LowDepthSampler, t1, 0).r) - here);
    float d2 = abs(distanceOf(texelFetch(LowDepthSampler, t2, 0).r) - here);
    float d3 = abs(distanceOf(texelFetch(LowDepthSampler, t3, 0).r) - here);
    vec4 c;
    if (max(max(d0, d1), max(d2, d3)) < 0.1 * abs(here) + 0.05) {
        c = texture(LayerSampler, texCoord);
    } else {
        ivec2 best = t0;
        float bestD = d0;
        if (d1 < bestD) { best = t1; bestD = d1; }
        if (d2 < bestD) { best = t2; bestD = d2; }
        if (d3 < bestD) { best = t3; }
        c = texelFetch(LayerSampler, best, 0);
    }
    if (c.a == 0.0) discard;
    fragColor = c;
}
