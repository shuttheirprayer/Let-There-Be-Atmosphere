#version 150

uniform mat4 Transform;
uniform vec3 Offset;
uniform vec4 Clock;
uniform vec4 Sun;
uniform vec4 Screen;
uniform sampler2D DataSampler;

out vec2 texel;
flat out vec2 cell;
flat out vec4 tint;

const vec3 MOON = vec3(0.8, 0.9, 1.0);

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    texel = vec2(0.0);
    cell = vec2(0.0);
    tint = vec4(0.0);
}

void main() {
    int slot = gl_VertexID / 6;
    int corner = gl_VertexID - slot * 6;
    int vertex = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 3 : 0));
    vec4 a = texelFetch(DataSampler, ivec2(slot, 0), 0);
    vec4 b = texelFetch(DataSampler, ivec2(slot, 1), 0);
    float t = (Clock.x - a.w) / max(b.x, 0.05);
    vec3 rel = a.xyz + Offset;
    float dist = length(rel);
    vec3 view = rel / max(dist, 0.001);
    float glitter = smoothstep(0.72, 0.99, dot(vec3(view.x, -view.y, view.z), Sun.xyz));
    float fog = 1.0 - clamp((dist - Clock.y) / max(Clock.z - Clock.y, 0.001), 0.0, 1.0);
    float shine = Clock.w * fog * mix(0.14, 1.0, glitter) * (1.0 - smoothstep(48.0, 64.0, dist));
    if (t < 0.0 || t > 1.0 || view.y > -0.004 || shine < 0.06) {
        hide();
        return;
    }
    vec4 clip = Transform * vec4(a.xyz - view * 0.15, 1.0);
    if (clip.w < 0.1) {
        hide();
        return;
    }
    float top = dist < 12.0 ? 4.0 : dist < 28.0 ? 3.0 : dist < 56.0 ? 2.0 : 1.0;
    top = clamp(top + (glitter > 0.5 ? 1.0 : 0.0) - (glitter < 0.2 ? 1.0 : 0.0) - (b.y < 0.3 ? 1.0 : 0.0), 1.0, 4.0);
    float rise = 1.0 - abs(2.0 * t - 1.0);
    float frame = min(floor(rise * (top + 1.0)), top);
    cell = vec2(frame * 9.0, b.y > 0.75 ? 9.0 : 0.0);
    vec2 pixel = floor((clip.xy / clip.w * 0.5 + 0.5) * Screen.xy);
    float size = 9.0 * Screen.z;
    vec2 low = vec2(pixel.x - floor(size * 0.5), pixel.y);
    vec2 q = vec2(vertex == 1 || vertex == 2 ? 1.0 : 0.0, vertex >= 2 ? 1.0 : 0.0);
    vec2 at = low + q * size;
    gl_Position = vec4((at / Screen.xy * 2.0 - 1.0) * clip.w, clip.z, clip.w);
    texel = vec2(q.x, 1.0 - q.y) * 9.0;
    tint = vec4(MOON, smoothstep(0.04, 0.4, shine));
}
