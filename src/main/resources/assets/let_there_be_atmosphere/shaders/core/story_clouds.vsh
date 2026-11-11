#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CloudOffset;
uniform float CellSize;
uniform vec4 ColorModulator;
uniform vec2 FadeRange;
uniform vec4 Light;
uniform vec4 Pastel;
uniform float DepthPass;

out vec4 vertexColor;
out vec3 relative;
out vec2 cellUv;

void main() {
    relative = Position + CloudOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(relative, 1.0);
    vertexColor = Color;
    cellUv = Position.xz / CellSize;
    gl_ClipDistance[0] = 1.0;
    if (DepthPass > 0.5 && Pastel.w < 0.5 && (Pastel.x > 0.5 || Light.x < 0.5)) {
        float keep = 1.0 - 0.5 / max(Color.a * ColorModulator.a, 0.0001);
        gl_ClipDistance[0] = keep <= 0.0 ? -1.0
                : FadeRange.x + (FadeRange.y - FadeRange.x) * (0.5 - sin(asin(1.0 - 2.0 * keep) / 3.0)) - length(relative.xz);
    }
}
