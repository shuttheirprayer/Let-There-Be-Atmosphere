#version 150

uniform sampler2D GlowSampler;
uniform vec4 ShaftColor;
uniform vec4 MistColor;
uniform vec4 StormColor;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 glow = texture(GlowSampler, texCoord).rgb;
    vec3 colour = min(ShaftColor.rgb * glow.r + MistColor.rgb * glow.g + StormColor.rgb * glow.b, vec3(1.0));
    if (all(lessThanEqual(colour, vec3(0.0)))) discard;
    fragColor = vec4(colour, 1.0);
}
