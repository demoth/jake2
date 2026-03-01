attribute vec3 a_position;
attribute vec2 a_texCoord0;
attribute vec2 a_texCoord1;

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform vec4 u_diffuseUVTransform;

varying vec2 v_diffuseUv;
varying vec2 v_lightmapUv;
varying vec3 v_worldPos;

void main() {
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    v_diffuseUv = a_texCoord0 * u_diffuseUVTransform.zw + u_diffuseUVTransform.xy;
    v_lightmapUv = a_texCoord1;
    v_worldPos = worldPos.xyz;
    gl_Position = u_projViewTrans * worldPos;
}
