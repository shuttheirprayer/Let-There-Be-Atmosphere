#version 150

uniform sampler3D NebulaSampler;
uniform mat3 Face;

in vec2 texCoord;

out vec4 fragColor;

const mat3 RA = mat3(0.6749, 0.4227, -0.6048, 0.0000, 0.8196, 0.5729, 0.7379, -0.3866, 0.5532);
const mat3 RB = mat3(-0.3327, -0.4271, -0.8408, 0.0000, 0.8916, -0.4529, 0.9430, -0.1507, -0.2967);
const vec3 BAND = vec3(0.3003, 0.8610, 0.4105);
const vec3 TEAL = vec3(0.184, 0.847, 0.784);
const vec3 RAMP[6] = vec3[6](vec3(0.035, 0.016, 0.094), vec3(0.122, 0.047, 0.271), vec3(0.290, 0.114, 0.576),
        vec3(0.627, 0.235, 0.753), vec3(0.780, 0.340, 0.720), vec3(0.880, 0.620, 0.860));

vec3 ramp(float t) {
    float x = clamp(t, 0.0, 1.0) * 5.0;
    int i = min(int(x), 4);
    return mix(RAMP[i], RAMP[i + 1], x - float(i));
}

void main() {
    vec3 dir = normalize(Face * vec3(texCoord * 2.0 - 1.0, 1.0));
    vec4 a = texture(NebulaSampler, RA * dir * 0.25);
    vec4 b = texture(NebulaSampler, RB * dir * 0.55 + (a.rgb - 0.5) * 0.35);
    float fbm = b.r * 0.55 + b.g * 0.3 + b.b * 0.15;
    float lane = dot(dir, BAND);
    float d = (fbm - 0.5) * 1.3 + 0.5 + exp(-lane * lane * 12.0) * 0.55 + (a.a - 0.5) * 0.3 - 0.32;
    float t = smoothstep(0.3, 1.0, d);
    t = clamp(t + (b.b - 0.5) * 0.25 * smoothstep(0.35, 0.7, t), 0.0, 1.0);
    float q = floor(t * 5.0);
    t = mix(t, (q + smoothstep(0.75, 1.0, t * 5.0 - q)) / 5.0, 0.6);
    vec3 color = ramp(t);
    float teal = smoothstep(0.6, 0.8, a.g) * smoothstep(0.1, 0.4, t) * (1.0 - smoothstep(0.6, 0.85, t)) * 0.8;
    fragColor = vec4(mix(color, TEAL * (0.35 + 0.9 * t), teal), (a.r * 9.0 + b.g * 5.0) / 14.0);
}
