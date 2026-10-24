#version 150

uniform sampler2D DepthSampler;
uniform sampler2D CloudSampler;
uniform sampler2D NoiseSampler;
uniform sampler2D HeightSampler;
uniform sampler2D CoverSampler;
uniform float Cover;
uniform mat4 InverseTransform;
uniform vec4 Cloud;
uniform vec2 CloudGrid;
uniform vec4 Sun;
uniform vec4 Ray;
uniform vec4 Mist;
uniform vec4 MistDrift;
uniform vec4 Ground;
uniform vec4 GroundOrigin;
uniform vec4 Puddle;
uniform vec2 HeightCenter;
uniform vec4 Wet;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy) * 2, 0).r;
    vec4 world = InverseTransform * vec4(texCoord * 2.0 - 1.0, min(depth, 0.9999) * 2.0 - 1.0, 1.0);
    vec3 p = world.xyz / world.w;
    vec3 normal = cross(dFdx(p), dFdy(p));
    float upright = abs(normal.y) / max(length(normal), 1.0e-6);
    if (depth >= 1.0) {
        vec2 offset = (texCoord - Ray.xy) * vec2(Ray.z, 1.0);
        float open = Cover > 0.5 ? 1.0 - 0.9 * texture(CoverSampler, texCoord).a : 1.0;
        fragColor = vec4(Ray.z > 0.0 ? exp(-dot(offset, offset) * 3.0) * open : 0.0, 1.0, 1.0, 1.0);
        return;
    }
    if (Cloud.z > 4.0 && p.y > Cloud.z - 2.0 - length(p) * 0.03) {
        fragColor = vec4(0.0, 1.0, 1.0, 1.0);
        return;
    }
    vec2 local = Ground.xy + p.xz;
    float exposed = 1.0;
    float soil = 1.0;
    float dry = 0.0;
    vec2 block = floor(local) + GroundOrigin.xy;
    vec2 chunk = floor(block / 16.0) - HeightCenter;
    if (Ground.w > 0.5 && chunk.x >= -4.0 && chunk.y >= -4.0 && chunk.x <= 3.0 && chunk.y <= 3.0) {
        vec2 column = mod(block, 128.0);
        float top = texelFetch(HeightSampler, ivec2(column), 0).r;
        dry = top > 10000.0 ? 1.0 : 0.0;
        top -= dry * 16384.0;
        float leafy = top > 2048.0 ? 1.0 : 0.0;
        soil = (1.0 - leafy) * (1.0 - dry);
        exposed = step(top - leafy * 4096.0 - 1.0, Ground.z + p.y);
    }
    float shade = 1.0;
    if (Cloud.w > 0.0) {
        float fade = clamp((Sun.w - length(p)) / (Sun.w - Sun.z), 0.0, 1.0) * clamp((Cloud.z - p.y) * 0.125, 0.0, 1.0);
        if (fade > 0.0) {
            vec2 hit = p.xz + vec2(Sun.x, 0.0) * ((Cloud.z - p.y) / Sun.y);
            float cover = smoothstep(0.5 - Ray.w, 0.5 + Ray.w, texture(CloudSampler, (Cloud.xy + hit / CloudGrid.x) / CloudGrid.y).a);
            shade = 1.0 - Cloud.w * cover * fade * exposed;
        }
    }
    float distance = length(p);
    float mist = 0.0;
    if (Mist.y > 0.0) {
        float h0 = clamp(-Mist.x, -4.0, 60.0);
        float h1 = clamp(p.y - Mist.x, -4.0, 60.0);
        float e0 = exp(-Mist.z * h0);
        float e1 = exp(-Mist.z * h1);
        float rise = h1 - h0;
        float average = abs(rise) > 0.01 ? (e0 - e1) / (Mist.z * rise) : e0;
        float wisp = texture(NoiseSampler, MistDrift.xy + p.xz * (0.5 * MistDrift.z)).r;
        mist = min(1.0 - exp(-Mist.y * distance * average * mix(1.0, wisp * 1.8, Mist.w)), 0.98);
    }
    float puddle = 0.0;
    if (Puddle.w > 0.0 && exposed * soil > 0.5 && distance < 96.0) {
        vec2 n = texture(NoiseSampler, Puddle.xy + local * Puddle.z).rg;
        float field = mix(n.r, n.g, 0.3);
        puddle = smoothstep(Puddle.w, Puddle.w + 0.035, field) * smoothstep(0.92, 0.98, upright) * (1.0 - smoothstep(48.0, 96.0, distance));
        if (GroundOrigin.w > 0.0 && puddle > 0.0 && distance < 24.0) {
            float ring = 0.0;
            for (int layer = 0; layer < 2; layer++) {
                vec2 cellPos = (local + mod(GroundOrigin.xy, 64.0) + float(layer) * vec2(0.31, 0.47)) / 0.6;
                vec2 cell = mod(floor(cellPos), 256.0);
                float seed = fract(sin(dot(cell + float(layer) * 19.0, vec2(12.9898, 78.233))) * 43758.5453);
                vec2 spot = 0.25 + 0.5 * vec2(seed, fract(seed * 7.13));
                float phase = fract(GroundOrigin.z * 1.6 + seed + float(layer) * 0.5);
                float r = length(fract(cellPos) - spot);
                ring = max(ring, (1.0 - smoothstep(0.0, 0.05, abs(r - phase * 0.26))) * (1.0 - phase) * (1.0 - phase));
            }
            puddle = min(puddle * (0.75 + 1.1 * ring * GroundOrigin.w * (1.0 - distance / 24.0)), 0.98);
        }
    }
    if (Wet.x > 0.0) {
        vec3 n = normal / max(length(normal), 1.0e-6);
        n *= -sign(dot(n, p));
        float wet = n.y > -0.4 ? 1.0 : 0.0;
        vec2 dampBlock = floor(Ground.xy + p.xz + n.xz * 0.1) + GroundOrigin.xy;
        vec2 dampChunk = floor(dampBlock / 16.0) - HeightCenter;
        if (wet > 0.0 && Ground.w > 0.5 && dampChunk.x >= -4.0 && dampChunk.y >= -4.0 && dampChunk.x <= 3.0 && dampChunk.y <= 3.0) {
            vec2 roof = texelFetch(HeightSampler, ivec2(mod(dampBlock, 128.0)), 0).rg;
            float roofTop = roof.r > 10000.0 ? roof.r - 16384.0 : roof.r;
            roofTop -= roofTop > 2048.0 ? 4096.0 : 0.0;
            wet = roof.g > -10000.0 ? step(roofTop - 1.0, Ground.z + p.y + n.y * 0.1) : 0.0;
        }
        wet *= (1.0 - dry) * clamp((Sun.w - distance) / max(Sun.w - Sun.z, 1.0), 0.0, 1.0);
        shade *= 1.0 - Wet.y * wet;
        puddle = max(puddle, Wet.z * wet * smoothstep(0.92, 0.98, upright) * (1.0 - smoothstep(48.0, 96.0, distance)));
    }
    fragColor = vec4(puddle, shade, mist, clamp(distance / Sun.w, 0.0, 1.0));
}
