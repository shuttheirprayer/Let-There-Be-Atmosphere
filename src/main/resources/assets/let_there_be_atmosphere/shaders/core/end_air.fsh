#version 150

uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;
uniform mat4 InverseTransform;
uniform vec4 Cam;
uniform vec4 Mist;
uniform vec4 Shadow;
uniform vec4 Drift;
uniform vec4 Haze;

in vec2 texCoord;

out vec4 fragColor;

vec4 over(vec4 base, vec3 color, float alpha) {
    return vec4(color * alpha + base.rgb * (1.0 - alpha), alpha + base.a * (1.0 - alpha));
}

void main() {
    ivec2 size = textureSize(DepthSampler, 0);
    float depth = texelFetch(DepthSampler, min(ivec2(texCoord * vec2(size)), size - 1), 0).r;
    vec4 world = InverseTransform * vec4(texCoord * 2.0 - 1.0, min(depth, 0.9999) * 2.0 - 1.0, 1.0);
    vec3 p = world.xyz / world.w;
    float dist = length(p);
    vec3 dir = p / max(dist, 0.0001);
    vec4 c = vec4(0.0);
    vec3 n = cross(dFdx(p), dFdy(p));

    if (depth >= 1.0) {
        float below = 1.0 - smoothstep(-0.45, 0.03, dir.y);
        c = over(c, vec3(0.085, 0.03, 0.2), below * below * 0.62 * Haze.x);
        fragColor = c;
        return;
    }

    vec3 w = p + Cam.xyz;
    float ground = smoothstep(0.7, 0.9, abs(n.y) / max(length(n), 1.0e-6));

    if (Shadow.z > 0.0 && ground > 0.0) {
        float s = texture(NoiseSampler, vec2(w.x / 256.0, w.z / 128.0) + Shadow.xy).r;
        float blob = smoothstep(0.52, 0.74, s);
        float a = blob * min(Shadow.z, 1.0) * 0.42 * ground * (1.0 - smoothstep(90.0, 170.0, dist));
        c = over(c, vec3(0.12, 0.04, 0.38), min(a * 1.3, 0.6));
    }

    if (Mist.z > 0.0) {
        float eye = abs(Cam.y - w.y);
        float path = dist * Mist.w / max(eye, Mist.w);
        float m1 = texture(NoiseSampler, w.xz / 32.0 + Drift.xy).g;
        float m2 = texture(NoiseSampler, w.xz / 64.0 + Drift.zw).r;
        float m = mix(m1, m2, 0.4);
        float density = smoothstep(0.32, 0.78, m);
        float tau = path * density * Mist.z * 0.026;
        float a = min(1.0 - exp(-tau), 0.65);
        a += ground * smoothstep(0.55, 0.85, m2) * 0.1 * min(Mist.z, 1.5);
        c = over(c, vec3(0.6, 0.48, 0.9) * (0.85 + 0.15 * m1), a);
    }

    if (Haze.x > 0.0) {
        float drop = clamp((Cam.y - w.y) / 48.0, 0.0, 1.0);
        float a = (1.0 - exp(-dist * 0.012)) * drop * 0.6 * min(Haze.x, 1.5);
        c = over(c, vec3(0.11, 0.045, 0.24), a);
    }

    fragColor = c;
}
