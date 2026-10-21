#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;

uniform float uExposure;
uniform float uEv;
uniform float uBrilliance;
uniform float uVignette;
uniform float uNoiseReduction;
uniform float uDefinition;
uniform float uSharpness;
uniform float uTint;
uniform float uWarmth;
uniform float uVibrance;
uniform float uBlackPoint;
uniform float uBrightness;
uniform float uContrast;
uniform float uShadows;
uniform float uHighlights;

uniform sampler3D uLut;
uniform float uLutIntensity;

const vec3 LUMINANCE_VECTOR = vec3(0.2126, 0.7152, 0.0722);

float luminance(vec3 color) {
    return dot(color, LUMINANCE_VECTOR);
}

vec3 adjustExposure(vec3 color, float exposure) {
    return color * pow(2.0, exposure);
}

vec3 adjustBrightnessContrast(vec3 color, float brightness, float contrast) {
    color = color + brightness;
    color = (color - 0.5) * (1.0 + contrast) + 0.5;
    return color;
}

vec3 adjustShadowsHighlights(vec3 color, float shadows, float highlights) {
    float lum = luminance(color);
    float shadowMask = smoothstep(0.0, 0.5, 1.0 - lum);
    float highlightMask = smoothstep(0.5, 1.0, lum);
    color += shadows * shadowMask * (1.0 - lum) * 0.5;
    color -= highlights * highlightMask * lum * 0.5;
    return color;
}

vec3 adjustBlackPoint(vec3 color, float blackPoint) {
    return max(color, vec3(blackPoint * 0.01));
}

vec3 adjustWarmthTint(vec3 color, float warmth, float tint) {
    float lum = luminance(color);
    vec3 warmColor = vec3(1.0, 0.8, 0.6);
    vec3 coolColor = vec3(0.6, 0.8, 1.0);
    vec3 tintColor = vec3(0.8, 1.0, 0.8);
    vec3 antiTintColor = vec3(1.0, 0.8, 1.0);
    color = mix(color, warmColor * lum, warmth * 0.5);
    color = mix(color, coolColor * lum, -warmth * 0.5);
    color = mix(color, tintColor * lum, tint * 0.3);
    color = mix(color, antiTintColor * lum, -tint * 0.3);
    return color;
}

vec3 adjustVibrance(vec3 color, float vibrance) {
    float lum = luminance(color);
    float maxC = max(max(color.r, color.g), color.b);
    float sat = maxC - lum;
    float vibranceFactor = 1.0 + vibrance * (1.0 - sat) * 0.5;
    return mix(vec3(lum), color, vibranceFactor);
}

vec3 adjustDefinition(vec3 color, float definition) {
    return color;
}

vec3 adjustVignette(vec3 color, float vignette, vec2 center, float radius) {
    float dist = length((vTexCoord - center) / radius);
    float vignetteFactor = 1.0 - vignette * smoothstep(0.5, 1.0, dist);
    return color * vignetteFactor;
}

vec3 applyLut(vec3 color, sampler3D lut, float intensity) {
    if (intensity <= 0.0) return color;
    vec3 lutColor = texture(lut, color).rgb;
    return mix(color, lutColor, intensity);
}

vec3 applySharpness(vec3 color, vec2 texCoord, sampler2D tex, float sharpness) {
    if (sharpness <= 0.0) return color;
    vec2 texelSize = 1.0 / vec2(textureSize(tex, 0));
    vec3 center = texture(tex, texCoord).rgb;
    vec3 up = texture(tex, texCoord + vec2(0.0, texelSize.y)).rgb;
    vec3 down = texture(tex, texCoord - vec2(0.0, texelSize.y)).rgb;
    vec3 left = texture(tex, texCoord - vec2(texelSize.x, 0.0)).rgb;
    vec3 right = texture(tex, texCoord + vec2(texelSize.x, 0.0)).rgb;
    vec3 laplacian = (up + down + left + right) * 0.25 - center;
    return color + laplacian * sharpness * 0.5;
}

float noiseReductionWeight(vec3 center, vec3 neighbor, float strength) {
    float diff = length(center - neighbor);
    return exp(-diff * strength * 10.0);
}

vec3 applyNoiseReduction(vec3 color, vec2 texCoord, sampler2D tex, float strength) {
    if (strength <= 0.0) return color;
    vec2 texelSize = 1.0 / vec2(textureSize(tex, 0));
    vec3 center = color;
    vec3 sum = vec3(0.0);
    float totalWeight = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            if (x == 0 && y == 0) continue;
            vec2 offset = vec2(float(x), float(y)) * texelSize;
            vec3 neighbor = texture(tex, texCoord + offset).rgb;
            float weight = noiseReductionWeight(center, neighbor, strength);
            sum += neighbor * weight;
            totalWeight += weight;
        }
    }
    float centerWeight = 1.0;
    sum += center * centerWeight;
    totalWeight += centerWeight;
    return sum / totalWeight;
}

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;
    
    color = adjustExposure(color, uExposure + uEv);
    color = adjustBlackPoint(color, uBlackPoint);
    color = adjustBrightnessContrast(color, uBrightness, uContrast);
    color = adjustShadowsHighlights(color, uShadows, uHighlights);
    color = adjustWarmthTint(color, uWarmth, uTint);
    color = adjustVibrance(color, uVibrance);
    
    color = applyNoiseReduction(color, vTexCoord, uTexture, uNoiseReduction);
    
    color = adjustDefinition(color, uDefinition);
    
    color = applySharpness(color, vTexCoord, uTexture, uSharpness);
    
    color = adjustVignette(color, uVignette, vec2(0.5), 0.7);
    
    color = applyLut(color, uLut, uLutIntensity);
    
    color = clamp(color, 0.0, 1.0);
    
    fragColor = vec4(color, 1.0);
}