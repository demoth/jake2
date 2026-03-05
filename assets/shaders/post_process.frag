#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_uv;

uniform sampler2D u_texture;
uniform vec4 u_blendColor;
uniform float u_vignetteEnabled;
uniform float u_vignetteStrength;
uniform float u_underwaterEnabled;
uniform float u_timeSeconds;

void main() {
    vec2 sceneUv = v_uv;
    if (u_underwaterEnabled > 0.5) {
        // Cake-style underwater postprocess:
        // combine directional wave offsets with a soft radial breathing distortion.
        float waveX = sin((sceneUv.y * 24.0) + u_timeSeconds * 2.2) * 0.0035;
        float waveY = cos((sceneUv.x * 21.0) - u_timeSeconds * 1.9) * 0.0030;
        vec2 fromCenter = sceneUv - vec2(0.5, 0.5);
        float radial = dot(fromCenter, fromCenter);
        vec2 radialWarp = fromCenter * (0.018 * sin(u_timeSeconds * 1.3 + radial * 18.0));
        sceneUv += vec2(waveX, waveY) + radialWarp;
        sceneUv = clamp(sceneUv, vec2(0.001, 0.001), vec2(0.999, 0.999));
    }

    vec4 scene = texture2D(u_texture, sceneUv);

    // Cake-style blend: preserve center readability and bias tint towards screen edges.
    float distanceToCenter = distance(v_uv, vec2(0.5, 0.5));
    float vignetteMask = smoothstep(0.20, 0.78, distanceToCenter);
    float vignetteWeight = u_blendColor.a * mix(0.35, 1.0, vignetteMask);
    float enabled = step(0.5, u_vignetteEnabled);
    float blendWeight = clamp(vignetteWeight * enabled * u_vignetteStrength, 0.0, 1.0);

    scene.rgb = mix(scene.rgb, u_blendColor.rgb, blendWeight);
    gl_FragColor = vec4(scene.rgb, 1.0);
}
