#version 150

uniform mat4 Transform;
uniform vec3 Right;
uniform vec3 Up;
uniform vec4 Clock;
uniform vec4 PuffFog;
uniform vec4 Puffs[480];

out vec2 texCoord;
flat out float cell;
flat out float cool;
out vec4 vertexColor;

void main() {
    int slot = gl_VertexID / 6;
    int corner = gl_VertexID - slot * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 a = Puffs[slot * 3];
    vec4 b = Puffs[slot * 3 + 1];
    vec4 c = Puffs[slot * 3 + 2];
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    float kind = floor(c.w + 0.5);
    float spin = b.y * 6.2831853 + b.w * (b.y - 0.5) * 1.4;
    vec2 local = vec2(sx, sy) * a.w;
    local = vec2(local.x * cos(spin) - local.y * sin(spin), local.x * sin(spin) + local.y * cos(spin));
    if (kind > 0.5 && kind < 1.5) {
        local = vec2(sx, sy) * a.w;
    }
    vec3 world = a.xyz + Right * local.x + Up * local.y;
    gl_Position = Transform * vec4(world, 1.0);

    float variant = kind > 0.5 && kind < 1.5 ? 4.0 : floor(fract(b.y * 7.31) * 3.999);
    texCoord = vec2(0.5 + sx * 0.5, 0.5 - sy * 0.5);
    cell = variant;

    float dist = length(a.xyz);
    float span = max(Clock.y - Clock.x, 0.001);
    float fog = smoothstep(Clock.w * 0.5, Clock.w, dist) * 0.5;
    float light = b.z;
    float alpha = b.x * (0.55 + 0.45 * light)
            * smoothstep(0.2, 0.7, dist)
            * (1.0 - smoothstep(Clock.w * 0.75, Clock.w, dist))
            * (1.0 - smoothstep(Clock.x + span * 0.3, Clock.x + span, dist));
    cool = kind > 1.5 ? 1.0 : 0.3;
    if (kind > 1.5 && kind < 2.5) {
        vec2 ndc = gl_Position.xy / max(gl_Position.w, 0.001);
        alpha *= smoothstep(0.06, 0.4, length(ndc * vec2(1.6, 1.0)));
    }
    vec3 lit = c.rgb * (0.34 + 0.66 * light);
    lit = mix(lit, PuffFog.rgb * (0.5 + 0.5 * light), 0.05 + 0.3 * (1.0 - light));
    vertexColor = vec4(mix(lit, PuffFog.rgb, fog), alpha);
}
