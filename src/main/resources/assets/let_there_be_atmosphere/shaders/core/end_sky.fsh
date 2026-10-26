#version 150

uniform samplerCube CubeSampler;
uniform mat3 Spin;
uniform vec4 Sky;
uniform vec4 Horizon;

in vec4 ray;
in vec3 turned;

out vec4 fragColor;

const vec3 HOLE = vec3(0.4472136, 0.5477226, -0.7071068);
const float CORE = 0.075;
const float TILT = 0.3;
const float LEAN = 0.32;

void main() {
    vec3 dir = ray.xyz * inversesqrt(dot(ray.xyz, ray.xyz));
    vec3 look = turned;
    vec2 p = vec2(0.0);
    float r = 1.0e3;
    float facing = dot(dir, HOLE);
    vec3 across = normalize(cross(HOLE, vec3(0.0, 1.0, 0.0)));
    vec3 upward = cross(across, HOLE);
    if (Sky.w > 0.0 && facing > 0.2) {
        p = vec2(dot(dir, across), dot(dir, upward)) / facing;
        r = length(p);
        vec2 source = p * max(1.0 - 2.4 * CORE * CORE / max(r * r, 1.0e-6), -1.0);
        look = Spin * normalize(HOLE + across * source.x + upward * source.y);
    }
    vec4 nebula = texture(CubeSampler, look);
    float wave = abs(fract(nebula.a * 3.0 - Sky.x) * 2.0 - 1.0);
    float glow = max(nebula.r, nebula.b);
    vec3 color = nebula.rgb * (1.0 + Sky.z * (wave * wave * (3.0 - 2.0 * wave) - 0.5) * smoothstep(0.08, 0.3, glow));
    if (r < 0.6) {
        vec2 q = mat2(cos(LEAN), sin(LEAN), -sin(LEAN), cos(LEAN)) * p;
        float ring = length(vec2(q.x, q.y / TILT)) / CORE;
        float angle = atan(q.y / TILT, q.x);
        float swirl = 0.5 + 0.5 * sin(angle * 3.0 - log(max(ring, 0.01)) * 7.0 + Sky.x * 6.2831853 * 2.0);
        float streaks = 0.55 + 0.45 * swirl * (0.6 + 0.4 * sin(angle * 7.0 - ring * 2.3 - Sky.x * 6.2831853 * 3.0));
        float band = smoothstep(1.15, 1.6, ring) * (1.0 - smoothstep(2.6, 7.5, ring));
        float beam = clamp(1.0 - q.x / (CORE * 12.0), 0.8, 1.2);
        vec3 hot = mix(vec3(1.0, 0.86, 0.66), vec3(0.96, 0.44, 0.86), smoothstep(1.4, 4.5, ring));
        vec3 disc = hot * band * streaks * beam * Sky.w * 1.1;
        float shade = smoothstep(CORE * 0.92, CORE * 1.02, r);
        float behind = q.y > 0.0 ? shade : 1.0;
        float halo = exp(-r / (CORE * 2.4)) * 0.22 + exp(-r / (CORE * 7.0)) * 0.08 * (1.0 - smoothstep(0.3, 0.6, r));
        float photon = exp(-pow((r - CORE * 1.08) / (CORE * 0.07), 2.0)) + 0.35 * exp(-pow((r - CORE * 1.3) / (CORE * 0.12), 2.0));
        color = color * mix(1.0, shade, Sky.w);
        color += vec3(0.62, 0.36, 1.0) * halo * Sky.w * shade;
        color += vec3(1.0, 0.9, 0.82) * photon * Sky.w * (0.75 + 0.25 * swirl);
        color += disc * behind;
        color += hot * exp(-pow(r / CORE - 1.55, 2.0) * 9.0) * smoothstep(0.0, CORE, q.y) * streaks * Sky.w * 0.7;
    }
    float y = dir.y;
    float haze = max(1.0 - abs(y) * 3.2, 0.0);
    color = mix(color, Horizon.rgb, Horizon.a * haze * haze);
    fragColor = vec4(color * mix(1.0, 0.6, smoothstep(0.0, -0.8, y)) * Sky.y, 1.0);
}
