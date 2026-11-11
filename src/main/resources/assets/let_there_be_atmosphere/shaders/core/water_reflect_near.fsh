#version 150
#extension GL_ARB_texture_gather : enable

uniform sampler2D SolidSampler;
uniform sampler2D DepthSampler;

out vec4 fragColor;

void main() {
    ivec2 base = ivec2(gl_FragCoord.xy) * 2;
    ivec2 edge = textureSize(SolidSampler, 0) - 1;
#ifdef GL_ARB_texture_gather
    if (base.x < edge.x && base.y < edge.y) {
        vec2 at = (vec2(base) + 1.0) / vec2(edge + 1);
        vec4 solid = textureGather(SolidSampler, at);
        vec4 now = textureGather(DepthSampler, at);
        bvec4 water = bvec4(now.x < 1.0 && now.x < solid.x, now.y < 1.0 && now.y < solid.y, now.z < 1.0 && now.z < solid.z, now.w < 1.0 && now.w < solid.w);
        fragColor = vec4(any(water) ? 1.0 : 0.0, 0.0, 0.0, 1.0);
        return;
    }
#endif
    bool water = false;
    for (int i = 0; i < 4; i++) {
        ivec2 at = min(base + ivec2(i & 1, i >> 1), edge);
        float now = texelFetch(DepthSampler, at, 0).r;
        water = water || (now < 1.0 && now < texelFetch(SolidSampler, at, 0).r);
    }
    fragColor = vec4(water ? 1.0 : 0.0, 0.0, 0.0, 1.0);
}
