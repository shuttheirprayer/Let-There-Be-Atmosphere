#version 150

uniform sampler2D DepthSampler;
uniform mat4 InverseTransform;
uniform vec4 Surface;
uniform vec4 Horizon;
uniform vec4 Zenith;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    vec4 world = InverseTransform * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 p = world.xyz / world.w;
    vec3 n = cross(dFdx(p), dFdy(p));
    float level = abs(n.y) / max(length(n), 1.0e-6);
    float dist = length(p);
    if (depth >= 1.0 || p.y > -0.05 || level < 0.96 || abs(fract(p.y + Surface.x) - 0.8884) > 0.004 + dist * 0.00004) {
        discard;
    }
    vec3 view = p / dist;
    float graze = 1.0 - clamp(-view.y, 0.0, 1.0);
    float fresnel = graze * graze * graze * graze;
    vec3 sky = mix(Horizon.rgb, Zenith.rgb, smoothstep(0.02, 0.45, -view.y));
    float fog = clamp((dist - Surface.z) / max(Surface.w - Surface.z, 0.001), 0.0, 1.0);
    fragColor = vec4(sky, Surface.y * (0.06 + 0.64 * fresnel) * (1.0 - fog));
}
