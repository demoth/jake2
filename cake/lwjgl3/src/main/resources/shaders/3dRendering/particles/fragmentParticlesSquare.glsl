// it gets attributes and uniforms from fragmentCommon3D

in vec4 passColor;

void main()
{
    // outColor = passColor;
    // so far we didn't use gamma correction for square particles, but this way
    // uniCommon is referenced so hopefully Intels Ivy Bridge HD4000 GPU driver
    // for Windows stops shitting itself (see https://github.com/yquake2/yquake2/issues/391)
    outColor.rgb = pow(passColor.rgb, vec3(gamma));
    outColor.a = passColor.a;
}