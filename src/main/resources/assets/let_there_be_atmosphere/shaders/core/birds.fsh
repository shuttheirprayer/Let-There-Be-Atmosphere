#version 150

uniform sampler2D Sampler0;
uniform vec4 Clock;
uniform vec4 BirdFog;

in vec2 texCoord;
in vec3 tint;
in float fogDistance;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord);
    if (color.a < 0.3) {
        discard;
    }
    float fog = clamp((fogDistance - Clock.y) / max(Clock.z - Clock.y, 0.001), 0.0, 1.0);
    fragColor = vec4(mix(color.rgb * tint, BirdFog.rgb * color.a, fog), color.a);
}
