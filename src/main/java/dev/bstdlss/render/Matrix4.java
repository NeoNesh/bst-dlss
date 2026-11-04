package dev.bstdlss.render;

import java.nio.FloatBuffer;

/**
 * High-performance 4x4 matrix implementation for 3D camera reprojection and motion vector generation.
 * Uses standard column-major order (OpenGL compatible).
 */
public final class Matrix4 {
    // Column-major:
    // [0]=m00, [1]=m10, [2]=m20, [3]=m30  (column 0)
    // [4]=m01, [5]=m11, [6]=m21, [7]=m31  (column 1)
    // [8]=m02, [9]=m12, [10]=m22, [11]=m32 (column 2)
    // [12]=m03, [13]=m13, [14]=m23, [15]=m33 (column 3)
    public final float[] m = new float[16];

    public Matrix4() {
        identity();
    }

    public Matrix4 identity() {
        for (int i = 0; i < 16; i++) m[i] = 0.0f;
        m[0] = 1.0f;
        m[5] = 1.0f;
        m[10] = 1.0f;
        m[15] = 1.0f;
        return this;
    }

    public static Matrix4 perspective(float fovYDeg, float aspect, float zNear, float zFar) {
        Matrix4 mat = new Matrix4();
        float tanHalfFov = (float) Math.tan(Math.toRadians(fovYDeg * 0.5f));
        mat.m[0] = 1.0f / (aspect * tanHalfFov);
        mat.m[5] = 1.0f / tanHalfFov;
        mat.m[10] = -(zFar + zNear) / (zFar - zNear);
        mat.m[11] = -1.0f;
        mat.m[14] = -(2.0f * zFar * zNear) / (zFar - zNear);
        mat.m[15] = 0.0f;
        return mat;
    }

    public static Matrix4 inversePerspective(float fovYDeg, float aspect, float zNear, float zFar) {
        Matrix4 inv = new Matrix4();
        float tanHalfFov = (float) Math.tan(Math.toRadians(fovYDeg * 0.5f));
        float m00 = 1.0f / (aspect * tanHalfFov);
        float m11 = 1.0f / tanHalfFov;
        float m22 = -(zFar + zNear) / (zFar - zNear);
        float m23 = -(2.0f * zFar * zNear) / (zFar - zNear);

        inv.m[0] = 1.0f / m00;
        inv.m[5] = 1.0f / m11;
        inv.m[10] = 0.0f;
        inv.m[11] = 1.0f / m23;
        inv.m[14] = -1.0f;
        inv.m[15] = m22 / m23;
        return inv;
    }

    public static Matrix4 multiply(Matrix4 a, Matrix4 b) {
        Matrix4 res = new Matrix4();
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    sum += a.m[k * 4 + row] * b.m[col * 4 + k];
                }
                res.m[col * 4 + row] = sum;
            }
        }
        return res;
    }

    /**
     * Constructs the 3x3 rotation matrix for Minecraft camera (yaw around Y, pitch around X)
     * and returns the 4x4 View matrix (world -> view space).
     */
    public static Matrix4 cameraViewRotation(float yawDeg, float pitchDeg) {
        Matrix4 mat = new Matrix4();
        double yawRad = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(-pitchDeg);

        float cy = (float) Math.cos(yawRad);
        float sy = (float) Math.sin(yawRad);
        float cx = (float) Math.cos(pitchRad);
        float sx = (float) Math.sin(pitchRad);

        // R = Rx(-pitch) * Ry(yaw)
        // Stored in column-major order:
        mat.m[0] = cy;
        mat.m[1] = -sx * sy;
        mat.m[2] = -cx * sy;
        mat.m[3] = 0.0f;

        mat.m[4] = 0.0f;
        mat.m[5] = cx;
        mat.m[6] = -sx;
        mat.m[7] = 0.0f;

        mat.m[8] = -sy;
        mat.m[9] = -sx * cy;
        mat.m[10] = -cx * cy;
        mat.m[11] = 0.0f;

        mat.m[12] = 0.0f;
        mat.m[13] = 0.0f;
        mat.m[14] = 0.0f;
        mat.m[15] = 1.0f;

        return mat;
    }

    /**
     * Computes the full Clip-to-Previous-Clip reprojection matrix:
     * M = P * M_relView * P^-1
     */
    public static Matrix4 computeClipToPrevClip(
        float fovYDeg, float aspect, float zNear, float zFar,
        float currYaw, float currPitch, float currX, float currY, float currZ,
        float prevYaw, float prevPitch, float prevX, float prevY, float prevZ
    ) {
        Matrix4 p = perspective(fovYDeg, aspect, zNear, zFar);
        Matrix4 pInv = inversePerspective(fovYDeg, aspect, zNear, zFar);

        Matrix4 rCurr = cameraViewRotation(currYaw, currPitch);
        Matrix4 rPrev = cameraViewRotation(prevYaw, prevPitch);

        // rRel = rPrev * rCurr^T
        Matrix4 rCurrT = new Matrix4();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                rCurrT.m[c * 4 + r] = rCurr.m[r * 4 + c];
            }
        }
        rCurrT.m[15] = 1.0f;

        Matrix4 rRel = multiply(rPrev, rCurrT);

        // Delta camera position: deltaC = currPos - prevPos
        float dx = currX - prevX;
        float dy = currY - prevY;
        float dz = currZ - prevZ;

        // tRel = rPrev * deltaC
        float tx = rPrev.m[0] * dx + rPrev.m[4] * dy + rPrev.m[8] * dz;
        float ty = rPrev.m[1] * dx + rPrev.m[5] * dy + rPrev.m[9] * dz;
        float tz = rPrev.m[2] * dx + rPrev.m[6] * dy + rPrev.m[10] * dz;

        Matrix4 mRelView = new Matrix4();
        for (int i = 0; i < 16; i++) mRelView.m[i] = rRel.m[i];
        mRelView.m[12] = tx;
        mRelView.m[13] = ty;
        mRelView.m[14] = tz;
        mRelView.m[15] = 1.0f;

        Matrix4 temp = multiply(mRelView, pInv);
        return multiply(p, temp);
    }

    public void toBuffer(FloatBuffer buffer) {
        buffer.clear();
        buffer.put(m);
        buffer.flip();
    }
}
