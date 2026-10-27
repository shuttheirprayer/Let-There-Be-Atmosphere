#version 150

uniform sampler2D FrostMap;
uniform vec4 Screen;
uniform vec4 Frost;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    ivec2 p = ivec2(floor(gl_FragCoord.x / Screen.x), floor((Screen.y - gl_FragCoord.y) / Screen.x));
    p = clamp(p, ivec2(0), ivec2(Screen.zw) - 1);
    vec2 m = texelFetch(FrostMap, p, 0).rg;
    float a = m.r;
    if (a <= 0.0 || a > Frost.x + 0.002) discard;
    int k = int(m.g * 255.0 + 0.5);
    vec4 col;
    if (k == 1) {
        col = vec4(0.97, 0.995, 1.0, 0.94);
    } else if (k == 2) {
        col = vec4(0.5, 0.74, 0.95, 0.9);
    } else if (a < 0.5) {
        col = vec4(0.7, 0.87, 1.0, 0.62);
    } else {
        col = vec4(0.82, 0.93, 1.0, 0.36);
    }
    float fresh = 1.0 - smoothstep(0.0, 0.06, Frost.x - a);
    col.rgb = mix(col.rgb, vec3(1.0), fresh * 0.6);
    fragColor = col;
}
