// it gets attributes and uniforms from fragmentCommon3D

uniform sampler2D tex;

in vec4 passColor;

void main()
{
    vec4 texel = texture(tex, passTexCoord);

    // apply gamma correction and intensity
    texel.rgb *= intensity;
    texel.a *= alpha; // is alpha even used here?
    texel *= min(vec4(1.5), passColor);

    outColor.rgb = pow(texel.rgb, vec3(gamma));
    outColor.a = texel.a; // I think alpha shouldn't be modified by gamma and intensity
}