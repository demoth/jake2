// it gets attributes and uniforms from vertexCommon3D

out vec4 passColor;

void main()
{
    passColor = vertColor;
    gl_Position = transProjView * transModel * vec4(position, 1.0);

    // abusing texCoord for pointSize, pointDist for particles
    float pointDist = texCoord.y*0.1; // with factor 0.1 it looks good.

    gl_PointSize = texCoord.x/pointDist;
}