#version 330
#extension GL_ARB_separate_shader_objects : require

// An anime impact frame, figures only: whatever bodies are on screen (the game's own silhouette buffer, Mask) are inked
// as solid shapes, and everything else, ground included, becomes flat paper with focus lines rushing in from the
// edges. Where two figures overlap, a thin paper rim (from the depth step between them) keeps them apart.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D MaskSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DepthSize;
    vec2 MaskSize;
};

layout(std140) uniform InkConfig {
    vec4 InkColor;
    vec4 PaperColor;
    vec4 LineColor;
    // x: unused, y: seed for the focus lines, z: how many lines, w: how much of the paper they cover
    vec4 Params;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// Reversed depth (1 at the near plane, 0 at infinity): distance in blocks is close to 0.05 / depth.
float blocks(float d) {
    return 0.05 / max(1e-6, d);
}

float hash(float x) {
    return fract(sin(x * 127.1 + Params.y * 311.7) * 43758.5453);
}

void main() {
    vec2 px = 1.0 / DepthSize;
    float z = blocks(texture(DepthSampler, texCoord).r);
    float zl = blocks(texture(DepthSampler, texCoord - vec2(px.x, 0.0)).r);
    float zr = blocks(texture(DepthSampler, texCoord + vec2(px.x, 0.0)).r);
    float zu = blocks(texture(DepthSampler, texCoord - vec2(0.0, px.y)).r);
    float zd = blocks(texture(DepthSampler, texCoord + vec2(0.0, px.y)).r);
    float edge = clamp((abs(z - zl) + abs(z - zr) + abs(z - zu) + abs(z - zd)) / max(z, 0.6) * 0.9, 0.0, 1.0);

    float ink = smoothstep(0.15, 0.55, texture(MaskSampler, texCoord).a);

    vec2 c = texCoord - 0.5;
    c.x *= OutSize.x / OutSize.y;
    float r = length(c);
    float a = (atan(c.y, c.x) + 3.14159265) / 6.2831853;
    float lane = floor(a * Params.z);
    float within = fract(a * Params.z);
    float width = 0.18 + hash(lane + 3.0) * 0.5;
    float on = step(1.0 - Params.w, hash(lane)) * step(abs(within - 0.5) * 2.0, width);
    float reach = 0.16 + hash(lane + 11.0) * 0.32;
    float line = on * smoothstep(reach, reach + 0.18, r);

    vec3 paper = mix(PaperColor.rgb, LineColor.rgb, line);
    vec3 col = mix(paper, InkColor.rgb, ink);
    col = mix(col, PaperColor.rgb, edge * ink * 0.85);
    fragColor = vec4(col, 1.0);
}
