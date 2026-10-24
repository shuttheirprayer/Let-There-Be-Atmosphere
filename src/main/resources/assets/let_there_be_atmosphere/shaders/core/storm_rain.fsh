#version 150

uniform sampler2D Sampler0;
uniform vec4 RainFog;
uniform vec4 FogRange;

in vec2 texCoord;
in vec4 vertexColor;
in float fogDistance;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord) * vertexColor;
    if (color.a < 0.01) {
        discard;
    }
    float fog = clamp((fogDistance - FogRange.x) / max(FogRange.y - FogRange.x, 0.001), 0.0, 1.0);
    fragColor = vec4(mix(color.rgb, RainFog.rgb, fog), color.a);
}
