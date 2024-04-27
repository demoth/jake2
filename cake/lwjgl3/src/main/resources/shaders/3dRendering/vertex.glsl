// it gets attributes and uniforms from vertexCommon3D

void main()
{
    passTexCoord = texCoord;
    gl_Position = transProjView * transModel * vec4(position, 1.0);
}