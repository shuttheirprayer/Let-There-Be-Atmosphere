#version 150

uniform mat4 Transform;
uniform vec3 Right;
uniform vec3 Up;
uniform vec4 Clock;
uniform vec4 MistFog;
uniform vec4 Wisps[192];

out vec2 texCoord;
flat out float cell;
out vec4 vertexColor;

void main() {
    int slot = gl_VertexID / 6;
    int corner = gl_VertexID - slot * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 a = Wisps[slot * 2];
    vec4 b = Wisps[slot * 2 + 1];
    float seed = b.y;
    float sx = (vertex == 1 || vertex == 2) ? 1.0 : -1.0;
    float sy = vertex >= 2 ? 1.0 : -1.0;
    float tilt = (fract(seed * 5.13) - 0.5) * 0.3;
    vec2 local = vec2(sx * a.w, sy * a.w * 0.5);
    local = vec2(local.x * cos(tilt) - local.y * sin(tilt), local.x * sin(tilt) + local.y * cos(tilt));
    vec3 world = a.xyz + Right * local.x + Up * local.y;
    gl_Position = Transform * vec4(world, 1.0);

    float variant = floor(fract(seed * 7.31) * 3.999);
    float u = fract(seed * 3.7) > 0.5 ? -sx : sx;
    texCoord = vec2(0.5 + u * 0.5, 0.5 - sy * 0.5);
    cell = variant;

    float dist = length(a.xyz);
    float day = Clock.z;
    float span = max(Clock.y - Clock.x, 0.001);
    float fog = clamp((dist - Clock.x) / span, 0.0, 1.0);
    float alpha = b.x * 0.32 * (0.55 + 0.45 * day)
            * smoothstep(a.w + 2.0, a.w * 2.0 + 6.0, dist)
            * (1.0 - smoothstep(Clock.w * 0.8, Clock.w * 1.1, dist))
            * (1.0 - smoothstep(Clock.x + span * 0.3, Clock.x + span, dist));
    vec3 night = mix(vec3(0.6, 0.7, 1.0), vec3(1.0), day);
    vec3 base = mix(MistFog.rgb, night, 0.55 + 0.2 * day);
    vec3 pastel = mix(vec3(1.0), 0.5 + 0.5 * cos(6.2831853 * (seed + vec3(0.0, 0.33, 0.67))), 0.05);
    vertexColor = vec4(mix(base * pastel, MistFog.rgb, fog), alpha);
}
