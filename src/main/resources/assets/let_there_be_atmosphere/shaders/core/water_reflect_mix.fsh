#version 150

uniform sampler2D ReflectSampler;
uniform sampler2D SceneSampler;
uniform sampler2D BaseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D SolidSampler;
uniform mat4 InverseTransform;
uniform vec4 Spread;

in vec2 texCoord;

out vec4 fragColor;

vec3 unproject(vec2 ndc, float depth) {
    vec4 p = InverseTransform * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    float depth = texelFetch(DepthSampler, px, 0).r;
    if (depth >= 1.0 || depth >= texelFetch(SolidSampler, px, 0).r) discard;
    ivec2 reflectSize = textureSize(ReflectSampler, 0);
    vec2 halfPos = (vec2(px) + 0.5) * 0.5 - 0.5;
    ivec2 cell = ivec2(floor(halfPos));
    vec2 f = halfPos - vec2(cell);
    vec3 gathered = vec3(0.0);
    float alpha = 0.0;
    float cover = 0.0;
    for (int i = 0; i < 4; i++) {
        ivec2 o = ivec2(i & 1, i >> 1);
        vec4 t = texelFetch(ReflectSampler, clamp(cell + o, ivec2(0), reflectSize - 1), 0);
        if (t.a <= 0.0) continue;
        float w = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y) + 1.0e-4;
        gathered += t.rgb * t.a * w;
        alpha += t.a * w;
        cover += w;
    }
    if (alpha <= 0.0) discard;
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 eye = unproject(ndc, 0.0);
    vec3 surface = unproject(ndc, depth);
    vec4 centre = vec4(gathered / alpha, alpha / cover);
    vec3 dir = normalize(surface - eye);
    float foot = length(surface) * Spread.x / max(-dir.y, 0.02);
    float radius = clamp((foot - 0.12) * 3.0, 0.0, 8.0);
    vec3 colour = centre.rgb * centre.a;
    float weight = centre.a;
    float norm = 1.0;
    if (radius > 0.05) {
        int top = reflectSize.y - 1;
        for (int i = 1; i <= 3; i++) {
            float offset = float(i) * radius / 3.0;
            int step = int(offset + 0.5);
            if (step == 0) continue;
            float g = exp(-float(i * i) / 4.5);
            for (int side = -1; side <= 1; side += 2) {
                vec4 tap = texelFetch(ReflectSampler, ivec2(px.x / 2, clamp((px.y + side * step) / 2, 0, top)), 0);
                if (tap.a <= 0.0) continue;
                colour += tap.rgb * tap.a * g;
                weight += tap.a * g;
                norm += g;
            }
        }
    }
    vec3 reflection = colour / max(weight, 1.0e-4);
    weight /= norm;
    vec3 base = texelFetch(BaseSampler, px, 0).rgb;
    vec3 over = texelFetch(SceneSampler, px, 0).rgb - base;
    float busy = smoothstep(0.05, 0.2, max(abs(over.r), max(abs(over.g), abs(over.b))));
    fragColor = vec4(max(mix(base, reflection, weight * (1.0 - busy)) + over, 0.0), 1.0);
}
