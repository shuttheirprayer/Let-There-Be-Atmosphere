#version 150

uniform sampler2D Sampler0;
uniform sampler2D DepthSampler;
uniform vec4 Depth;
uniform vec4 Ember;
uniform vec4 Ash;
uniform vec4 Fume;
uniform vec4 Murk;
uniform vec4 FogColor;

in vec2 texCoord;
in float vertexAlpha;
in vec2 info;
in vec2 puff;

out vec4 fragColor;

void main() {
    int code = int(info.x + 0.5);
    float scene = Depth.z < 0.5 ? texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r : 1.0;
    if (code != 7 && gl_FragCoord.z > scene) {
        discard;
    }
    vec4 tex;
    float alpha;
    if (code == 7) {
        float r = length(texCoord);
        float ang = atan(texCoord.y, texCoord.x);
        float lump = 1.0 + 0.16 * sin(ang * 3.0 + puff.x) + 0.09 * sin(ang * 5.0 + puff.y) + 0.05 * sin(ang * 8.0 - puff.x);
        float body = 1.0 - smoothstep(0.25, 0.95, r * lump);
        float sceneDist = Depth.y / (Depth.x + scene * 2.0 - 1.0);
        float soft = clamp((sceneDist - Depth.y / (Depth.x + gl_FragCoord.z * 2.0 - 1.0)) / 7.0, 0.0, 1.0);
        float shade = 0.5 + 0.25 * texCoord.y + 0.15 * sin(texCoord.x * 2.3 + puff.y);
        tex = vec4(shade, shade, shade, body * body * soft);
    } else {
        tex = texture(Sampler0, texCoord);
    }
    alpha = tex.a * vertexAlpha;
    if (alpha < 0.01) {
        discard;
    }
    float lum = tex.r;
    vec3 color;
    if (code == 0) {
        color = mix(Ember.rgb * 0.7, vec3(1.0, 0.95, 0.7), smoothstep(0.8, 1.0, lum));
    } else if (code == 1) {
        color = Ash.rgb * (0.6 + 0.9 * lum) + Ember.rgb * (lum > 0.95 ? 0.18 : 0.0);
    } else if (code == 2) {
        color = Fume.rgb * (0.7 + 0.5 * lum);
    } else if (code == 3 || code == 5) {
        color = Fume.rgb * (0.55 + 0.55 * lum);
    } else if (code == 7) {
        color = mix(Murk.rgb * 1.3, Fume.rgb * 1.5, smoothstep(0.3, 0.85, lum));
    } else {
        color = Murk.rgb * (0.7 + 0.5 * lum);
    }
    color = mix(color, FogColor.rgb, info.y);
    if (code <= 2) alpha *= 1.0 - info.y;
    fragColor = vec4(color, alpha);
}
