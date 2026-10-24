#version 150

uniform mat4 Transform;
uniform vec4 Origin;
uniform vec4 Origin2;
uniform vec4 Anchor;
uniform vec4 Reach;
uniform vec2 HeightCenter;
uniform vec4 Eye;
uniform vec4 Wind;
uniform vec4 Drift0;
uniform vec4 Drift1;
uniform vec4 Drift2;
uniform vec4 Look;
uniform vec4 Counts;
uniform vec4 Tint;
uniform vec3 Right;
uniform vec3 Up;
uniform sampler2D Sampler1;
uniform sampler2D HeightSampler;

out vec2 texCoord;
out vec4 vertexColor;
out float fogDistance;
out float shape;
out vec4 lumpA;
out vec2 lumpB;

const vec3 BOX = vec3(56.0, 28.0, 56.0);
const float TAU = 6.2831853;

uint mixBits(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352dU;
    x ^= x >> 15;
    x *= 0x846ca68bU;
    x ^= x >> 16;
    return x;
}

vec4 roll(uint id, uint salt) {
    uint h = mixBits(id * 0x9E3779B1U ^ mixBits(salt * 0x85EBCA77U + 0x27D4EB2FU));
    return vec4(float(h & 255U), float((h >> 8) & 255U), float((h >> 16) & 255U), float(h >> 24)) / 255.0;
}

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    texCoord = vec2(0.0);
    vertexColor = vec4(0.0);
    fogDistance = 0.0;
    shape = 0.0;
    lumpA = vec4(0.0);
    lumpB = vec2(0.0);
}

bool surface(vec2 block, out float top, out bool leafy) {
    vec2 chunk = floor(block / 16.0) - HeightCenter;
    top = 0.0;
    leafy = false;
    if (chunk.x < -4.0 || chunk.y < -4.0 || chunk.x > 3.0 || chunk.y > 3.0) return false;
    float r = texelFetch(HeightSampler, ivec2(mod(block, 128.0)), 0).r;
    if (r > 10000.0) r -= 16384.0;
    if (r < -9000.0) return false;
    if (r > 2048.0) {
        r -= 4096.0;
        leafy = true;
    }
    top = r;
    return true;
}

float ground(vec2 where, float fallback) {
    if (Anchor.w < 0.5) return fallback;
    float sum = 0.0;
    float covered = 0.0;
    for (int i = 0; i < 4; i++) {
        vec2 p = where + vec2((i & 1) == 0 ? -3.0 : 3.0, (i & 2) == 0 ? -3.0 : 3.0) - 0.5;
        vec2 base = floor(p);
        vec2 f = p - base;
        for (int j = 0; j < 4; j++) {
            vec2 c = base + vec2(float(j & 1), float(j >> 1));
            float k = ((j & 1) == 1 ? f.x : 1.0 - f.x) * ((j >> 1) == 1 ? f.y : 1.0 - f.y);
            float top;
            bool leafy;
            if (surface(c, top, leafy)) {
                sum += top * k;
                covered += k;
            }
        }
    }
    return (sum + (4.0 - covered) * fallback) * 0.25;
}

void main() {
    int instance = gl_VertexID / 6;
    int corner = gl_VertexID - instance * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : 0.0;
    float sy = vertex >= 2 ? 1.0 : 0.0;
    int local = instance;
    int kind = 0;
    int span = int(Counts.x);
    if (local >= span) {
        local -= span;
        kind = 1;
        span = int(Counts.y);
        if (local >= span) {
            local -= span;
            kind = 2;
            span = int(Counts.z);
            if (local >= span) {
                local -= span;
                kind = 3;
                span = int(Counts.w);
                if (local >= span) {
                    local -= span;
                    kind = 4;
                    span = int(Reach.x);
                    if (local >= span) {
                        local -= span;
                        kind = 5;
                    }
                }
            }
        }
    }
    uint id = uint(local) + uint(kind) * 65537U;
    vec4 a = roll(id, 0U);
    vec4 b = roll(id, 1U);
    float t = Origin.w;
    float sand = Look.x;
    float phase = b.z * TAU;
    vec2 perp = vec2(-Wind.y, Wind.x);
    float eyeY = Anchor.z + Eye.y;

    vec3 sky = texelFetch(Sampler1, ivec2(0, 15), 0).rgb;
    float dim = mix(0.45, 1.0, smoothstep(0.12, 0.7, dot(sky, vec3(0.299, 0.587, 0.114))));
    vec3 light = mix(sky, vec3(1.0), 0.14) * dim;

    if (kind >= 3) {
        float box = kind == 4 ? 384.0 : 160.0;
        vec2 org = kind == 4 ? Origin2.zw : Origin2.xy;
        vec2 spot = a.xz * box + (kind == 3 ? Drift1.zw : (kind == 4 ? Drift2.xy : Drift2.zw));
        spot += perp * sin(t * (0.15 + 0.2 * b.w) + phase) * (kind == 5 ? 0.6 : 4.0);
        vec2 r = mod(spot - org - Eye.xz, box) - box * 0.5;
        float hd = length(r);
        float ring;
        if (kind == 3) {
            ring = smoothstep(16.0, 30.0, hd) * (1.0 - smoothstep(58.0, 80.0, hd));
        } else if (kind == 4) {
            ring = smoothstep(64.0, 95.0, hd) * (1.0 - smoothstep(150.0, 188.0, hd));
        } else {
            ring = smoothstep(10.0, 22.0, hd) * (1.0 - smoothstep(46.0, 62.0, hd));
        }
        if (ring <= 0.0) {
            hide();
            return;
        }
        vec2 where = Anchor.xy + Eye.xz + r;
        float width = 1.0;
        float height = 1.0;
        float y;
        float lumpSpread = 0.42;
        float lumpHeight = 0.36;
        float alpha = Tint.a * ring;
        float fogScale = 0.55;
        vec3 color = sand < 0.5 ? Tint.rgb * dim : Tint.rgb * light;
        if (kind == 3) {
            bool bank = b.z > 0.45;
            width = (bank ? 20.0 + 18.0 * b.y : 11.0 + 12.0 * b.y) * Look.z;
            height = width * (bank ? 0.4 : 0.8);
            y = ground(where, Reach.z) + (bank ? height * 0.32 + a.y * a.y * 2.5 : width * 0.12 + a.y * a.y * 8.0);
            if (bank) {
                lumpSpread = 0.48;
                lumpHeight = 0.14;
            }
            alpha *= 0.42 * (0.6 + 0.4 * b.x);
            color *= 0.8 + 0.3 * b.w;
        } else if (kind == 4) {
            width = (46.0 + 52.0 * b.y) * Look.z;
            height = width * 0.38;
            y = Reach.z - 6.0 + a.y * 26.0;
            alpha *= 0.36 * (0.6 + 0.4 * b.x);
            color *= 0.74 + 0.38 * b.w;
            lumpSpread = 0.48;
            lumpHeight = 0.14;
            fogScale = 0.2;
        } else {
            y = ground(where, Reach.z) + 0.5 + a.y * 5.0;
            alpha *= 0.34 * (0.5 + 0.5 * b.x);
            color *= 0.9 + 0.25 * b.w;
            fogScale = 0.5;
        }
        vec3 rel = vec3(r.x, y - eyeY, r.y);
        float dist = length(rel);
        vec3 world;
        if (kind == 5) {
            vec3 along = normalize(vec3(Wind.x, -Wind.w * 0.04, Wind.y));
            float len = 3.0 + 6.0 * b.y;
            float w = 0.16 + 0.34 * b.x;
            float pixel = Eye.w * dist;
            float thin = 1.0;
            if (w < pixel * 1.3) {
                thin = w / (pixel * 1.3);
                w = pixel * 1.3;
            }
            vec3 side = cross(along, rel);
            float sideLength = length(side);
            if (sideLength < 1.0e-4) {
                hide();
                return;
            }
            side /= sideLength;
            world = rel + along * (sx - 0.5) * len + side * (sy - 0.5) * w;
            alpha *= thin;
            shape = 2.0;
        } else {
            alpha *= smoothstep(width * 0.55, width * 1.25, dist);
            world = rel + Right * (sx - 0.5) * width + Up * (sy - 0.5) * height;
            shape = 1.0;
            float tt = t * (kind == 4 ? 0.05 : 0.12);
            lumpA = vec4(cos(tt + phase) * lumpSpread, sin(tt * 1.3 + phase * 2.0) * lumpHeight,
                    cos(tt * 0.8 + phase + 2.1) * lumpSpread, sin(tt * 0.9 + phase * 3.0 + 1.0) * lumpHeight);
            lumpB = vec2(cos(tt * 1.1 + phase + 4.2) * lumpSpread, sin(tt + phase * 5.0 + 2.0) * lumpHeight);
        }
        texCoord = vec2(sx, sy);
        vertexColor = vec4(color, alpha);
        gl_Position = Transform * vec4(world + Eye.xyz, 1.0);
        fogDistance = length(world) * fogScale;
        return;
    }

    float speed = Wind.z * (0.75 + 0.5 * b.x) * (kind == 2 ? 0.35 : (kind == 1 && sand < 0.5 ? 0.85 : 1.0));
    float fall = Wind.w * (0.7 + 0.6 * b.y) * (kind == 2 ? 0.0 : 1.0);
    vec3 vel = vec3(Wind.x * speed, -fall, Wind.y * speed);
    vec3 spot = a.xyz * BOX + vec3(0.0, -fall * t, 0.0);
    spot.xz += kind == 0 ? Drift0.xy : (kind == 1 ? Drift0.zw : Drift1.xy);
    spot.xz += perp * sin(t * (0.9 + 0.8 * b.w) + phase) * Look.y;
    spot.y += sin(t * (1.3 + b.x) + phase * 1.7) * Look.y * 0.5;
    vec3 rel = mod(spot - Origin.xyz - Eye.xyz, BOX) - BOX * 0.5;
    float edge = max(abs(rel.x), abs(rel.z)) / (BOX.x * 0.5);
    if (kind == 2) {
        rel.y = -1.7 + pow(a.y, 2.0) * 8.0 + sin(t * 0.4 + phase) * 0.4;
    } else {
        edge = max(edge, abs(rel.y) / (BOX.y * 0.5));
    }
    float edgeFade = 1.0 - smoothstep(0.7, 1.0, edge);
    float dist = length(rel);
    if (edgeFade <= 0.0 || dist < 0.3) {
        hide();
        return;
    }
    if (Anchor.w > 0.5) {
        float top;
        bool leafy;
        if (surface(floor(Anchor.xy + Eye.xz + rel.xz), top, leafy) && !leafy) {
            edgeFade *= smoothstep(top - 3.0, top - 0.5, eyeY + rel.y);
            if (edgeFade <= 0.0) {
                hide();
                return;
            }
        }
    }
    float pixel = Eye.w * dist;

    vec3 world;
    float alpha = Tint.a * edgeFade;
    vec3 color = Tint.rgb * light;
    shape = 0.0;
    lumpA = vec4(0.0);
    lumpB = vec2(0.0);

    if (kind == 2) {
        float size = (3.5 + 5.0 * b.y) * Look.z;
        world = rel + Right * (sx - 0.5) * size + Up * (sy - 0.5) * size;
        texCoord = vec2(sx, sy);
        shape = 1.0;
        float tt = t * 0.12;
        lumpA = vec4(cos(tt + phase) * 0.42, sin(tt * 1.3 + phase * 2.0) * 0.36,
                cos(tt * 0.8 + phase + 2.1) * 0.42, sin(tt * 0.9 + phase * 3.0 + 1.0) * 0.36);
        lumpB = vec2(cos(tt * 1.1 + phase + 4.2) * 0.42, sin(tt + phase * 5.0 + 2.0) * 0.36);
        alpha *= 0.5 * (0.6 + 0.4 * b.x) * smoothstep(size * 0.3, size * 0.85, dist);
        color = sand < 0.5 ? Tint.rgb * dim * (0.8 + 0.3 * b.w) : color * (0.8 + 0.3 * b.w);
    } else if (sand < 0.5) {
        float size = max(Look.z * (kind == 0 ? 0.09 + 0.04 * b.y : 0.17 + 0.07 * b.y), 2.4 * pixel);
        world = rel + Right * (sx - 0.5) * size + Up * (sy - 0.5) * size;
        int variant = int(b.z * 3.99);
        texCoord = vec2((4.0 + float(variant) * 4.0 + sx * 3.0) / 32.0, (sy > 0.5 ? 0.0 : 3.0) / 8.0);
        alpha *= (kind == 0 ? 0.9 : 0.85) * smoothstep(0.6, 1.8, dist);
    } else if (kind == 0) {
        vec3 along = normalize(vec3(vel.x, vel.y * 0.3, vel.z));
        float len = 0.18 + speed * 0.012;
        float width = 0.012;
        float thin = 1.0;
        if (width < pixel * 1.3) {
            thin = width / (pixel * 1.3);
            width = pixel * 1.3;
        }
        vec3 side = cross(along, rel);
        float sideLength = length(side);
        if (sideLength < 1.0e-4) {
            hide();
            return;
        }
        side /= sideLength;
        world = rel + along * (sx - 0.5) * len + side * (sy - 0.5) * width;
        texCoord = vec2(2.0 / 32.0, 2.0 / 8.0);
        alpha *= 0.8 * thin * smoothstep(0.6, 1.8, dist);
        color *= 0.82 + 0.3 * b.w;
    } else {
        float size = Look.z * (0.07 + 0.15 * b.y * b.y);
        float spin = phase + t * (1.0 + 2.0 * b.x);
        vec2 corner = vec2(sx - 0.5, sy - 0.5);
        corner = vec2(corner.x * cos(spin) - corner.y * sin(spin), corner.x * sin(spin) + corner.y * cos(spin));
        world = rel + Right * corner.x * size + Up * corner.y * size;
        texCoord = vec2(2.0 / 32.0, 2.0 / 8.0);
        alpha *= (0.3 + 0.35 * b.x) * smoothstep(0.6, 1.8, dist);
        color = mix(color, light, 0.25 * b.w);
    }
    vertexColor = vec4(color, alpha);
    gl_Position = Transform * vec4(world + Eye.xyz, 1.0);
    fogDistance = length(world);
}
