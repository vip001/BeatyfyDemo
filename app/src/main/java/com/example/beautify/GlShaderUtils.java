package com.example.beautify;

import android.content.Context;
import android.opengl.GLES20;

final class GlShaderUtils {
    private static final String TAG = "GlShaderUtils";

    private GlShaderUtils() {}

    static String getGLAsset(Context context, String assetPath) {
        try {
            return AssetBlob.utf8(context, assetPath);
        } catch (RuntimeException e) {
            BeautifyLog.e(TAG, "read asset " + assetPath, e);
            return "";
        }
    }

    private static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] != GLES20.GL_TRUE) {
                BeautifyLog.e(TAG, "load shader error type=" + shaderType + ": "
                        + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (vertexShader != 0 && fragmentShader != 0) {
            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
            int[] status = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
            if (status[0] != GLES20.GL_TRUE) {
                BeautifyLog.e(TAG, "create program error: " + GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            return program;
        }
        return 0;
    }
}
