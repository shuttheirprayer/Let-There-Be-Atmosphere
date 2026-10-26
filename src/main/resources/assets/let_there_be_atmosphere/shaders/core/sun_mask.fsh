#version 150

uniform sampler2D DepthSampler;
uniform sampler2D CloudSampler;
uniform sampler2D NoiseSampler;
uniform sampler2D HeightSampler;
uniform sampler2D CoverSampler;
uniform float Cover;
uniform mat4 InverseTransform;
uniform vec4 Cloud;
uniform vec2 CloudGrid;
uniform vec4 Sun;
uniform vec4 Ray;
uniform vec4 Mist;
uniform vec4 MistDrift;
uniform vec4 Ground;
uniform vec4 GroundOrigin;
uniform vec4 Puddle;
uniform vec2 HeightCenter;
uniform vec4 Wet;
uniform vec4 Occlusion;
uniform mat4 ViewProjection;
uniform vec4 SunShadow;

in vec2 texCoord;

out vec4 fragColor;

vec3 pointAt(ivec2 pixel) {
    ivec2 size = textureSize(DepthSampler, 0);
    pixel = clamp(pixel, ivec2(0), size - 1);
    float d = texelFetch(DepthSampler, pixel, 0).r;
    vec4 w = InverseTransform * vec4((vec2(pixel) + 0.5) / vec2(size) * 2.0 - 1.0, min(d, 0.9999) * 2.0 - 1.0, 1.0);
    return w.xyz / w.w;
}

vec2 column(vec2 block) {
    vec2 area = floor(block / 16.0) - HeightCenter;
    if (area.x < -4.0 || area.y < -4.0 || area.x > 3.0 || area.y > 3.0) return vec2(-1.0e6, 0.0);
    float top = texelFetch(HeightSampler, ivec2(mod(block, 128.0)), 0).r;
    top -= top > 10000.0 ? 16384.0 : 0.0;
    return top > 2048.0 ? vec2(top - 4096.0, 1.0) : vec2(top, 0.0);
}

float columnTop(vec2 block) {
    return column(block).x;
}

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy) * 2, 0).r;
    vec4 world = InverseTransform * vec4(texCoord * 2.0 - 1.0, min(depth, 0.9999) * 2.0 - 1.0, 1.0);
    vec3 p = world.xyz / world.w;
    if (depth >= 1.0) {
        vec2 offset = (texCoord - Ray.xy) * vec2(Ray.z, 1.0);
        float open = Cover > 0.5 ? 1.0 - 0.9 * texture(CoverSampler, texCoord).a : 1.0;
        fragColor = vec4(Ray.z > 0.0 ? exp(-dot(offset, offset) * 3.0) * open : 0.0, 1.0, 1.0, 1.0);
        return;
    }
    if (Cloud.z > 4.0 && p.y > Cloud.z - 2.0 - length(p) * 0.03) {
        fragColor = vec4(0.0, 1.0, 1.0, 1.0);
        return;
    }
    ivec2 centre = ivec2(gl_FragCoord.xy) * 2;
    float closeness = length(p);
    vec3 c = p;
    vec3 normal = vec3(0.0, 1.0, 0.0);
    float upright = 1.0;
    if ((Puddle.w > 0.0 && closeness < 64.0) || Wet.x > 0.0 || (Occlusion.x > 0.0 && closeness < 64.0) || (SunShadow.z > 0.0 && closeness < 48.0 && Ground.w > 0.5)) {
        c = pointAt(centre);
        vec3 right = pointAt(centre + ivec2(1, 0)) - c, left = c - pointAt(centre - ivec2(1, 0));
        vec3 up = pointAt(centre + ivec2(0, 1)) - c, down = c - pointAt(centre - ivec2(0, 1));
        normal = cross(dot(right, right) < dot(left, left) ? right : left, dot(up, up) < dot(down, down) ? up : down);
        upright = abs(normal.y) / max(length(normal), 1.0e-6);
    }
    vec2 local = Ground.xy + p.xz;
    float exposed = 1.0;
    float soil = 1.0;
    float dry = 0.0;
    vec2 block = floor(local) + GroundOrigin.xy;
    vec2 chunk = floor(block / 16.0) - HeightCenter;
    if (Ground.w > 0.5 && chunk.x >= -4.0 && chunk.y >= -4.0 && chunk.x <= 3.0 && chunk.y <= 3.0) {
        vec2 column = mod(block, 128.0);
        float top = texelFetch(HeightSampler, ivec2(column), 0).r;
        dry = top > 10000.0 ? 1.0 : 0.0;
        top -= dry * 16384.0;
        float leafy = top > 2048.0 ? 1.0 : 0.0;
        soil = (1.0 - leafy) * (1.0 - dry);
        exposed = step(top - leafy * 4096.0 - 0.95, Ground.z + p.y);
    }
    float shade = 1.0;
    if (Cloud.w > 0.0) {
        float fade = clamp((Sun.w - length(p)) / (Sun.w - Sun.z), 0.0, 1.0) * clamp((Cloud.z - p.y) * 0.125, 0.0, 1.0);
        if (fade > 0.0) {
            vec2 hit = p.xz + vec2(Sun.x, 0.0) * ((Cloud.z - p.y) / Sun.y);
            float cover = smoothstep(0.5 - Ray.w, 0.5 + Ray.w, texture(CloudSampler, (Cloud.xy + hit / CloudGrid.x) / CloudGrid.y).a);
            shade = 1.0 - Cloud.w * cover * fade * exposed;
        }
    }
    float distance = length(p);
    float mist = 0.0;
    if (Mist.y > 0.0) {
        float h0 = clamp(-Mist.x, -4.0, 60.0);
        float h1 = clamp(p.y - Mist.x, -4.0, 60.0);
        float e0 = exp(-Mist.z * h0);
        float e1 = exp(-Mist.z * h1);
        float rise = h1 - h0;
        float average = abs(rise) > 0.01 ? (e0 - e1) / (Mist.z * rise) : e0;
        float wisp = texture(NoiseSampler, MistDrift.xy + p.xz * (0.5 * MistDrift.z)).r;
        mist = min(1.0 - exp(-Mist.y * distance * average * mix(1.0, wisp * 1.8, Mist.w)), 0.98);
    }
    float puddle = 0.0;
    if (Puddle.w > 0.0 && exposed * soil > 0.5 && distance < 64.0) {
        vec2 n = texture(NoiseSampler, Puddle.xy + local * Puddle.z).rg;
        float field = mix(n.r, n.g, 0.3);
        puddle = smoothstep(Puddle.w, Puddle.w + 0.035, field) * smoothstep(0.92, 0.98, upright) * (1.0 - smoothstep(32.0, 64.0, distance));
        if (GroundOrigin.w > 0.0 && puddle > 0.0 && distance < 24.0) {
            float ring = 0.0;
            for (int layer = 0; layer < 2; layer++) {
                vec2 cellPos = (local + mod(GroundOrigin.xy, 64.0) + float(layer) * vec2(0.31, 0.47)) / 0.6;
                vec2 cell = mod(floor(cellPos), 256.0);
                float seed = fract(sin(dot(cell + float(layer) * 19.0, vec2(12.9898, 78.233))) * 43758.5453);
                vec2 spot = 0.25 + 0.5 * vec2(seed, fract(seed * 7.13));
                float phase = fract(GroundOrigin.z * 1.6 + seed + float(layer) * 0.5);
                float r = length(fract(cellPos) - spot);
                ring = max(ring, (1.0 - smoothstep(0.0, 0.05, abs(r - phase * 0.26))) * (1.0 - phase) * (1.0 - phase));
            }
            puddle = min(puddle * (0.75 + 1.1 * ring * GroundOrigin.w * (1.0 - distance / 24.0)), 0.98);
        }
    }
    if (Wet.x > 0.0) {
        vec3 n = normal / max(length(normal), 1.0e-6);
        n *= -sign(dot(n, p));
        float wet = n.y > -0.4 ? 1.0 : 0.0;
        vec2 dampBlock = floor(Ground.xy + p.xz + n.xz * 0.1) + GroundOrigin.xy;
        vec2 dampChunk = floor(dampBlock / 16.0) - HeightCenter;
        if (wet > 0.0 && Ground.w > 0.5 && dampChunk.x >= -4.0 && dampChunk.y >= -4.0 && dampChunk.x <= 3.0 && dampChunk.y <= 3.0) {
            vec2 roof = texelFetch(HeightSampler, ivec2(mod(dampBlock, 128.0)), 0).rg;
            float roofTop = roof.r > 10000.0 ? roof.r - 16384.0 : roof.r;
            roofTop -= roofTop > 2048.0 ? 4096.0 : 0.0;
            wet = roof.g > -10000.0 ? step(roofTop - 1.0, Ground.z + p.y + n.y * 0.1) : 0.0;
        }
        wet *= (1.0 - dry) * clamp((Sun.w - distance) / max(Sun.w - Sun.z, 1.0), 0.0, 1.0);
        shade *= 1.0 - Wet.y * wet;
        puddle = max(puddle, Wet.z * wet * smoothstep(0.92, 0.98, upright) * (1.0 - smoothstep(32.0, 64.0, distance)));
    }
    if (Occlusion.x > 0.0 && distance < 64.0) {
        vec3 n = normal / max(length(normal), 1.0e-6);
        n *= -sign(dot(n, c));
        float reach = clamp(Occlusion.y * Occlusion.z / max(length(c), 0.5), 2.0, 96.0);
        ivec2 phase = ivec2(gl_FragCoord.xy) & 1;
        float spin = float(phase.x + phase.y * 2) * 1.5707963 + 0.4;
        float hidden = 0.0;
        for (int i = 0; i < 10; i++) {
            float a = spin + float(i) * 2.3999632;
            vec2 tap = vec2(cos(a), sin(a)) * reach * sqrt((float(i) + 0.5) / 10.0);
            vec3 v = pointAt(centre + ivec2(tap)) - c;
            float len = length(v);
            hidden += clamp(dot(n, v) / max(len, 1.0e-4) - 0.1, 0.0, 1.0) * (1.0 - smoothstep(Occlusion.y, Occlusion.y * 2.0, len));
        }
        shade *= 1.0 - Occlusion.x * clamp(hidden * 0.18, 0.0, 0.7) * (1.0 - smoothstep(40.0, 64.0, distance));
    }
    if (SunShadow.z > 0.0 && distance < 48.0 && Ground.w > 0.5) {
        vec3 n = normal / max(length(normal), 1.0e-6);
        n *= -sign(dot(n, c));
        ivec2 phase = ivec2(gl_FragCoord.xy) & 1;
        vec2 wobble = vec2(phase) * 2.0 - 1.0;
        vec3 sun = vec3(SunShadow.xy, 0.0);
        vec3 dir = normalize(sun + 0.015 * (wobble.x * vec3(0.0, 0.0, 1.0) + wobble.y * vec3(-sun.y, sun.x, 0.0)));
        vec3 from = vec3(Ground.x, Ground.z, Ground.y) + c + n * (0.04 + length(c) * 0.004);
        vec2 cell = floor(from.xz);
        vec2 stride = sign(dir.xz);
        vec2 span = 1.0 / max(abs(dir.xz), vec2(1.0e-5));
        vec2 next = (stride * (cell - from.xz) + max(stride, 0.0)) * span;
        float t = 0.0;
        float dark = 0.0;
        float leaf = 0.0;
        for (int i = 0; i < 24; i++) {
            vec2 top = column(cell + GroundOrigin.xy);
            float cover = smoothstep(0.0, 0.05 + 0.06 * t, top.x - from.y - dir.y * t);
            if (top.y < 0.5) {
                dark = max(dark, cover);
            } else if (cover > leaf) {
                leaf = cover;
            }
            if (dark > 0.99) break;
            if (next.x < next.y) {
                t = next.x;
                next.x += span.x;
                cell.x += stride.x;
            } else {
                t = next.y;
                next.y += span.y;
                cell.y += stride.y;
            }
            if (t > SunShadow.w) break;
        }
        if (leaf > dark) {
            vec2 spot = mod(GroundOrigin.xy, 60.0) + from.xz + dir.xz * (4.0 / max(dir.y, 0.2));
            float field = 0.6 * texture(NoiseSampler, spot / 12.0).r + 0.4 * texture(NoiseSampler, spot / 20.0).g;
            dark = max(dark, leaf * mix(0.8, 0.0, smoothstep(0.5, 0.56, field)));
        }
        if (dark < 0.99) {
            float jitter = float(phase.x * 2 + phase.y) * 0.25;
            vec2 size = vec2(textureSize(DepthSampler, 0));
            vec3 start = c + n * (0.04 + length(c) * 0.004);
            vec4 clipStart = ViewProjection * vec4(start, 1.0);
            vec4 clipStep = ViewProjection * vec4(dir, 0.0);
            for (int i = 0; i < 12; i++) {
                float s = 0.3 + 4.0 * (float(i) + jitter) / 12.0;
                vec3 q = start + dir * s;
                vec4 clip = clipStart + clipStep * s;
                if (clip.w < 0.05) break;
                vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
                if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) break;
                float d = texelFetch(DepthSampler, ivec2(uv * size), 0).r;
                if (d >= 1.0) continue;
                vec4 h = InverseTransform * vec4(clip.xy / clip.w, min(d, 0.9999) * 2.0 - 1.0, 1.0);
                vec3 body = h.xyz / h.w;
                float behind = length(q) - length(body);
                float tol = 0.03 + length(q) * 0.006;
                float w = smoothstep(tol, tol * 2.0, behind) * (1.0 - smoothstep(0.7, 1.0, behind)) * smoothstep(0.3, 0.6, length(body - c));
                vec2 edge = min(uv, 1.0 - uv);
                w *= smoothstep(0.0, 0.06, min(edge.x, edge.y));
                if (w > dark) {
                    vec2 spot = Ground.xy + body.xz + GroundOrigin.xy;
                    float ground = max(max(columnTop(floor(spot + vec2(0.1, 0.1))), columnTop(floor(spot + vec2(-0.1, 0.1)))),
                            max(columnTop(floor(spot + vec2(0.1, -0.1))), columnTop(floor(spot + vec2(-0.1, -0.1)))));
                    w *= smoothstep(0.4, 0.6, Ground.z + body.y - ground);
                }
                dark = max(dark, w);
            }
        }
        shade *= 1.0 - min(SunShadow.z * dark, 0.75) * (1.0 - smoothstep(32.0, 48.0, distance));
    }
    fragColor = vec4(puddle, shade, mist, clamp(distance / Sun.w, 0.0, 1.0));
}
