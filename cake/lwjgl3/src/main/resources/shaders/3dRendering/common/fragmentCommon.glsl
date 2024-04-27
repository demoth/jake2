in vec2 passTexCoord;

out vec4 outColor;

// for UBO shared between all shaders (incl. 2D)
layout (std140) uniform uniCommon
{
    float gamma; // this is 1.0/vid_gamma
    float intensity;
    float intensity2D; // for HUD, menus etc

    vec4 color; // really?
};
// for UBO shared between all 3D shaders
layout (std140) uniform uni3D
{
    mat4 transProjView;
    mat4 transModel;

    float scroll; // for SURF_FLOWING
    float time;
    float alpha;
    float overbrightbits;
    float particleFadeFactor;
    float lightScaleForTurb; // surfaces with SURF_DRAWTURB (water, lava) don't have lightmaps, use this instead
    float _pad_1; // AMDs legacy windows driver needs this, otherwise uni3D has wrong size
    float _pad_2;
};