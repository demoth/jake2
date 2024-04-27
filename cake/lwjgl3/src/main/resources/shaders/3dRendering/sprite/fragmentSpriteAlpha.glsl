// it gets attributes and uniforms from fragmentCommon3D

uniform sampler2D tex;

void main()
{
    vec4 texel = texture(tex, passTexCoord);

    if(texel.a <= 0.666)
    discard;

    // apply gamma correction and intensity
    texel.rgb *= intensity;
    outColor.rgb = pow(texel.rgb, vec3(gamma));
    outColor.a = texel.a*alpha; // I think alpha shouldn't be modified by gamma and intensity
}