// it gets attributes and uniforms from fragmentCommon3D

in vec4 passColor;

void main()
{
    vec2 offsetFromCenter = 2.0*(gl_PointCoord - vec2(0.5, 0.5)); // normalize so offset is between 0 and 1 instead 0 and 0.5
    float distSquared = dot(offsetFromCenter, offsetFromCenter);
    if(distSquared > 1.0) // this makes sure the particle is round
    discard;

    vec4 texel = passColor;

    // apply gamma correction and intensity
    //texel.rgb *= intensity; TODO: intensity? Probably not?
    outColor.rgb = pow(texel.rgb, vec3(gamma));

    // I want the particles to fade out towards the edge, the following seems to look nice
    texel.a *= min(1.0, particleFadeFactor*(1.0 - distSquared));

    outColor.a = texel.a; // I think alpha shouldn't be modified by gamma and intensity
}