#version 150

uniform mat4 Transform;
uniform vec4 Eye;
uniform vec3 Right;
uniform vec3 Up;
uniform vec4 Clock;
uniform vec4 Gain;
uniform sampler2D DataSampler;

out vec2 texCoord;
out vec4 hotColor;
out vec3 deepColor;
out vec4 aux;

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    texCoord = vec2(0.0);
    hotColor = vec4(0.0);
    deepColor = vec3(0.0);
    aux = vec4(-1.0, 0.0, 0.0, 0.0);
}

vec3 rayColor(int look) {
    return look == 1 ? vec3(1.0, 0.32, 0.3) : look == 2 ? vec3(0.5, 0.86, 1.0) : look == 3 ? vec3(0.4, 1.0, 0.86)
            : look == 4 ? vec3(1.0, 0.82, 0.66) : vec3(1.0, 0.64, 0.28);
}

float rand(float seed, float k) {
    return fract(sin(seed * (12.9898 + k * 7.31) + k * 78.233) * 43758.5453);
}

void main() {
    int slot = gl_VertexID / 6;
    int corner = gl_VertexID - slot * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    float t = Clock.x - a.w;
    int kind = int(b.x + 0.5);
    if (b.w <= 0.0 || t < 0.0 || t > b.w || (kind == 0) != (Clock.w < 0.5)) {
        hide();
        return;
    }
    float f = t / b.w;
    int look = int(b.y + 0.001);
    float s = fract(b.y);
    vec4 h = vec4(rand(s, 0.0), rand(s, 1.0), rand(s, 2.0), rand(s, 3.0));
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    float qx = sx * 0.5 + 0.5;
    float qy = sy * 0.5 + 0.5;
    vec3 pos = a.xyz;
    vec3 toEye = pos - Eye.xyz;
    vec3 flat_right = normalize(vec3(-toEye.z, 0.0, toEye.x) + vec3(0.0001, 0.0, 0.0));
    vec3 world;
    float alpha = 1.0;
    float fogAmount = 0.0;
    vec2 uv;
    vec3 hot = look == 1 ? vec3(1.0, 0.64, 0.62) : vec3(1.0, 0.94, 0.52);
    vec3 deep = look == 1 ? vec3(0.95, 0.08, 0.16) : vec3(1.0, 0.45, 0.05);
    float dist = length(toEye);
    if (kind == 0) {
        float halfWidth = 2.6 + 2.6 * h.x;
        float height = b.z;
        world = pos + flat_right * (sx * halfWidth) + vec3(0.0, qy * height, 0.0);
        int variant = int(h.y * 3.99);
        uv = vec2((float(variant) + qx) * 0.25, 1.0 - qy);
        float d = length(toEye.xz);
        float life = smoothstep(0.0, 0.2, f) * (1.0 - smoothstep(0.78, 1.0, f));
        float near = smoothstep(22.0, 34.0, d) * (1.0 - smoothstep(70.0, 96.0, d));
        alpha = Gain.x * life * near * (0.8 + 0.2 * sin(Clock.x * 0.7 + h.w * 40.0));
        hot = rayColor(look);
    } else if (kind == 1) {
        float cell = (0.7 + 0.5 * h.x) * b.z;
        world = pos + flat_right * (sx * cell * 0.5) + vec3(0.0, qy * cell - cell * 0.06, 0.0);
        float frame = min(floor(f * 4.0), 3.0);
        uv = vec2((frame + qx) * 0.125, (1.0 - qy) * 0.25);
        alpha = 1.0 - smoothstep(0.7, 1.0, f);
        fogAmount = 1.0;
    } else if (kind == 2) {
        float ang = h.x * 6.2832;
        float push = 0.5 + 1.3 * h.y;
        float up = 2.4 + 2.2 * h.z;
        float rise = up * t - 5.5 * t * t;
        if (rise < -0.04) {
            hide();
            return;
        }
        vec3 center = pos + vec3(cos(ang) * push * t, rise, sin(ang) * push * t);
        float cell = 0.5 * (0.8 + 0.5 * h.w);
        world = center + Right * (sx * cell * 0.5) + Up * (sy * cell * 0.5);
        float idx = floor(rand(s, 5.0) * 15.99);
        uv = vec2(0.5 + (mod(idx, 8.0) + qx) * 0.0625, (floor(idx / 8.0) + (1.0 - qy)) * 0.125);
        alpha = 1.0 - smoothstep(0.55, 1.0, f);
        hot = mix(hot, deep, f * 0.8);
        fogAmount = 1.0;
    } else if (kind == 3) {
        float cell = (1.0 + 0.8 * h.x) * b.z;
        world = pos + vec3(sx * cell * 0.5, 0.035, sy * cell * 0.5);
        float frame = min(floor(f * 5.0), 4.0);
        uv = vec2((frame + qx) * 0.125, 0.25 + (1.0 - qy) * 0.25);
        alpha = 0.95 * (1.0 - f * f);
        fogAmount = 1.0;
    } else if (kind == 4) {
        float bell = sin(f * 3.14159);
        vec3 center = pos + vec3(sin(t * 1.7 + h.x * 6.28) * 0.12, (0.5 + 0.6 * h.y) * t, cos(t * 1.4 + h.z * 6.28) * 0.12);
        float radius = (0.07 + 0.07 * h.z) * (0.45 + 0.9 * bell) * b.z;
        float cell = radius * 3.2;
        world = center + Right * (sx * cell) + Up * (sy * cell);
        uv = vec2(qx, qy);
        alpha = sqrt(bell);
        fogAmount = 1.0;
    } else {
        vec3 center = pos + vec3((h.x - 0.5) * 0.4 * t + sin(t * 1.1 + h.y * 6.28) * 0.12, 0.45 * t, (h.z - 0.5) * 0.4 * t);
        float cell = 0.6 + 0.8 * f;
        world = center + Right * (sx * cell * 0.5) + Up * (sy * cell * 0.5);
        float variant = floor(h.w * 2.99);
        uv = vec2(0.625 + (variant + qx) * 0.125, 0.25 + (1.0 - qy) * 0.25);
        alpha = sin(f * 3.14159) * 0.8;
        fogAmount = 1.0;
        hot = vec3(0.85, 0.5, 0.36);
        deep = vec3(0.4, 0.18, 0.14);
    }
    gl_Position = Transform * vec4(world, 1.0);
    texCoord = uv;
    hotColor = vec4(hot, alpha * (kind == 0 ? 1.0 : Gain.y));
    deepColor = deep;
    aux = vec4(float(kind), fogAmount * smoothstep(Clock.y, Clock.z, length(world - Eye.xyz)), qx, qy);
}
