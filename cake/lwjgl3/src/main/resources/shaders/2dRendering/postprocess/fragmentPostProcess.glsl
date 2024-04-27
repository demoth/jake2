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

uniform sampler2D tex;
uniform vec4 v_blend;

out vec4 outColor;

void main()
{
    // no gamma or intensity here, it has been applied before
    // (this is just for postprocessing)
    vec4 res = texture(tex, passTexCoord);
    // apply the v_blend, usually blended as a colored quad with:
    // glBlendEquation(GL_FUNC_ADD); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    res.rgb = v_blend.a * v_blend.rgb + (1.0 - v_blend.a)*res.rgb;
    outColor =  res;
}