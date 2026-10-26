#version 150

uniform sampler2D Sampler0;

in vec2 texCoord;
flat in float cell;
flat in float cool;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, vec2((cell + texCoord.x) * 0.2, texCoord.y));
    float alpha = tex.a * vertexColor.a;
    if (alpha < 0.002) {
        discard;
    }
    vec3 shade = mix(vec3(dot(tex.rgb, vec3(0.34, 0.33, 0.33))), tex.rgb, cool);
    fragColor = vec4(vertexColor.rgb * shade, alpha);
}
