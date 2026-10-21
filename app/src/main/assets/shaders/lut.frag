#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform sampler3D uLut;
uniform float uLutIntensity;

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;
    
    if (uLutIntensity > 0.0) {
        vec3 lutColor = texture(uLut, color).rgb;
        color = mix(color, lutColor, uLutIntensity);
    }
    
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}