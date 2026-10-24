#version 150

uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;
uniform mat4 InverseTransform;
uniform vec4 ShaftShape;
uniform vec4 Mist;
uniform vec4 MistShape;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    ivec2 size = textureSize(DepthSampler, 0);
    float depth = texelFetch(DepthSampler, min(ivec2(texCoord * vec2(size)), size - 1), 0).r;
    vec4 world = InverseTransform * vec4(texCoord * 2.0 - 1.0, min(depth, 0.9999) * 2.0 - 1.0, 1.0);
    vec3 p = world.xyz / world.w;
    float dist = length(p);
    vec3 dir = p / max(dist, 0.0001);
    float reach = min(dist, MistShape.z);
    ivec2 cell = ivec2(gl_FragCoord.xy) & 3;
    float jitter = (float[](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0)[cell.y * 4 + cell.x] + 0.5) / 16.0;
    float mist = 0.0;
    for (int i = 0; i < 8; i++) {
        float s = (float(i) + jitter) / 8.0;
        float t = reach * s * s;
        float dt = reach * 2.0 * s / 8.0;
        float y = t * dir.y + ShaftShape.z;
        float far = 1.0 - smoothstep(MistShape.w * 0.35, MistShape.w, t);
        mist += (exp(-max(y - ShaftShape.w, 0.0) * MistShape.y) + MistShape.x) * far * smoothstep(4.0, 20.0, t) * dt;
    }
    mist *= mix(0.7, 1.3, texture(NoiseSampler, Mist.xy + dir.xz * (reach * 0.8) * Mist.z).g);
    fragColor = vec4(0.0, min(1.0 - exp(-mist * Mist.w), 0.3), 0.0, 1.0);
}
