#version 150

uniform vec4 Ring;
uniform vec3 Fade;
uniform vec3 Tint;

in float height;

out vec4 fragColor;

void main() {
    float a = height < Ring.y
            ? mix(Fade.x, Fade.y, smoothstep(Ring.x, Ring.y, height))
            : mix(Fade.z, Fade.y, smoothstep(Ring.z, Ring.y, height));
    float dither = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))) - 0.5;
    a = clamp(a + dither / 255.0, 0.0, 1.0);
    if (a <= 0.0) discard;
    fragColor = vec4(Tint, a);
}
