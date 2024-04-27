in vec2 passTexCoord;

// for UBO shared between all shaders (incl. 2D)
layout (std140) uniform uniCommon
{
    float gamma;
    float intensity;
    float intensity2D; // for HUD, menu etc

    vec4 color;
};

uniform sampler2D tex;

out vec4 outColor;

void main()
{
    vec4 texel = texture(tex, passTexCoord);
    // the gl1 renderer used glAlphaFunc(GL_GREATER, 0.666);
    // and glEnable(GL_ALPHA_TEST); for 2D rendering
    // this should do the same
    if(texel.a <= 0.666)
    discard;

    // apply gamma correction and intensity
    texel.rgb *= intensity2D;
    outColor.rgb = pow(texel.rgb, vec3(gamma));
    outColor.a = texel.a; // I think alpha shouldn't be modified by gamma and intensity
}