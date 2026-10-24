#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform vec4 WakeFog;
uniform vec3 Right;
uniform vec3 Up;
uniform sampler2D DataSampler;
uniform sampler2D Sampler1;

out vec2 local;
out vec3 lightColor;
out float fogDistance;
flat out vec4 info;
flat out vec3 water;

const float GRAVITY = 14.0;
const float CROWN_LIFE = 0.6;

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    local = vec2(0.0);
    lightColor = vec3(0.0);
    fogDistance = 0.0;
    info = vec4(0.0);
    water = vec3(0.0);
}

vec3 decodeTint(float v) {
    int c = int(v + 0.5);
    return vec3(float((c >> 16) & 255), float((c >> 8) & 255), float(c & 255)) / 255.0;
}

vec3 decodeLight(float v) {
    int c = int(v + 0.5);
    int blockLight = c / 16;
    return texelFetch(Sampler1, ivec2(blockLight, c - blockLight * 16), 0).rgb;
}

void main() {
    bool crown = WakeFog.w < 0.5;
    int per = crown ? 12 : 6;
    int slot = gl_VertexID / per;
    int k = gl_VertexID - slot * per;
    int quad = k / 6;
    int corner = k - quad * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec2 q = vec2(vertex == 1 || vertex == 2 ? 1.0 : -1.0, vertex >= 2 ? 1.0 : 0.0);
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    vec4 c = texelFetch(DataSampler, ivec2(slot, 2), 0);
    float age = Clock.x - a.w;
    vec3 p;
    if (crown) {
        if (age < 0.0 || age > CROWN_LIFE) {
            hide();
            return;
        }
        float w = b.x * (0.55 + 0.7 * age / CROWN_LIFE) * 0.5;
        float rise = smoothstep(0.0, 0.12, age);
        float fall = 1.0 - smoothstep(0.22, 0.58, age);
        float h = b.y * rise * fall;
        if (h < 0.03) {
            hide();
            return;
        }
        vec2 dir = quad == 0 ? vec2(cos(c.y), sin(c.y)) : vec2(-sin(c.y), cos(c.y));
        p = a.xyz + vec3(dir.x, 0.0, dir.y) * q.x * w + vec3(0.0, 0.012 + q.y * h, 0.0);
        local = vec2(q.x * w, q.y * h);
        info = vec4(w, h, age, c.x + float(quad) * 7.0);
        water = decodeTint(b.w);
        lightColor = decodeLight(b.z);
    } else {
        float life = c.z;
        if (age < 0.0 || age > life) {
            hide();
            return;
        }
        vec3 centre = a.xyz + b.xyz * age + vec3(0.0, -0.5 * GRAVITY * age * age, 0.0);
        if (age > 0.05 && centre.y < a.y) {
            hide();
            return;
        }
        vec2 off = vec2(q.x * 0.5, q.y - 0.5) * b.w;
        p = centre + Right * off.x + Up * off.y;
        local = vec2(0.0);
        info = vec4(0.0, 0.0, age / life, 0.0);
        water = decodeTint(c.y);
        lightColor = decodeLight(c.x);
    }
    gl_Position = Transform * vec4(p, 1.0);
    fogDistance = length(p + Offset);
}
