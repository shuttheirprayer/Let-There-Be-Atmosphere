#version 150

uniform sampler2D SunSampler;
uniform vec4 Flare;
uniform mat4 BloomReproject;
uniform float BloomReused;

out vec2 texCoord;
flat out float seen;
out vec3 bloomThen;

void main() {
    vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    texCoord = corner;
    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
    vec4 then = vec4(corner * 2.0 - 1.0, 1.0, 1.0);
    if (BloomReused > 0.5) then = BloomReproject * then;
    bloomThen = then.xyw;
    seen = 0.0;
    if (Flare.z > 0.0) {
        for (int i = 0; i < 5; i++) {
            vec2 o = Flare.xy + vec2(i == 1 ? 0.01 : (i == 2 ? -0.01 : 0.0), i == 3 ? 0.016 : (i == 4 ? -0.016 : 0.0));
            if (o.x > 0.0 && o.x < 1.0 && o.y > 0.0 && o.y < 1.0) seen += step(0.99, textureLod(SunSampler, o, 0.0).b);
        }
    }
}
