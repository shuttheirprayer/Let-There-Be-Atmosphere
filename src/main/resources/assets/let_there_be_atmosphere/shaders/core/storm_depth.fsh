#version 150

uniform sampler2D DepthSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    ivec2 last = textureSize(DepthSampler, 0) - 1;
    ivec2 p = ivec2(gl_FragCoord.xy) * 2;
    ivec2 q = min(p + 1, last);
    float far = max(max(texelFetch(DepthSampler, p, 0).r, texelFetch(DepthSampler, ivec2(q.x, p.y), 0).r),
                    max(texelFetch(DepthSampler, ivec2(p.x, q.y), 0).r, texelFetch(DepthSampler, q, 0).r));
    fragColor = vec4(far, 0.0, 0.0, 1.0);
}
