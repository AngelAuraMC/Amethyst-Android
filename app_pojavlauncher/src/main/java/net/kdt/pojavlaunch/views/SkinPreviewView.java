package net.kdt.pojavlaunch.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * Lightweight front-facing preview assembled from the front UV regions of a Minecraft skin.
 * This is a 2D preview, not a rotating 3D renderer.
 */
public class SkinPreviewView extends View {
    private final Paint paint = new Paint();
    private Bitmap skin;

    public SkinPreviewView(Context context) {
        super(context);
        init();
    }

    public SkinPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SkinPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setFilterBitmap(false);
        paint.setDither(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setSkin(Bitmap bitmap) {
        skin = bitmap;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT);
        float scale = Math.min(getWidth() / 24f, getHeight() / 38f);
        if (scale <= 0) return;
        float left = (getWidth() - 24f * scale) / 2f;
        float top = (getHeight() - 38f * scale) / 2f;
        if (skin == null) {
            paint.setColor(0x5533D6B3);
            canvas.drawRoundRect(left + 4 * scale, top + 1 * scale,
                    left + 20 * scale, top + 37 * scale, 3 * scale, 3 * scale, paint);
            return;
        }

        boolean modern = skin.getWidth() >= 64 && skin.getHeight() >= 64;
        // Legs, arms, torso and head base layers.
        draw(canvas, 4,20,8,32, 8,21,12,37, left,top,scale);
        draw(canvas, 20,20,28,32, 8,9,16,21, left,top,scale);
        draw(canvas, 44,20,48,32, 4,9,8,21, left,top,scale);
        draw(canvas, 44,20,48,32, 16,9,20,21, left,top,scale);
        draw(canvas, 4,20,8,32, 12,21,16,37, left,top,scale);
        draw(canvas, 8,8,16,16, 8,0,16,8, left,top,scale);

        if (modern) {
            // Independent left limb UVs and the outer (second) skin layer.
            draw(canvas, 20,52,24,64, 12,21,16,37, left,top,scale);
            draw(canvas, 36,52,40,64, 16,9,20,21, left,top,scale);
            draw(canvas, 4,52,8,64, 8,21,12,37, left,top,scale);
            draw(canvas, 52,52,56,64, 16,9,20,21, left,top,scale);
            draw(canvas, 4,36,8,48, 8,21,12,37, left,top,scale);
            draw(canvas, 4,52,8,64, 12,21,16,37, left,top,scale);
            draw(canvas, 44,36,48,48, 4,9,8,21, left,top,scale);
            draw(canvas, 20,36,28,48, 8,9,16,21, left,top,scale);
            draw(canvas, 52,52,56,64, 16,9,20,21, left,top,scale);
            draw(canvas, 40,8,48,16, 8,0,16,8, left,top,scale);
        }
    }

    private void draw(Canvas canvas, int sx, int sy, int sw, int sh,
                      int dx, int dy, int dw, int dh,
                      float left, float top, float scale) {
        if (skin == null || sx < 0 || sy < 0 || sx + sw > skin.getWidth() || sy + sh > skin.getHeight()) return;
        Rect source = new Rect(sx, sy, sx + sw, sy + sh);
        Rect destination = new Rect(
                Math.round(left + dx * scale),
                Math.round(top + dy * scale),
                Math.round(left + dw * scale),
                Math.round(top + dh * scale)
        );
        canvas.drawBitmap(skin, source, destination, paint);
    }
}
