#version 150

uniform sampler2D DepthSampler;
uniform mat4 InverseTransform;
uniform vec4 Crust;
uniform vec4 CrustFog;
uniform vec4 Anchor;

in vec2 texCoord;

out vec4 fragColor;

const vec3 WARM[5] = vec3[5](vec3(0.83, 0.28, 0.04), vec3(0.95, 0.42, 0.055), vec3(1.0, 0.55, 0.08), vec3(1.0, 0.69, 0.165), vec3(1.0, 0.82, 0.37));
const vec3 RED[5] = vec3[5](vec3(0.55, 0.04, 0.1), vec3(0.75, 0.06, 0.16), vec3(0.88, 0.065, 0.18), vec3(1.0, 0.17, 0.28), vec3(1.0, 0.42, 0.49));

float hash1(ivec2 cell, uint salt) {
    vec3 p = fract(vec3(cell.x, cell.y, float(salt)) * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float blobs(vec2 q) {
    ivec2 i = ivec2(floor(q));
    vec2 f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash1(i, 9u), hash1(i + ivec2(1, 0), 9u), f.x), mix(hash1(i + ivec2(0, 1), 9u), hash1(i + ivec2(1, 1), 9u), f.x), f.y);
}

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    vec4 world = InverseTransform * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 p = world.xyz / world.w;
    vec3 n = cross(dFdx(p), dFdy(p));
    float distance = length(p);
    float tolerance = min(0.006 + distance * distance * 0.000004, 0.0105);
    float steps = fract(p.y + Crust.z + 0.001) * 9.0;
    float whole = floor(steps + 0.5);
    if (depth >= 1.0 || whole < 1.0 || whole > 8.0 || abs(steps - whole) / 9.0 > tolerance || abs(n.y) < 0.9 * length(n)) {
        discard;
    }
    float time = CrustFog.w;
    vec2 on = p.xz + Crust.xy;
    ivec2 texel = ivec2(floor(on * 16.0)) + ivec2(Anchor.xy) * 16;
    float phase = hash1(texel, 2u);
    uint tick = uint(floor(time * 0.6 + phase * 5.0));
    vec2 flow = vec2(time * 0.18, -time * 0.11);
    float value = blobs(vec2(texel) / 9.0 + flow * 0.5) * 0.52 + blobs(vec2(texel) / 3.5 + flow) * 0.28 + hash1(texel, 3u + tick * 7u) * 0.2;
    value = clamp((value - 0.5) * 1.7 + 0.5, 0.0, 1.0);
    int tone = clamp(int(floor(value * 5.0)), 0, 4);
    vec3 color = mix(WARM[tone], RED[tone], CrustFog.z);
    vec3 mean = mix(WARM[2], RED[2], CrustFog.z);
    color = mix(color, mean, smoothstep(34.0, 60.0, distance));
    float glint = hash1(texel, 5u + uint(floor(time * 3.0)) * 11u);
    if (glint < 0.004 * Crust.w * 2.0) {
        color = mix(color, vec3(1.0, 0.96, 0.78), 0.85);
    }
    float alpha = min(Crust.w * 1.8, 0.92) * (1.0 - smoothstep(55.0, 84.0, distance)) * (1.0 - smoothstep(CrustFog.x, CrustFog.y, distance));
    if (alpha < 0.01) discard;
    fragColor = vec4(color * alpha, alpha);
}
