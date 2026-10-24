#version 150

uniform sampler2D TailSampler;
uniform sampler2D HeadSampler;

in vec2 texCoord;
in vec4 vertexColor;
flat in int head;

out vec4 fragColor;

void main() {
    vec4 color = (head == 1 ? texture(HeadSampler, texCoord) : texture(TailSampler, texCoord)) * vertexColor;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = color;
}
