in vec2 passTexCoord;

// for UBO shared between all shaders (incl. 2D)
// TODO: not needed here, remove?
layout (std140) uniform uniCommon
{
    float gamma;
    float intensity;
    float intensity2D; // for HUD, menu etc

    vec4 color;
};

const float PI = 3.14159265358979323846;

uniform sampler2D tex;

uniform float time;
uniform vec4 v_blend;

out vec4 outColor;

void main()
{
    vec2 uv = passTexCoord;

    // warping based on vkquake2
    // here uv is always between 0 and 1 so ignore all that scrWidth and gl_FragCoord stuff
    //float sx = pc.scale - abs(pc.scrWidth  / 2.0 - gl_FragCoord.x) * 2.0 / pc.scrWidth;
    //float sy = pc.scale - abs(pc.scrHeight / 2.0 - gl_FragCoord.y) * 2.0 / pc.scrHeight;
    float sx = 1.0 - abs(0.5-uv.x)*2.0;
    float sy = 1.0 - abs(0.5-uv.y)*2.0;
    float xShift = 2.0 * time + uv.y * PI * 10.0;
    float yShift = 2.0 * time + uv.x * PI * 10.0;
    vec2 distortion = vec2(sin(xShift) * sx, sin(yShift) * sy) * 0.00666;

    uv += distortion;
    uv = clamp(uv, vec2(0.0, 0.0), vec2(1.0, 1.0));

    // no gamma or intensity here, it has been applied before
    // (this is just for postprocessing)
    vec4 res = texture(tex, uv);
    // apply the v_blend, usually blended as a colored quad with:
    // glBlendEquation(GL_FUNC_ADD); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    res.rgb = v_blend.a * v_blend.rgb + (1.0 - v_blend.a)*res.rgb;
    outColor =  res;
}