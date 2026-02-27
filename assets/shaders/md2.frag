in vec2 v_diffuseUV;
in vec3 v_modelNormalFrame2;

uniform int u_skinIndex;
uniform int u_skinCount;
// Per-instance opacity from material BlendingAttribute (set by Md2Shader).
uniform float u_opacity;
uniform vec3 u_entityLightColor;
uniform vec3 u_shadeVector;
uniform float u_gammaExponent;
uniform float u_intensity;
uniform sampler2D u_skinTexture0;
uniform sampler2D u_skinTexture1;
uniform sampler2D u_skinTexture2;
uniform sampler2D u_skinTexture3;
uniform sampler2D u_skinTexture4;
uniform sampler2D u_skinTexture5;
uniform sampler2D u_skinTexture6;
uniform sampler2D u_skinTexture7;
uniform sampler2D u_skinTexture8;
uniform sampler2D u_skinTexture9;
uniform sampler2D u_skinTexture10;
uniform sampler2D u_skinTexture11;

vec4 sampleSkin(int skinIndex, vec2 uv) {
    if (skinIndex == 0) return texture(u_skinTexture0, uv);
    if (skinIndex == 1) return texture(u_skinTexture1, uv);
    if (skinIndex == 2) return texture(u_skinTexture2, uv);
    if (skinIndex == 3) return texture(u_skinTexture3, uv);
    if (skinIndex == 4) return texture(u_skinTexture4, uv);
    if (skinIndex == 5) return texture(u_skinTexture5, uv);
    if (skinIndex == 6) return texture(u_skinTexture6, uv);
    if (skinIndex == 7) return texture(u_skinTexture7, uv);
    if (skinIndex == 8) return texture(u_skinTexture8, uv);
    if (skinIndex == 9) return texture(u_skinTexture9, uv);
    if (skinIndex == 10) return texture(u_skinTexture10, uv);
    return texture(u_skinTexture11, uv);
}

void main() {
    int maxIndex = max(u_skinCount - 1, 0);
    int skinIndex = clamp(u_skinIndex, 0, maxIndex);
    vec4 color = sampleSkin(skinIndex, v_diffuseUV);
    // Legacy alias counterpart:
    // - quantized yaw shadevector bucket (`SHADEDOT_QUANT = 16`),
    // - current-frame normal index and `shadedots` table (`dot + 1` response).
    float shadeVectorLength = length(u_shadeVector);
    float directional = 1.0;
    if (shadeVectorLength > 0.0001) {
        directional = dot(normalize(v_modelNormalFrame2), u_shadeVector / shadeVectorLength) + 1.0;
        // Yamagi/id alias shading (anormtab) stays within [0.70, 1.99].
        // Clamping here avoids over-dark side faces from raw dot-product extremes.
        directional = clamp(directional, 0.70, 1.99);
    }
    color.rgb *= u_entityLightColor * directional;
    color.rgb *= u_intensity;
    color.rgb = pow(max(color.rgb, vec3(0.0)), vec3(u_gammaExponent));
    color.a *= u_opacity;
    gl_FragColor = color;
}
