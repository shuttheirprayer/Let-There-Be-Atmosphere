#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform vec3 Right;
uniform vec3 Up;
uniform sampler2D DataSampler;
uniform sampler2D Sampler1;

out vec2 uv;
out vec3 lightColor;
out float fogDistance;
out float strength;
flat out vec4 rect;
flat out float wrapY;

const float GRAVITY = 14.0;
const int DROPS = 12;
const int PUFFS = 5;
const int PER = 1 + DROPS + PUFFS + 4;
const float PUFF_LIFE[3] = float[3](2.0, 2.4, 3.0);

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    uv = vec2(0.0);
    lightColor = vec3(0.0);
    fogDistance = 0.0;
    strength = 0.0;
    rect = vec4(0.0, 0.0, 1.0, 1.0);
    wrapY = 0.0;
}

float hash(float n) {
    return fract(sin(n * 127.1 + 311.7) * 43758.5453);
}

void main() {
    int quad = gl_VertexID / 6;
    int corner = gl_VertexID - quad * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec2 c = vec2(vertex == 1 || vertex == 2 ? 1.0 : 0.0, vertex >= 2 ? 1.0 : 0.0);
    int slot = quad / PER;
    int part = quad - slot * PER;
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    if (a.w <= 0.0 || Clock.w <= 0.0) {
        hide();
        return;
    }
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    vec3 centre = vec3(a.x + 0.5, a.y, a.z + 0.5);
    float dist = length(centre + Offset);
    float fade = 1.0 - smoothstep(32.0, 52.0, dist);
    if (fade < 0.01) {
        hide();
        return;
    }
    float seed = dot(a.xz, vec2(12.9898, 78.233)) + a.y * 3.7;
    float inten = b.x;
    float frac = clamp(Clock.w * (0.35 + 0.5 * inten), 0.0, 1.0);
    float amount = min(Clock.w, 1.0);
    vec3 p;
    float alpha;
    wrapY = 0.0;

    if (part == 0) {
        float size = b.w > 0.5 ? 4.0 : 2.0;
        p = vec3(centre.x + (c.x - 0.5) * size, centre.y + 0.004, centre.z + (c.y - 0.5) * size);
        float v = hash(seed);
        int variant = int(v * 8.0);
        vec2 t = c;
        if ((variant & 1) != 0) {
            t = t.yx;
        }
        if ((variant & 2) != 0) {
            t.x = 1.0 - t.x;
        }
        if ((variant & 4) != 0) {
            t.y = 1.0 - t.y;
        }
        int frame = (int(floor(Clock.x * 6.0)) + int(v * 4.0)) & 3;
        rect = vec4(float(frame * 32), 0.0, 32.0, 32.0);
        uv = t * 32.0;
        alpha = (0.55 + 0.45 * inten) * amount;
    } else if (part <= DROPS) {
        float k = float(part - 1);
        if (k >= float(DROPS) * frac) {
            hide();
            return;
        }
        float s = seed + k * 3.17;
        float period = 0.8 + 0.2 * floor(hash(s) * 3.0);
        float age = fract((Clock.x + hash(s + 1.0) * period) / period) * period;
        float angle = hash(s + 2.0) * 6.2832;
        float out_ = (0.5 + hash(s + 3.0)) * (0.6 + 0.6 * inten);
        float up = (2.4 + 2.0 * hash(s + 4.0)) * (0.7 + 0.5 * inten);
        float life = 2.0 * up / GRAVITY;
        if (age > life) {
            hide();
            return;
        }
        vec3 dir = vec3(cos(angle), 0.0, sin(angle));
        vec3 pos = centre + dir * (0.3 + 0.35 * hash(s + 5.0) + out_ * age)
                + vec3(0.0, 0.05 + up * age - 0.5 * GRAVITY * age * age, 0.0);
        float size = 0.15 + 0.1 * hash(s + 6.0);
        p = pos + (Right * (c.x - 0.5) + Up * (c.y - 0.5)) * size;
        int variant = int(hash(s + 7.0) * 4.0);
        rect = vec4(float(64 + variant * 8), 32.0, 8.0, 8.0);
        uv = vec2(c.x, 1.0 - c.y) * 8.0;
        alpha = 1.0 - smoothstep(0.7 * life, life, age);
    } else if (part <= DROPS + PUFFS) {
        float k = float(part - DROPS - 1);
        if (k >= float(PUFFS) * frac) {
            hide();
            return;
        }
        float s = seed + 40.0 + k * 5.31;
        float period = PUFF_LIFE[int(hash(s) * 3.0)];
        float phase = fract((Clock.x + hash(s + 1.0) * period) / period);
        float age = phase * period;
        float angle = hash(s + 2.0) * 6.2832;
        vec3 dir = vec3(cos(angle), 0.0, sin(angle));
        vec3 pos = centre + dir * (0.2 + 0.5 * hash(s + 3.0) + 0.28 * age)
                + vec3(0.0, 0.15 + (0.35 + 0.35 * inten) * age, 0.0);
        float size = (0.55 + 0.9 * phase) * (0.7 + 0.5 * inten);
        p = pos + (Right * (c.x - 0.5) + Up * (c.y - 0.5)) * size;
        int variant = int(hash(s + 4.0) * 4.0);
        rect = vec4(float(variant * 16), 32.0, 16.0, 16.0);
        uv = vec2(c.x, 1.0 - c.y) * 16.0;
        alpha = pow(sin(3.14159 * phase), 1.3) * 0.4 * (0.4 + 0.6 * inten) * amount;
    } else {
        int face = part - DROPS - PUFFS - 1;
        if (((int(b.z + 0.5) >> face) & 1) == 0) {
            hide();
            return;
        }
        vec3 origin = face == 0 ? vec3(a.x, a.y, a.z) : (face == 1 ? vec3(a.x + 1.0, a.y, a.z)
                : (face == 2 ? vec3(a.x + 1.0, a.y, a.z + 1.0) : vec3(a.x, a.y, a.z + 1.0)));
        vec3 tangent = face == 0 ? vec3(1.0, 0.0, 0.0) : (face == 1 ? vec3(0.0, 0.0, 1.0)
                : (face == 2 ? vec3(-1.0, 0.0, 0.0) : vec3(0.0, 0.0, -1.0)));
        vec3 normal = vec3(tangent.z, 0.0, -tangent.x);
        float height = b.y;
        p = origin + tangent * c.x + vec3(0.0, c.y * height, 0.0) + normal * 0.012;
        int variant = int(hash(seed + float(face) * 1.9) * 4.0);
        rect = vec4(float(variant * 16), 48.0, 16.0, 32.0);
        uv = vec2(c.x * 16.0, 32.0 + c.y * height * 16.0 + mod(Clock.x, 2.0) * 16.0);
        wrapY = 1.0;
        alpha = (1.0 - c.y * 0.85) * 0.6 * (0.5 + 0.5 * inten) * amount;
    }
    strength = alpha * fade;
    gl_Position = Transform * vec4(p, 1.0);
    int packed = int(a.w - 0.5);
    int block = packed / 16;
    lightColor = texelFetch(Sampler1, ivec2(block, packed - block * 16), 0).rgb;
    fogDistance = length(p + Offset);
}
