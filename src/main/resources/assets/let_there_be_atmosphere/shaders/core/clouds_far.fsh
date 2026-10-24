#version 150

uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform float FogStart;
uniform float FogEnd;

in vec4 vertexColor;
in float vertexDistance;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    float end = FogStart + (FogEnd - FogStart) * 4.0;
    float fade = vertexDistance <= FogStart ? 1.0 : vertexDistance >= end ? 0.0 : smoothstep(end, FogStart, vertexDistance);
    float alpha = clamp(color.a * fade * FogColor.a, 0.0, 1.0);
    if (alpha < 0.08) {
        discard;
    }
    fragColor = vec4(mix(FogColor.rgb, color.rgb, 0.7), alpha);
}
