#version 150

uniform sampler2D DepthSampler;
uniform sampler2D MaskSampler;
uniform mat4 InverseTransform;
uniform mat4 Reproject;
uniform vec4 Delta;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy) * 2, 0).r;
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec4 clip = Reproject * (InverseTransform * vec4(ndc, depth * 2.0 - 1.0, 1.0));
    vec2 uv = clip.w > 0.0 ? clip.xy / clip.w * 0.5 + 0.5 : texCoord;
    float expect = 1.0;
    if (depth < 1.0) {
        vec4 world = InverseTransform * vec4(ndc, min(depth, 0.9999) * 2.0 - 1.0, 1.0);
        expect = clamp(length(world.xyz / world.w + Delta.xyz) * Delta.w, 0.0, 1.0);
    }
    ivec2 edge = textureSize(MaskSampler, 0) - 1;
    vec2 coord = uv * vec2(edge + 1) - 0.5;
    ivec2 base = ivec2(floor(coord));
    vec2 f = coord - vec2(base);
    vec4 sum = vec4(0.0);
    float total = 0.0;
    for (int i = 0; i < 4; i++) {
        ivec2 o = ivec2(i & 1, i >> 1);
        vec4 s = texelFetch(MaskSampler, clamp(base + o, ivec2(0), edge), 0);
        vec2 b = mix(1.0 - f, f, vec2(o));
        float w = (b.x * b.y + 0.001) / (1.0 + abs(s.a - expect) * 400.0);
        sum += s * w;
        total += w;
    }
    fragColor = vec4(sum.rgb / total, expect);
}
