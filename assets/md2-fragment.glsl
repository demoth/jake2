#version 330 core

in int vert_id;
in vec2 a_texCoord;
uniform samplerBuffer texture_positions;
uniform int frame1;
uniform int frame2;
uniform float lerp_factor;
uniform int verticesPerFrame;

out vec2 uv;

void main() {
    int indexFrame1 = (frame1 * verticesPerFrame + vert_id) * 3;
    int indexFrame2 = (frame2 * verticesPerFrame + vert_id) * 3;

    vec3 pos1 = texelFetch(texture_positions, indexFrame1).xyz;
    vec3 pos2 = texelFetch(texture_positions, indexFrame2).xyz;

    vec3 interpolated = mix(pos1, pos2, lerp_factor);
    gl_Position = vec4(interpolated, 1.0);
    uv = a_texCoord;
}