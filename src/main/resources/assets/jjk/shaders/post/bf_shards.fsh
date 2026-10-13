#version 330
#extension GL_ARB_separate_shader_objects : require

// A Black Flash impact frame (after JJS's Resolute Black Flash finisher): the picture crushed to black and white, torn
// into big angular shards radiating from the impact, some flooded crimson, some burnt white, the rest black.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ShardConfig {
    // x: seed, y: shards round the frame, z: threshold of the crushed picture, w: how much of the picture shows
    vec4 Seed;
    // rgb: the hot shards' colour, a: their share
    vec4 Hot;
    // rgb: the second shards' colour, a: their share
    vec4 Cold;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7)) + Seed.x * 17.13) * 43758.5453);
}

void main() {
    vec2 p = (texCoord - 0.5) * vec2(OutSize.x / max(1.0, OutSize.y), 1.0);
    float ang = atan(p.y, p.x);
    float r = length(p);
    // Jagged sectors from the centre, broken into bands: angular shards.
    // Straight-edged: each ring of the frame turns its wedges by a different step, so the edges break at angles.
    float ring = floor(r * 3.2 + 0.6 * hash(vec2(floor(ang * 1.7), 3.0)));
    float s = ang / 6.2831853 * Seed.y + 0.55 * hash(vec2(ring, 11.0)) + 0.18 * (r * 3.2 - ring);
    float sector = floor(s);
    float band = ring;
    float h = hash(vec2(sector, band));
    vec3 shard = h < Hot.a ? Hot.rgb : (h < Hot.a + Cold.a ? Cold.rgb : vec3(0.0));
    // The picture, crushed: figures and edges black on white.
    vec3 c = texture(InSampler, texCoord).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    float paper = step(Seed.z, l);
    // Shard borders: thin crimson seams.
    float f = fract(s);
    float seam = 1.0 - smoothstep(0.0, 0.035, min(f, 1.0 - f));
    // Where the picture is light, the shard's colour (white where the shard is black); where dark, black.
    bool none = h >= Hot.a + Cold.a;
    vec3 picture = paper * (none ? vec3(1.0) : shard);
    vec3 col = mix(shard, picture, Seed.w);
    col = mix(col, vec3(0.85, 0.02, 0.06), seam * 0.9);
    // A white-hot core at the impact.
    col += vec3(1.0, 0.9, 0.85) * smoothstep(0.09, 0.0, r);
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
