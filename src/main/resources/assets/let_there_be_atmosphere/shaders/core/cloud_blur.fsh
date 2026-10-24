#version 150

uniform sampler2D LayerSampler;
uniform vec4 Step;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 d = Step.xy;
    vec4 c = texture(LayerSampler, texCoord) * 0.2042;
    vec2 near = d * 1.40726;
    vec2 far = d * 3.29389;
    c += (texture(LayerSampler, texCoord + near) + texture(LayerSampler, texCoord - near)) * 0.3040;
    c += (texture(LayerSampler, texCoord + far) + texture(LayerSampler, texCoord - far)) * 0.0939;
    fragColor = c;
}
