#version 150

uniform sampler2D MaskSampler;

out vec4 fragColor;

void main() {
    ivec2 base = ivec2(gl_FragCoord.xy) * 4;
    ivec2 edge = textureSize(MaskSampler, 0) - 1;
    float water = 0.0;
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            water = max(water, texelFetch(MaskSampler, min(base + ivec2(x, y), edge), 0).r);
        }
    }
    fragColor = vec4(water);
}
