#version 150

uniform sampler2D MaskSampler;
uniform vec4 Ray;
uniform vec2 Fall;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 stride = (Ray.xy - texCoord) * (Ray.z / 12.0);
    vec2 uv = texCoord + stride * (Fall.y > 0.0 ? fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))) : 0.5);
    float sum = 0.0;
    float total = 0.0;
    float weight = 1.0;
    for (int i = 0; i < 12; i++) {
        vec4 source = texture(MaskSampler, uv);
        sum += (Fall.y > 0.0 ? source.r : source.r * step(0.99, source.b)) * weight;
        total += weight;
        weight *= Ray.w;
        uv += stride;
    }
    vec2 offset = (texCoord - Ray.xy) * vec2(Fall.x, 1.0);
    float light = sum / total;
    fragColor = vec4(Fall.y > 0.0 ? light * sqrt(light) * exp(-dot(offset, offset) * Fall.y) : light);
}
