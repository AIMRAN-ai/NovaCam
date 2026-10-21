#version 300 es
precision highp float;

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;

out vec2 vTexCoord;

uniform mat4 uMVPMatrix;
uniform mat3 uCropMatrix;

void main() {
    vec3 pos = uCropMatrix * vec3(aPosition, 1.0);
    gl_Position = uMVPMatrix * vec4(pos.xy, 0.0, 1.0);
    vTexCoord = aTexCoord;
}