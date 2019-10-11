package jake2.client.render.opengl;


public interface QGLConst {

    /*
     * alpha functions
     */
    public static final int GL_NEVER = 0x0200;

    public static final int GL_LESS = 0x0201;

    public static final int GL_EQUAL = 0x0202;

    public static final int GL_LEQUAL = 0x0203;

    public static final int GL_GREATER = 0x0204;

    public static final int GL_NOTEQUAL = 0x0205;

    public static final int GL_GEQUAL = 0x0206;

    public static final int GL_ALWAYS = 0x0207;

    /*
     * attribute masks
     */
    public static final int GL_DEPTH_BUFFER_BIT = 0x00000100;

    public static final int GL_STENCIL_BUFFER_BIT = 0x00000400;

    public static final int GL_COLOR_BUFFER_BIT = 0x00004000;

    /*
     * begin modes
     */
    public static final int GL_POINTS = 0x0000;

    public static final int GL_LINES = 0x0001;

    public static final int GL_LINE_LOOP = 0x0002;

    public static final int GL_LINE_STRIP = 0x0003;

    public static final int GL_TRIANGLES = 0x0004;

    public static final int GL_TRIANGLE_STRIP = 0x0005;

    public static final int GL_TRIANGLE_FAN = 0x0006;

    public static final int GL_QUADS = 0x0007;

    public static final int GL_QUAD_STRIP = 0x0008;

    public static final int GL_POLYGON = 0x0009;

    /*
     * blending factors
     */
    public static final int GL_ZERO = 0;

    public static final int GL_ONE = 1;

    public static final int GL_SRC_COLOR = 0x0300;

    public static final int GL_ONE_MINUS_SRC_COLOR = 0x0301;

    public static final int GL_SRC_ALPHA = 0x0302;

    public static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;

    public static final int GL_DST_ALPHA = 0x0304;

    public static final int GL_ONE_MINUS_DST_ALPHA = 0x0305;

    /*
     * boolean
     */
    public static final int GL_TRUE = 1;

    public static final int GL_FALSE = 0;

    /*
     * data types
     */
    public static final int GL_BYTE = 0x1400;

    public static final int GL_UNSIGNED_BYTE = 0x1401;

    public static final int GL_SHORT = 0x1402;

    public static final int GL_UNSIGNED_SHORT = 0x1403;

    public static final int GL_INT = 0x1404;

    public static final int GL_UNSIGNED_INT = 0x1405;

    public static final int GL_FLOAT = 0x1406;

    /*
     * draw buffer modes
     */
    public static final int GL_FRONT = 0x0404;

    public static final int GL_BACK = 0x0405;

    public static final int GL_FRONT_AND_BACK = 0x0408;

    /*
     * errors
     */
    public static final int GL_NO_ERROR = 0;

    public static final int GL_POINT_SMOOTH = 0x0B10;

    public static final int GL_CULL_FACE = 0x0B44;

    public static final int GL_DEPTH_TEST = 0x0B71;

    public static final int GL_MODELVIEW_MATRIX = 0x0BA6;

    public static final int GL_ALPHA_TEST = 0x0BC0;

    public static final int GL_BLEND = 0x0BE2;

    public static final int GL_SCISSOR_TEST = 0x0C11;

    public static final int GL_PACK_ALIGNMENT = 0x0D05;

    public static final int GL_TEXTURE_2D = 0x0DE1;

    /*
     * hints
     */
    public static final int GL_PERSPECTIVE_CORRECTION_HINT = 0x0C50;

    public static final int GL_DONT_CARE = 0x1100;

    public static final int GL_FASTEST = 0x1101;

    public static final int GL_NICEST = 0x1102;

    /*
     * matrix modes
     */
    public static final int GL_MODELVIEW = 0x1700;

    public static final int GL_PROJECTION = 0x1701;

    /*
     * pixel formats
     */
    public static final int GL_COLOR_INDEX = 0x1900;

    public static final int GL_RED = 0x1903;

    public static final int GL_GREEN = 0x1904;

    public static final int GL_BLUE = 0x1905;

    public static final int GL_ALPHA = 0x1906;

    public static final int GL_RGB = 0x1907;

    public static final int GL_RGBA = 0x1908;

    public static final int GL_LUMINANCE = 0x1909;

    public static final int GL_LUMINANCE_ALPHA = 0x190A;

    /*
     * polygon modes
     */

    public static final int GL_POINT = 0x1B00;

    public static final int GL_LINE = 0x1B01;

    public static final int GL_FILL = 0x1B02;

    /*
     * shading models
     */
    public static final int GL_FLAT = 0x1D00;

    public static final int GL_SMOOTH = 0x1D01;

    public static final int GL_REPLACE = 0x1E01;

    /*
     * string names
     */
    public static final int GL_VENDOR = 0x1F00;

    public static final int GL_RENDERER = 0x1F01;

    public static final int GL_VERSION = 0x1F02;

    public static final int GL_EXTENSIONS = 0x1F03;

    /*
     * TextureEnvMode
     */
    public static final int GL_MODULATE = 0x2100;

    /*
     * TextureEnvParameter
     */
    public static final int GL_TEXTURE_ENV_MODE = 0x2200;

    public static final int GL_TEXTURE_ENV_COLOR = 0x2201;

    /*
     * TextureEnvTarget
     */
    public static final int GL_TEXTURE_ENV = 0x2300;

    public static final int GL_NEAREST = 0x2600;

    public static final int GL_LINEAR = 0x2601;

    public static final int GL_NEAREST_MIPMAP_NEAREST = 0x2700;

    public static final int GL_LINEAR_MIPMAP_NEAREST = 0x2701;

    public static final int GL_NEAREST_MIPMAP_LINEAR = 0x2702;

    public static final int GL_LINEAR_MIPMAP_LINEAR = 0x2703;

    /*
     * TextureParameterName
     */
    public static final int GL_TEXTURE_MAG_FILTER = 0x2800;

    public static final int GL_TEXTURE_MIN_FILTER = 0x2801;

    public static final int GL_TEXTURE_WRAP_S = 0x2802;

    public static final int GL_TEXTURE_WRAP_T = 0x2803;

    /*
     * TextureWrapMode
     */
    public static final int GL_CLAMP = 0x2900;

    public static final int GL_REPEAT = 0x2901;

    /*
     * texture
     */
    public static final int GL_LUMINANCE8 = 0x8040;

    public static final int GL_INTENSITY8 = 0x804B;

    public static final int GL_R3_G3_B2 = 0x2A10;

    public static final int GL_RGB4 = 0x804F;

    public static final int GL_RGB5 = 0x8050;

    public static final int GL_RGB8 = 0x8051;

    public static final int GL_RGBA2 = 0x8055;

    public static final int GL_RGBA4 = 0x8056;

    public static final int GL_RGB5_A1 = 0x8057;

    public static final int GL_RGBA8 = 0x8058;

    /*
     * vertex arrays
     */
    public static final int GL_VERTEX_ARRAY = 0x8074;

    public static final int GL_COLOR_ARRAY = 0x8076;

    public static final int GL_TEXTURE_COORD_ARRAY = 0x8078;

    public static final int GL_T2F_V3F = 0x2A27;

    /*
     * OpenGL 1.2, 1.3 constants
     */
    public static final int GL_SHARED_TEXTURE_PALETTE_EXT = 0x81FB;

    public static final int GL_TEXTURE0 = 0x84C0;

    public static final int GL_TEXTURE1 = 0x84C1;

    public static final int GL_TEXTURE0_ARB = 0x84C0;

    public static final int GL_TEXTURE1_ARB = 0x84C1;

    public static final int GL_BGR = 0x80E0;

    public static final int GL_BGRA = 0x80E1;

    /*
     * point parameters
     */
    public static final int GL_POINT_SIZE_MIN_EXT = 0x8126;

    public static final int GL_POINT_SIZE_MAX_EXT = 0x8127;

    public static final int GL_POINT_FADE_THRESHOLD_SIZE_EXT = 0x8128;

    public static final int GL_DISTANCE_ATTENUATION_EXT = 0x8129;

}