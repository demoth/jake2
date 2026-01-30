in float a_vat_index;
in vec2 a_texCoord1; // Diffuse Texture coordinates

uniform mat4 u_worldTrans; // World transformation matrix
uniform mat4 u_projViewTrans; // View transformation matrix

uniform sampler2D u_vertexAnimationTexture; // Texture containing animated vertex positions (float texture)
uniform int u_textureHeight; // Height of the vertex texture (number of animation frames)
uniform int u_textureWidth; // Width of the vertex texture (number of vertices)
uniform int u_frame1; // Index of the first frame in the animation texture
uniform int u_frame2; // Index of the second frame in the animation texture
uniform float u_interpolation; // Interpolation factor between two animation frames (0.0 to 1.0)

out vec2 v_diffuseUV;

void main() {
    vec2 texelSize = vec2(1.0 / u_textureWidth, 1.0 / u_textureHeight);
    vec2 vertexTextureCoord1 = vec2((a_vat_index + 0.5) * texelSize.x, (u_frame1 + 0.5) * texelSize.y);
    vec2 vertexTextureCoord2 = vec2((a_vat_index + 0.5) * texelSize.x, (u_frame2 + 0.5) * texelSize.y);

    // Sample the vertex texture to get the animated positions for the two frames
    // The texture stores vec3 positions in RGB channels (assuming float texture)
    vec3 animatedPosition1 = texture(u_vertexAnimationTexture, vertexTextureCoord1).rgb;
    vec3 animatedPosition2 = texture(u_vertexAnimationTexture, vertexTextureCoord2).rgb;

    // Interpolate between the two animated positions
    vec3 finalPosition = mix(animatedPosition1, animatedPosition2, u_interpolation);

    // Apply the final interpolated position
    gl_Position = u_projViewTrans * u_worldTrans * vec4(finalPosition, 1.0);
    v_diffuseUV = a_texCoord1;
}
