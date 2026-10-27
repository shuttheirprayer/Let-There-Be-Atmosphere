#version 150

uniform sampler2D Sampler0;
uniform vec4 BatFog;

in vec2 texCoord;
in float fade;
in float haze;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord);
    if (color.a < 0.3) {
        discard;
    }
    float alpha = color.a * fade;
    fragColor = vec4(mix(color.rgb, BatFog.rgb, haze) * alpha, alpha);
}
