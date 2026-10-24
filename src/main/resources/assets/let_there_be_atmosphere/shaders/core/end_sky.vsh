#version 150

uniform mat4 InverseTransform;
uniform mat3 Spin;

out vec4 ray;
out vec3 turned;

void main() {
    vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2) * 2.0 - 1.0;
    ray = InverseTransform * vec4(corner, 1.0, 1.0);
    turned = Spin * ray.xyz;
    gl_Position = vec4(corner, 0.0, 1.0);
}
