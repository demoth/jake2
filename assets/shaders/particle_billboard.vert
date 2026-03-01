attribute vec3 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;

uniform mat4 u_projViewTrans;

varying vec4 v_color;
varying vec2 v_uv;

void main() {
    v_color = a_color;
    v_uv = a_texCoord0;
    gl_Position = u_projViewTrans * vec4(a_position, 1.0);
}
