#version 150

uniform sampler2D BeamSampler;
uniform sampler2D DepthSampler;
uniform sampler2D LitSampler;
uniform sampler2D HeightSampler;
uniform mat4 InverseTransform;
uniform vec4 Slope;
uniform vec4 Camera;
uniform vec4 Tint;
uniform vec4 Patch;

in vec2 texCoord;

out vec4 fragColor;

float sunlit(vec3 l) {
    if (any(lessThan(l, vec3(0.0))) || any(greaterThanEqual(l, vec3(32.0)))) return 0.0;
    int i = int(floor((l.x - l.y * Slope.x - Slope.y) * Slope.z));
    if (l.y <= texelFetch(LitSampler, ivec2(clamp(i, 0, int(Slope.w) - 1), int(l.z)), 0).r) return 0.0;
    return l.y < texelFetch(HeightSampler, ivec2(int(l.x) + 48, int(l.z)), 0).g ? 1.0 : 0.0;
}

void main() {
    float beam = texture(BeamSampler, texCoord).r;
    float light = 0.0;
    if (Patch.x > 0.0) {
        float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
        if (depth < 1.0) {
            vec4 world = InverseTransform * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
            vec3 p = world.xyz / world.w;
            float d = length(p);
            light = sunlit(Camera.xyz + p * (1.0 - (0.02 + d * 0.002) / max(d, 0.01))) * (1.0 - smoothstep(Camera.w * 0.65, Camera.w, d));
        }
    }
    float a = min(beam * Tint.w + light * Patch.x, 0.6);
    fragColor = vec4(Tint.rgb * a, a);
}
