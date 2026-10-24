#version 150

uniform sampler2D GlowSampler;
uniform vec4 ShaftColor;
uniform vec4 MistColor;
uniform vec4 StormColor;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 glow = texture(GlowSampler, texCoord).rgb;
    fragColor = vec4(min(ShaftColor.rgb * glow.r + MistColor.rgb * glow.g + StormColor.rgb * glow.b, vec3(1.0)), 1.0);
}
