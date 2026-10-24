#version 150

uniform sampler2D BlurSampler;
uniform sampler2D DepthSampler;
uniform mat4 InverseTransform;
uniform vec4 Layers;

in vec2 texCoord;

out vec4 fragColor;

float entry(vec3 dir, float bottom, float thickness) {
    if (thickness <= 0.0) return 1.0e9;
    if (abs(dir.y) < 1.0e-5) return bottom <= 0.0 && bottom + thickness >= 0.0 ? 0.0 : 1.0e9;
    float a = bottom / dir.y;
    float b = (bottom + thickness) / dir.y;
    return max(a, b) < 0.0 ? 1.0e9 : max(min(a, b), 0.0);
}

void main() {
    vec4 c = texture(BlurSampler, texCoord);
    if (c.a < 0.002) discard;
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    if (depth < 1.0) {
        vec2 ndc = texCoord * 2.0 - 1.0;
        vec4 far = InverseTransform * vec4(ndc, 1.0, 1.0);
        vec3 dir = normalize(far.xyz / far.w);
        vec4 w = InverseTransform * vec4(ndc, depth * 2.0 - 1.0, 1.0);
        if (length(w.xyz / w.w) < min(entry(dir, Layers.x, Layers.y), entry(dir, Layers.z, Layers.w)) - 0.5) discard;
    }
    fragColor = c;
}
