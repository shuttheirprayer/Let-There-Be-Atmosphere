#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 FogColor;

in vec2 texCoord;
in vec4 hotColor;
in vec3 deepColor;
in vec4 aux;

out vec4 fragColor;

void main() {
    int kind = int(aux.x + 0.5);
    if (aux.x < -0.5 || hotColor.a < 0.003) {
        discard;
    }
    if (kind == 0) {
        vec4 tex = texture(Sampler1, texCoord);
        if (tex.a < 0.01) discard;
        vec3 color = hotColor.rgb * (0.7 + 0.3 * tex.r) * tex.a * hotColor.a;
        fragColor = vec4(min(color, vec3(0.6)), 0.0);
        return;
    }
    vec3 color;
    float alpha;
    float glue = 0.75;
    if (kind == 4) {
        vec2 c = vec2(aux.z, aux.w) * 2.0 - 1.0;
        float d = length(c);
        float glow = floor(pow(max(1.0 - d, 0.0), 1.7) * 7.0) / 7.0;
        float core = 1.0 - smoothstep(0.0, 0.3, d);
        if (glow <= 0.0) discard;
        color = mix(mix(deepColor, hotColor.rgb, 0.55 + 0.45 * glow), vec3(1.0, 0.98, 0.82), core * 0.85);
        alpha = hotColor.a * (0.35 + 0.65 * glow);
        glue = 0.25;
    } else {
        vec4 tex = texture(Sampler0, texCoord);
        if (tex.a < 0.01) discard;
        color = mix(deepColor, hotColor.rgb, smoothstep(0.42, 1.0, tex.r));
        alpha = tex.a * hotColor.a;
        glue = kind == 5 ? 1.0 : 0.7;
    }
    color = mix(color, FogColor.rgb, aux.y * 0.8);
    alpha *= 1.0 - aux.y * 0.6;
    fragColor = vec4(color * alpha, alpha * glue);
}
