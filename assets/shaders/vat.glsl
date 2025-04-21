#version 100 // Specify OpenGL ES 2.0 or 3.0 compatibility

attribute vec3 a_position; // Original vertex position (can be dummy or used for other purposes)
attribute vec2 a_texCoord0; // Texture coordinates (used here to determine vertex index for VAT)

uniform mat4 u_worldTrans; // World transformation matrix
uniform float u_animationTime; // Current animation time (e.g., in seconds)
uniform sampler2D u_vertexTexture; // Texture containing animated vertex positions (float texture)
//uniform float u_textureWidth; // Width of the vertex texture (number of vertices)
uniform float u_textureHeight; // Height of the vertex texture (number of animation frames)
uniform float u_animationDuration; // Total duration of the animation

void main() {
    // Calculate the current animation progress (0.0 to 1.0)
    float animationProgress = mod(u_animationTime, u_animationDuration) / u_animationDuration;

    // Calculate the current frame index (0 to u_textureHeight - 1)
    float currentFrame = animationProgress * (u_textureHeight - 1.0);

    // Get the integer part for the current frame index
    float frameIndex1 = floor(currentFrame);
    // Get the next frame index (looping back to 0 if necessary)
    float frameIndex2 = mod(frameIndex1 + 1.0, u_textureHeight);

    // Calculate the interpolation factor between the two frames
    float interpolationFactor = fract(currentFrame);

    // Calculate the texture coordinate to sample the vertex texture
    // The x-coordinate corresponds to the vertex index. We use a_texCoord0.x
    // and map it to the texture width.
    // The y-coordinate corresponds to the animation frame index.
    // We sample two frames for interpolation.
    vec2 vertexTextureCoord1 = vec2(a_texCoord0.x, frameIndex1 / u_textureHeight);
    vec2 vertexTextureCoord2 = vec2(a_texCoord0.x, frameIndex2 / u_textureHeight);

    // Sample the vertex texture to get the animated positions for the two frames
    // The texture stores vec3 positions in RGB channels (assuming float texture)
    vec3 animatedPosition1 = texture2D(u_vertexTexture, vertexTextureCoord1).rgb;
    vec3 animatedPosition2 = texture2D(u_vertexTexture, vertexTextureCoord2).rgb;

    // Interpolate between the two animated positions
    vec3 finalPosition = mix(animatedPosition1, animatedPosition2, interpolationFactor);

    // Apply the final interpolated position
    gl_Position = u_worldTrans * vec4(finalPosition, 1.0);
}
