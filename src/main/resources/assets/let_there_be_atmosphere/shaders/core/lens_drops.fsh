#version 150

#define MAX_DROPS 64

uniform sampler2D DiffuseSampler;
uniform vec4 Drops[MAX_DROPS];
uniform vec4 Flow[MAX_DROPS];
uniform vec4 Screen;

in vec2 texCoord;

out vec4 fragColor;

vec3 scene(vec2 p) {
    return texture(DiffuseSampler, clamp(vec2(p.x / Screen.x, p.y), vec2(0.001), vec2(0.999))).rgb;
}

vec3 bead(vec2 p, vec2 q, float e, float r) {
    float blur = r * 0.16;
    vec2 at = p - 2.0 * q * r * (0.95 + 0.55 * e * e);
    vec3 c = scene(at) * 0.4;
    c += scene(at + vec2(blur, 0.0)) * 0.15;
    c += scene(at - vec2(blur, 0.0)) * 0.15;
    c += scene(at + vec2(0.0, blur)) * 0.15;
    c += scene(at - vec2(0.0, blur)) * 0.15;
    float lum = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(lum), c, 1.12) * 1.05;

    vec2 light = normalize(vec2(-0.55, 0.8));
    vec2 n = q / max(e, 0.001);
    float facing = dot(n, light);
    float rim = smoothstep(0.62, 1.0, e);
    c *= 1.0 - 0.34 * rim * smoothstep(-0.1, 0.9, facing);
    c += vec3(0.26, 0.28, 0.3) * rim * smoothstep(0.1, 0.95, -facing) * (0.55 + 0.45 * lum);
    c += vec3(0.5) * smoothstep(0.88, 1.0, e) * 0.35;
    vec2 h = (q - vec2(-0.34, 0.4)) / vec2(0.2, 0.15);
    c += vec3(1.0, 0.99, 0.96) * (1.0 - smoothstep(0.55, 1.0, length(h))) * 0.85;
    vec2 g = (q - vec2(0.3, -0.45)) / vec2(0.12, 0.08);
    c += vec3(0.9, 0.95, 1.0) * (1.0 - smoothstep(0.5, 1.0, length(g))) * 0.22;
    return c;
}

void main() {
    float px = Screen.y;
    vec2 p = vec2(texCoord.x * Screen.x, texCoord.y);
    vec3 col = texture(DiffuseSampler, texCoord).rgb;
    bool hit = false;
    int count = int(Screen.z);
    for (int i = 0; i < count; i++) {
        vec4 d = Drops[i];
        vec4 f = Flow[i];
        float r = d.z;
        vec2 o = p - vec2(d.x * Screen.x, d.y);
        float reach = r * 1.4 + px;
        if (abs(o.x) > reach + f.y * 0.5 + 0.01 || o.y < -reach || o.y > reach + f.y) continue;
        if (f.w > 0.002 && f.y > 0.0) {
            vec2 dir = vec2(sin(f.z), cos(f.z));
            float t = dot(o, dir);
            float s = o.x * dir.y - o.y * dir.x;
            float k = clamp(t / f.y, 0.0, 1.0);
            float w = max(r * 0.26, px * 2.0) * mix(1.0, 0.45, k);
            float cover = (1.0 - smoothstep(w - px, w + px, abs(s))) * smoothstep(-px, px, t) * (1.0 - smoothstep(f.y - w, f.y, t));
            cover *= f.w * d.w * (1.0 - k * 0.8);
            if (cover > 0.002) {
                float u = s / w;
                vec3 c = scene(p - vec2(dir.y, -dir.x) * s * 1.5);
                c = c * 1.04 + vec3(0.22) * smoothstep(0.45, 1.0, u) + vec3(0.06) * smoothstep(0.3, 1.0, -u) * 0.0;
                c -= vec3(0.08) * smoothstep(0.4, 1.0, -u);
                col = mix(col, c, cover);
                hit = true;
            }
        }
        vec2 q = o / (r * vec2(1.0 - 0.12 * f.x, 1.0 + 0.28 * f.x));
        float e = length(q);
        float aa = px / r;
        float cover = (1.0 - smoothstep(1.0 - aa, 1.0 + aa, e)) * d.w;
        if (cover > 0.002) {
            col = mix(col, bead(p, q, min(e, 1.0), r), cover);
            hit = true;
        }
    }
    if (!hit) discard;
    fragColor = vec4(col, 1.0);
}
