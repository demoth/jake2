attribute vec3 a_position;
attribute vec4 a_color;
attribute float a_size;

uniform mat4 u_projViewTrans;
uniform vec3 u_cameraPos;
uniform float u_pointProjectionScale;

varying vec4 v_color;

void main() {
    v_color = a_color;
    gl_Position = u_projViewTrans * vec4(a_position, 1.0);
    float pointDist = max(length(a_position - u_cameraPos), 1.0);
    gl_PointSize = max(1.0, (a_size * u_pointProjectionScale) / pointDist);
}
