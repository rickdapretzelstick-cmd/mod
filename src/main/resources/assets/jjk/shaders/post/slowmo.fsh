#version 330
#extension GL_ARB_separate_shader_objects : require

// The look of time slowing: colour drained toward grey, a heavy vignette, and the edges of the frame split slightly
// into red and blue as if smeared.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform SlowConfig {
    // x: how much colour is drained, y: vignette strength, z: edge split (texels), w: tint toward cold blue
    vec4 Params;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 c = texCoord - 0.5;
    float r = length(c * vec2(OutSize.x / OutSize.y, 1.0));
    vec2 split = c * Params.z / InSize;
    vec3 col;
    col.r = texture(InSampler, texCoord + split).r;
    col.g = texture(InSampler, texCoord).g;
    col.b = texture(InSampler, texCoord - split).b;
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(l), Params.x);
    col = mix(col, col * vec3(0.82, 0.92, 1.12), Params.w);
    col *= 1.0 - smoothstep(0.35, 0.95, r) * Params.y;
    fragColor = vec4(col, 1.0);
}
