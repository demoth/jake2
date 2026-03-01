varying vec4 v_color;

uniform float u_gammaExponent;

void main() {
    vec2 uv = gl_PointCoord * 2.0 - 1.0;
    float distSquared = dot(uv, uv);
    if (distSquared > 1.0) {
        discard;
    }

    vec3 corrected = pow(max(v_color.rgb, vec3(0.0)), vec3(u_gammaExponent));
    gl_FragColor = vec4(corrected, v_color.a);
}
