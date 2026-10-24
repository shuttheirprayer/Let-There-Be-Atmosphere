#version 150

uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;
uniform mat4 InverseTransform;
uniform vec4 Surface;
uniform vec4 Light;

in vec2 texCoord;

out vec4 fragColor;

const int STEPS = 6;

float shaft(vec2 at) {
    vec2 uv = Light.xy + at * Light.z;
    float a = texture(NoiseSampler, uv + vec2(Light.w * 0.004, Light.w * 0.003)).r;
    float b = texture(NoiseSampler, uv * 2.0 - vec2(Light.w * 0.006, -Light.w * 0.005)).g;
    return smoothstep(0.55, 0.85, a * 0.7 + b * 0.3);
}

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy) * 4 + 1, 0).r;
    vec4 world = InverseTransform * vec4(texCoord * 2.0 - 1.0, min(depth, 0.9999) * 2.0 - 1.0, 1.0);
    vec3 end = world.xyz / world.w;
    float reach = length(end);
    if (reach > Surface.w) end *= Surface.w / reach;
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec2 slant = Surface.yz;
    float sum = 0.0;
    for (int i = 0; i < STEPS; i++) {
        vec3 q = end * ((float(i) + jitter) / float(STEPS));
        float below = Surface.x - q.y;
        if (below <= 0.0) continue;
        vec2 hit = q.xz + slant * below;
        float far = length(q);
        sum += shaft(hit) * exp(-below * 0.07 - far * 0.04) * smoothstep(1.0, 6.0, far);
    }
    fragColor = vec4(sum / float(STEPS));
}
