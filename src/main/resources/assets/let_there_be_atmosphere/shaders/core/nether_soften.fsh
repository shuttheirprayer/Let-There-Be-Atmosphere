#version 150

uniform sampler2D GlowSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(GlowSampler, 0));
    vec2 c = (floor(gl_FragCoord.xy) + 0.5) * texel;
    vec4 inner = texture(GlowSampler, c + texel * vec2(-0.5, -0.5)) + texture(GlowSampler, c + texel * vec2(1.5, -0.5))
            + texture(GlowSampler, c + texel * vec2(-0.5, 1.5)) + texture(GlowSampler, c + texel * vec2(1.5, 1.5));
    vec4 outer = texture(GlowSampler, c + texel * vec2(-2.5, -2.5)) + texture(GlowSampler, c + texel * vec2(3.5, -2.5))
            + texture(GlowSampler, c + texel * vec2(-2.5, 3.5)) + texture(GlowSampler, c + texel * vec2(3.5, 3.5));
    fragColor = 0.15 * inner + 0.1 * outer;
}
