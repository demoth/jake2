// it gets attributes and uniforms from vertexCommon3D

out vec4 passColor;

void main()
{
    passColor = vertColor*overbrightbits;
    passTexCoord = texCoord;
    gl_Position = transProjView* transModel * vec4(position, 1.0);
}