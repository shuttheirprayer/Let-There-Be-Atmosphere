#version 150

uniform usampler2D OccSampler;
uniform sampler2D HeightSampler;
uniform vec4 Slope;

out vec4 fragColor;

int highest(uint v) {
    int r = 0;
    if (v >= 0x10000u) { v >>= 16; r += 16; }
    if (v >= 0x100u) { v >>= 8; r += 8; }
    if (v >= 0x10u) { v >>= 4; r += 4; }
    if (v >= 0x4u) { v >>= 2; r += 2; }
    if (v >= 0x2u) r += 1;
    return r;
}

uint span(int a, int b) {
    uint upper = b >= 31 ? 0xFFFFFFFFu : (1u << uint(b + 1)) - 1u;
    return upper & ~((1u << uint(a)) - 1u);
}

float heightAt(int c, int z) {
    return texelFetch(HeightSampler, ivec2(clamp(c + 48, 0, 127), z), 0).r;
}

void main() {
    int z = int(gl_FragCoord.y);
    float k = Slope.x;
    float u = Slope.y + (floor(gl_FragCoord.x) + 0.5) / Slope.z;
    float yLo = 0.0;
    float yHi = 32.0;
    if (abs(k) < 1.0e-4) {
        if (u < 0.0 || u >= 32.0) { fragColor = vec4(-1.0e4); return; }
    } else {
        float a = -u / k;
        float b = (32.0 - u) / k;
        yLo = max(yLo, min(a, b));
        yHi = min(yHi, max(a, b));
        if (yLo >= yHi) { fragColor = vec4(-1.0e4); return; }
    }
    float xs = u + yHi * k;
    if (abs(k) < 1.0e-4) {
        if (heightAt(int(floor(u)), z) > yHi) { fragColor = vec4(1.0e4); return; }
    } else {
        int step = k > 0.0 ? 1 : -1;
        int c = k > 0.0 ? int(floor(xs)) : int(ceil(xs)) - 1;
        for (int i = 0; i < 48; i++) {
            if (c < -48 || c > 79) break;
            float enter = max(yHi, ((k > 0.0 ? float(c) : float(c + 1)) - u) / k);
            if (enter > 400.0) break;
            if (enter < heightAt(c, z)) { fragColor = vec4(1.0e4); return; }
            c += step;
        }
    }
    int col;
    int dir;
    if (abs(k) < 1.0e-4) {
        col = int(floor(u));
        dir = 0;
    } else {
        col = k > 0.0 ? int(ceil(xs)) - 1 : int(floor(xs));
        dir = k > 0.0 ? -1 : 1;
    }
    col = clamp(col, 0, 31);
    for (int i = 0; i < 33; i++) {
        if (col < 0 || col > 31) break;
        float top = yHi;
        float bottom = yLo;
        if (dir != 0) {
            float y0 = (float(col) - u) / k;
            float y1 = (float(col + 1) - u) / k;
            top = min(yHi, max(y0, y1));
            bottom = max(yLo, min(y0, y1));
        }
        int a = max(int(floor(bottom)), 0);
        int b = min(int(ceil(top)) - 1, 31);
        if (a <= b) {
            uint hit = texelFetch(OccSampler, ivec2(col, z), 0).r & span(a, b);
            if (hit != 0u) {
                fragColor = vec4(min(top, float(highest(hit) + 1)));
                return;
            }
        }
        if (dir == 0) break;
        col += dir;
    }
    fragColor = vec4(-1.0e4);
}
