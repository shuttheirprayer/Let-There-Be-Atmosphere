#version 150

uniform sampler2D MaskSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(MaskSampler, 0));
    float sum = texture(MaskSampler, texCoord).r * 2.0;
    sum += texture(MaskSampler, texCoord + vec2(0.0, texel.y * 1.5)).r * 1.5;
    sum += texture(MaskSampler, texCoord - vec2(0.0, texel.y * 1.5)).r * 1.5;
    sum += texture(MaskSampler, texCoord + vec2(0.0, texel.y * 3.5)).r;
    sum += texture(MaskSampler, texCoord - vec2(0.0, texel.y * 3.5)).r;
    sum += texture(MaskSampler, texCoord + vec2(texel.x * 1.5, 0.0)).r;
    sum += texture(MaskSampler, texCoord - vec2(texel.x * 1.5, 0.0)).r;
    fragColor = vec4(sum / 9.0);
}
