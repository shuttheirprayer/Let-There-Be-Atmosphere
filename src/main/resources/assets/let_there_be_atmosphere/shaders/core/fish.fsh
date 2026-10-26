#version 150

uniform sampler2D Sampler0;
uniform vec4 Clock;
uniform vec4 FishFog;

in vec2 texCoord;
in vec3 lightColor;
in float fogDistance;
in float lift;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord);
    if (color.a < 0.5 || lift < -0.02) {
        discard;
    }
    float fog = clamp((fogDistance - Clock.y) / max(Clock.z - Clock.y, 0.001), 0.0, 1.0);
    fragColor = vec4(mix(color.rgb * lightColor, FishFog.rgb, fog), 1.0);
}
