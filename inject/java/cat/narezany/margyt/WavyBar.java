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

    private boolean scrubOnly;

    WavyBar(Context context, View bar, boolean scrubOnly) {
        this(context, bar, null, null);
        this.scrubOnly = scrubOnly;
        if (scrubOnly) synchronized (scrubbers) {
            scrubbers.put(this, Boolean.TRUE);
        }
    }

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

    private float scrub;
    private float finger;

    private View host() {
        return overSeekBar() ? bar : MD3Progress.seekBar(this);
    }

    private float hostProgress(View host) {
        if (host == null) return -1f;
        try {
            if (read == null || !read.getDeclaringClass().isInstance(host)) read = host.getClass().getMethod("getMProgress");
            Object value = read.invoke(host);
            return value instanceof Number ? Math.max(0f, Math.min(1f, ((Number) value).floatValue() / 100f)) : -1f;
        } catch (Throwable error) {
            return -1f;
        }
    }

    private float progress() {
        float played = PlayerTools.progress();
        if (played >= 0f) return played;
        View host = host();
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

    private static final java.util.WeakHashMap<WavyBar, Boolean> scrubbers = new java.util.WeakHashMap<WavyBar, Boolean>();

    private int startSeq = -1;
    private float start;

    private float moved() {
        float at = hostProgress(bar);
        if (at < 0f) return -1f;
        if (startSeq != MD3Progress.fingerSeq) {
            startSeq = MD3Progress.fingerSeq;
            start = at;
        }
        return Math.abs(at - start);
    }

    private boolean followsFinger() {
        float mine = moved();
        if (mine < 0.002f) return false;
        synchronized (scrubbers) {
            for (WavyBar other : scrubbers.keySet()) {
                if (other == this || other.getRootView() != getRootView() || !other.bar.isAttachedToWindow()
                        || other.bar.getWidth() == 0) continue;
                float theirs = other.moved();
                if (theirs > mine || (theirs == mine && System.identityHashCode(other) < System.identityHashCode(this))) return false;
            }
        }
        return true;
    }

    private boolean lineWaveBack() {
        int height = getResources().getDisplayMetrics().heightPixels;
        int[] at = new int[2];
        synchronized (lineWaves) {
            for (WavyBar other : lineWaves.keySet()) {
                if (other.getRootView() != getRootView() || !other.isShown() || other.getWidth() == 0) continue;
                other.getLocationOnScreen(at);
                if (at[1] <= 0 || at[1] >= height) continue;
                float alpha = 1f;
                for (Object v = other; v instanceof View && alpha > 0.5f; v = ((View) v).getParent()) alpha *= ((View) v).getAlpha();
                if (alpha > 0.5f) return true;
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

        if (overSeekBar() && !scrubOnly) {

            if (bar.getVisibility() != View.VISIBLE || bar.getWidth() == 0 || lineWaveShown()) {
                postInvalidateOnAnimation();
                return;
            }
        }
        if (bar.getAlpha() != 0f) bar.setAlpha(0f);
        MD3Progress.hideSeekBars();
        if (!scrubOnly && MD3Progress.fingerDown && MD3Progress.scrubbing(getRootView())) {
            postInvalidateOnAnimation();
            return;
        }
        float mid = lineY();

        if (!scrubOnly) {
            int[] at = new int[2];
            getLocationOnScreen(at);
            MD3Progress.lift(getRootView(), at[1] + mid - dp(5f));
        }

        played.setColor(Accent.colour());
        float left = dp(2);
        float right = getWidth() - dp(2);
        float shown = progress();
        View seek = host();
        float dragged = scrubOnly && seek != null ? hostProgress(seek) : -1f;
        boolean scrubbing = scrubOnly && dragged >= 0f && MD3Progress.fingerDown && followsFinger()
                && MD3Progress.scrubbing(getRootView());
        if (scrubbing) {
            finger = dragged;
            shown = dragged;
        } else if (scrub > 0.02f) {
            shown = finger;
        }
        scrub += ((scrubbing ? 1f : 0f) - scrub) * (scrubbing ? 0.25f : 0.5f);
        if (scrub < 0.01f) scrub = 0f;
        if (!scrubbing && scrub == 0f) finger = shown;
        if (scrubOnly && !scrubbing && scrub == 0f) {
            postInvalidateOnAnimation();
            return;
        }

        float line = dp(4) + dp(4) * scrub;
        played.setStrokeWidth(line);
        rest.setStrokeWidth(line);
        float x = left + (right - left) * shown;
        long now = SystemClock.uptimeMillis();

        float handle = dp(4) * scrub;
        float gap = dp(6) + handle + dp(2) * scrub;
        if (right - (x + gap) > 0) canvas.drawLine(x + gap, mid, right, mid, rest);

        if (scrub > 0f) {
            float half = dp(12) * scrub;
            Paint thumb = rest;
            int keep = thumb.getColor();
            thumb.setColor(Accent.colour());
            canvas.drawLine(x, mid - half, x, mid + half, thumb);
            thumb.setColor(keep);
        }

        if (x > left) {
            float amplitude = dp(2.5f) + dp(2f) * scrub;
            float length = dp(24);
            float phase = (now % 1600L) / 1600f * length;
            wave.reset();
            wave.moveTo(left, mid);
            float end = x - (dp(4) + dp(2)) * scrub;
            for (float px = left; px <= end; px += dp(1.5f)) {
                float y = mid + (float) Math.sin((px - left + phase) / length * 2 * Math.PI) * amplitude;
                wave.lineTo(px, y);
            }
            canvas.drawPath(wave, played);
        }
        postInvalidateOnAnimation();
    }
}
