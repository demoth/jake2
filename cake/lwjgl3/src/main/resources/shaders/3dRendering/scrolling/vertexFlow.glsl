// it gets attributes and uniforms from vertexCommon3D

void main()
{
    passTexCoord = texCoord + vec2(scroll, 0.0);
    gl_Position = transProjView * transModel * vec4(position, 1.0);
}