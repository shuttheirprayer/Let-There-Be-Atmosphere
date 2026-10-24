#version 150

uniform samplerCube CubeSampler;
uniform vec4 Sky;
uniform vec4 Horizon;

in vec4 ray;
in vec3 turned;

out vec4 fragColor;

void main() {
    vec4 nebula = texture(CubeSampler, turned);
    float wave = abs(fract(nebula.a * 3.0 - Sky.x) * 2.0 - 1.0);
    float glow = max(nebula.r, nebula.b);
    vec3 color = nebula.rgb * (1.0 + Sky.z * (wave * wave * (3.0 - 2.0 * wave) - 0.5) * smoothstep(0.08, 0.3, glow));
    float y = ray.y * inversesqrt(dot(ray.xyz, ray.xyz));
    float haze = max(1.0 - abs(y) * 3.2, 0.0);
    color = mix(color, Horizon.rgb, Horizon.a * haze * haze);
    fragColor = vec4(color * mix(1.0, 0.6, smoothstep(0.0, -0.8, y)) * Sky.y, 1.0);
}
