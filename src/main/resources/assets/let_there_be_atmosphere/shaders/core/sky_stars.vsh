#version 150

uniform mat4 Transform;
uniform vec4 Field;
uniform vec4 Screen;
uniform vec4 Band;

out vec2 local;
out vec3 tint;
out float kind;
out float glow;
out vec2 dust;

const vec3 NORMAL = vec3(0.34, 0.31, 0.888);

float hash(float n) {
    return fract(sin(n * 12.9898 + 4.1414) * 43758.5453);
}

float elevation(vec3 d) {
    return d.y * Screen.z - d.z * Screen.w;
}

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    local = vec2(0.0);
    tint = vec3(0.0);
    kind = 0.0;
    glow = 0.0;
    dust = vec2(0.0);
}

void main() {
    int stars = int(Field.z);
    int corner = gl_VertexID - (gl_VertexID / 6) * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec2 c = vec2((vertex == 1 || vertex == 2) ? 1.0 : -1.0, vertex >= 2 ? 1.0 : -1.0);
    if (gl_VertexID >= stars * 6) {
        if (Band.x < 0.5) {
            hide();
            return;
        }
        int quad = (gl_VertexID - stars * 6) / 6;
        vec3 e1 = normalize(cross(NORMAL, vec3(0.0, 1.0, 0.0)));
        vec3 e2 = cross(NORMAL, e1);
        float theta = (float(quad) + 0.5 + c.x * 0.5) * 6.2831853 / 96.0;
        vec3 along = cos(theta) * e1 + sin(theta) * e2;
        float width = 0.3;
        vec3 dir = normalize(along + NORMAL * c.y * width);
        gl_Position = Transform * vec4(dir * 100.0, 1.0);
        local = c;
        kind = 4.0;
        glow = Field.x * smoothstep(-0.08, 0.3, elevation(dir));
        tint = vec3(0.0);
        dust = vec2(theta * 2.4, c.y);
        return;
    }
    float s = float(gl_VertexID / 6);
    float z = hash(s * 1.37 + 0.11) * 2.0 - 1.0;
    float a = hash(s * 2.71 + 0.37) * 6.2831853;
    vec3 dir = vec3(sqrt(1.0 - z * z) * cos(a), z, sqrt(1.0 - z * z) * sin(a));
    if (Band.x > 0.5 && hash(s * 5.31) < 0.38) {
        dir = normalize(dir - NORMAL * dot(dir, NORMAL) * (0.82 + 0.16 * hash(s * 7.7)));
    }
    float r = hash(s * 3.91);
    kind = r < 0.72 ? 1.0 : r < 0.955 ? 2.0 : 3.0;
    float t = hash(s * 9.13);
    tint = t < 0.55 ? vec3(0.86, 0.91, 1.0) : t < 0.8 ? vec3(1.0, 0.93, 0.78) : t < 0.94 ? vec3(0.7, 0.8, 1.0) : vec3(1.0, 0.68, 0.52);
    float level = kind == 1.0 ? 0.5 + 0.5 * hash(s * 4.4) : kind == 2.0 ? 0.8 + 0.2 * hash(s * 4.4) : 1.0;
    float twinkle = 1.0 - Field.w * (kind == 1.0 ? 0.35 : 0.6) * (0.5 + 0.5 * sin(Field.y * (1.3 + 2.2 * hash(s * 6.1)) + s));
    glow = Field.x * level * twinkle * smoothstep(-0.05, 0.22, elevation(dir));
    vec4 clip = Transform * vec4(dir * 100.0, 1.0);
    if (clip.w <= 0.0 || glow < 0.003) {
        hide();
        return;
    }
    vec2 res = 2.0 / Screen.xy;
    vec2 pixel = floor((clip.xy / clip.w * 0.5 + 0.5) * res) + 0.5;
    float scale = max(floor(Band.y + 0.5), 1.0);
    float extent = (kind == 1.0 ? 0.5 : kind == 2.0 ? 1.5 : 3.5) * scale;
    vec2 ndc = (pixel + c * extent) / res * 2.0 - 1.0;
    gl_Position = vec4(ndc * clip.w, clip.z, clip.w);
    local = c;
    dust = vec2(0.0);
}
