#version 130 // same as vertex

uniform sampler2D u_diffuseTexture;    // the new colour texture – unit 1

varying vec2 v_texCoord;       // real UVs from vertex shader

void main() {
    gl_FragColor = texture2D(u_diffuseTexture, v_texCoord); // doesn't work: I see a black screen
}