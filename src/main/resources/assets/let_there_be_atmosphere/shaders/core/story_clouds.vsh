#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CloudOffset;
uniform float CellSize;

out vec4 vertexColor;
out vec3 relative;
out vec2 cellUv;

void main() {
    relative = Position + CloudOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(relative, 1.0);
    vertexColor = Color;
    cellUv = Position.xz / CellSize;
}
