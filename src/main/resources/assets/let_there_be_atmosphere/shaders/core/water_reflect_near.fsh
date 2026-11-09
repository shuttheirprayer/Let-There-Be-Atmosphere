#version 150
#extension GL_ARB_texture_gather : enable

uniform sampler2D SolidSampler;
uniform sampler2D DepthSampler;
uniform float Masked;
uniform float Gather;

out vec4 fragColor;

void main() {
    ivec2 base = ivec2(gl_FragCoord.xy) * 2;
    ivec2 edge = textureSize(SolidSampler, 0) - 1;
#ifdef GL_ARB_texture_gather
    if (Gather > 0.5 && base.x < edge.x && base.y < edge.y) {
        vec2 at = (vec2(base) + 1.0) / vec2(edge + 1);
        vec4 solid = textureGather(SolidSampler, at);
        gl_FragDepth = min(min(solid.x, solid.y), min(solid.z, solid.w));
        if (Masked < 0.5) {
            fragColor = vec4(1.0);
            return;
        }
        vec4 now = textureGather(DepthSampler, at);
        bvec4 water = bvec4(now.x < 1.0 && now.x < solid.x, now.y < 1.0 && now.y < solid.y, now.z < 1.0 && now.z < solid.z, now.w < 1.0 && now.w < solid.w);
        fragColor = vec4(any(water) ? 1.0 : 0.0, 0.0, 0.0, 1.0);
        return;
    }
#endif
    ivec2 pa = min(base, edge);
    ivec2 pb = min(base + ivec2(1, 0), edge);
    ivec2 pc = min(base + ivec2(0, 1), edge);
    ivec2 pd = min(base + ivec2(1, 1), edge);
    float a = texelFetch(SolidSampler, pa, 0).r;
    float b = texelFetch(SolidSampler, pb, 0).r;
    float c = texelFetch(SolidSampler, pc, 0).r;
    float d = texelFetch(SolidSampler, pd, 0).r;
    gl_FragDepth = min(min(a, b), min(c, d));
    if (Masked < 0.5) {
        fragColor = vec4(1.0);
        return;
    }
    float wa = texelFetch(DepthSampler, pa, 0).r;
    float wb = texelFetch(DepthSampler, pb, 0).r;
    float wc = texelFetch(DepthSampler, pc, 0).r;
    float wd = texelFetch(DepthSampler, pd, 0).r;
    bool water = (wa < 1.0 && wa < a) || (wb < 1.0 && wb < b) || (wc < 1.0 && wc < c) || (wd < 1.0 && wd < d);
    fragColor = vec4(water ? 1.0 : 0.0, 0.0, 0.0, 1.0);
}
