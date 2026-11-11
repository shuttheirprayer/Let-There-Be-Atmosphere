#version 150
#extension GL_ARB_texture_gather : enable

uniform sampler2D SolidSampler;
uniform sampler2D DepthSampler;
uniform float Rows;

out vec4 fragColor;

void main() {
    ivec2 base = ivec2(gl_FragCoord.xy) * 2;
    ivec2 edge = textureSize(SolidSampler, 0) - 1;
    bool below = gl_FragCoord.y < Rows;
#ifdef GL_ARB_texture_gather
    if (base.x < edge.x && base.y < edge.y) {
        vec2 at = (vec2(base) + 1.0) / vec2(edge + 1);
        vec4 d = below ? textureGather(SolidSampler, at) : textureGather(DepthSampler, at);
        fragColor = vec4(min(min(d.x, d.y), min(d.z, d.w)));
        return;
    }
#endif
    float nearest = 1.0;
    for (int i = 0; i < 4; i++) {
        ivec2 at = min(base + ivec2(i & 1, i >> 1), edge);
        nearest = min(nearest, below ? texelFetch(SolidSampler, at, 0).r : texelFetch(DepthSampler, at, 0).r);
    }
    fragColor = vec4(nearest);
}
