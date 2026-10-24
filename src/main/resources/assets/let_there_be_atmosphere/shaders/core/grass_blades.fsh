#version 150

uniform sampler2D Sampler0;
uniform vec4 Clock;
uniform vec4 BladeFog;

in vec2 texCoord;
in vec3 tintColor;
in vec3 lightColor;
in float fogDistance;
in float shade;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord);
    if (tex.a < 0.5) {
        discard;
    }
    vec3 color = tex.rgb * tintColor * lightColor * shade;
    float fog = clamp((fogDistance - Clock.y) / max(Clock.z - Clock.y, 0.001), 0.0, 1.0);
    fragColor = vec4(mix(color, BladeFog.rgb, fog), 1.0);
}
