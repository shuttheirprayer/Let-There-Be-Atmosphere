#version 150

uniform mat4 Transform;
uniform vec4 Shape;
uniform vec4 Layout;
uniform vec3 TopColor;
uniform vec3 MidColor;
uniform vec3 LowColor;
uniform sampler2D Noise;
uniform vec4 Band[8];
uniform float Phase[136];
uniform vec2 Glow[17];

out vec4 vertexColor;

const float TWO_PI = 6.283185307179586;
const int RISE = 0;
const int RISE_PREV = 10;
const int WAVE = 20;
const int WAVE_PREV = 28;

float noise(int row, int prev, int column, float t) {
    float before = texelFetch(Noise, ivec2(column, prev), 0).r;
    float now = texelFetch(Noise, ivec2(column, row), 0).r;
    return before + (now - before) * t;
}

float alpha(float value) {
    return float(clamp(int(value * 255.0), 0, 255)) / 255.0;
}

void main() {
    int detail = int(Layout.x);
    int columns = detail - 1;
    int quad = gl_VertexID / 6;
    int corner = gl_VertexID - quad * 6;
    int layer = quad & 3;
    int strip = quad >> 2;
    int band = strip / columns;
    int k = strip - band * columns + 1;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));

    float f = float(k) / float(detail);
    float fromEdge = min(f, 1.0 - f);
    float ramp = fromEdge < Layout.y ? fromEdge / Layout.y : 1.0;
    float glow = 0.0;
    int glows = int(Layout.w);
    int phaseBase = band * 17;
    for (int i = 0; i <= glows; i++) {
        glow += Glow[i].x * cos(mod(f * Glow[i].y + Phase[phaseBase + i], TWO_PI));
    }
    float shade = ramp * (0.5 + glow * 0.5);
    float brightness = Band[band].x;
    float aTop = alpha(0.6 * brightness * shade);
    float aMid = alpha(0.9 * brightness * shade);
    float aLow = alpha(0.1 * brightness * shade);
    if (shade <= 0.0 || aTop + aMid + aLow <= 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        vertexColor = vec4(0.0);
        return;
    }

    float t = Layout.z;
    float hLow = noise(RISE + band, RISE_PREV + band, k, t) * 200.0;
    float hMid = noise(RISE + band + 1, RISE_PREV + band + 1, k, t) * 500.0;
    float hTop = noise(RISE + band + 2, RISE_PREV + band + 2, k, t) * 3000.0;
    float halfHeight = Band[band].z;
    float blue = -halfHeight;
    float green = halfHeight * 6.0 + blue;
    float red = halfHeight * 2.0 + green;
    float lowTop = blue + hLow;
    float mid = green + hLow + hMid;
    float top = red + hLow + hMid;

    bool upper = vertex >= 2;
    float y;
    vec3 rgb;
    float a;
    if (layer == 0) {
        y = upper ? lowTop : blue - halfHeight * 0.5 - hLow;
        rgb = LowColor;
        a = upper ? aLow : 0.0;
    } else if (layer == 1) {
        y = upper ? mid : lowTop;
        rgb = upper ? MidColor : LowColor;
        a = upper ? aMid : aLow;
    } else if (layer == 2) {
        y = upper ? top : mid;
        rgb = upper ? TopColor : MidColor;
        a = upper ? aTop : aMid;
    } else {
        y = upper ? red + halfHeight + red + hLow + hMid + hTop : top;
        rgb = TopColor;
        a = upper ? 0.0 : aTop;
    }

    int p = (vertex == 1 || vertex == 2) ? k + 1 : k;
    float fp = float(p) / float(detail);
    float angle = TWO_PI * fp;
    float s = sin(angle);
    float c = cos(angle);
    float bandOffset = 1600.0 * float(band);
    float halfWidth = Band[band].y;
    float r = noise(WAVE + band, WAVE_PREV + band, p, t) * Shape.w - bandOffset;
    float across = -halfWidth + halfWidth * 2.0 * fp + bandOffset;
    float x = Shape.x * s + s * r + across * Shape.z;
    float z = Shape.y * c + c * r;
    gl_Position = Transform * vec4(x, y, z, 1.0);
    vertexColor = vec4(rgb, a);
}
