#version 150

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
uniform sampler2D SolidSampler;
uniform sampler2D NoiseSampler;
uniform sampler2D NearSampler;
uniform mat4 InverseTransform;
uniform mat4 ViewProjection;
uniform vec4 Surface;
uniform vec4 Waves;
uniform vec4 Horizon;
uniform vec4 Zenith;

in vec2 texCoord;

out vec4 fragColor;

const int STEPS = 24;
const int REFINE = 5;
const float LEVEL = 0.8884;

vec2 size;
vec4 wRow;

vec3 unproject(vec2 ndc, float depth) {
    vec4 p = InverseTransform * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

float sceneW(vec2 ndc, float depth) {
    return 1.0 / dot(wRow, vec4(ndc, depth * 2.0 - 1.0, 1.0));
}

float depthAt(vec2 ndc) {
    ivec2 coarse = textureSize(NearSampler, 0);
    return texelFetch(NearSampler, clamp(ivec2((ndc * 0.5 + 0.5) * size) / 2, ivec2(0), coarse - 1), 0).r;
}

bool onPlane(ivec2 px, float plane, float tol) {
    ivec2 at = clamp(px, ivec2(0), ivec2(size) - 1);
    float d = texelFetch(DepthSampler, at, 0).r;
    vec3 p = unproject((vec2(at) + 0.5) / size * 2.0 - 1.0, d);
    return d < 1.0 && abs(p.y + Surface.x - plane) < tol;
}

vec3 pointAt(ivec2 px) {
    ivec2 at = clamp(px, ivec2(0), ivec2(size) - 1);
    return unproject((vec2(at) + 0.5) / size * 2.0 - 1.0, texelFetch(DepthSampler, at, 0).r);
}

bool sloped(ivec2 px, vec3 c, float height) {
    if (abs(height - floor(height + 0.5)) < 0.05) return false;
    vec3 right = pointAt(px + ivec2(1, 0)) - c, left = c - pointAt(px - ivec2(1, 0));
    vec3 up = pointAt(px + ivec2(0, 1)) - c, down = c - pointAt(px - ivec2(0, 1));
    vec3 n = cross(dot(right, right) < dot(left, left) ? right : left, dot(up, up) < dot(down, down) ? up : down);
    return abs(n.y) > 0.95 * length(n);
}

float edgeFade(vec2 ndc) {
    vec2 e = 1.0 - abs(ndc);
    return smoothstep(0.0, 0.1, min(e.x, e.y));
}

void main() {
    size = vec2(textureSize(DepthSampler, 0));
    wRow = vec4(InverseTransform[0][3], InverseTransform[1][3], InverseTransform[2][3], InverseTransform[3][3]);
    ivec2 last = ivec2(size) - 1;
    ivec2 origin = ivec2(gl_FragCoord.xy) * 2;
    ivec2 px = origin;
    float d = 1.0;
    for (int i = 0; i < 4; i++) {
        ivec2 at = min(origin + ivec2(i == 1 || i == 2 ? 1 : 0, i == 1 || i == 3 ? 1 : 0), last);
        float candidate = texelFetch(DepthSampler, at, 0).r;
        if (candidate < 1.0 && candidate < texelFetch(SolidSampler, at, 0).r) {
            px = at;
            d = candidate;
            break;
        }
    }
    if (d >= 1.0) discard;

    vec2 ndc = (vec2(px) + 0.5) / size * 2.0 - 1.0;
    vec3 eye = unproject(ndc, 0.0);
    vec3 surface = unproject(ndc, d);
    vec3 dir = normalize(surface - eye);
    float dist = length(surface);
    float height = surface.y + Surface.x;
    float plane = floor(height - LEVEL + 0.5) + LEVEL;
    float tol = 0.004 + dist * 0.00004 + dist * dist * 1.5e-6;
    if (dir.y > -0.01) discard;
    float wide = tol * 2.5;
    if (abs(height - plane) > tol
            || !(onPlane(px + ivec2(1, 0), plane, wide) || onPlane(px - ivec2(1, 0), plane, wide))
            || !(onPlane(px + ivec2(0, 1), plane, wide) || onPlane(px - ivec2(0, 1), plane, wide))) {
        if (!sloped(px, surface, height)) discard;
        plane = height;
    }
    if (plane - Surface.x > eye.y - 0.05) discard;

    vec3 p = eye + dir * ((plane - Surface.x - eye.y) / dir.y);
    float foot = length(p) * Zenith.w / max(-dir.y, 0.02);
    vec2 world = (p.xz + Waves.xy) / 16.0;
    vec2 g = texture(NoiseSampler, world + vec2(Waves.z * 0.02, Waves.z * 0.0125)).rg
           + texture(NoiseSampler, world * 2.0 + vec2(-Waves.z * 0.015, Waves.z * 0.025)).rg - 1.0;
    float amp = Waves.w * (1.0 - smoothstep(0.06, 0.3, foot));
    vec3 n = normalize(vec3(g.x * amp, 1.0, g.y * amp));
    vec3 r = reflect(dir, n);
    r.y = max(r.y, 0.01);
    r = normalize(r);

    vec4 c0 = ViewProjection * vec4(p, 1.0);
    vec4 cr = ViewProjection * vec4(r, 0.0);
    float reach = 512.0;
    if (cr.w < 0.0) reach = min(reach, (c0.w - 0.1) / -cr.w);
    vec4 c1 = c0 + cr * reach;
    vec3 s0 = vec3(c0.xy / c0.w, 1.0 / c0.w);
    vec3 s1 = vec3(c1.xy / c1.w, 1.0 / c1.w);
    vec2 span = s1.xy - s0.xy;
    float k = 1.0;
    if (span.x > 0.0) k = min(k, (1.0 - s0.x) / span.x);
    else if (span.x < 0.0) k = min(k, (-1.0 - s0.x) / span.x);
    if (span.y > 0.0) k = min(k, (1.0 - s0.y) / span.y);
    else if (span.y < 0.0) k = min(k, (-1.0 - s0.y) / span.y);
    s1 = mix(s0, s1, clamp(k, 0.0, 1.0));

    float prevW = c0.w;
    float lo = 0.0;
    float hi = -1.0;
    for (int i = 1; i <= STEPS; i++) {
        float u = float(i) / float(STEPS);
        u *= u;
        vec3 s = mix(s0, s1, u);
        float rw = 1.0 / s.z;
        float sd = depthAt(s.xy);
        if (sd < 1.0) {
            float sw = sceneW(s.xy, sd);
            if (sw < rw && sw > prevW - 0.5 - 0.02 * prevW) {
                hi = u;
                break;
            }
        }
        prevW = rw;
        lo = u;
    }

    vec3 hit = vec3(0.0);
    float fade = 0.0;
    if (hi > 0.0) {
        for (int i = 0; i < REFINE; i++) {
            float mid = 0.5 * (lo + hi);
            vec3 s = mix(s0, s1, mid);
            float sd = depthAt(s.xy);
            if (sd < 1.0 && sceneW(s.xy, sd) < 1.0 / s.z) hi = mid;
            else lo = mid;
        }
        vec3 s = mix(s0, s1, hi);
        if (unproject(s.xy, depthAt(s.xy)).y + Surface.x > plane - 0.1) {
            hit = texture(SceneSampler, s.xy * 0.5 + 0.5).rgb;
            fade = (k < 1.0 ? smoothstep(0.0, 0.3, (1.0 - hi) * length(span * k)) : 1.0) * smoothstep(0.0, 0.25, 1.0 - abs(s.x));
        }
    }

    vec3 sky = mix(Horizon.rgb, Zenith.rgb, smoothstep(0.02, 0.45, r.y));
    float open = 0.0;
    if (fade < 1.0) {
        float num = dot(vec3(ViewProjection[0][0], ViewProjection[1][0], ViewProjection[2][0]), r);
        float den = dot(vec3(ViewProjection[0][3], ViewProjection[1][3], ViewProjection[2][3]), r);
        float column = den > 1.0e-4 ? clamp(num / den, -0.95, 0.95) : (num >= 0.0 ? 0.95 : -0.95);
        vec3 middle = unproject(vec2(column, 0.0), 0.5);
        vec3 rise = unproject(vec2(column, 1.0), 0.5) - middle;
        middle -= eye;
        float below = -1.0;
        float above = 1.0;
        for (int i = 0; i < 8; i++) {
            float mid = 0.5 * (below + above);
            if (normalize(middle + rise * mid).y < r.y) below = mid;
            else above = mid;
        }
        float top = normalize(middle + rise).y;
        vec2 look = vec2(column, 0.5 * (below + above));
        float ld = depthAt(look);
        if (ld >= 1.0) {
            open = 1.0 - smoothstep(0.0, 0.25, r.y - top);
            sky = mix(sky, texture(SceneSampler, look * 0.5 + 0.5).rgb, open);
        } else {
            vec3 q = unproject(look, ld) - p;
            float along = dot(q, r);
            open = along > 0.5 ? smoothstep(0.02, 0.06, length(cross(q, r)) / along) : 1.0;
        }
        float t = reach;
        for (int i = 0; i < 4; i++) {
            vec4 cq = ViewProjection * vec4(p + r * t, 1.0);
            if (cq.w <= 0.0) break;
            vec2 sq = cq.xy / cq.w;
            if (abs(sq.x) > 1.0 || abs(sq.y) > 1.0) break;
            float sd = depthAt(sq);
            if (sd >= 1.0) {
                float trust = edgeFade(sq);
                sky = mix(sky, texture(SceneSampler, sq * 0.5 + 0.5).rgb, trust);
                open = mix(open, 1.0, trust);
                break;
            }
            vec3 q = unproject(sq, sd);
            float along = dot(q - p, r);
            if (along <= 0.5 || q.y + Surface.x < plane - 0.1) break;
            if (i == 3 || abs(along - t) < 0.02 * along) {
                float trust = (1.0 - smoothstep(0.02, 0.06, length(cross(q - p, r)) / along)) * edgeFade(sq);
                sky = mix(sky, texture(SceneSampler, sq * 0.5 + 0.5).rgb, trust);
                open = mix(open, 1.0, trust);
                break;
            }
            t = along;
        }
    }
    vec3 raw = mix(sky, hit, max(fade, 1.0 - Horizon.w));
    float known = mix(open * Horizon.w, 1.0, fade);

    float facing = clamp(-dir.y, 0.0, 1.0);
    float graze = 1.0 - facing;
    float fresnel = 0.08 + 0.92 * graze * graze * graze;
    float fog = clamp((length(p) - Surface.z) / max(Surface.w - Surface.z, 0.001), 0.0, 1.0);
    float weight = clamp(Surface.y * fresnel, 0.0, 1.0) * mix(known, 1.0, 0.6 * Horizon.w);
    fragColor = vec4(mix(raw, Horizon.rgb, fog), max(weight, 1.0 / 255.0));
}
