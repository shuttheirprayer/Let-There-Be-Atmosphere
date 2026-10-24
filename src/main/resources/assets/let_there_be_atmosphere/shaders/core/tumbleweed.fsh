#version 150

uniform sampler2D Sampler0;
uniform vec4 Clock;
uniform vec4 TumbleFog;

in vec2 texCoord;
in vec3 lightColor;
in float fogDistance;
in float fade;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord);
    if (tex.a < 0.5) {
        discard;
    }
    float noise = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    if (noise >= fade) {
        discard;
    }
    vec3 color = tex.rgb * lightColor;
    float fog = clamp((fogDistance - Clock.y) / max(Clock.z - Clock.y, 0.001), 0.0, 1.0);
    fragColor = vec4(mix(color, TumbleFog.rgb, fog), 1.0);
}
