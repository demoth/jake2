// it gets attributes and uniforms from fragmentCommon3D

in vec4 passColor;

void main()
{
    vec4 texel = passColor;

    // apply gamma correction and intensity
    // texel.rgb *= intensity; // TODO: color-only rendering probably shouldn't use intensity?
    texel.a *= alpha; // is alpha even used here?
    outColor.rgb = pow(texel.rgb, vec3(gamma));
    outColor.a = texel.a; // I think alpha shouldn't be modified by gamma and intensity
}