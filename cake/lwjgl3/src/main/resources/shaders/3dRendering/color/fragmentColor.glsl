// it gets attributes and uniforms from fragmentCommon3D

void main()
{
    vec4 texel = color;

    // apply gamma correction and intensity
    // texel.rgb *= intensity; TODO: use intensity here? (this is used for beams)
    outColor.rgb = pow(texel.rgb, vec3(gamma));
    outColor.a = texel.a*alpha; // I think alpha shouldn't be modified by gamma and intensity
}