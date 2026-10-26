#version 150

uniform sampler2D DepthSampler;
uniform mat4 InverseTransform;
uniform vec4 Crust;
uniform vec4 CrustFog;
uniform vec4 Anchor;

in vec2 texCoord;

out vec4 fragColor;

const vec3 WARM[6] = vec3[6](vec3(0.6, 0.14, 0.03), vec3(0.83, 0.28, 0.04), vec3(0.95, 0.42, 0.055), vec3(1.0, 0.55, 0.08), vec3(1.0, 0.69, 0.165), vec3(1.0, 0.82, 0.37));
const vec3 RED[6] = vec3[6](vec3(0.38, 0.02, 0.07), vec3(0.55, 0.04, 0.1), vec3(0.75, 0.06, 0.16), vec3(0.88, 0.065, 0.18), vec3(1.0, 0.17, 0.28), vec3(1.0, 0.42, 0.49));
const float HOUR = 6.2831853 / 3600.0;
const float CYCLE = 8.0;
const float FLOW = 0.3;
const float SEAM = 0.33;

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

vec3 at(vec2 pixel) {
    vec2 size = vec2(textureSize(DepthSampler, 0));
    float depth = texelFetch(DepthSampler, ivec2(pixel), 0).r;
    vec4 world = InverseTransform * vec4(pixel / size * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return world.xyz / world.w;
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
    bool level = whole >= 1.0 && whole <= 8.0 && abs(steps - whole) / 9.0 <= tolerance && abs(n.y) >= 0.9 * length(n);
    if (depth >= 1.0 || abs(n.y) < 0.7 * length(n)) {
        discard;
    }
    vec2 flow = vec2(0.0);
    float reach = 1.0;
    if (!level || abs(n.y) < 0.998 * length(n)) {
        vec2 middle = clamp(gl_FragCoord.xy, vec2(2.5), vec2(textureSize(DepthSampler, 0)) - 2.5);
        vec3 centre = middle == gl_FragCoord.xy ? p : at(middle);
        vec3 l = at(middle - vec2(2.0, 0.0)), r = at(middle + vec2(2.0, 0.0));
        vec3 d = at(middle - vec2(0.0, 2.0)), u = at(middle + vec2(0.0, 2.0));
        vec3 wide = normalize(cross(r - l, u - d));
        wide = wide.y < 0.0 ? -wide : wide;
        float bend = max(length(l + r - 2.0 * centre) / max(length(r - l), 1e-4), length(d + u - 2.0 * centre) / max(length(u - d), 1e-4));
        float tilt = length(wide.xz);
        bool flowing = bend < 0.25 && tilt > 0.06 && tilt < 0.68 && distance < 48.0;
        if (!level && !flowing) {
            discard;
        }
        if (flowing) {
            float angle = floor(atan(wide.z, wide.x) / 0.7853982 + 0.5) * 0.7853982;
            flow = vec2(cos(angle), sin(angle)) * FLOW;
            reach = level ? 1.0 : 1.0 - smoothstep(36.0, 48.0, distance);
        }
    }
    float time = CrustFog.w;
    vec2 on = p.xz + Crust.xy;
    ivec2 texel = ivec2(floor(on * 16.0)) + ivec2(Anchor.xy) * 16;
    vec2 wander = vec2(sin(time * HOUR * 40.0), cos(time * HOUR * 31.0)) * 1.6;
    float cycles = time / CYCLE;
    float value = 0.0;
    float spread = 0.0;
    for (int k = 0; k < 2; k++) {
        float c = cycles + 0.5 * float(k);
        float phase = fract(c);
        float weight = 1.0 - abs(2.0 * phase - 1.0);
        ivec2 id = ivec2(int(mod(floor(c), 3600.0 / CYCLE)), k);
        vec2 seed = floor(vec2(hash1(id, 13u), hash1(id, 17u)) * 512.0);
        vec2 cell = floor(vec2(texel) + 0.5 - (flow * phase * CYCLE + wander) * 16.0) + seed;
        value += blobs(mat2(0.8, -0.6, 0.6, 0.8) * cell / 13.0) * weight;
        spread += weight * weight;
    }
    value = clamp(0.5 + (value - 0.5) / sqrt(spread) * 2.4, 0.0, 1.0);
    float pulse = 0.5 + 0.5 * sin(time * HOUR * 760.0 + hash1(texel >> 5, 21u) * 6.2832);
    float edge = abs(value - SEAM);
    int tone = value < SEAM ? (value < SEAM - 0.16 ? 0 : 1) : clamp(3 + int(floor((value - SEAM) * 4.0 + pulse * 0.7)), 3, 4);
    vec3 color = mix(WARM[tone], RED[tone], CrustFog.z);
    if (edge < 0.03 + 0.05 * pulse) {
        color = mix(mix(WARM[5], RED[5], CrustFog.z), vec3(1.0, 0.96, 0.78), 0.25 + 0.5 * pulse);
    }
    vec3 mean = mix(WARM[2], RED[2], CrustFog.z);
    color = mix(color, mean, smoothstep(34.0, 60.0, distance));
    float glint = hash1(texel, 5u + uint(floor(time * 3.0)) * 11u);
    if (glint < 0.004 * Crust.w * 2.0) {
        color = mix(color, vec3(1.0, 0.96, 0.78), 0.85);
    }
    float alpha = min(Crust.w * 1.8, 0.92) * reach * (1.0 - smoothstep(55.0, 84.0, distance)) * (1.0 - smoothstep(CrustFog.x, CrustFog.y, distance));
    if (alpha < 0.01) discard;
    fragColor = vec4(color * alpha, alpha);
}
