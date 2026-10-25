package cat.narezany.margyt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

final class WavyBar extends View {

    private final View bar;

    private final View track;
    private final View fill;
    private final Paint played = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rest = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();
    private Method read;

    WavyBar(Context context, View bar, View track, View fill) {
        super(context);
        this.bar = bar;
        this.track = track;
        this.fill = fill;
        float line = dp(4);
        played.setStyle(Paint.Style.STROKE);
        played.setStrokeCap(Paint.Cap.BUTT);
        played.setStrokeWidth(line);
        rest.setStyle(Paint.Style.STROKE);
        rest.setStrokeCap(Paint.Cap.ROUND);
        rest.setStrokeWidth(line);
        rest.setColor(0x66FFFFFF);
        setWillNotDraw(false);
        if (track != null) synchronized (lineWaves) {
            lineWaves.put(this, Boolean.TRUE);
        }
    }

    private boolean overSeekBar() {
        return track == null;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float progress() {
        float played = PlayerTools.progress();
        if (played >= 0f) return played;
        View host = overSeekBar() ? bar : MD3Progress.seekBar(this);
        if (host == null) return 0f;
        try {
            if (read == null || !read.getDeclaringClass().isInstance(host)) read = host.getClass().getMethod("getMProgress");
            Object value = read.invoke(host);
            float percent = value instanceof Number ? ((Number) value).floatValue() : 0f;
            return Math.max(0f, Math.min(1f, percent / 100f));
        } catch (Throwable error) {
            return 0f;
        }
    }

    private static final java.util.WeakHashMap<WavyBar, Boolean> lineWaves = new java.util.WeakHashMap<WavyBar, Boolean>();

    private boolean lineWaveShown() {
        int height = getResources().getDisplayMetrics().heightPixels;
        int[] at = new int[2];
        synchronized (lineWaves) {
            for (WavyBar other : lineWaves.keySet()) {
                if (other.getRootView() != getRootView() || !other.isShown() || other.getWidth() == 0) continue;
                other.getLocationOnScreen(at);
                if (at[1] > 0 && at[1] < height) return true;
            }
        }
        return false;
    }

    private float lineY() {
        if (!overSeekBar()) return getHeight() / 2f;
        int[] mine = new int[2];
        int[] theirs = new int[2];
        getLocationOnScreen(mine);
        bar.getLocationOnScreen(theirs);
        return theirs[1] - mine[1] + bar.getHeight() / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!MD3Progress.on() || !bar.isAttachedToWindow()) {

            bar.setAlpha(1f);
            if (track != null) track.setAlpha(1f);
            if (fill != null) fill.setAlpha(1f);
            if (!MD3Progress.on()) {
                MD3Progress.showSeekBars();
                MD3Progress.unlift();
            }
            final ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) post(new Runnable() {
                @Override
                public void run() {
                    parent.removeView(WavyBar.this);
                    MD3Progress.forget(bar);
                }
            });
            return;
        }

        if (overSeekBar()) {

            if (bar.getVisibility() != View.VISIBLE || bar.getWidth() == 0 || lineWaveShown()) {
                postInvalidateOnAnimation();
                return;
            }
        }
        if (bar.getAlpha() != 0f) bar.setAlpha(0f);
        MD3Progress.hideSeekBars();
        float mid = lineY();

        int[] at = new int[2];
        getLocationOnScreen(at);
        MD3Progress.lift(getRootView(), at[1] + mid - dp(5f));

        played.setColor(Accent.colour());
        float left = dp(2);
        float right = getWidth() - dp(2);
        float x = left + (right - left) * progress();
        long now = SystemClock.uptimeMillis();

        float gap = dp(6);
        if (right - (x + gap) > 0) canvas.drawLine(x + gap, mid, right, mid, rest);

        if (x > left) {
            float amplitude = dp(2.5f);
            float length = dp(24);
            float phase = (now % 1600L) / 1600f * length;
            wave.reset();
            wave.moveTo(left, mid);
            for (float px = left; px <= x; px += dp(1.5f)) {
                float y = mid + (float) Math.sin((px - left + phase) / length * 2 * Math.PI) * amplitude;
                wave.lineTo(px, y);
            }
            canvas.drawPath(wave, played);
        }
        postInvalidateOnAnimation();
    }
}
