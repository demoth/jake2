// it gets attributes and uniforms from fragmentCommon3D

uniform sampler2D tex;

void main()
{
    vec2 tc = passTexCoord;
    tc.s += sin( passTexCoord.t*0.125 + time ) * 4.0;
    tc.s += scroll;
    tc.t += sin( passTexCoord.s*0.125 + time ) * 4.0;
    tc *= 1.0/64.0; // do this last

    vec4 texel = texture(tex, tc);

    // apply intensity and gamma
    texel.rgb *= intensity * lightScaleForTurb;
    outColor.rgb = pow(texel.rgb, vec3(gamma));
    outColor.a = texel.a*alpha; // I think alpha shouldn't be modified by gamma and intensity
}