in vec2 position; // GL3_ATTRIB_POSITION
in vec2 texCoord; // GL3_ATTRIB_TEXCOORD

// for UBO shared between 2D shaders
layout (std140) uniform uni2D
{
    mat4 trans;
};

out vec2 passTexCoord;

void main()
{
    gl_Position = trans * vec4(position, 0.0, 1.0);
    passTexCoord = texCoord;
}