#version 330
#extension GL_ARB_separate_shader_objects : require

// An anime impact frame: everything near the camera inked as one solid silhouette, everything beyond it turned to
// flat paper, with radial focus lines rushing in from the edges. Depth decides ink from paper, so the figures read
// as shapes whatever their colours; a thin paper rim keeps overlapping figures apart.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DepthSize;
};

layout(std140) uniform InkConfig {
    vec4 InkColor;
    vec4 PaperColor;
    vec4 LineColor;
    // x: how near (blocks) is inked, y: seed for the focus lines, z: how many lines, w: how much of the paper they cover
    vec4 Params;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// Distance in blocks for a depth value. The game renders with reversed depth (1 at the near plane, 0 at infinity), so
// with its 0.05 near plane and a far plane far beyond it, distance is close to 0.05 / depth; the sky (0) is infinitely far.
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

    float ink = 1.0 - smoothstep(Params.x * 0.9, Params.x * 1.05, z);

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
