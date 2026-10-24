#version 150

uniform sampler2D NearSampler;
uniform sampler2D MidSampler;
uniform sampler2D FarSampler;
uniform vec3 Weights;

in vec2 texCoord;

out vec4 fragColor;

vec3 smoothSample(sampler2D image, vec2 uv) {
    vec2 size = vec2(textureSize(image, 0));
    vec2 coord = uv * size - 0.5;
    vec2 base = floor(coord);
    vec2 f = coord - base;
    vec2 f2 = f * f;
    vec2 f3 = f2 * f;
    vec2 w0 = (1.0 - 3.0 * f + 3.0 * f2 - f3) / 6.0;
    vec2 w1 = (4.0 - 6.0 * f2 + 3.0 * f3) / 6.0;
    vec2 w2 = (1.0 + 3.0 * f + 3.0 * f2 - 3.0 * f3) / 6.0;
    vec2 w3 = f3 / 6.0;
    vec2 g0 = w0 + w1;
    vec2 g1 = w2 + w3;
    vec2 p0 = (base - 0.5 + w1 / g0) / size;
    vec2 p1 = (base + 1.5 + w3 / g1) / size;
    return g0.y * (g0.x * texture(image, p0).rgb + g1.x * texture(image, vec2(p1.x, p0.y)).rgb)
         + g1.y * (g0.x * texture(image, vec2(p0.x, p1.y)).rgb + g1.x * texture(image, p1).rgb);
}

void main() {
    fragColor = vec4(smoothSample(NearSampler, texCoord) * Weights.x
            + smoothSample(MidSampler, texCoord) * Weights.y
            + smoothSample(FarSampler, texCoord) * Weights.z, 0.0);
}
