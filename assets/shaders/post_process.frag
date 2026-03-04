#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_uv;

uniform sampler2D u_sceneTexture;
uniform vec4 u_blendColor;
uniform float u_vignetteEnabled;

void main() {
    vec4 scene = texture2D(u_sceneTexture, v_uv);

    // Cake-style blend: preserve center readability and bias tint towards screen edges.
    float distanceToCenter = distance(v_uv, vec2(0.5, 0.5));
    float vignetteMask = smoothstep(0.20, 0.78, distanceToCenter);
    float vignetteWeight = u_blendColor.a * mix(0.35, 1.0, vignetteMask);
    float enabled = step(0.5, u_vignetteEnabled);
    float blendWeight = clamp(vignetteWeight * enabled, 0.0, 1.0);

    scene.rgb = mix(scene.rgb, u_blendColor.rgb, blendWeight);
    gl_FragColor = vec4(scene.rgb, 1.0);
}
