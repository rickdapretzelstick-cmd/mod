#version 330
#extension GL_ARB_separate_shader_objects : require

// A negative impact frame: the picture inverted and crushed to hard contrast, with a colour cast.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform NegativeConfig {
    // rgb: the cast multiplied in, a: contrast
    vec4 Cast;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec3 c = 1.0 - texture(InSampler, texCoord).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    vec3 hard = clamp((vec3(l) - 0.5) * Cast.a + 0.5, 0.0, 1.0);
    fragColor = vec4(mix(hard, hard * Cast.rgb, 0.65), 1.0);
}
