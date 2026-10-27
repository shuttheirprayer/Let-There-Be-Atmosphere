#version 150

uniform mat4 Transform;
uniform vec3 Right;
uniform vec3 Up;
uniform vec3 Offset;
uniform vec4 Clock;
uniform vec4 Sprites[17];
uniform sampler2D DataSampler;
uniform sampler2D Sampler1;

out vec2 texCoord;
out vec4 vertexColor;
out float fogDistance;
out vec3 spot;

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    texCoord = vec2(0.0);
    vertexColor = vec4(0.0);
    fogDistance = 0.0;
    spot = vec3(0.0);
}

vec3 netherHue(int look) {
    return look == 1 ? vec3(1.0, 0.3, 0.16) : look == 2 ? vec3(0.35, 0.86, 1.0) : look == 3 ? vec3(0.25, 1.0, 0.75)
            : look == 4 ? vec3(1.0, 0.8, 0.55) : vec3(1.0, 0.52, 0.12);
}

void nether(int kind, float t, float f, vec3 h, vec3 pos, vec4 b, int vertex) {
    int tag = int(b.y);
    bool flake = kind == 22 && tag >= 8;
    vec3 hue = netherHue(tag & 7);
    float fade = min(1.0, min(f * 5.0, (1.0 - f) * 3.0));
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    vec3 across = Right;
    vec3 along = Up;
    float ex;
    float ey;
    float extra = 0.0;
    vec3 color;
    float alpha;
    int packed = int(b.z + 0.5);
    if (kind == 20) {
        pos += vec3(sin(t * 0.7 + h.x * 6.283) * 0.4 + (h.x - 0.5) * 0.1 * t,
                t * (0.16 + 0.22 * h.y) + sin(t * 1.9 + h.z * 6.283) * 0.08,
                cos(t * 0.6 + h.y * 6.283) * 0.4 + (h.z - 0.5) * 0.1 * t);
        extra = 0.5 + 0.5 * sin(t * (2.2 + 2.5 * h.x) + h.y * 6.283);
        ex = ey = 0.1 + 0.05 * h.y;
        alpha = fade * (0.6 + 0.4 * extra);
        color = hue * (0.85 + 0.3 * extra);
    } else if (kind == 21) {
        pos += vec3(sin(t * (1.1 + 0.6 * h.y) + h.x * 6.283) * 0.45 + (h.z - 0.5) * 0.2 * t, -(0.3 + 0.25 * h.y) * t,
                cos(t * 0.9 + h.z * 6.283) * 0.45 + (h.x - 0.5) * 0.2 * t);
        ey = 0.075 + 0.04 * h.z;
        ex = ey * (0.3 + 0.7 * abs(cos(t * (1.4 + h.y) + h.x * 6.283)));
        int block = packed / 16;
        extra = dot(texelFetch(Sampler1, ivec2(block, packed - block * 16), 0).rgb, vec3(0.3, 0.5, 0.2));
        float heat = (1.0 - smoothstep(0.15, 0.9, f)) * (0.7 + 0.3 * sin(t * 7.0 + h.x * 40.0));
        color = mix(vec3(0.3, 0.05, 0.03), hue * 1.2, heat);
        alpha = fade;
    } else {
        float heading = float(packed) / 256.0 * 6.283;
        vec3 wind = vec3(cos(heading), 0.0, sin(heading));
        vec3 side = vec3(-wind.z, 0.0, wind.x);
        float speed = kind == 23 ? 7.0 + 6.0 * h.x : flake ? 4.5 + 3.0 * h.x : 3.0 + 2.5 * h.x;
        float rise = (h.y - 0.35) * 1.6;
        float w1 = 2.3 + h.z;
        float w2 = 3.1 + h.x;
        pos += wind * speed * t + side * sin(w1 * t + h.y * 6.283) * 0.6 + vec3(0.0, rise * t + sin(w2 * t + h.z * 6.283) * 0.3, 0.0);
        vec3 vel = wind * speed + side * cos(w1 * t + h.y * 6.283) * 0.6 * w1 + vec3(0.0, rise + cos(w2 * t + h.z * 6.283) * 0.3 * w2, 0.0);
        if (kind == 23) {
            vec2 screen = vec2(dot(vel, Right), dot(vel, Up));
            float sl = length(screen);
            vec2 d = sl > 0.01 ? screen / sl : vec2(1.0, 0.0);
            across = Right * d.x + Up * d.y;
            along = Up * d.x - Right * d.y;
            ex = 0.09 + 0.07 * sl;
            ey = 0.065;
            color = hue * 1.1;
            alpha = fade * (0.75 + 0.25 * h.z);
        } else if (flake) {
            ey = 0.1 + 0.05 * h.z;
            ex = ey * (0.3 + 0.7 * abs(cos(t * (3.0 + h.y) + h.x * 6.283)));
            extra = 0.9;
            color = mix(vec3(0.3, 0.05, 0.03), hue * 1.2, (1.0 - smoothstep(0.1, 0.7, f)) * (0.7 + 0.3 * sin(t * 9.0 + h.x * 40.0)));
            alpha = fade;
        } else {
            extra = 0.5 + 0.5 * sin(t * 9.0 + h.y * 6.283);
            ey = 0.26 + 0.14 * h.z;
            ex = ey * (0.45 + 0.55 * abs(cos(t * (2.0 + h.y) + h.x * 6.283)));
            color = hue * (0.9 + 0.2 * extra);
            alpha = fade;
        }
    }
    vec3 world = pos + across * (sx * ex) + along * (sy * ey);
    gl_Position = Transform * vec4(world, 1.0);
    texCoord = vec2(extra, h.x);
    vertexColor = vec4(color, alpha * (kind >= 22 ? smoothstep(1.5, 4.0, length(pos + Offset)) : 1.0) * (1.0 - smoothstep(40.0, 48.0, length(pos + Offset))));
    if (length(pos + Offset) > 48.0) gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    fogDistance = length(world + Offset) * (kind == 21 || flake ? 1.0 : 0.55);
    spot = vec3(sx, sy, flake ? 21.0 : float(kind));
}

vec3 endHue(float pick) {
    return pick < 0.4 ? vec3(0.8, 0.58, 1.0) : pick < 0.65 ? vec3(1.0, 0.52, 0.9) : pick < 0.85 ? vec3(0.42, 0.95, 1.0) : vec3(0.95, 0.9, 1.0);
}

void endMote(int kind, float t, float f, vec3 h, vec3 pos, float variant, int vertex) {
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    vec3 lift;
    float size;
    float alpha;
    float frame = 0.0;
    float look = 28.0;
    vec3 color;
    float squish = 0.0;
    if (kind == 28 && variant > 0.5) {
        pos += vec3(sin(t * 0.9 + h.x * 6.283) * 0.5 + (h.x - 0.5) * 0.2 * t,
                -(0.22 + 0.12 * h.y) * t + sin(t * 1.7 + h.z * 6.283) * 0.1,
                cos(t * 0.8 + h.y * 6.283) * 0.5 + (h.z - 0.5) * 0.2 * t);
        frame = 0.0;
        size = 0.05 + 0.03 * h.z;
        alpha = min(1.0, min(f * 6.0, (1.0 - f) * 4.0)) * 0.9;
        color = h.x < 0.6 ? vec3(1.0, 0.45, 0.78) : vec3(0.95, 0.35, 0.9);
        squish = smoothstep(0.7, 1.0, f);
    } else if (kind == 28) {
        pos += vec3(sin(t * 0.31 + h.x * 6.283) * 0.6 + (h.x - 0.5) * 0.12 * t,
                sin(t * 0.23 + h.y * 6.283) * 0.3 + 0.05 * t,
                cos(t * 0.27 + h.z * 6.283) * 0.6 + (h.z - 0.5) * 0.12 * t);
        frame = 0.5 + 0.5 * sin(t * (1.5 + 2.5 * h.y) + h.x * 6.283);
        size = 0.08 + 0.05 * h.z;
        alpha = min(1.0, min(f * 5.0, (1.0 - f) * 4.0)) * (0.65 + 0.35 * frame);
        color = endHue(h.x);
        squish = smoothstep(0.75, 1.0, f);
    } else if (kind == 29) {
        float height = 0.9 + 1.4 * h.y;
        pos += vec3(sin(t * 0.8 + h.z * 6.283) * 0.05, (0.25 + 0.25 * h.x) * t + height, cos(t * 0.7 + h.x * 6.283) * 0.05);
        size = 0.06 + 0.08 * h.z;
        alpha = sin(f * 3.14159) * (0.22 + 0.14 * h.x);
        color = mix(vec3(0.35, 1.0, 0.88), vec3(0.75, 0.55, 1.0), h.x * h.x);
        look = 29.0;
        lift = vec3(0.0, height, 0.0);
    } else if (kind == 32) {
        float life = f > 0.001 ? t / f : 100.0;
        float pop = 1.0 - clamp((life - t) / 0.2, 0.0, 1.0);
        float wobble = 0.04 + 0.1 * (1.0 - exp(-0.35 * t));
        pos += vec3(sin(t * (1.9 + h.y) + h.x * 6.283) * wobble + (h.x - 0.5) * 0.04 * t,
                (0.55 + 0.2 * variant) * t,
                cos(t * (1.6 + h.x) + h.z * 6.283) * wobble + (h.z - 0.5) * 0.04 * t);
        frame = pop;
        size = (0.04 + 0.045 * h.z) * (1.0 + 0.03 * t) * (1.0 + 0.9 * pop);
        alpha = min(1.0, t * 6.0) * (1.0 - pop * pop) * 0.9;
        color = vec3(0.8, 0.96, 1.0);
        look = 32.0;
    } else if (kind == 33) {
        pos += vec3(sin(t * 0.27 + h.x * 6.283) * 0.7 + (h.x - 0.5) * 0.1 * t,
                sin(t * 0.2 + h.y * 6.283) * 0.35 + 0.04 * t,
                cos(t * 0.23 + h.z * 6.283) * 0.7 + (h.z - 0.5) * 0.1 * t);
        frame = 0.5 + 0.5 * sin(t * (0.8 + 1.2 * h.y) + h.x * 6.283);
        size = 0.06 + 0.03 * h.z;
        alpha = min(1.0, min(f * 4.0, (1.0 - f) * 3.0)) * (0.3 + 0.4 * frame);
        color = h.z < 0.7 ? vec3(0.12, 0.72, 0.78) : vec3(0.3, 0.9, 0.85);
    } else if (kind == 34) {
        size = 4.5 * (0.3 + 0.7 * sqrt(f));
        alpha = (1.0 - f) * (1.0 - f) * 0.7;
        color = vec3(0.1, 0.8, 0.85);
        look = 30.0;
    } else if (kind == 35) {
        pos += vec3(sin(t * 0.9 + h.x * 6.283) * 0.18, (0.35 + 0.35 * h.y) * t, cos(t * 0.8 + h.z * 6.283) * 0.18);
        frame = 0.5 + 0.5 * sin(t * (2.0 + 2.0 * h.y) + h.x * 6.283);
        size = 0.07 + 0.045 * h.z;
        alpha = min(1.0, min(f * 5.0, (1.0 - f) * 2.5)) * (0.55 + 0.45 * frame);
        color = h.x < 0.45 ? vec3(0.4, 1.0, 0.9) : h.x < 0.85 ? vec3(0.78, 0.58, 1.0) : vec3(0.95, 0.92, 1.0);
    } else if (kind == 36) {
        float axis = length(pos.xz + Offset.xz);
        float height = 2.6;
        pos.y += height;
        size = 0.9;
        frame = 0.5 + 0.5 * sin(t * 1.3 + h.x * 6.283);
        alpha = sin(f * 3.14159) * 0.28 * smoothstep(1.2, 2.8, axis);
        color = vec3(1.0);
        look = 36.0;
        lift = vec3(0.0, height, 0.0);
    } else if (kind == 30 && variant > 0.5) {
        size = (variant > 1.5 ? 3.4 : 1.9) * (0.35 + 0.65 * sqrt(f));
        alpha = (1.0 - f) * (1.0 - f);
        color = vec3(0.45, 1.0, 0.88);
        look = 30.0;
    } else if (kind == 30) {
        float angle = h.x * 6.283;
        float spread = 0.4 + 0.9 * h.y;
        float climb = 2.0 + 2.2 * h.z;
        float k = (1.0 - exp(-1.6 * t)) / 1.6;
        pos += vec3(cos(angle) * spread * k, climb * k + 0.15 * t, sin(angle) * spread * k);
        frame = 1.0 - f;
        size = 0.09 + 0.06 * h.z;
        alpha = 1.0 - f * f;
        color = h.y < 0.45 ? vec3(0.4, 1.0, 0.9) : h.y < 0.8 ? vec3(0.8, 0.6, 1.0) : vec3(0.95, 0.95, 1.0);
    } else {
        pos += vec3(sin(t * 0.29 + h.x * 6.283) * 1.2 + sin(t * 1.1 + h.y * 6.283) * 0.15,
                sin(t * 0.21 + h.z * 6.283) * 0.5 + sin(t * 1.5 + h.x * 9.0) * 0.08,
                cos(t * 0.25 + h.y * 6.283) * 1.2 + cos(t * 0.9 + h.z * 6.283) * 0.15);
        float blink = smoothstep(-0.2, 0.9, sin(t * (0.8 + h.x * 0.7) + h.y * 6.283));
        frame = blink;
        size = 0.06 + 0.04 * blink;
        alpha = min(1.0, min(f * 5.0, (1.0 - f) * 5.0)) * (0.35 + 0.65 * blink);
        color = h.z < 0.6 ? vec3(0.4, 1.0, 0.88) : vec3(0.78, 0.6, 1.0);
    }
    if (look != 29.0 && look != 36.0) lift = Up * size;
    vec3 world = pos + Right * (sx * size * (1.0 - 0.85 * squish)) + lift * (sy * (1.0 + 0.7 * squish));
    gl_Position = Transform * vec4(world, 1.0);
    texCoord = vec2(frame, 0.0);
    fogDistance = length(world + Offset);
    if (kind == 28 || kind == 31 || kind == 32 || kind == 33 || kind == 35) alpha *= smoothstep(1.5, 3.5, length(pos + Offset));
    alpha *= 1.0 - smoothstep(40.0, 48.0, length(pos + Offset));
    if (length(pos + Offset) > 48.0) gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    vertexColor = vec4(color, alpha);
    spot = vec3(sx, sy, look);
}

void drip(float t, float f, vec3 h, vec3 pos, vec4 b, int vertex) {
    float angle = h.x * 6.283;
    float push = 0.1 + 0.3 * h.y;
    pos += vec3(cos(angle) * push * t, -3.0 * t * t, sin(angle) * push * t);
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    float size = 0.06 + 0.02 * h.z;
    vec3 world = pos + Right * (sx * size * 0.6) + Up * (sy * size);
    gl_Position = Transform * vec4(world, 1.0);
    int packed = int(b.z + 0.5);
    int block = packed / 16;
    vec3 light = texelFetch(Sampler1, ivec2(block, packed - block * 16), 0).rgb;
    vertexColor = vec4(max(light, vec3(0.45)), min(1.0, t * 25.0) * (1.0 - smoothstep(0.9, 1.0, f)));
    texCoord = vec2(0.0);
    fogDistance = length(world + Offset);
    spot = vec3(sx, sy, 26.0);
    if (length(pos + Offset) > 24.0) gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
}

void main() {
    int slot = gl_VertexID / 6;
    int corner = gl_VertexID - slot * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    float t = Clock.x - a.w;
    if (b.w <= 0.0 || t < 0.0 || t > b.w) {
        hide();
        return;
    }
    float f = t / b.w;
    vec3 h = fract(vec3(b.y, b.y * 7.13, b.y * 13.7));
    float fade = min(1.0, min(f * 6.0, (1.0 - f) * 4.0));
    vec3 pos = a.xyz;
    float size;
    float alpha;
    vec3 tint;
    int sprite = int(h.z * 2.99);
    int kind = int(b.x + 0.5);
    if (kind >= 20 && kind <= 23) {
        nether(kind, t, f, h, pos, b, vertex);
        return;
    }
    if (kind == 26) {
        drip(t, f, h, pos, b, vertex);
        return;
    }
    if (kind >= 28 && kind <= 36) {
        endMote(kind, t, f, h, pos, floor(b.y), vertex);
        return;
    }
    if (kind == 0) {
        pos += vec3(sin(t * 0.55 + h.x * 6.283), sin(t * 0.4 + h.y * 6.283) * 0.5, cos(t * 0.5 + h.z * 6.283)) * 0.25
                + (h - vec3(0.5, 0.45, 0.5)) * t * 0.06;
        size = 0.03 + h.y * 0.04;
        alpha = (0.6 + 0.3 * h.z) * fade;
        tint = vec3(0.75 + 0.2 * h.x) * vec3(1.0, 0.95, 0.86);
    } else if (kind == 1) {
        float bell = sin(f * 3.14159);
        size = (0.12 + h.y * 0.08) * (0.4 + 0.8 * bell);
        alpha = bell;
        tint = vec3(1.0, 0.82, 1.0);
        sprite = 3 + min(int(f * 8.0), 7);
    } else if (kind == 2) {
        pos += vec3(sin(t * 1.6 + h.x * 6.283) * 0.06 + (h.x - 0.5) * 0.08 * t, t * (0.12 + h.y * 0.12), (h.z - 0.5) * 0.08 * t);
        size = 0.04 + h.y * 0.04;
        alpha = 0.85 * fade * (0.75 + 0.25 * sin(t * 3.0 + h.x * 6.283));
        tint = vec3(0.3, 0.85, 0.9);
    } else if (kind == 3) {
        size = 0.08 + f * 0.16 + h.y * 0.04;
        alpha = 0.75 * (1.0 - f);
        tint = vec3(0.85, 0.9, 1.0);
        sprite = 11 + min(int(f * 4.0), 3);
        pos.y += size * 0.6;
    } else if (kind == 4) {
        float rise = 1.0 + h.y * 1.2;
        pos += vec3((h.x - 0.5) * 0.5 * t + sin(t * 5.0 + h.z * 6.283) * 0.08, rise * t - 0.12 * t * t,
                (h.z - 0.5) * 0.5 * t + cos(t * 4.3 + h.x * 6.283) * 0.08);
        size = (0.05 + h.z * 0.05) * (1.0 - 0.5 * f);
        alpha = min(1.0, (1.0 - f) * 3.0) * (0.75 + 0.25 * sin(t * 23.0 + h.y * 40.0));
        tint = mix(vec3(1.6, 1.25, 0.55), vec3(0.9, 0.2, 0.04), f);
        sprite = 15;
    } else if (kind == 5) {
        float lift = 0.35 * min(t, 2.0) - 0.03 * max(t - 2.0, 0.0);
        pos += vec3((h.x - 0.5) * 0.4 * t + sin(t * 0.9 + h.y * 6.283) * 0.3, lift, (h.z - 0.5) * 0.4 * t + cos(t * 0.8 + h.x * 6.283) * 0.3);
        size = 0.035 + h.y * 0.035;
        alpha = 0.8 * fade;
        tint = vec3(0.16 + 0.06 * h.x);
    } else if (kind == 6) {
        float bell = sin(f * 3.14159);
        size = 0.12 + 0.3 * f;
        alpha = bell;
        tint = vec3(1.5, 0.9, 0.35);
        sprite = 16;
        pos.y += 0.05 + 0.1 * f;
    } else if (kind == 7) {
        float angle = h.x * 6.283;
        float push = 0.6 + h.y * 1.2;
        pos += vec3(cos(angle) * push * t, (2.4 + h.z * 1.6) * t - 7.0 * t * t, sin(angle) * push * t);
        size = 0.05 + h.z * 0.03;
        alpha = 1.0 - f * f;
        tint = mix(vec3(1.5, 0.95, 0.4), vec3(1.0, 0.35, 0.08), f);
        sprite = 15;
    } else if (kind == 9) {
        pos += vec3(sin(t * 0.37 + h.x * 6.283) * 1.1 + sin(t * 1.3 + h.y * 6.283) * 0.18,
                sin(t * 0.29 + h.z * 6.283) * 0.45 + sin(t * 1.7 + h.x * 9.0) * 0.08,
                cos(t * 0.33 + h.y * 6.283) * 1.1 + cos(t * 1.1 + h.z * 6.283) * 0.18);
        float glow = smoothstep(-0.1, 0.85, sin(t * (0.9 + h.x * 0.8) + h.y * 6.283));
        size = 0.12 + 0.14 * glow;
        alpha = min(1.0, min(f * 5.0, (1.0 - f) * 5.0)) * (0.3 + 0.7 * glow);
        tint = mix(vec3(0.45, 0.8, 0.05), vec3(0.75, 1.0, 0.12), glow);
    } else if (kind == 11) {
        pos += vec3(sin(t * 0.31 + h.x * 6.283) * 0.5 + (h.x - 0.5) * 0.18 * t,
                sin(t * 0.43 + h.y * 6.283) * 0.25 + 0.02 * t,
                cos(t * 0.27 + h.z * 6.283) * 0.5 + (h.z - 0.5) * 0.18 * t);
        float glint = 0.5 + 0.5 * sin(t * (1.1 + h.y) + h.x * 6.283);
        size = 0.035 + 0.03 * h.y;
        alpha = fade * (0.3 + 0.55 * glint);
        tint = vec3(1.0, 0.96, 0.8) * (0.85 + 0.35 * glint);
    } else if (kind == 10) {
        pos += vec3(sin(t * 0.21 + h.x * 6.283) * 0.35 + (h.x - 0.5) * 0.05 * t, sin(t * 0.17 + h.y * 6.283) * 0.2 - 0.015 * t,
                cos(t * 0.19 + h.z * 6.283) * 0.35 + (h.z - 0.5) * 0.05 * t);
        size = 0.026 + h.y * 0.03;
        alpha = 0.55 * fade * (0.7 + 0.3 * sin(t * 1.3 + h.x * 6.283));
        tint = vec3(0.8, 0.95, 0.92);
        sprite = int(h.z * 2.99);
    } else if (kind == 12) {
        pos += vec3(sin(t * 0.17 + h.x * 6.283) * 0.3, -(0.1 + 0.12 * h.y) * t + sin(t * 0.23 + h.z * 6.283) * 0.1,
                cos(t * 0.15 + h.z * 6.283) * 0.3);
        size = 0.07 + h.y * 0.06;
        alpha = 0.9 * fade;
        tint = vec3(0.86, 0.93, 1.0);
        sprite = int(h.z * 2.99);
    } else {
        float rise = 0.9 - 0.25 * min(t, 2.5);
        pos += vec3((h.x - 0.5) * 0.3 * t + sin(t * 1.2 + h.y * 6.283) * 0.12, rise * t, (h.z - 0.5) * 0.3 * t);
        size = 0.2 + 0.5 * f + h.y * 0.1;
        alpha = 0.14 * min(1.0, f * 8.0) * (1.0 - f);
        tint = vec3(0.7, 0.71, 0.73);
        sprite = 16;
    }
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    vec3 world = pos + (Right * sx + Up * sy) * size;
    gl_Position = Transform * vec4(world, 1.0);
    vec4 uv = Sprites[sprite];
    texCoord = vec2(sx > 0.0 ? uv.z : uv.x, sy > 0.0 ? uv.y : uv.w);
    int packed = int(b.z + 0.5);
    int block = packed / 16;
    vec3 light = texelFetch(Sampler1, ivec2(block, packed - block * 16), 0).rgb;
    if (kind == 9) light = vec3(1.0);
    if (kind == 12) light = max(light, vec3(0.8));
    vertexColor = vec4(tint * light, alpha);
    fogDistance = kind == 9 ? 0.0 : length(world + Offset) * (kind == 12 ? 0.15 : 1.0);
    spot = vec3(sx, sy, kind == 9 ? 1.0 : kind == 11 ? 2.0 : 0.0);
}
