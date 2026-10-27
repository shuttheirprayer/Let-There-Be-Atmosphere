#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform vec4 LitterFog;
uniform sampler2D DataSampler;
uniform sampler2D Sampler1;

out vec2 texCoord;
out vec3 tintColor;
out vec3 lightColor;
out float fogDistance;
out float fade;

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    texCoord = vec2(0.0);
    tintColor = vec3(0.0);
    lightColor = vec3(0.0);
    fogDistance = 0.0;
    fade = 0.0;
}

mat3 spin(vec3 axis, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    float k = 1.0 - c;
    return mat3(c + axis.x * axis.x * k, axis.y * axis.x * k + axis.z * s, axis.z * axis.x * k - axis.y * s,
                axis.x * axis.y * k - axis.z * s, c + axis.y * axis.y * k, axis.z * axis.y * k + axis.x * s,
                axis.x * axis.z * k + axis.y * s, axis.y * axis.z * k - axis.x * s, c + axis.z * axis.z * k);
}

void main() {
    int slot = gl_VertexID / 6;
    int corner = gl_VertexID - slot * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    int code = int(b.x + 0.5);
    int kind = code / 32;
    int sprite = code - kind * 32;
    float t = Clock.x - a.w;
    if (b.w == 0.0 || t < 0.0 || (b.w > 0.0 && t > b.w) || (kind != 0 && length(a.xyz + Offset) > LitterFog.w)) {
        hide();
        return;
    }
    vec4 c = texelFetch(DataSampler, ivec2(slot, 2), 0);
    vec4 d = texelFetch(DataSampler, ivec2(slot, 3), 0);
    vec3 h = fract(vec3(b.y, b.y * 7.13, b.y * 13.7));
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    float scale = abs(d.z);
    vec3 pos = a.xyz;
    vec3 local;
    vec2 origin;
    vec2 extent;
    if (kind == 0) {
        int variant = int(fract(b.y * 91.7) * 2.0);
        if (sprite == 2) {
            origin = vec2(0.0, 112.0 + 8.0 * float(variant));
            extent = vec2(16.0, 8.0);
        } else {
            origin = vec2(float(sprite) * 8.0, 96.0 + 8.0 * float(variant));
            extent = vec2(8.0);
        }
        float dt = clamp(Clock.x - d.y, 0.0, 0.07);
        pos += vec3(c.z, d.x, c.w) * dt;
        pos.y = max(pos.y, c.y);
        float above = pos.y - c.y;
        float settle = smoothstep(0.0, 0.9, above);
        float tumble = step(0.78, h.x);
        float th = t * mix(2.2, 3.4, h.y) * (1.0 + 0.6 * tumble) + h.z * 6.283;
        float turn = h.x * 6.283 + t * 0.21;
        vec3 axis = vec3(cos(turn), 0.0, sin(turn));
        float amp = mix(0.16, 0.34, h.z) * settle * (1.0 - 0.5 * tumble);
        float swing = sin(th);
        pos += axis * swing * amp;
        pos.y += amp * 0.5 * swing * swing + 0.002 + h.x * 0.004;
        float yaw = d.w;
        mat2 turnYaw = mat2(cos(yaw), sin(yaw), -sin(yaw), cos(yaw));
        local = vec3(sx * extent.x * 0.02125 * scale, 0.0, sy * extent.y * 0.02125 * scale);
        local.xz = turnYaw * local.xz;
        vec3 across = vec3(-axis.z, 0.0, axis.x);
        float pitch = (0.75 * cos(th + 0.6) + 0.15 * sin(t * 4.1 + h.y * 6.283)) * settle;
        float roll = (tumble > 0.5 ? 1.35 * sin(th * 1.5) : 0.22 * sin(t * 2.7 + h.x * 6.283)) * settle;
        local = spin(across, pitch) * (spin(axis, roll) * local);
        if (d.z < 0.0 && above < 0.02) {
            pos.y += sin(t * 1.8 + h.z * 6.283) * 0.018;
            local = spin(axis, 0.06 * sin(t * 1.3 + h.y * 6.283)) * local;
        }
        fade = min(t * 4.0, 1.0) * clamp((b.w - t) / 3.0, 0.0, 1.0);
    } else {
        origin = vec2(float(sprite - (sprite / 8) * 8) * 16.0, float(sprite / 8) * 16.0);
        extent = vec2(16.0);
        float yaw = h.x * 6.283;
        if (d.w > 0.5) {
            float dt = clamp(Clock.x - d.x, 0.0, 0.07);
            pos += vec3(c.z, c.y, c.w) * dt;
            yaw += d.y;
        }
        if ((sprite >= 16 && sprite <= 24) || sprite == 27) {
            vec3 eye = -(pos + Offset);
            vec3 facing = eye / max(length(eye), 0.001);
            vec3 right = cross(vec3(0.0, 1.0, 0.0), facing);
            float span = length(right);
            right = span > 0.01 ? right / span : vec3(1.0, 0.0, 0.0);
            vec3 up = cross(facing, right);
            float lean = (h.z - 0.5) * 0.5 + 0.06 * sin(Clock.x * 1.6 + h.y * 6.283);
            vec3 across = right * cos(lean) + up * sin(lean);
            vec3 rise = up * cos(lean) - right * sin(lean);
            float small = scale * 0.5;
            local = across * (sx * small) + rise * ((sy + 1.0) * small);
        } else {
            mat2 turn = mat2(cos(yaw), sin(yaw), -sin(yaw), cos(yaw));
            local = vec3(sx * scale, 0.0, sy * scale);
            local.xz = turn * local.xz;
        }
        fade = 1.0;
    }
    vec3 world = pos + local;
    float dist = length(world + Offset);
    if (kind != 0) fade *= min(t * 2.0, 1.0) * (1.0 - smoothstep(26.0, 34.0, dist));
    gl_Position = Transform * vec4(world, 1.0);
    texCoord = (origin + vec2(sx > 0.0 ? extent.x - 0.03 : 0.03, sy > 0.0 ? 0.03 : extent.y - 0.03)) / 128.0;
    int word = int(b.z + 0.5);
    int block = word / 16;
    lightColor = texelFetch(Sampler1, ivec2(block, word - block * 16), 0).rgb;
    int tint = int(c.x);
    tintColor = vec3(float((tint >> 16) & 255), float((tint >> 8) & 255), float(tint & 255)) / 255.0;
    fogDistance = dist;
}
