// it gets attributes and uniforms from fragmentCommon3D

struct DynLight { // gl3UniDynLight in C
                  vec3 lightOrigin;
                  float _pad;
                  //vec3 lightColor;
                  //float lightIntensity;
                  vec4 lightColor; // .a is intensity; this way it also works on OSX...
                  // (otherwise lightIntensity always contained 1 there)
};

layout (std140) uniform uniLights
{
    DynLight dynLights[32];
    uint numDynLights;
    uint _pad1; uint _pad2; uint _pad3; // FFS, AMD!
};

uniform sampler2D tex;

uniform sampler2D lightmap0;
uniform sampler2D lightmap1;
uniform sampler2D lightmap2;
uniform sampler2D lightmap3;

uniform vec4 lmScales[4];

in vec2 passLMcoord;
in vec3 passWorldCoord;
in vec3 passNormal;
flat in uint passLightFlags;

void main()
{
    vec4 texel = texture(tex, passTexCoord);

    // apply intensity
    texel.rgb *= intensity;

    // apply lightmap
    vec4 lmTex = texture(lightmap0, passLMcoord) * lmScales[0];
    lmTex     += texture(lightmap1, passLMcoord) * lmScales[1];
    lmTex     += texture(lightmap2, passLMcoord) * lmScales[2];
    lmTex     += texture(lightmap3, passLMcoord) * lmScales[3];

    if(passLightFlags != 0u)
    {
        // TODO: or is hardcoding 32 better?
        for(uint i=0u; i<numDynLights; ++i)
        {
            // I made the following up, it's probably not too cool..
            // it basically checks if the light is on the right side of the surface
            // and, if it is, sets intensity according to distance between light and pixel on surface

            // dyn light number i does not affect this plane, just skip it
            if((passLightFlags & (1u << i)) == 0u)  continue;

            float intens = dynLights[i].lightColor.a;

            vec3 lightToPos = dynLights[i].lightOrigin - passWorldCoord;
            float distLightToPos = length(lightToPos);
            float fact = max(0.0, intens - distLightToPos - 52.0);

            // move the light source a bit further above the surface
            // => helps if the lightsource is so close to the surface (e.g. grenades, rockets)
            //    that the dot product below would return 0
            // (light sources that are below the surface are filtered out by lightFlags)
            lightToPos += passNormal*32.0;

            // also factor in angle between light and point on surface
            fact *= max(0.0, dot(passNormal, normalize(lightToPos)));


            lmTex.rgb += dynLights[i].lightColor.rgb * fact * (1.0/256.0);
        }
    }

    lmTex.rgb *= overbrightbits;
    outColor = lmTex*texel;
    outColor.rgb = pow(outColor.rgb, vec3(gamma)); // apply gamma correction to result

    outColor.a = 1.0; // lightmaps aren't used with translucent surfaces
}