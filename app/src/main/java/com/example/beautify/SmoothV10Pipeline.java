package com.example.beautify;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** Still-image SmoothV10 chain only (no live/cover path). */
public class SmoothV10Pipeline {
    private static final String TAG = "SmoothV10Pipeline";
    private static final String GL = "ss_gl/";

    private static final int MESH_VERTS = Face145Topology.VERTEX_COUNT;
    private static final int MESH_INDICES = Face145Topology.INDEX_COUNT;

    private static final float JOINT_VF_BLUR3 = 3.140625f;
    private static final float JOINT_VF_BLUR4 = 2.890625f;

    private static final int PROCESS_W = 720;
    private static final int PROCESS_H = 1280;
    private static final int BLUR_RT_W = 324;
    private static final int BLUR_RT_H = 576;
    private static final int SKIN_RT_W = 72;
    private static final int SKIN_RT_H = 120;

    private final Context mContext;
    private final FloatBuffer mVertexBuffer;
    private final FloatBuffer mUvBuffer;
    private final FloatBuffer mFlipUvBuffer;
    private final FloatBuffer mComposeUvBuf;
    private final FloatBuffer mCoverOutUvBuf;
    private final float[] mComposeUvs = new float[8];
    private final float[] mCoverOutUvs = {0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f};
    private final float[] mVerts = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};
    private final float[] mUvs = {0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f};
    private final float[] mFlipUvs = {0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f};
    private final float[] mMvpMatrix = new float[16];
    private final float[] mStMatrix = new float[16];
    private final float[] mLmEffect = new float[212];
    private final float[] mFitContentUv = new float[4];
    private final float[] mMeshXy = new float[MESH_VERTS * 2];
    private final float[] mMeshNdc = new float[MESH_VERTS * 2];
    private final FloatBuffer mMeshPosBuf;
    private final FloatBuffer mMeshUvBuf;

    private int mVboId;
    private int mMeshMaskProgram;
    private int mGblurProgram;
    private int mBlendProgram;
    private int mJointProgram;
    private int mJoint2Program;
    private int mSmoothProgram;
    private int mSkinEstProgram;
    private int mHblurProgram;
    private int mMaskRefineProgram;
    private int mMaskRefineFinalProgram;
    private int mBlitProgram;

    private int mBlendLutTex;
    private int mFaceMaskTex;
    private int mSkinMaskStubTex;
    private int mSkinLibTex;
    private int mMeshVbo;
    private int mMeshIbo;
    private volatile float[] mLandmarks106Uv;
    private boolean mMeshReady;

    private int mMaskFbo, mMaskTex;
    private int mBlur1Fbo, mBlur1Tex;
    private int mBlur2Fbo, mBlur2Tex;
    private int mBlendFbo, mBlendTex;
    private int mTmpFbo, mTmpTex;
    private int mSkinFbo, mSkinTex;
    private int mSkinHBlurFbo, mSkinHBlurTex;
    private int mSkinBlur2Fbo, mSkinBlur2Tex;
    private int mUprightFbo, mUprightTex;
    private int mMaskRefineFbo, mMaskRefineTex;
    private int mLastFinalMaskFbo, mLastFinalMaskTex;

    private boolean mInputNeedsYFlip = true;
    private int mWidth;
    private int mHeight;
    private int mBlurRtW = BLUR_RT_W;
    private int mBlurRtH = BLUR_RT_H;
    private int mSkinRtW = SKIN_RT_W;
    private int mSkinRtH = SKIN_RT_H;
    private boolean mReady;

    public SmoothV10Pipeline(Context context) {
        mContext = context.getApplicationContext();
        mVertexBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder())
                .asFloatBuffer().put(mVerts);
        mVertexBuffer.position(0);
        mUvBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder())
                .asFloatBuffer().put(mUvs);
        mUvBuffer.position(0);
        mFlipUvBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder())
                .asFloatBuffer().put(mFlipUvs);
        mFlipUvBuffer.position(0);
        mComposeUvBuf = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mCoverOutUvBuf = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mCoverOutUvBuf.put(mCoverOutUvs).position(0);
        mMeshPosBuf = ByteBuffer.allocateDirect(MESH_VERTS * 8).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mMeshUvBuf = ByteBuffer.allocateDirect(MESH_VERTS * 8).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mMeshUvBuf.put(Face145Topology.TEMPLATE_UV).position(0);
    }

    public void setLandmarks106Uv(float[] xyUv) {
        if (xyUv == null || xyUv.length < Face145Topology.LANDMARK_COUNT * 2) {
            mLandmarks106Uv = null;
            return;
        }
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        int finite = 0;
        for (int i = 0; i < Face145Topology.LANDMARK_COUNT; i++) {
            float x = xyUv[i * 2];
            float y = xyUv[i * 2 + 1];
            if (Float.isNaN(x) || Float.isNaN(y)) continue;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            finite++;
        }
        if (finite < 3 || maxX - minX < 1e-3f || maxY - minY < 1e-3f) {
            mLandmarks106Uv = null;
            return;
        }
        mLandmarks106Uv = Face106IndexMap.hyperLandmarkToTtFace(xyUv);
    }

    public void setInputNeedsYFlip(boolean needsYFlip) {
        mInputNeedsYFlip = needsYFlip;
    }

    private String gl(String file) {
        return GlShaderUtils.getGLAsset(mContext, GL + file);
    }

    public boolean init() {
        releaseGl();
        String vs = gl("ss_passthrough_vs.glsl");
        mMeshMaskProgram = GlShaderUtils.createProgram(
                gl("ss_mesh_mask_mvp_vs.glsl"), gl("ss_mesh_mask.frag"));
        mBlitProgram = GlShaderUtils.createProgram(vs, gl("ss_passthrough.frag"));
        mBlendProgram = GlShaderUtils.createProgram(vs, gl("ss_blend.frag"));
        String jointVs = gl("ss_joint_vs.glsl");
        mJointProgram = GlShaderUtils.createProgram(jointVs, gl("ss_joint.frag"));
        mJoint2Program = GlShaderUtils.createProgram(jointVs, gl("ss_joint2.frag"));
        mSmoothProgram = GlShaderUtils.createProgram(
                gl("ss_smooth_ba_vs.glsl"), gl("ss_smooth_ba.frag"));
        Matrix.setIdentityM(mStMatrix, 0);
        mSkinEstProgram = GlShaderUtils.createProgram(vs, gl("ss_skin_est.frag"));
        mHblurProgram = GlShaderUtils.createProgram(vs, gl("ss_hblur.frag"));
        mMaskRefineProgram = GlShaderUtils.createProgram(vs, gl("ss_mask_r.frag"));
        mMaskRefineFinalProgram = GlShaderUtils.createProgram(vs, gl("ss_mask_rf.frag"));
        mGblurProgram = GlShaderUtils.createProgram(
                gl("ss_gblur_vs.glsl"), gl("ss_gblur.frag"));
        if (mMeshMaskProgram == 0 || mGblurProgram == 0
                || mBlendProgram == 0 || mSmoothProgram == 0 || mBlitProgram == 0
                || mJointProgram == 0 || mJoint2Program == 0
                || mSkinEstProgram == 0 || mHblurProgram == 0 || mMaskRefineProgram == 0
                || mMaskRefineFinalProgram == 0) {
            BeautifyLog.e(TAG, "program compile failed");
            mReady = false;
            return false;
        }
        int[] vbo = new int[1];
        GLES20.glGenBuffers(1, vbo, 0);
        mVboId = vbo[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 96, null, GLES20.GL_STATIC_DRAW);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, 32, mVertexBuffer);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 32, 32, mUvBuffer);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 64, 32, mFlipUvBuffer);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        initFace145MeshBuffers();
        mBlendLutTex = loadAssetTexture("beauty/ss_blend_lut.png");
        mFaceMaskTex = loadAssetTexture("beauty/ss_mesh_mask.png");
        mSkinMaskStubTex = createSolidR8Tex(0);
        mSkinLibTex = loadAssetTexture("beauty/ss_skin.png");
        mReady = mBlendLutTex != 0 && mSkinLibTex != 0;
        if (!mReady) {
            BeautifyLog.e(TAG, "ss_blend_lut/ss_skin assets missing");
        }
        if (mFaceMaskTex == 0) {
            mMeshReady = false;
        }
        return mReady;
    }

    private void initFace145MeshBuffers() {
        ShortBuffer ibo = ByteBuffer.allocateDirect(MESH_INDICES * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        ibo.put(Face145Topology.INDICES).position(0);
        int[] ids = new int[2];
        GLES20.glGenBuffers(2, ids, 0);
        mMeshVbo = ids[0];
        mMeshIbo = ids[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mMeshVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, MESH_VERTS * 16, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, MESH_VERTS * 8, MESH_VERTS * 8, mMeshUvBuf);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mMeshIbo);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, MESH_INDICES * 2, ibo, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        mMeshReady = mMeshVbo != 0 && mMeshIbo != 0;
    }

    public boolean isReady() {
        return mReady;
    }

    private void ensureSize(int width, int height) {
        if (width < 1 || height < 1) return;
        if (width == mWidth && height == mHeight
                && mMaskFbo != 0 && mBlur1Fbo != 0 && mBlur2Fbo != 0
                && mBlendFbo != 0 && mTmpFbo != 0
                && mSkinFbo != 0 && mSkinHBlurFbo != 0 && mSkinBlur2Fbo != 0
                && mUprightFbo != 0 && mMaskRefineFbo != 0 && mLastFinalMaskFbo != 0) {
            return;
        }
        deleteTargets();
        mWidth = width;
        mHeight = height;
        mBlurRtW = Math.max(2, Math.round(width * (BLUR_RT_W / (float) PROCESS_W)));
        mBlurRtH = Math.max(2, Math.round(height * (BLUR_RT_H / (float) PROCESS_H)));
        mSkinRtW = Math.max(2, Math.round(width * (SKIN_RT_W / (float) PROCESS_W)));
        mSkinRtH = Math.max(2, Math.round(height * (SKIN_RT_H / (float) PROCESS_H)));

        mMaskTex = createTex(mBlurRtW, mBlurRtH);
        mMaskFbo = createFbo(mMaskTex);
        if (mMaskFbo == 0) mMaskTex = 0;
        mBlur1Tex = createTex(mBlurRtW, mBlurRtH);
        mBlur1Fbo = createFbo(mBlur1Tex);
        if (mBlur1Fbo == 0) mBlur1Tex = 0;
        mBlur2Tex = createTex(mBlurRtW, mBlurRtH);
        mBlur2Fbo = createFbo(mBlur2Tex);
        if (mBlur2Fbo == 0) mBlur2Tex = 0;
        mBlendTex = createTex(width, height);
        mBlendFbo = createFbo(mBlendTex);
        if (mBlendFbo == 0) mBlendTex = 0;
        mTmpTex = createTex(width, height);
        mTmpFbo = createFbo(mTmpTex);
        if (mTmpFbo == 0) mTmpTex = 0;
        mUprightTex = createTex(width, height);
        mUprightFbo = createFbo(mUprightTex);
        if (mUprightFbo == 0) mUprightTex = 0;
        mSkinTex = createTex(mSkinRtW, mSkinRtH);
        mSkinFbo = createFbo(mSkinTex);
        if (mSkinFbo == 0) mSkinTex = 0;
        mSkinHBlurTex = createTex(mSkinRtW, mSkinRtH);
        mSkinHBlurFbo = createFbo(mSkinHBlurTex);
        if (mSkinHBlurFbo == 0) mSkinHBlurTex = 0;
        mSkinBlur2Tex = createTex(mSkinRtW, mSkinRtH);
        mSkinBlur2Fbo = createFbo(mSkinBlur2Tex);
        if (mSkinBlur2Fbo == 0) mSkinBlur2Tex = 0;
        mMaskRefineTex = createTex(mSkinRtW, mSkinRtH);
        mMaskRefineFbo = createFbo(mMaskRefineTex);
        if (mMaskRefineFbo == 0) mMaskRefineTex = 0;
        mLastFinalMaskTex = createTex(mSkinRtW, mSkinRtH);
        mLastFinalMaskFbo = createFbo(mLastFinalMaskTex);
        if (mLastFinalMaskFbo == 0) mLastFinalMaskTex = 0;

        if (mLastFinalMaskFbo != 0) {
            bind(mLastFinalMaskFbo, mSkinRtW, mSkinRtH);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }

        int ok = 0;
        if (mMaskFbo != 0) ok++;
        if (mBlur1Fbo != 0) ok++;
        if (mBlur2Fbo != 0) ok++;
        if (mBlendFbo != 0) ok++;
        if (mTmpFbo != 0) ok++;
        if (mSkinFbo != 0) ok++;
        if (mSkinHBlurFbo != 0) ok++;
        if (mSkinBlur2Fbo != 0) ok++;
        if (mUprightFbo != 0) ok++;
        if (mMaskRefineFbo != 0) ok++;
        if (mLastFinalMaskFbo != 0) ok++;
        if (ok < 11) {
            BeautifyLog.e(TAG, "ensureSize FBO incomplete ok=" + ok + "/11");
            deleteTargets();
        }
    }

    public void process(int inputTexture, int width, int height, boolean hasFace, float smooth) {
        if (!mReady || inputTexture == 0 || width < 1 || height < 1) return;

        int[] prevFbo = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0);
        boolean beautyPx = smooth > 0f;
        int ew = beautyPx ? PROCESS_W : width;
        int eh = beautyPx ? PROCESS_H : height;
        ensureSize(ew, eh);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0]);
        if (mUprightFbo == 0 || mUprightTex == 0) {
            blit(inputTexture, -1, width, height, mInputNeedsYFlip);
            unbindTexUnits();
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0]);
            return;
        }

        float tw = 1f / ew;
        float th = 1f / eh;
        float gTw = 1f / mBlurRtW;
        float gTh = 1f / mBlurRtH;

        blitContain(inputTexture, mUprightFbo, width, height, ew, eh, mInputNeedsYFlip, mFitContentUv);
        int src = mUprightTex;

        if (!hasFace || !beautyPx) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0]);
            blitSampleRect(src, width, height, mFitContentUv);
            unbindTexUnits();
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0]);
            return;
        }

        float[] lmScreen = mLandmarks106Uv;
        float[] lm = lmScreen;
        if (lmScreen != null) {
            remapUvIntoRect(lmScreen, mLmEffect, mFitContentUv);
            lm = mLmEffect;
        }

        boolean prevBlend = GLES20.glIsEnabled(GLES20.GL_BLEND);
        boolean prevDepth = GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST);
        boolean prevCull = GLES20.glIsEnabled(GLES20.GL_CULL_FACE);
        boolean prevScissor = GLES20.glIsEnabled(GLES20.GL_SCISSOR_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glColorMask(true, true, true, true);

        bind(mMaskFbo, mBlurRtW, mBlurRtH);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (lm != null) {
            drawFace145Mask(lm, ew, eh);
        }

        int skinMaskPass7 = buildSkinMask(src);
        if (skinMaskPass7 == 0) {
            skinMaskPass7 = mSkinTex != 0 ? mSkinTex : mSkinMaskStubTex;
        }

        float smoothMat = smooth * 0.85f;
        bind(mBlur1Fbo, mBlurRtW, mBlurRtH);
        drawGblur(src, mMaskTex, gTw, gTh);
        bind(mBlur2Fbo, mBlurRtW, mBlurRtH);
        drawGblur(mBlur1Tex, mMaskTex, gTw, gTh);

        bind(mBlendFbo, ew, eh);
        GLES20.glUseProgram(mBlendProgram);
        bindQuad(mBlendProgram, false);
        bindTex(GLES20.GL_TEXTURE0, src, loc(mBlendProgram, "_MainTex"), 0);
        bindTex(GLES20.GL_TEXTURE1, mMaskTex, loc(mBlendProgram, "faceSkinMaskTexture"), 1);
        bindTex(GLES20.GL_TEXTURE2, mBlur2Tex, loc(mBlendProgram, "blurImageTexture"), 2);
        bindTex(GLES20.GL_TEXTURE3, mBlendLutTex, loc(mBlendProgram, "lutImageTexture"), 3);
        draw();

        bind(mBlur1Fbo, mBlurRtW, mBlurRtH);
        drawJoint(mJointProgram, mBlendTex, skinMaskPass7, JOINT_VF_BLUR3, true);
        bind(mBlur2Fbo, mBlurRtW, mBlurRtH);
        drawJoint(mJoint2Program, mBlur1Tex, 0, JOINT_VF_BLUR4, false);
        drawPass7(src, mBlendTex, mBlur1Tex, mBlur2Tex, mMaskTex, tw, th, smoothMat, ew, eh);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0]);
        blitSampleRect(mTmpTex, width, height, mFitContentUv);
        unbindTexUnits();
        if (prevCull) GLES20.glEnable(GLES20.GL_CULL_FACE);
        if (prevDepth) GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        if (prevBlend) GLES20.glEnable(GLES20.GL_BLEND);
        if (prevScissor) GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0]);
    }

    private void drawPass7(
            int ori, int pre, int blur1, int blur2, int faceMask,
            float tw, float th, float smooth, int ew, int eh
    ) {
        bind(mTmpFbo, ew, eh);
        int prog = mSmoothProgram;
        GLES20.glUseProgram(prog);
        bindQuad(prog, false);
        bindTex(GLES20.GL_TEXTURE0, ori, loc(prog, "_MainTex"), 0);
        bindTex(GLES20.GL_TEXTURE1, pre, loc(prog, "preImg"), 1);
        bindTex(GLES20.GL_TEXTURE2, blur1, loc(prog, "blur1"), 2);
        bindTex(GLES20.GL_TEXTURE3, blur2, loc(prog, "blur2"), 3);
        bindTex(GLES20.GL_TEXTURE4, faceMask, loc(prog, "face_mask"), 4);
        GLES20.glUniform1f(loc(prog, "smoothIntensity"), smooth);
        draw();
    }

    private int buildSkinMask(int srcTex) {
        float ratio = BeautyDefaults.MASKREFINE_RATIO_STEADY;
        if (mMaskRefineFbo != 0 && mMaskRefineProgram != 0 && mMaskTex != 0) {
            bind(mMaskRefineFbo, mSkinRtW, mSkinRtH);
            GLES20.glUseProgram(mMaskRefineProgram);
            bindQuad(mMaskRefineProgram, false);
            bindTex(GLES20.GL_TEXTURE0, mMaskTex, loc(mMaskRefineProgram, "_MainTex"), 0);
            int before = mLastFinalMaskTex != 0 ? mLastFinalMaskTex : mSkinMaskStubTex;
            bindTex(GLES20.GL_TEXTURE1, before, loc(mMaskRefineProgram, "maskbefore"), 1);
            GLES20.glUniform1f(loc(mMaskRefineProgram, "ratio"), ratio);
            draw();
        }
        if (mSkinFbo != 0 && mSkinEstProgram != 0 && srcTex != 0) {
            bind(mSkinFbo, mSkinRtW, mSkinRtH);
            GLES20.glUseProgram(mSkinEstProgram);
            bindQuad(mSkinEstProgram, false);
            bindTex(GLES20.GL_TEXTURE0, srcTex, loc(mSkinEstProgram, "_MainTex"), 0);
            bindTex(GLES20.GL_TEXTURE1, mSkinLibTex, loc(mSkinEstProgram, "skinhist"), 1);
            draw();
        }
        if (mSkinHBlurFbo != 0 && mHblurProgram != 0 && mSkinTex != 0) {
            bind(mSkinHBlurFbo, mSkinRtW, mSkinRtH);
            GLES20.glUseProgram(mHblurProgram);
            bindQuad(mHblurProgram, false);
            bindTex(GLES20.GL_TEXTURE0, mSkinTex, loc(mHblurProgram, "_MainTex"), 0);
            GLES20.glUniform2f(loc(mHblurProgram, "jumpstep"), 1f / mSkinRtW, 0f);
            draw();
        }
        if (mSkinBlur2Fbo != 0 && mMaskRefineFinalProgram != 0
                && mSkinHBlurTex != 0 && mMaskRefineTex != 0) {
            bind(mSkinBlur2Fbo, mSkinRtW, mSkinRtH);
            GLES20.glUseProgram(mMaskRefineFinalProgram);
            bindQuad(mMaskRefineFinalProgram, false);
            bindTex(GLES20.GL_TEXTURE0, mSkinHBlurTex, loc(mMaskRefineFinalProgram, "binary"), 0);
            bindTex(GLES20.GL_TEXTURE1, mMaskRefineTex, loc(mMaskRefineFinalProgram, "maskfinal"), 1);
            GLES20.glUniform2f(loc(mMaskRefineFinalProgram, "jumpstep"), 0f, 1f / mSkinRtH);
            draw();
        }
        if (mLastFinalMaskFbo != 0 && mMaskRefineTex != 0) {
            blit(mMaskRefineTex, mLastFinalMaskFbo, mSkinRtW, mSkinRtH, false);
        }
        if (mSkinBlur2Tex != 0) return mSkinBlur2Tex;
        if (mSkinTex != 0) return mSkinTex;
        return mSkinMaskStubTex;
    }

    public void release() {
        releaseGl();
    }

    private void drawJoint(int program, int src, int mask, float valueFactor, boolean withMask) {
        GLES20.glUseProgram(program);
        int vp = GLES20.glGetAttribLocation(program, "v_Position");
        int fp = GLES20.glGetAttribLocation(program, "f_Position");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboId);
        if (vp >= 0) {
            GLES20.glEnableVertexAttribArray(vp);
            GLES20.glVertexAttribPointer(vp, 2, GLES20.GL_FLOAT, false, 8, 0);
        }
        if (fp >= 0) {
            GLES20.glEnableVertexAttribArray(fp);
            GLES20.glVertexAttribPointer(fp, 2, GLES20.GL_FLOAT, false, 8, 32);
        }
        bindTex(GLES20.GL_TEXTURE0, src, loc(program, "_MainTex"), 0);
        if (withMask) {
            int locSkin = loc(program, "skin_mask");
            int locFace = loc(program, "face_mask");
            if (locFace >= 0) bindTex(GLES20.GL_TEXTURE1, mask, locFace, 1);
            else if (locSkin >= 0) bindTex(GLES20.GL_TEXTURE1, mask, locSkin, 1);
        }
        GLES20.glUniform1f(loc(program, "value_factor"), valueFactor);
        int uLoc = loc(program, "unit_uv");
        if (uLoc >= 0) {
            GLES20.glUniform2f(uLoc, 1f / PROCESS_W, 1f / PROCESS_H);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        if (vp >= 0) GLES20.glDisableVertexAttribArray(vp);
        if (fp >= 0) GLES20.glDisableVertexAttribArray(fp);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private boolean drawFace145Mask(float[] landmarksUv, int processW, int processH) {
        int prog = mMeshMaskProgram;
        if (!mMeshReady || mFaceMaskTex == 0 || prog == 0) return false;
        if (processW < 2 || processH < 2) return false;
        if (!Face145Driver.updateFromLandmarks106(landmarksUv, mMeshXy)) return false;
        float pw = processW;
        float ph = processH;
        for (int i = 0; i < MESH_VERTS; i++) {
            float u = mMeshXy[i * 2];
            float v = mMeshXy[i * 2 + 1];
            mMeshNdc[i * 2] = u * pw;
            mMeshNdc[i * 2 + 1] = (1f - v) * ph;
        }
        mMeshPosBuf.position(0);
        mMeshPosBuf.put(mMeshNdc).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mMeshVbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, MESH_VERTS * 8, mMeshPosBuf);
        buildMakeupMvp(processW, processH, mMvpMatrix);
        GLES20.glUseProgram(prog);
        int vp = GLES20.glGetAttribLocation(prog, "v_Position");
        int fp = GLES20.glGetAttribLocation(prog, "f_Position");
        if (vp >= 0) {
            GLES20.glEnableVertexAttribArray(vp);
            GLES20.glVertexAttribPointer(vp, 2, GLES20.GL_FLOAT, false, 8, 0);
        }
        if (fp >= 0) {
            GLES20.glEnableVertexAttribArray(fp);
            GLES20.glVertexAttribPointer(fp, 2, GLES20.GL_FLOAT, false, 8, MESH_VERTS * 8);
        }
        int mvpLoc = loc(prog, "uMVPMatrix");
        int stLoc = loc(prog, "uSTMatrix");
        if (mvpLoc >= 0) GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mMvpMatrix, 0);
        if (stLoc >= 0) GLES20.glUniformMatrix4fv(stLoc, 1, false, mStMatrix, 0);
        bindTex(GLES20.GL_TEXTURE0, mFaceMaskTex, loc(prog, "faceMaskTexture"), 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mMeshIbo);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, MESH_INDICES, GLES20.GL_UNSIGNED_SHORT, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        if (vp >= 0) GLES20.glDisableVertexAttribArray(vp);
        if (fp >= 0) GLES20.glDisableVertexAttribArray(fp);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        return true;
    }

    private static void buildMakeupMvp(int w, int h, float[] out16) {
        Matrix.setIdentityM(out16, 0);
        Matrix.translateM(out16, 0, -1f, -1f, 0f);
        Matrix.scaleM(out16, 0, 2f, 2f, 1f);
        Matrix.translateM(out16, 0, 0f, 1f, 0f);
        Matrix.scaleM(out16, 0, 1f, -1f, 1f);
        Matrix.scaleM(out16, 0, 1f / w, 1f / h, 1f);
    }

    private void drawGblur(int src, int mask, float tw, float th) {
        GLES20.glUseProgram(mGblurProgram);
        int vp = GLES20.glGetAttribLocation(mGblurProgram, "v_Position");
        int fp = GLES20.glGetAttribLocation(mGblurProgram, "f_Position");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboId);
        GLES20.glEnableVertexAttribArray(vp);
        GLES20.glVertexAttribPointer(vp, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glEnableVertexAttribArray(fp);
        GLES20.glVertexAttribPointer(fp, 2, GLES20.GL_FLOAT, false, 8, 32);
        GLES20.glUniform1f(loc(mGblurProgram, "texBlurWidthOffset"), tw);
        GLES20.glUniform1f(loc(mGblurProgram, "texBlurHeightOffset"), th);
        bindTex(GLES20.GL_TEXTURE0, src, loc(mGblurProgram, "_MainTex"), 0);
        bindTex(GLES20.GL_TEXTURE1, mask, loc(mGblurProgram, "faceSkinMaskTexture"), 1);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(vp);
        GLES20.glDisableVertexAttribArray(fp);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void blitContain(
            int srcTex, int dstFbo,
            int srcW, int srcH, int dstW, int dstH,
            boolean flipY, float[] outContentUvTl
    ) {
        float scale = Math.min(dstW / (float) srcW, dstH / (float) srcH);
        float dw = srcW * scale;
        float dh = srcH * scale;
        float x0 = (dstW - dw) * 0.5f;
        float y0 = (dstH - dh) * 0.5f;
        outContentUvTl[0] = x0 / dstW;
        outContentUvTl[1] = y0 / dstH;
        outContentUvTl[2] = (x0 + dw) / dstW;
        outContentUvTl[3] = (y0 + dh) / dstH;
        float nx0 = outContentUvTl[0] * 2f - 1f;
        float nx1 = outContentUvTl[2] * 2f - 1f;
        float nyTop = 1f - 2f * outContentUvTl[1];
        float nyBot = 1f - 2f * outContentUvTl[3];
        mComposeUvs[0] = nx0;
        mComposeUvs[1] = nyBot;
        mComposeUvs[2] = nx1;
        mComposeUvs[3] = nyBot;
        mComposeUvs[4] = nx0;
        mComposeUvs[5] = nyTop;
        mComposeUvs[6] = nx1;
        mComposeUvs[7] = nyTop;
        mComposeUvBuf.position(0);
        mComposeUvBuf.put(mComposeUvs).position(0);
        float vBot = flipY ? 1f : 0f;
        float vTop = flipY ? 0f : 1f;
        mCoverOutUvs[0] = 0f;
        mCoverOutUvs[1] = vBot;
        mCoverOutUvs[2] = 1f;
        mCoverOutUvs[3] = vBot;
        mCoverOutUvs[4] = 0f;
        mCoverOutUvs[5] = vTop;
        mCoverOutUvs[6] = 1f;
        mCoverOutUvs[7] = vTop;
        mCoverOutUvBuf.position(0);
        mCoverOutUvBuf.put(mCoverOutUvs).position(0);
        bind(dstFbo, dstW, dstH);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(mBlitProgram);
        int vp = GLES20.glGetAttribLocation(mBlitProgram, "v_Position");
        int fp = GLES20.glGetAttribLocation(mBlitProgram, "f_Position");
        if (vp >= 0) {
            GLES20.glEnableVertexAttribArray(vp);
            GLES20.glVertexAttribPointer(vp, 2, GLES20.GL_FLOAT, false, 8, mComposeUvBuf);
        }
        if (fp >= 0) {
            GLES20.glEnableVertexAttribArray(fp);
            GLES20.glVertexAttribPointer(fp, 2, GLES20.GL_FLOAT, false, 8, mCoverOutUvBuf);
        }
        bindTex(GLES20.GL_TEXTURE0, srcTex, loc(mBlitProgram, "sTexture"), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        if (vp >= 0) GLES20.glDisableVertexAttribArray(vp);
        if (fp >= 0) GLES20.glDisableVertexAttribArray(fp);
        mCoverOutUvs[0] = 0f;
        mCoverOutUvs[1] = 0f;
        mCoverOutUvs[2] = 1f;
        mCoverOutUvs[3] = 0f;
        mCoverOutUvs[4] = 0f;
        mCoverOutUvs[5] = 1f;
        mCoverOutUvs[6] = 1f;
        mCoverOutUvs[7] = 1f;
        mCoverOutUvBuf.position(0);
        mCoverOutUvBuf.put(mCoverOutUvs).position(0);
    }

    private void blitSampleRect(int srcTex, int dstW, int dstH, float[] contentUvTl) {
        float u0 = contentUvTl[0];
        float v0 = contentUvTl[1];
        float u1 = contentUvTl[2];
        float v1 = contentUvTl[3];
        float vGlBot = 1f - v1;
        float vGlTop = 1f - v0;
        mComposeUvs[0] = u0;
        mComposeUvs[1] = vGlBot;
        mComposeUvs[2] = u1;
        mComposeUvs[3] = vGlBot;
        mComposeUvs[4] = u0;
        mComposeUvs[5] = vGlTop;
        mComposeUvs[6] = u1;
        mComposeUvs[7] = vGlTop;
        mComposeUvBuf.position(0);
        mComposeUvBuf.put(mComposeUvs).position(0);
        GLES20.glViewport(0, 0, dstW, dstH);
        GLES20.glUseProgram(mBlitProgram);
        int vp = GLES20.glGetAttribLocation(mBlitProgram, "v_Position");
        int fp = GLES20.glGetAttribLocation(mBlitProgram, "f_Position");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboId);
        if (vp >= 0) {
            GLES20.glEnableVertexAttribArray(vp);
            GLES20.glVertexAttribPointer(vp, 2, GLES20.GL_FLOAT, false, 8, 0);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        if (fp >= 0) {
            GLES20.glEnableVertexAttribArray(fp);
            GLES20.glVertexAttribPointer(fp, 2, GLES20.GL_FLOAT, false, 8, mComposeUvBuf);
        }
        bindTex(GLES20.GL_TEXTURE0, srcTex, loc(mBlitProgram, "sTexture"), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        if (vp >= 0) GLES20.glDisableVertexAttribArray(vp);
        if (fp >= 0) GLES20.glDisableVertexAttribArray(fp);
    }

    private static void remapUvIntoRect(float[] srcTl, float[] dst, float[] rectTl) {
        float u0 = rectTl[0];
        float v0 = rectTl[1];
        float du = rectTl[2] - rectTl[0];
        float dv = rectTl[3] - rectTl[1];
        int n = Math.min(srcTl.length, dst.length) / 2;
        for (int i = 0; i < n; i++) {
            dst[i * 2] = u0 + srcTl[i * 2] * du;
            dst[i * 2 + 1] = v0 + srcTl[i * 2 + 1] * dv;
        }
    }

    private void blit(int srcTex, int dstFbo, int w, int h, boolean flipY) {
        if (dstFbo > 0) {
            bind(dstFbo, w, h);
        } else {
            GLES20.glViewport(0, 0, w, h);
        }
        GLES20.glUseProgram(mBlitProgram);
        bindQuad(mBlitProgram, flipY);
        bindTex(GLES20.GL_TEXTURE0, srcTex, loc(mBlitProgram, "sTexture"), 0);
        draw();
    }

    private void bind(int fbo, int w, int h) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glViewport(0, 0, w, h);
    }

    private void bindQuad(int program, boolean flipY) {
        int vp = GLES20.glGetAttribLocation(program, "v_Position");
        int fp = GLES20.glGetAttribLocation(program, "f_Position");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboId);
        if (vp >= 0) {
            GLES20.glEnableVertexAttribArray(vp);
            GLES20.glVertexAttribPointer(vp, 2, GLES20.GL_FLOAT, false, 8, 0);
        }
        if (fp >= 0) {
            GLES20.glEnableVertexAttribArray(fp);
            GLES20.glVertexAttribPointer(fp, 2, GLES20.GL_FLOAT, false, 8, flipY ? 64 : 32);
        }
    }

    private void draw() {
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void bindTex(int unit, int tex, int uniform, int index) {
        GLES20.glActiveTexture(unit);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        if (uniform >= 0) GLES20.glUniform1i(uniform, index);
    }

    private void unbindTexUnits() {
        for (int i = 0; i < 5; i++) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
    }

    private static int loc(int program, String name) {
        return GLES20.glGetUniformLocation(program, name);
    }

    private int loadAssetTexture(String path) {
        Bitmap bmp = null;
        try {
            bmp = AssetBlob.decodeBitmap(mContext, path);
            if (bmp == null) return 0;
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            int id = ids[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            return id;
        } catch (Exception e) {
            BeautifyLog.e(TAG, "load " + path, e);
            return 0;
        } finally {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
    }

    private static int createSolidR8Tex(int r) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        int tex = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        byte v = (byte) (r & 0xff);
        ByteBuffer buf = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        buf.put(v).put(v).put(v).put((byte) 0xff).position(0);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static int createTex(int w, int h) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        int id = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return id;
    }

    private static int createFbo(int tex) {
        int[] prev = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prev, 0);
        int[] ids = new int[1];
        GLES20.glGenFramebuffers(1, ids, 0);
        int fbo = ids[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, tex, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prev[0]);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            if (tex != 0) GLES20.glDeleteTextures(1, new int[]{tex}, 0);
            return 0;
        }
        return fbo;
    }

    private void deleteTargets() {
        deleteFboTex(mMaskFbo, mMaskTex);
        deleteFboTex(mBlur1Fbo, mBlur1Tex);
        deleteFboTex(mBlur2Fbo, mBlur2Tex);
        deleteFboTex(mBlendFbo, mBlendTex);
        deleteFboTex(mTmpFbo, mTmpTex);
        deleteFboTex(mSkinFbo, mSkinTex);
        deleteFboTex(mSkinHBlurFbo, mSkinHBlurTex);
        deleteFboTex(mSkinBlur2Fbo, mSkinBlur2Tex);
        deleteFboTex(mUprightFbo, mUprightTex);
        deleteFboTex(mMaskRefineFbo, mMaskRefineTex);
        deleteFboTex(mLastFinalMaskFbo, mLastFinalMaskTex);
        mMaskFbo = mMaskTex = 0;
        mBlur1Fbo = mBlur1Tex = 0;
        mBlur2Fbo = mBlur2Tex = 0;
        mBlendFbo = mBlendTex = 0;
        mTmpFbo = mTmpTex = 0;
        mSkinFbo = mSkinTex = 0;
        mSkinHBlurFbo = mSkinHBlurTex = 0;
        mSkinBlur2Fbo = mSkinBlur2Tex = 0;
        mUprightFbo = mUprightTex = 0;
        mMaskRefineFbo = mMaskRefineTex = 0;
        mLastFinalMaskFbo = mLastFinalMaskTex = 0;
        mWidth = mHeight = 0;
    }

    private static void deleteFboTex(int fbo, int tex) {
        if (fbo != 0) GLES20.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        if (tex != 0) GLES20.glDeleteTextures(1, new int[]{tex}, 0);
    }

    private void releasePrograms() {
        int[] programs = {
                mMeshMaskProgram, mGblurProgram, mBlendProgram,
                mJointProgram, mJoint2Program, mSmoothProgram,
                mSkinEstProgram, mHblurProgram, mMaskRefineProgram,
                mMaskRefineFinalProgram, mBlitProgram
        };
        for (int p : programs) {
            if (p != 0) GLES20.glDeleteProgram(p);
        }
        mMeshMaskProgram = mGblurProgram = mBlendProgram = 0;
        mJointProgram = mJoint2Program = mSmoothProgram = 0;
        mSkinEstProgram = mHblurProgram = mMaskRefineProgram = 0;
        mMaskRefineFinalProgram = mBlitProgram = 0;
    }

    private void releaseGl() {
        deleteTargets();
        releasePrograms();
        if (mBlendLutTex != 0) {
            GLES20.glDeleteTextures(1, new int[]{mBlendLutTex}, 0);
            mBlendLutTex = 0;
        }
        if (mFaceMaskTex != 0) {
            GLES20.glDeleteTextures(1, new int[]{mFaceMaskTex}, 0);
            mFaceMaskTex = 0;
        }
        if (mSkinMaskStubTex != 0) {
            GLES20.glDeleteTextures(1, new int[]{mSkinMaskStubTex}, 0);
            mSkinMaskStubTex = 0;
        }
        if (mSkinLibTex != 0) {
            GLES20.glDeleteTextures(1, new int[]{mSkinLibTex}, 0);
            mSkinLibTex = 0;
        }
        if (mMeshVbo != 0 || mMeshIbo != 0) {
            GLES20.glDeleteBuffers(2, new int[]{mMeshVbo, mMeshIbo}, 0);
            mMeshVbo = mMeshIbo = 0;
        }
        if (mVboId != 0) {
            GLES20.glDeleteBuffers(1, new int[]{mVboId}, 0);
            mVboId = 0;
        }
        mMeshReady = false;
        mReady = false;
    }
}
