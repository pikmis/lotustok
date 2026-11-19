package cat.narezany.margyt;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import java.lang.reflect.Method;

/**
 * What turns while TikTok is fetching something.
 *
 * TikTok's own is two circles, one red and one cyan, passing each other. This
 * draws Material 3 Expressive's instead: smooth vector morphing between the
 * official M3 Expressive shapes (4-lobe Clover, Rounded Squircle, 4-point Star,
 * Circle) in the Monet accent colour, turning with the emphasized spring curve.
 *
 * The two views that draw the circles are found by what they are -- a plain
 * View that keeps a PorterDuffXfermode and has a `setProgressBarInfo` -- and
 * each has one call put at the top of its `onDraw`, which lands in {@link
 * #draw}. That call decides who draws.
 *
 * When to draw is not decided here, because nothing here knows: the views
 * keep their state in fields the obfuscator named, and a loader that is on
 * the screen is not always one that is showing. So TikTok is asked. Its own
 * `onDraw` is run first, onto a bitmap the size of a fingernail, and whether
 * it put anything there is the answer. That run also does everything else the
 * original did -- counts its frames, asks for the next one -- so the view
 * goes on living exactly as it did, and only what reaches the screen differs.
 */
public final class Loading {

    private Loading() {}

    public static final String KEY = "loading_shapes";

    private static volatile Boolean cached;

    static void reloadSettings() {
        cached = null;
    }

    public static boolean isEnabled() {
        if (Version.LITE) return false;
        Boolean known = cached;
        if (known != null) return known;
        try {
            android.content.Context context = Margy.context();
            if (context == null) return true;  // on by default out-of-the-box
            boolean on = context.getSharedPreferences(Margy.PREFS,
                    android.content.Context.MODE_PRIVATE).getBoolean(KEY, true);
            cached = on;
            return on;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setEnabled(boolean enabled) {
        cached = enabled;
        try {
            android.content.Context context = Margy.context();
            if (context == null) return;
            context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------- who draws

    /** True while TikTok's own drawing is being run to see what it does. */
    private static boolean asking;
    private static boolean complained;

    /**
     * The top of the loader's `onDraw`. True when the mod has drawn it, and
     * the original must not.
     */
    public static boolean draw(View view, Canvas canvas) {
        // the question below comes back through here, and is TikTok's to answer
        if (asking) return false;
        try {
            if (view == null || canvas == null || !isEnabled()) return false;
            if (Looper.myLooper() != Looper.getMainLooper()) return false;
            int width = view.getWidth();
            int height = view.getHeight();
            if (width <= 0 || height <= 0) return false;

            if (!wouldDraw(view, width, height)) return true;
            shape(canvas, width, height);
            // TikTok asks for its next frame some milliseconds on; a shape
            // that turns wants every one
            if (view.isShown()) view.postInvalidateOnAnimation();
            return true;
        } catch (Throwable error) {
            if (!complained) {
                complained = true;
                Diary.note("loading: " + error);
            }
            return false;
        }
    }

    private static final int PROBE = 24;
    private static Bitmap probe;
    private static Canvas probeCanvas;
    private static final int[] seen = new int[PROBE * PROBE];
    private static Method onDraw;

    /** Whether TikTok would have put anything on the screen this frame. */
    private static boolean wouldDraw(View view, int width, int height) throws Exception {
        if (probe == null) {
            probe = Bitmap.createBitmap(PROBE, PROBE, Bitmap.Config.ARGB_8888);
            probeCanvas = new Canvas(probe);
            onDraw = View.class.getDeclaredMethod("onDraw", Canvas.class);
            onDraw.setAccessible(true);
        }
        probe.eraseColor(0);
        int saved = probeCanvas.save();
        probeCanvas.scale(PROBE / (float) width, PROBE / (float) height);
        asking = true;
        try {
            onDraw.invoke(view, probeCanvas);
        } finally {
            asking = false;
            probeCanvas.restoreToCount(saved);
        }
        probe.getPixels(seen, 0, PROBE, 0, 0, PROBE, PROBE);
        for (int pixel : seen) {
            if ((pixel >>> 24) > 24) return true;
        }
        return false;
    }

    // ------------------------------------------------------- the shape

    /** How long one shape takes to become the next, in milliseconds. */
    private static final long STEP = 650;
    private static final int SHAPE_COUNT = 4;

    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path();

    private static final float[] START_X = new float[] {0.96f, 0.88f, 1.00f, 0.85f};
    private static final float[][] SHAPES = new float[SHAPE_COUNT][];

    static {
        // 1. 4-lobe Clover / Flower (smooth rounded squircle petal lobes at 0, 90, 180, 270)
        SHAPES[0] = buildShape(new float[] {
                0.96f, 0.00f,
                0.96f, 0.30f,
                0.88f, 0.40f,
                0.76f, 0.42f,
                0.58f, 0.44f,
                0.38f, 0.16f,
                0.24f, 0.24f
        });

        // 2. Rounded Square / Squircle (smooth 4-corner rounded polygon)
        SHAPES[1] = buildShape(new float[] {
                0.88f, 0.00f,
                0.88f, 0.22f,
                0.84f, 0.36f,
                0.78f, 0.42f,
                0.70f, 0.48f,
                0.60f, 0.44f,
                0.52f, 0.52f
        });

        // 3. 4-point Rounded Star / Diamond
        SHAPES[2] = buildShape(new float[] {
                1.00f, 0.00f,
                1.00f, 0.16f,
                0.74f, 0.20f,
                0.54f, 0.26f,
                0.40f, 0.30f,
                0.34f, 0.16f,
                0.24f, 0.24f
        });

        // 4. Circle / Oval
        float rc = 0.85f;
        float k = (float) (rc * (4.0 / 3.0) * Math.tan(Math.PI / 32.0));
        float th1 = (float) (Math.PI / 8.0);
        float th2 = (float) (Math.PI / 4.0);
        float p1x = (float) (rc * Math.cos(th1));
        float p1y = (float) (rc * Math.sin(th1));
        float p2x = (float) (rc * Math.cos(th2));
        float p2y = (float) (rc * Math.sin(th2));
        float sin1 = (float) Math.sin(th1);
        float cos1 = (float) Math.cos(th1);
        float invSqrt2 = (float) (1.0 / Math.sqrt(2.0));

        SHAPES[3] = buildShape(new float[] {
                rc, 0.00f,
                rc, k,
                p1x + k * sin1, p1y - k * cos1,
                p1x, p1y,
                p1x - k * sin1, p1y + k * cos1,
                p2x + k * invSqrt2, p2y - k * invSqrt2,
                p2x, p2y
        });
    }

    private static float[] buildShape(float[] o) {
        float p0x = o[0];
        float c01x = o[2], c01y = o[3];
        float c02x = o[4], c02y = o[5];
        float p1x = o[6], p1y = o[7];
        float c11x = o[8], c11y = o[9];
        float c12x = o[10], c12y = o[11];
        float p2x = o[12], p2y = o[13];

        float[][] q0 = new float[][] {
                {c01x, c01y, c02x, c02y, p1x, p1y},
                {c11x, c11y, c12x, c12y, p2x, p2y},
                {c12y, c12x, c11y, c11x, p1y, p1x},
                {c02y, c02x, c01y, c01x, 0f, p0x}
        };

        float[] out = new float[96];
        int idx = 0;
        float[] cosQ = {1f, 0f, -1f, 0f};
        float[] sinQ = {0f, 1f, 0f, -1f};

        for (int q = 0; q < 4; q++) {
            float c = cosQ[q];
            float s = sinQ[q];
            for (float[] seg : q0) {
                float sc1x = seg[0], sc1y = seg[1];
                float sc2x = seg[2], sc2y = seg[3];
                float spx = seg[4], spy = seg[5];

                out[idx++] = sc1x * c - sc1y * s;
                out[idx++] = sc1x * s + sc1y * c;
                out[idx++] = sc2x * c - sc2y * s;
                out[idx++] = sc2x * s + sc2y * c;
                out[idx++] = spx * c - spy * s;
                out[idx++] = spx * s + spy * c;
            }
        }
        return out;
    }

    private static void shape(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        float size = Math.min(width, height) * 0.46f;
        drawIndicator(canvas, cx, cy, size, Accent.colour() | 0xFF000000, 1f);
    }

    /**
     * Draw the Material 3 Expressive Loading Indicator centered at (cx, cy).
     *
     * @param canvas the target canvas
     * @param cx center X
     * @param cy center Y
     * @param size radius / scale
     * @param colour the Monet accent colour
     * @param alpha opacity factor in [0, 1]
     */
    public static void drawIndicator(Canvas canvas, float cx, float cy, float size, int colour, float alpha) {
        if (alpha <= 0.001f || size <= 0f || canvas == null) return;
        long now = SystemClock.uptimeMillis();
        float along = (now % (STEP * SHAPE_COUNT)) / (float) STEP;
        int from = (int) along;
        int to = (from + 1) % SHAPE_COUNT;
        float t = along - from;
        float eased = settle(t);
        float angle = (from + eased) * 90f;

        float[] fromShape = SHAPES[from];
        float[] toShape = SHAPES[to];
        float startX = START_X[from] + (START_X[to] - START_X[from]) * eased;

        synchronized (path) {
            path.rewind();
            path.moveTo(startX, 0f);
            for (int i = 0; i < 96; i += 6) {
                float c1x = fromShape[i] + (toShape[i] - fromShape[i]) * eased;
                float c1y = fromShape[i + 1] + (toShape[i + 1] - fromShape[i + 1]) * eased;
                float c2x = fromShape[i + 2] + (toShape[i + 2] - fromShape[i + 2]) * eased;
                float c2y = fromShape[i + 3] + (toShape[i + 3] - fromShape[i + 3]) * eased;
                float px = fromShape[i + 4] + (toShape[i + 4] - fromShape[i + 4]) * eased;
                float py = fromShape[i + 5] + (toShape[i + 5] - fromShape[i + 5]) * eased;
                path.cubicTo(c1x, c1y, c2x, c2y, px, py);
            }
            path.close();

            int saved = canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(angle);
            canvas.scale(size, size);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(alpha < 0.999f ? alphaColor(colour, alpha) : colour);
            canvas.drawPath(path, paint);
            canvas.restoreToCount(saved);
        }
    }

    private static int alphaColor(int colour, float factor) {
        int a = Math.round(((colour >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (colour & 0xFFFFFF);
    }

    /**
     * Material 3 Expressive emphasized motion curve (fast in, settle spring).
     */
    private static float settle(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return 1f - (float) (Math.exp(-6.0 * t) * Math.cos(8.0 * t));
    }
}