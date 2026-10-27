#version 150

uniform sampler2D DepthSampler;
uniform sampler2D LitSampler;
uniform sampler2D HeightSampler;
uniform mat4 InverseTransform;
uniform vec4 Slope;
uniform vec4 Camera;
uniform vec4 Sun;

in vec2 texCoord;

out vec4 fragColor;

float sunlit(vec3 l) {
    if (any(lessThan(l, vec3(0.0))) || any(greaterThanEqual(l, vec3(32.0)))) return 0.0;
    int i = int(floor((l.x - l.y * Slope.x - Slope.y) * Slope.z));
    if (l.y <= texelFetch(LitSampler, ivec2(clamp(i, 0, int(Slope.w) - 1), int(l.z)), 0).r) return 0.0;
    return l.y < texelFetch(HeightSampler, ivec2(int(l.x) + 48, int(l.z)), 0).g ? 1.0 : 0.0;
}

void main() {
    ivec2 size = textureSize(DepthSampler, 0);
    float depth = texelFetch(DepthSampler, ivec2(texCoord * vec2(size)), 0).r;
    vec4 world = InverseTransform * vec4(texCoord * 2.0 - 1.0, min(depth, 0.9999) * 2.0 - 1.0, 1.0);
    vec3 p = world.xyz / world.w;
    float reach = depth >= 1.0 ? Camera.w : min(length(p), Camera.w);
    vec3 dir = normalize(p);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float stride = reach / 16.0;
    float thick = 0.0;
    for (int i = 0; i < 16; i++) {
        float t = (float(i) + jitter) * stride;
        thick += sunlit(Camera.xyz + dir * t) * (1.0 - smoothstep(Camera.w * 0.65, Camera.w, t));
    }
    thick *= stride;
    float across = (1.0 - abs(dot(dir, Sun.xyz))) * 2.999;
    float band = (floor(across) + smoothstep(0.9, 1.0, fract(across))) * 0.5;
    fragColor = vec4((1.0 - exp(-thick * Sun.w)) * mix(0.5, 1.0, min(band, 1.0)));
}
