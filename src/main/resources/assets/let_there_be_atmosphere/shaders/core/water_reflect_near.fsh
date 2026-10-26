#version 150

uniform sampler2D SolidSampler;

out vec4 fragColor;

void main() {
    ivec2 base = ivec2(gl_FragCoord.xy) * 2;
    ivec2 edge = textureSize(SolidSampler, 0) - 1;
    float a = texelFetch(SolidSampler, min(base, edge), 0).r;
    float b = texelFetch(SolidSampler, min(base + ivec2(1, 0), edge), 0).r;
    float c = texelFetch(SolidSampler, min(base + ivec2(0, 1), edge), 0).r;
    float d = texelFetch(SolidSampler, min(base + ivec2(1, 1), edge), 0).r;
    gl_FragDepth = min(min(a, b), min(c, d));
    fragColor = vec4(0.0);
}
