#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_diffuseTexture;
uniform sampler2D u_lightmapTexture0;
uniform sampler2D u_lightmapTexture1;
uniform sampler2D u_lightmapTexture2;
uniform sampler2D u_lightmapTexture3;
uniform vec4 u_lightStyleWeights;
uniform float u_opacity;
uniform float u_gammaExponent;
uniform float u_intensity;
uniform float u_overbrightbits;
uniform int u_dynamicLightCount;
uniform vec4 u_dynamicLightPosRadius[8];
uniform vec4 u_dynamicLightColor[8];

varying vec2 v_diffuseUv;
varying vec2 v_lightmapUv;
varying vec3 v_worldPos;

vec3 accumulateDynamicLights(vec3 worldPos) {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 8; ++i) {
        if (i >= u_dynamicLightCount) {
            break;
        }
        vec3 lightPos = u_dynamicLightPosRadius[i].xyz;
        float radius = max(u_dynamicLightPosRadius[i].w, 0.001);
        float distanceToLight = distance(lightPos, worldPos);
        if (distanceToLight >= radius) {
            continue;
        }
        float attenuation = 1.0 - (distanceToLight / radius);
        sum += u_dynamicLightColor[i].rgb * attenuation;
    }
    return sum;
}

void main() {
    vec4 albedo = texture2D(u_diffuseTexture, v_diffuseUv);
    vec3 light0 = texture2D(u_lightmapTexture0, v_lightmapUv).rgb * u_lightStyleWeights.x;
    vec3 light1 = texture2D(u_lightmapTexture1, v_lightmapUv).rgb * u_lightStyleWeights.y;
    vec3 light2 = texture2D(u_lightmapTexture2, v_lightmapUv).rgb * u_lightStyleWeights.z;
    vec3 light3 = texture2D(u_lightmapTexture3, v_lightmapUv).rgb * u_lightStyleWeights.w;
    vec3 light = (light0 + light1 + light2 + light3) * u_overbrightbits;
    light += accumulateDynamicLights(v_worldPos);
    // Safety guard: if sampled lightmap is effectively black (invalid upload/UV path),
    // fall back to unlit albedo to avoid fully disappearing world geometry.
    if (max(light.r, max(light.g, light.b)) < 0.001) {
        light = vec3(1.0);
    }
    vec3 lit = albedo.rgb * light;
    lit *= u_intensity;
    lit = pow(max(lit, vec3(0.0)), vec3(u_gammaExponent));
    gl_FragColor = vec4(lit, albedo.a * u_opacity);
}
