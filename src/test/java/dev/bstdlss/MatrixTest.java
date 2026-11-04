package dev.bstdlss;

import dev.bstdlss.render.Matrix4;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MatrixTest {

    @Test
    public void testIdentityReprojectionWhenStationary() {
        Matrix4 m = Matrix4.computeClipToPrevClip(
            70.0f, 16.0f / 9.0f, 0.05f, 1000.0f,
            45.0f, 10.0f, 100.0f, 64.0f, -200.0f,
            45.0f, 10.0f, 100.0f, 64.0f, -200.0f
        );

        // When stationary, m must be identity
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                float expected = (row == col) ? 1.0f : 0.0f;
                assertEquals(expected, m.m[col * 4 + row], 1e-4f, "Mismatch at (" + row + ", " + col + ")");
            }
        }
    }

    @Test
    public void testPerspectiveInversion() {
        Matrix4 p = Matrix4.perspective(70.0f, 1.777f, 0.05f, 1000.0f);
        Matrix4 inv = Matrix4.inversePerspective(70.0f, 1.777f, 0.05f, 1000.0f);
        Matrix4 prod = Matrix4.multiply(p, inv);

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                float expected = (row == col) ? 1.0f : 0.0f;
                assertEquals(expected, prod.m[col * 4 + row], 1e-3f, "P * P^-1 mismatch at (" + row + ", " + col + ")");
            }
        }
    }

    @Test
    public void testCameraYawReprojection() {
        // Player turns right by 5 degrees (currYaw = 50, prevYaw = 45)
        Matrix4 m = Matrix4.computeClipToPrevClip(
            70.0f, 1.777f, 0.05f, 1000.0f,
            50.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            45.0f, 0.0f, 0.0f, 0.0f, 0.0f
        );

        // A point straight ahead in current frame: NDC (0, 0, 0.5, 1)
        float[] clip = transform(m, 0.0f, 0.0f, 0.5f, 1.0f);
        float prevNdcX = clip[0] / clip[3];
        // When turning right, object should have been to the RIGHT (+X in previous frame)
        assertTrue(prevNdcX > 0.0f, "Object should be at positive NDC X in previous frame: " + prevNdcX);
    }

    @Test
    public void testCameraPitchReprojection() {
        // Player pitches down by 5 degrees (currPitch = 15, prevPitch = 10)
        Matrix4 m = Matrix4.computeClipToPrevClip(
            70.0f, 1.777f, 0.05f, 1000.0f,
            0.0f, 15.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 10.0f, 0.0f, 0.0f, 0.0f
        );

        // A point straight ahead in current frame: NDC (0, 0, 0.5, 1)
        float[] clip = transform(m, 0.0f, 0.0f, 0.5f, 1.0f);
        float prevNdcY = clip[1] / clip[3];
        // When pitching down, looking at current point means in previous frame (looking higher) the point was LOWER (-Y)
        assertTrue(prevNdcY < 0.0f, "Object should be at negative NDC Y in previous frame: " + prevNdcY);
    }

    private static float[] transform(Matrix4 mat, float x, float y, float z, float w) {
        float[] res = new float[4];
        for (int r = 0; r < 4; r++) {
            res[r] = mat.m[0 * 4 + r] * x +
                     mat.m[1 * 4 + r] * y +
                     mat.m[2 * 4 + r] * z +
                     mat.m[3 * 4 + r] * w;
        }
        return res;
    }
}
