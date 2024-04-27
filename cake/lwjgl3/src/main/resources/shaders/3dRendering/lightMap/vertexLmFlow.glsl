// it gets attributes and uniforms from vertexCommon3D

out vec2 passLMcoord;
out vec3 passWorldCoord;
out vec3 passNormal;
flat out uint passLightFlags;

void main()
{
    passTexCoord = texCoord + vec2(scroll, 0.0);
    passLMcoord = lmTexCoord;
    vec4 worldCoord = transModel * vec4(position, 1.0);
    passWorldCoord = worldCoord.xyz;
    vec4 worldNormal = transModel * vec4(normal, 0.0f);
    passNormal = normalize(worldNormal.xyz);
    passLightFlags = lightFlags;

    gl_Position = transProjView * worldCoord;
}