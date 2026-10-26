#version 150

uniform sampler2D Sampler0;
uniform vec4 MistFog;

in vec2 texCoord;
flat in float cell;
in vec4 vertexColor;

out vec4 fragColor;

vec4 churn(float t, float phase) {
    vec2 w = vec2(sin(texCoord.y * 7.0 + t * 0.35 + phase), cos(texCoord.x * 5.0 - t * 0.27 + phase * 1.7)) * 0.035;
    vec2 uv = clamp(texCoord + w, 0.0, 1.0);
    return texture(Sampler0, vec2((cell + uv.x) * 0.25, uv.y));
}

void main() {
    float t = MistFog.w;
    vec4 tex = mix(churn(t, cell * 2.1), churn(t * 0.8, cell * 2.1 + 3.0), 0.5);
    float alpha = tex.a * vertexColor.a;
    if (alpha < 0.002) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb * tex.rgb, alpha);
}
