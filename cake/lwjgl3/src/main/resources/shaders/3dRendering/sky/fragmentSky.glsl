// it gets attributes and uniforms from fragmentCommon3D

uniform sampler2D tex;

void main()
{
    vec4 texel = texture(tex, passTexCoord);

    // TODO: something about GL_BLEND vs GL_ALPHATEST etc

    // apply gamma correction
    // texel.rgb *= intensity; // TODO: really no intensity for sky?
    outColor.rgb = pow(texel.rgb, vec3(gamma));
    outColor.a = texel.a*alpha; // I think alpha shouldn't be modified by gamma and intensity
}