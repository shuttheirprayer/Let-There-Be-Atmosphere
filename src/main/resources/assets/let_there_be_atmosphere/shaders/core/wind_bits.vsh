#version 150

uniform mat4 Transform;
uniform vec3 Right;
uniform vec3 Up;
uniform vec3 Offset;
uniform vec4 Clock;
uniform sampler2D Sampler1;
uniform sampler2D DataSampler;

out vec2 spot;
out vec4 vertexColor;
out float fogDistance;
out vec3 look;

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    spot = vec2(0.0);
    vertexColor = vec4(0.0);
    fogDistance = 0.0;
    look = vec3(0.0);
}

void main() {
    int slot = gl_VertexID / 6;
    int corner = gl_VertexID - slot * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    vec4 c = texelFetch(DataSampler, ivec2(slot, 2), 0);
    float t = Clock.x - a.w;
    if (b.w <= 0.0 || t < 0.0 || t > b.w) {
        hide();
        return;
    }
    float f = t / b.w;
    int kind = int(b.x + 0.5);
    vec3 vel = texelFetch(DataSampler, ivec2(slot, 3), 0).xyz;
    float pace = length(vel);
    vec3 dir = pace > 0.001 ? vel / pace : vec3(1.0, 0.0, 0.0);
    vec3 side = vec3(-dir.z, 0.0, dir.x);
    vec3 h = fract(vec3(b.y, b.y * 7.13, b.y * 13.7));
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    float alpha = min(1.0, min(f * 6.0, (1.0 - f) * 4.0));
    vec3 pos = a.xyz;
    vec3 world;
    vec3 tint = c.rgb;
    if (kind == 3) {
        float bend = t * 1.4 + h.x * 6.2831853;
        pos += side * sin(bend) * 0.4;
        vec3 axis = normalize(dir + side * (0.12 * sin(bend)) + vec3(0.0, 0.05 * sin(t * 1.1 + h.z * 6.2831853), 0.0));
        vec3 gaze = normalize(pos + Offset);
        vec3 cut = cross(axis, gaze);
        float edge = length(cut);
        vec3 across = cut / max(edge, 0.0001);
        float len = 0.55 + 0.35 * h.y;
        world = pos + axis * (sx * len) + across * (sy * 0.02);
        alpha *= (0.2 + 0.1 * h.z) * smoothstep(0.12, 0.45, edge);
    } else if (kind == 0) {
        pos += side * sin(t * (2.0 + h.y) + h.x * 6.2831853) * 0.3
                + vec3(0.0, (h.y - 0.4) * 0.5 * t + sin(t * 3.1 + h.z * 6.2831853) * 0.1, 0.0);
        world = pos + (Right * sx + Up * sy) * 0.031;
    } else {
        float size = 0.078;
        if (kind == 1) {
            pos += side * sin(t * 2.3 + h.x * 6.2831853) * 0.45
                    + vec3(0.0, -0.3 * t + sin(t * 4.0 + h.y * 6.2831853) * 0.2, 0.0);
        } else {
            pos += side * sin(t * 2.6 + h.z * 6.2831853) * 0.3
                    + vec3(0.0, -0.18 * t + sin(t * 3.4 + h.x * 6.2831853) * 0.12, 0.0);
        }
        float spin = h.x * 6.2831853;
        vec3 roll = vec3(cos(spin), 0.0, sin(spin));
        vec3 rollSide = vec3(-roll.z, 0.0, roll.x);
        float turn = t * (kind == 1 ? 2.5 + 3.0 * h.y : 1.5 + 2.0 * h.y) + h.z * 6.2831853;
        vec3 flip = vec3(0.0, cos(turn), 0.0) + rollSide * sin(turn);
        world = pos + (roll * sx + flip * sy) * size;
    }
    gl_Position = Transform * vec4(world, 1.0);
    int cell = int(b.z + 0.5);
    int block = cell / 16;
    vec3 light = texelFetch(Sampler1, ivec2(block, cell - block * 16), 0).rgb;
    alpha *= smoothstep(1.0, 3.0, length(pos + Offset)) * (1.0 - smoothstep(30.0, 38.0, length(pos + Offset)));
    if (length(pos + Offset) > 38.0) gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    vertexColor = vec4(tint * light, alpha);
    fogDistance = length(world + Offset);
    spot = vec2(sx, sy);
    look = vec3(float(kind), mod(floor(t * 7.0 + h.x * 4.0), 4.0), h.x);
}
