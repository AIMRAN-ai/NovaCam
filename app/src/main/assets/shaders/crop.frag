#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec4 uCropRect;
uniform float uRotation;
uniform mat3 uPerspectiveMatrix;

void main() {
    vec2 uv = vTexCoord;
    
    if (uPerspectiveMatrix != mat3(1.0)) {
        vec3 pos = uPerspectiveMatrix * vec3(uv, 1.0);
        if (pos.z != 0.0) {
            uv = pos.xy / pos.z;
        }
    }
    
    float cosR = cos(uRotation);
    float sinR = sin(uRotation);
    vec2 centered = uv - 0.5;
    uv = vec2(
        centered.x * cosR - centered.y * sinR,
        centered.x * sinR + centered.y * cosR
    ) + 0.5;
    
    uv = uCropRect.xy + uv * uCropRect.zw;
    
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        discard;
    }
    
    vec3 color = texture(uTexture, uv).rgb;
    fragColor = vec4(color, 1.0);
}