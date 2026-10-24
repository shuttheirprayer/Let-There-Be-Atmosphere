#version 150

uniform sampler2D Sampler0;
uniform vec4 AirFog;
uniform vec4 FogRange;

in vec2 texCoord;
in vec4 vertexColor;
in float fogDistance;
in float shape;
in vec4 lumpA;
in vec2 lumpB;

out vec4 fragColor;

void main() {
    vec4 color;
    if (shape > 1.5) {
        float across = 1.0 - abs(texCoord.y * 2.0 - 1.0);
        float along = smoothstep(0.0, 0.3, texCoord.x) * smoothstep(1.0, 0.7, texCoord.x);
        color = vec4(vertexColor.rgb, vertexColor.a * across * across * along);
    } else if (shape > 0.5) {
        vec2 p = texCoord * 2.0 - 1.0;
        vec2 d0 = p - lumpA.xy;
        vec2 d1 = p - lumpA.zw;
        vec2 d2 = p - lumpB;
        float w0 = max(0.0, 1.0 - dot(d0, d0) * 4.0);
        float w1 = max(0.0, 1.0 - dot(d1, d1) * 4.0);
        float w2 = max(0.0, 1.0 - dot(d2, d2) * 4.0);
        float sum = w0 * w0 + w1 * w1 + w2 * w2;
        float body = 1.0 - exp(-1.9 * sum);
        float core = smoothstep(0.2, 1.1, sum);
        float shade = 0.9 + 0.14 * smoothstep(-0.7, 0.8, p.y);
        vec3 rim = min(AirFog.rgb * 1.08 + 0.03, vec3(1.0));
        color = vec4(mix(rim, vertexColor.rgb, 0.2 + 0.3 * core) * shade, vertexColor.a * body);
    } else {
        color = texture(Sampler0, texCoord) * vertexColor;
    }
    if (color.a < 0.004) {
        discard;
    }
    float fog = clamp((fogDistance - FogRange.x) / max(FogRange.y - FogRange.x, 0.001), 0.0, 1.0);
    fragColor = vec4(mix(color.rgb, AirFog.rgb, fog), color.a);
}
