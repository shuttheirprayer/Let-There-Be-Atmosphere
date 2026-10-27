#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform vec4 Wind;
uniform vec4 Player;
uniform vec4 Push1;
uniform vec4 Push2;
uniform vec4 Push3;
uniform sampler2D DataSampler;
uniform sampler2D Sampler1;

out vec2 texCoord;
out vec3 tintColor;
out vec3 lightColor;
out float fogDistance;
out float shade;

const int SHORT_BLADES = 12;
const int TALL_BLADES = 20;

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    texCoord = vec2(0.0);
    tintColor = vec3(0.0);
    lightColor = vec3(0.0);
    fogDistance = 0.0;
    shade = 0.0;
}

vec2 shove(vec4 who, vec3 root, float height) {
    if (who.w <= 0.0) return vec2(0.0);
    vec2 away = root.xz - who.xz;
    float near = length(away);
    float push = (1.0 - smoothstep(0.35, 1.3 * who.w, near)) * (1.0 - smoothstep(1.5, 2.5, abs(root.y - who.y)));
    return (near > 0.001 ? away / near : vec2(0.0)) * push * 0.7 * height;
}

float hash(float n) {
    return fract(sin(n) * 43758.5453);
}

float clumping(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(dot(i, vec2(127.1, 311.7)));
    float b = hash(dot(i + vec2(1.0, 0.0), vec2(127.1, 311.7)));
    float c = hash(dot(i + vec2(0.0, 1.0), vec2(127.1, 311.7)));
    float d = hash(dot(i + vec2(1.0, 1.0), vec2(127.1, 311.7)));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    bool tall = Clock.w > 0.5;
    int blades = tall ? TALL_BLADES : SHORT_BLADES;
    int slot = gl_VertexID / (blades * 6);
    int rest = gl_VertexID - slot * blades * 6;
    int blade = rest / 6;
    int corner = rest - blade * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    if (b.y <= 0.0) {
        hide();
        return;
    }
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    float clump = 0.35 + 0.65 * clumping(a.xz / 5.0);
    if (float(blade) >= float(blades) * min(b.z, 1.0) * clump) {
        hide();
        return;
    }
    float seed = b.y * 113.7 + float(blade) * 17.31;
    float h1 = hash(seed);
    float h2 = hash(seed + 1.7);
    float h3 = hash(seed + 3.3);
    float h4 = hash(seed + 5.1);
    vec3 root = vec3(a.x + 0.06 + 0.88 * h1, a.y, a.z + 0.06 + 0.88 * h2);
    float dist = length(root + Offset);
    float grow = (1.0 - smoothstep(Player.w * 0.7, Player.w, dist)) * clamp((Clock.x - b.w) * 2.0, 0.0, 1.0);
    if (grow <= 0.01) {
        hide();
        return;
    }
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : 0.0;
    float height = (tall ? 0.42 + 0.58 * h3 * clump : 0.16 + 0.3 * h3 * clump) * grow;
    float width = tall ? 0.11 + 0.05 * h4 : 0.065 + 0.035 * h4;
    float yaw = h4 * 6.283 + h1 * 3.1;
    vec2 dir = vec2(cos(yaw), sin(yaw));
    vec3 p = root + vec3(dir.x * sx * width, sy * height, dir.y * sx * width);
    vec2 lean = (vec2(h2, h1) - 0.5) * 0.3 * height;
    float phase = dot(root.xz, vec2(0.21, 0.17));
    float force = length(Wind.yz);
    vec2 along = force > 0.001 ? Wind.yz / force : vec2(1.0, 0.0);
    vec2 side = vec2(-along.y, along.x);
    float bend = force / (1.0 + 0.3 * force);
    float calm = 1.0 - smoothstep(0.4, 1.4, force);
    float gust = 0.55 + 0.9 * (0.5 + 0.5 * sin(Wind.x * 0.9 + phase * 0.6) * sin(Wind.x * 0.37 + phase * 0.23));
    float sway = sin(Wind.x * 1.7 + phase * 3.1 + h3 * 6.0) * 0.6 + sin(Wind.x * 2.9 + phase * 5.3) * 0.4;
    float quiver = sin(Wind.x * 11.0 + phase * 7.0 + h3 * 9.0) * 0.6 + sin(Wind.x * 17.0 + h1 * 12.0) * 0.4;
    vec2 blown = (along * (bend * gust + sway * 0.35 * calm * (0.3 + bend) + quiver * 0.06 * bend)
                + side * (sway * 0.12 * calm + quiver * 0.035 * bend)) * 0.5 * height * Wind.w;
    float reach = length(blown);
    if (reach > 0.6 * height) blown *= 0.6 * height / reach;
    lean += blown;
    vec2 away = root.xz - Player.xz;
    float near = length(away);
    float push = (1.0 - smoothstep(0.35, 1.3, near)) * (1.0 - smoothstep(1.5, 2.5, abs(root.y - Player.y)));
    lean += (near > 0.001 ? away / near : vec2(0.0)) * push * 0.7 * height;
    lean += shove(Push1, root, height) + shove(Push2, root, height) + shove(Push3, root, height);
    p.xz += lean * sy;
    p.y -= dot(lean, lean) * 0.8 * sy;
    gl_Position = Transform * vec4(p, 1.0);
    float cell = floor(h3 * 3.99) * 0.25;
    texCoord = vec2(cell + (sx > 0.0 ? 0.2495 : 0.0005), sy > 0.5 ? 0.001 : 0.999);
    int word = int(b.x + 0.5);
    int block = word / 16;
    lightColor = texelFetch(Sampler1, ivec2(block, word - block * 16), 0).rgb;
    int tint = int(a.w);
    tintColor = vec3(float((tint >> 16) & 255), float((tint >> 8) & 255), float(tint & 255)) / 255.0;
    shade = tall ? mix(0.82, 1.0, sy) : mix(0.9, 1.0, sy);
    fogDistance = length(p + Offset);
}
