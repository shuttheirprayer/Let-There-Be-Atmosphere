#version 150

uniform sampler2D AverageSampler;
uniform vec3 Threshold;

out vec2 texCoord;
flat out vec2 exposure;

void main() {
    vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    texCoord = corner;
    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
    exposure = vec2(Threshold.x, 1.0);
    if (Threshold.z > 0.5) {
        float average = 0.0;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                average += textureLod(AverageSampler, vec2(x, y) / 3.0 + 1.0 / 6.0, 0.0).a;
            }
        }
        float dark = 1.0 - smoothstep(0.02, 0.35, average / 9.0);
        float bright = smoothstep(0.3, 0.6, average / 9.0);
        exposure = vec2(mix(Threshold.x * mix(1.0, 0.65, dark), max(Threshold.x, 0.93), bright), mix(1.0, 6.0, dark));
    }
}
