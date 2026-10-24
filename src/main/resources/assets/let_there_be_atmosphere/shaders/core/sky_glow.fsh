#version 150

uniform sampler2D Sampler0;
uniform vec4 Mode;
uniform vec4 Center[6];
uniform vec4 Size[6];
uniform vec4 Tint[6];

in vec2 texCoord;
in vec4 vertexColor;
in vec2 local;

out vec4 fragColor;

vec4 layer(int i, inout float alpha) {
    vec4 s = Size[i];
    float a = dot(local, s.zw) / s.x;
    float b = dot(local, vec2(-s.w, s.z)) / s.y;
    vec4 color = texture(Sampler0, vec2(Center[i].w + (a * 0.5 + 0.5) * Tint[i].a * 0.5, 0.5 - 0.5 * b)) * vec4(Tint[i].rgb, 1.0);
    if (abs(a) > 1.0 || abs(b) > 1.0 || color.a < 0.004) return vec4(0.0);
    alpha = color.a;
    return vec4(color.rgb * color.a, 1.0);
}

void main() {
    if (Mode.z > 0.5) {
        float alpha = 0.0;
        vec4 sum = layer(1, alpha);
        sum += layer(2, alpha);
        sum += layer(3, alpha);
        sum += layer(4, alpha);
        if (sum.a == 0.0) discard;
        fragColor = vec4(sum.rgb, alpha);
        return;
    }
    vec4 tex = texture(Sampler0, texCoord);
    if (Mode.x > 0.5) {
        vec4 lit = vec4(tex.rgb * vertexColor.rgb, tex.a * Mode.y);
        if (max(max(lit.r, lit.g), max(lit.b, lit.a)) < 0.004) {
            discard;
        }
        fragColor = lit;
        return;
    }
    vec4 color = tex * vertexColor;
    if (color.a < 0.004) {
        discard;
    }
    fragColor = color;
}
