#version 150

uniform sampler2D Sampler0;

in vec2 texel;
flat in vec2 cell;
flat in vec4 tint;

out vec4 fragColor;

void main() {
    vec4 c = texelFetch(Sampler0, ivec2(cell + min(floor(texel), vec2(8.0))), 0);
    float a = c.a * tint.a;
    if (a < 0.01) {
        discard;
    }
    fragColor = vec4(c.rgb * tint.rgb * a, a);
}
