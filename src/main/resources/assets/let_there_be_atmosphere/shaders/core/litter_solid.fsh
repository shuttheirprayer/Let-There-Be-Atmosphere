#version 150

uniform sampler2D Sampler0;
uniform sampler2D AtlasSampler;
uniform vec4 Clock;
uniform vec4 LitterFog;

in vec3 baseColor;
in vec2 texCoord;
flat in float atlas;
in float fogDistance;
in float fade;

out vec4 fragColor;

void main() {
    float noise = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    if (noise >= fade) {
        discard;
    }
    vec3 color = (atlas > 0.5 ? texture(AtlasSampler, texCoord).rgb : texture(Sampler0, texCoord).rgb) * baseColor;
    float fog = clamp((fogDistance - Clock.y) / max(Clock.z - Clock.y, 0.001), 0.0, 1.0);
    fragColor = vec4(mix(color, LitterFog.rgb, fog), 1.0);
}
