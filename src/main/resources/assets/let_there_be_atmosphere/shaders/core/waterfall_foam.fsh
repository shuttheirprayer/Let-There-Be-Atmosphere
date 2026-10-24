#version 150

uniform sampler2D FoamSampler;
uniform vec4 Clock;
uniform vec4 FoamFog;

in vec2 uv;
in vec3 lightColor;
in float fogDistance;
in float strength;
flat in vec4 rect;
flat in float wrapY;

out vec4 fragColor;

void main() {
    ivec2 size = ivec2(rect.zw);
    ivec2 p = ivec2(floor(uv));
    p.x = clamp(p.x, 0, size.x - 1);
    p.y = wrapY > 0.5 ? ((p.y % size.y) + size.y) % size.y : clamp(p.y, 0, size.y - 1);
    vec4 texel = texelFetch(FoamSampler, ivec2(rect.xy) + p, 0);
    float alpha = texel.a * strength;
    if (alpha < 0.01) {
        discard;
    }
    float fog = clamp((fogDistance - Clock.y) / max(Clock.z - Clock.y, 0.001), 0.0, 1.0);
    float lum = dot(lightColor, vec3(0.299, 0.587, 0.114));
    vec3 lit = lightColor * mix(0.45, 1.0, smoothstep(0.12, 0.7, lum));
    fragColor = vec4(mix(texel.rgb * lit, FoamFog.rgb, fog), alpha * (1.0 - fog));
}
