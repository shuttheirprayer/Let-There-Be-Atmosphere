#version 150

uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;
uniform mat4 InverseTransform;
uniform vec4 Surface;
uniform vec4 Light;

in vec2 texCoord;

out vec4 fragColor;

float web(vec2 at, float t) {
    vec2 a = texture(NoiseSampler, Light.xy + at * Light.z + vec2(t * 0.021, t * 0.013)).rg;
    vec2 b = texture(NoiseSampler, Light.xy * 1.37 + at * Light.z * 1.37 - vec2(t * 0.017, -t * 0.024)).gr;
    float ridge = 1.0 - abs(a.r - b.r) * 2.4 - abs(a.g - b.g) * 0.9;
    return pow(clamp(ridge, 0.0, 1.0), 5.0);
}

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    if (depth >= 1.0) {
        discard;
    }
    vec4 world = InverseTransform * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 p = world.xyz / world.w;
    float below = Surface.x - p.y;
    if (below <= 0.05) {
        discard;
    }
    vec3 n = normalize(cross(dFdx(p), dFdy(p)));
    if (dot(n, p) > 0.0) n = -n;
    float up = 0.25 + 0.75 * clamp(n.y, 0.0, 1.0);
    vec2 hit = p.xz + vec2(Surface.y, 0.0) * below;
    float t = Light.w;
    float c = web(hit, t);
    c += 0.5 * web(hit * 1.9 + 3.7, t * 1.3);
    float fade = exp(-below * 0.05) * exp(-length(p) * 0.045) * smoothstep(0.05, 0.6, below);
    float glow = c * fade * up * Surface.w * 1.1;
    if (glow < 0.002) {
        discard;
    }
    fragColor = vec4(vec3(0.72, 0.93, 1.0) * glow, 1.0);
}
