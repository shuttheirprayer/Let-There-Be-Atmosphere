#version 150

uniform mat4 Transform;
uniform vec4 Origin;
uniform vec4 Eye;
uniform vec4 Fall;
uniform vec4 Shape;
uniform vec2 HeightCenter;
uniform vec3 Right;
uniform vec4 Range;
uniform vec4 Reach;
uniform vec4 Lag;
uniform sampler2D Sampler1;
uniform sampler2D HeightSampler;

out vec2 texCoord;
out vec4 vertexColor;
out float fogDistance;

const int RADIUS = 20;
const int SIDE = RADIUS * 2 + 1;
const int CELLS = SIDE * SIDE;
const float TRAINS = 10.0;
const float GRAVITY = 12.0;
const vec3 COOL = vec3(0.80, 0.90, 1.0);
const int MRADIUS = 16;
const int MSIDE = MRADIUS * 2 + 1;
const float MCELL = 4.0;
const float MSPAN = 34.0;

uint mixBits(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352dU;
    x ^= x >> 15;
    x *= 0x846ca68bU;
    x ^= x >> 16;
    return x;
}

vec4 roll(ivec2 cell, int salt) {
    uint h = mixBits(uint(cell.x) * 0x9E3779B1U ^ mixBits(uint(cell.y) * 0x85EBCA77U ^ mixBits(uint(salt) + 0x27D4EB2FU)));
    return vec4(float(h & 255U), float((h >> 8) & 255U), float((h >> 16) & 255U), float(h >> 24)) / 255.0;
}

float grain(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    ivec2 c = ivec2(i);
    float a = roll(c, 401).x;
    float b = roll(c + ivec2(1, 0), 401).x;
    float d = roll(c + ivec2(0, 1), 401).x;
    float g = roll(c + ivec2(1, 1), 401).x;
    return mix(mix(a, b, f.x), mix(d, g, f.x), f.y);
}

vec2 drift(vec4 e, vec2 at) {
    vec2 s = mix(Lag.xy, Fall.xy, e.x < 0.15 ? 0.3 * e.y : 0.85 + 0.15 * e.y);
    vec2 q = at / 18.0 + vec2(Origin.w * 0.045, -Origin.w * 0.035);
    float turn = (grain(q) * 0.7 + grain(q * 2.3 + 17.0) * 0.3 - 0.5) * 2.6 * Lag.w + (fract(e.y * 7.31 + e.z * 3.7) - 0.5) * 0.7 * Lag.w;
    float cs = cos(turn), sn = sin(turn);
    s = vec2(s.x * cs - s.y * sn, s.x * sn + s.y * cs) * (0.7 + 0.6 * grain(q * 1.4 + 41.0));
    float mag = length(s);
    vec2 side = mag > 1.0e-4 ? vec2(-s.y, s.x) / mag : vec2(0.0);
    s = s * (0.8 + 0.4 * e.z) + side * (e.w - 0.5) * 0.35 * mag;
    if (e.x > 0.92) s += vec2(cos(e.y * 6.2831853), sin(e.y * 6.2831853)) * (0.06 + 0.4 * Lag.z);
    return s;
}

bool column(vec2 block, out vec2 height) {
    vec2 chunk = floor(block / 16.0) - HeightCenter;
    height = vec2(0.0);
    if (chunk.x < -4.0 || chunk.y < -4.0 || chunk.x > 3.0 || chunk.y > 3.0) return false;
    height = texelFetch(HeightSampler, ivec2(mod(block, 128.0)), 0).rg;
    if (height.x > 10000.0) height.x -= 16384.0;
    return height.y > -10000.0;
}

bool openColumn(vec2 block, out vec2 height) {
    vec2 chunk = floor(block / 16.0) - HeightCenter;
    height = vec2(0.0);
    if (chunk.x < -4.0 || chunk.y < -4.0 || chunk.x > 3.0 || chunk.y > 3.0) return false;
    height = texelFetch(HeightSampler, ivec2(mod(block, 128.0)), 0).rg;
    if (height.x > 10000.0) height.x -= 16384.0;
    return height.x > -9000.0;
}

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    texCoord = vec2(0.0);
    vertexColor = vec4(0.0);
    fogDistance = 0.0;
}

void distant(int local, int vertex) {
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : 0.0;
    float sy = vertex >= 2 ? 1.0 : 0.0;
    vec3 eye = Eye.xyz;
    float eyeY = Origin.z + Eye.y;
    vec3 sky = texelFetch(Sampler1, ivec2(0, 15), 0).rgb;
    float dim = mix(0.45, 1.0, smoothstep(0.12, 0.7, dot(sky, vec3(0.299, 0.587, 0.114))));
    vec3 light = mix(sky, vec3(1.0), Shape.w) * dim * COOL;
    int mid = int(Range.y);
    if (local < mid) {
        int per = MSIDE * MSIDE;
        int train = local / per;
        int index = local - train * per;
        ivec2 offset = ivec2(index - (index / MSIDE) * MSIDE, index / MSIDE) - ivec2(MRADIUS);
        if (offset.x * offset.x + offset.y * offset.y > MRADIUS * MRADIUS) {
            hide();
            return;
        }
        ivec2 cell = ivec2(floor(Origin.xy / MCELL)) + offset;
        vec4 a = roll(cell, 101 + train);
        if (a.w >= Reach.x * (train == 0 ? 1.0 : 0.7)) {
            hide();
            return;
        }
        vec4 b = roll(cell, 111 + train);
        vec4 c = roll(cell, 121 + train);
        vec2 spot = (vec2(cell) + a.xy) * MCELL;
        vec2 land = spot - Origin.xy;
        float d = length(land - Eye.xz);
        float ring = smoothstep(18.0, 28.0, d) * (1.0 - smoothstep(50.0, 62.0, d));
        if (ring <= 0.0) {
            hide();
            return;
        }
        vec2 here;
        float yLand = Reach.z;
        if (openColumn(floor(spot), here)) {
            if (here.y < -10000.0) {
                hide();
                return;
            }
            bool leafy = here.x > 2048.0;
            yLand = (leafy && b.z >= 0.3) ? here.x - 4096.0 : here.y;
        }
        float speed = (13.0 + 5.0 * b.x) * Fall.z;
        float period = MSPAN / speed;
        float u = fract(Origin.w / period + a.z);
        float y = yLand + MSPAN * (1.0 - u);
        vec2 tilt = drift(roll(cell, 131 + train), spot);
        vec3 head = vec3(land.x - (y - yLand) * tilt.x, y - Origin.z, land.y - (y - yLand) * tilt.y);
        vec3 rise = normalize(vec3(-tilt.x, 1.0, -tilt.y));
        vec3 tail = head + rise * (1.2 + 1.4 * b.y);
        vec3 view = head - eye;
        float dist = length(view);
        vec3 side = cross(rise, view);
        float sideLength = length(side);
        if (sideLength < 1.0e-4) {
            hide();
            return;
        }
        side /= sideLength;
        float width = 0.1;
        float pixel = Eye.w * dist * 1.4;
        float thin = 1.0;
        if (width < pixel) {
            thin = width / pixel;
            width = pixel;
        }
        float xs = sx > 0.5 ? 1.0 : -1.0;
        vec3 world = mix(head, tail, sy) + side * xs * width * 0.5;
        int variant = int(c.z * 2.99);
        texCoord = vec2((float(variant * 4) + (xs > 0.0 ? 3.0 : 0.0)) / 64.0, sy > 0.5 ? 0.0 : 1.0);
        float fade = ring * thin * smoothstep(0.0, 0.08, u) * (1.0 - smoothstep(0.94, 1.0, u));
        vertexColor = vec4(light * (0.82 + 0.18 * c.x), fade * 0.4);
            gl_Position = Transform * vec4(world, 1.0);
        fogDistance = length(world - eye) * 0.6;
        return;
    }
    hide();
}

void main() {
    int instance = gl_VertexID / 6;
    int corner = gl_VertexID - instance * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    int base = int(Range.x);
    if (instance >= base) {
        distant(instance - base, vertex);
        return;
    }
    int train = instance / CELLS;
    int cellIndex = instance - train * CELLS;
    ivec2 offset = ivec2(cellIndex - (cellIndex / SIDE) * SIDE, cellIndex / SIDE) - ivec2(RADIUS);
    if (offset.x * offset.x + offset.y * offset.y > RADIUS * RADIUS) {
        hide();
        return;
    }
    ivec2 cell = ivec2(Origin.xy) + offset;
    vec4 a = roll(cell, train);
    if (a.w >= clamp(Fall.w * TRAINS - float(train), 0.0, 1.0)) {
        hide();
        return;
    }
    vec4 b = roll(cell, train + 7);
    vec4 c = roll(cell, train + 13);
    vec2 here;
    if (!column(vec2(cell), here)) {
        hide();
        return;
    }
    bool leafy = here.x > 2048.0;
    bool through = leafy && b.z < 0.3;
    bool soaked = leafy && !through;
    float yLand = soaked ? here.x - 4096.0 : here.y;
    vec2 land = vec2(offset) + a.xy;
    float speed = (13.0 + 5.0 * b.x) * Fall.z;
    float span = Shape.z * 2.2;
    float period = span / speed;
    float u = fract(Origin.w / period + a.z);
    float eyeY = Origin.z + Eye.y;
    float yTop = yLand + span * (1.0 - u);
    float k = max(0.0, ceil((eyeY - Shape.z - yTop) / span));
    float y = yTop + k * span;
    float since = u * period;
    vec3 eye = Eye.xyz;
    vec3 sky = texelFetch(Sampler1, ivec2(0, 15), 0).rgb;
    float dim = mix(0.45, 1.0, smoothstep(0.12, 0.7, dot(sky, vec3(0.299, 0.587, 0.114))));
    vec3 light = mix(sky, vec3(1.0), Shape.w) * dim * COOL;

    if (Shape.x > 0.5 && k < 0.5 && since < 0.42 && b.w < 0.7 && abs(yLand - eyeY) < Shape.z + 4.0) {
        vec4 d = roll(cell, train + 29);
        bool crown = d.w < 0.45;
        if (crown && since >= 0.24) {
            hide();
            return;
        }
        vec3 base = vec3(land.x, yLand - Origin.z + 0.02, land.y);
        float dist = length(base - eye);
        if (dist < 0.6 || dist > 20.0) {
            hide();
            return;
        }
        float nearFade = smoothstep(1.0, 2.5, dist) * (1.0 - smoothstep(13.0, 20.0, dist));
        float sx = (vertex == 1 || vertex == 2) ? 1.0 : 0.0;
        float sy = vertex >= 2 ? 1.0 : 0.0;
        if (crown) {
            vec3 side = normalize(vec3(Right.x, 0.0, Right.z) + vec3(1.0e-4, 0.0, 0.0));
            float size = (0.42 + 0.2 * c.y) * (1.0 + 0.025 * dist);
            vec3 world = base + side * (sx - 0.5) * size + vec3(0.0, sy * size, 0.0);
            int frame = min(int(since / 0.06), 3);
            texCoord = vec2((16.0 + float(frame) * 8.0 + sx * 8.0) / 64.0, sy > 0.5 ? 0.5 : 1.0);
            vertexColor = vec4(min(light * 1.25, vec3(1.0)) * (0.92 + 0.08 * c.x), nearFade);
            gl_Position = Transform * vec4(world, 1.0);
            fogDistance = length(world - eye);
            return;
        }
        float flight = 0.26 + 0.14 * d.z;
        if (since > flight) {
            hide();
            return;
        }
        float rise0 = 0.5 * GRAVITY * flight;
        float run = 0.7 + 0.9 * d.x;
        float angle = d.y * 6.2831853;
        vec2 heading = vec2(cos(angle), sin(angle)) * run + Fall.xy * 0.6;
        vec3 head = base + vec3(heading.x * since, rise0 * since - 0.5 * GRAVITY * since * since, heading.y * since);
        vec3 vel = vec3(heading.x, rise0 - GRAVITY * since, heading.y);
        vec3 along = normalize(vel);
        float arc = (0.34 + 0.14 * c.y) * (1.0 + 0.02 * dist);
        vec3 aim = head - eye;
        vec3 up = cross(along, aim);
        float upLength = max(length(up), 1.0e-4);
        up /= upLength;
        if (up.y < 0.0) up = -up;
        vec3 world = head - along * arc * (1.0 - sx) + up * (sy - 0.375) * arc * 0.5;
        float row = d.z > 0.5 ? 4.0 : 0.0;
        texCoord = vec2((48.0 + sx * 8.0) / 64.0, (row + (sy > 0.5 ? 0.0 : 4.0)) / 16.0);
        vertexColor = vec4(min(light * 1.2, vec3(1.0)) * (0.92 + 0.08 * c.x), nearFade * (1.0 - smoothstep(0.6, 1.0, since / flight)));
        gl_Position = Transform * vec4(world, 1.0);
        fogDistance = length(world - eye);
        return;
    }

    if (y > eyeY + Shape.z) {
        hide();
        return;
    }
    vec2 tilt = drift(roll(cell, train + 41), vec2(cell) + a.xy);
    vec3 head = vec3(land.x - (y - yLand) * tilt.x, y - Origin.z, land.y - (y - yLand) * tilt.y);
    vec2 now;
    if (!column(Origin.xy + floor(head.xz), now)) {
        hide();
        return;
    }
    float floorY = now.x > 2048.0 ? (through ? now.y : now.x - 4096.0) : now.x;
    if (y < floorY - 0.05) {
        hide();
        return;
    }
    vec3 rise = normalize(vec3(-tilt.x, 1.0, -tilt.y));
    vec3 tail = head + rise * (0.5 + 1.1 * b.y) * speed / 16.0;
    vec3 view = head - eye;
    float d = length(view);
    vec3 side = cross(rise, view);
    float sideLength = length(side);
    if (sideLength < 1.0e-4) {
        hide();
        return;
    }
    side /= sideLength;
    float width = 0.075;
    float pixel = Eye.w * d * 1.4;
    float thin = 1.0;
    if (width < pixel) {
        thin = width / pixel;
        width = pixel;
    }
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : 0.0;
    vec3 world = mix(head, tail, sy) + side * sx * width * 0.5;
    int variant = int(c.z * 2.99);
    texCoord = vec2((float(variant * 4) + (sx > 0.0 ? 3.0 : 0.0)) / 64.0, sy > 0.5 ? 0.0 : 1.0);
    float fade = smoothstep(0.8, 3.0, d) * (1.0 - smoothstep(float(RADIUS) * 0.6, float(RADIUS), length(view.xz)))
            * (1.0 - smoothstep(Shape.z - 4.0, Shape.z, abs(y - eyeY)));
    vertexColor = vec4(light * (0.82 + 0.18 * c.x), fade * thin);
    gl_Position = Transform * vec4(world, 1.0);
    fogDistance = length(world - eye);
}
