#version 150

uniform sampler2D SceneSampler;
uniform sampler2D MaskSampler;

out vec4 fragColor;

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    if (texelFetch(MaskSampler, px / 2, 0).r < 0.5) discard;
    fragColor = texelFetch(SceneSampler, px, 0);
}
