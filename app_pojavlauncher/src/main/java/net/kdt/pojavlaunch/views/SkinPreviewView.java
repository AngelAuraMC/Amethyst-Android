package net.kdt.pojavlaunch.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pixel-crisp touch-rotatable 3D-style Minecraft skin preview with no idle animation loop. */
public class SkinPreviewView extends View {
    private final Paint paint = new Paint();
    private final Matrix textureMatrix = new Matrix();
    private final Path facePath = new Path();
    private Bitmap skin;
    private float rotation = -0.20f;
    private float lastTouchX;
    private boolean dragging;

    public SkinPreviewView(Context context) { super(context); init(); }
    public SkinPreviewView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public SkinPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }

    private void init() {
        paint.setFilterBitmap(false);
        paint.setDither(false);
        paint.setAntiAlias(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
        setContentDescription("Rotatable Minecraft skin preview. Drag left or right to turn.");
    }

    public void setSkin(Bitmap bitmap) {
        skin = bitmap;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT);
        float scale = Math.min(getWidth() / 25f, getHeight() / 35f);
        if (scale <= 0) return;
        float centerX = getWidth() / 2f;
        float top = (getHeight() - 32f * scale) / 2f;
        if (skin == null || skin.isRecycled()) {
            paint.setColor(0x5533D6B3);
            canvas.drawRoundRect(centerX - 9 * scale, top + scale,
                    centerX + 9 * scale, top + 31 * scale, 3 * scale, 3 * scale, paint);
            return;
        }

        paint.setColor(Color.WHITE);
        float cosine = (float) Math.cos(rotation);
        float sine = (float) Math.sin(rotation);
        List<Face> baseFaces = new ArrayList<>();
        addCube(baseFaces, -4, 4, 0, 8, -4, 4,
                new int[][]{{8,8,8,8},{24,8,8,8},{16,8,8,8},{0,8,8,8}},
                centerX, top, scale, cosine, sine);
        addCube(baseFaces, -4, 4, 8, 20, -2, 2,
                new int[][]{{20,20,8,12},{32,20,8,12},{28,20,4,12},{16,20,4,12}},
                centerX, top, scale, cosine, sine);

        boolean modern = skin.getHeight() >= 64;
        addCube(baseFaces, -4, 0, 20, 32, -2, 2,
                new int[][]{{4,20,4,12},{12,20,4,12},{8,20,4,12},{0,20,4,12}},
                centerX, top, scale, cosine, sine);
        addCube(baseFaces, 0, 4, 20, 32, -2, 2,
                modern ? new int[][]{{20,52,4,12},{28,52,4,12},{24,52,4,12},{16,52,4,12}}
                        : new int[][]{{4,20,4,12},{12,20,4,12},{8,20,4,12},{0,20,4,12}},
                centerX, top, scale, cosine, sine);
        addCube(baseFaces, -8, -4, 8, 20, -2, 2,
                new int[][]{{44,20,4,12},{52,20,4,12},{48,20,4,12},{40,20,4,12}},
                centerX, top, scale, cosine, sine);
        addCube(baseFaces, 4, 8, 8, 20, -2, 2,
                modern ? new int[][]{{36,52,4,12},{44,52,4,12},{40,52,4,12},{32,52,4,12}}
                        : new int[][]{{44,20,4,12},{52,20,4,12},{48,20,4,12},{40,20,4,12}},
                centerX, top, scale, cosine, sine);
        drawFaces(canvas, baseFaces);

        if (modern) {
            List<Face> outerFaces = new ArrayList<>();
            addCube(outerFaces, -4.35f, 4.35f, -0.35f, 8.35f, -4.35f, 4.35f,
                    new int[][]{{40,8,8,8},{56,8,8,8},{48,8,8,8},{32,8,8,8}},
                    centerX, top, scale, cosine, sine);
            addCube(outerFaces, -4.15f, 4.15f, 8, 20, -2.15f, 2.15f,
                    new int[][]{{20,36,8,12},{32,36,8,12},{28,36,4,12},{16,36,4,12}},
                    centerX, top, scale, cosine, sine);
            drawFaces(canvas, outerFaces);
        }
    }

    /** UV rectangles are ordered front, back, left side, right side; each is x/y/w/h. */
    private void addCube(List<Face> faces, float x1, float x2, float y1, float y2,
                         float z1, float z2, int[][] uv,
                         float centerX, float top, float scale, float cosine, float sine) {
        addFace(faces, uv[0], new float[]{x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2},
                centerX, top, scale, cosine, sine);
        addFace(faces, uv[1], new float[]{x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1},
                centerX, top, scale, cosine, sine);
        addFace(faces, uv[2], new float[]{x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1},
                centerX, top, scale, cosine, sine);
        addFace(faces, uv[3], new float[]{x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2},
                centerX, top, scale, cosine, sine);
    }

    private void addFace(List<Face> faces, int[] uv, float[] vertices,
                         float centerX, float top, float scale, float cosine, float sine) {
        float depth = 0f;
        float[] destination = new float[8];
        for (int i = 0; i < 4; i++) {
            float x = vertices[i * 3], y = vertices[i * 3 + 1], z = vertices[i * 3 + 2];
            float rotatedX = x * cosine + z * sine;
            float rotatedZ = -x * sine + z * cosine;
            destination[i * 2] = centerX + rotatedX * scale;
            destination[i * 2 + 1] = top + y * scale;
            depth += rotatedZ / 4f;
        }
        if (depth > 0.001f) faces.add(new Face(uv, destination, depth));
    }

    private void drawFaces(Canvas canvas, List<Face> faces) {
        Collections.sort(faces, Comparator.comparingDouble(face -> face.depth));
        for (Face face : faces) {
            float left = face.uv[0], top = face.uv[1];
            float right = left + face.uv[2], bottom = top + face.uv[3];
            float[] source = {left,top,right,top,right,bottom,left,bottom};
            if (!textureMatrix.setPolyToPoly(source, 0, face.destination, 0, 4)) continue;
            facePath.reset();
            facePath.moveTo(face.destination[0], face.destination[1]);
            for (int i = 1; i < 4; i++) facePath.lineTo(face.destination[i * 2], face.destination[i * 2 + 1]);
            facePath.close();
            int save = canvas.save();
            canvas.clipPath(facePath);
            canvas.drawBitmap(skin, textureMatrix, paint);
            canvas.restoreToCount(save);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                dragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    rotation += (event.getX() - lastTouchX) * 0.012f;
                    lastTouchX = event.getX();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) { dragging = false; performClick(); }
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() { super.performClick(); return true; }

    private static final class Face {
        final int[] uv;
        final float[] destination;
        final float depth;
        Face(int[] uv, float[] destination, float depth) {
            this.uv = uv; this.destination = destination; this.depth = depth;
        }
    }
}
