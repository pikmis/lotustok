package cat.narezany.margyt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
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
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        // Material 3 thin sleek track (2dp height, like in MoltenGram's player)
        float line = dp(2);
        played.setStyle(Paint.Style.STROKE);
        played.setStrokeCap(Paint.Cap.ROUND);
        played.setStrokeWidth(line);
        rest.setStyle(Paint.Style.STROKE);
        rest.setStrokeCap(Paint.Cap.ROUND);
        rest.setStrokeWidth(line);
        rest.setColor(0x38FFFFFF);

        thumbPaint.setStyle(Paint.Style.FILL);
        thumbBorder.setStyle(Paint.Style.STROKE);
        thumbBorder.setStrokeWidth(dp(1.2f));
        thumbBorder.setColor(0x33000000);

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

    private static float dp(View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
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

    private float lineY() {
        if (!overSeekBar()) return getHeight() / 2f;
        int[] mine = new int[2];
        int[] theirs = new int[2];
        getLocationOnScreen(mine);
        bar.getLocationOnScreen(theirs);
        return theirs[1] - mine[1] + bar.getHeight() / 2f;
    }

    /** Find the active bottom navigation bar if present on screen. */
    static View findNavBar(View root) {
        if (root == null) return null;
        View current = NavBar.currentBar();
        if (current != null && current.isAttachedToWindow() && current.isShown() && current.getHeight() > 0) return current;
        View rail = BottomChrome.railView();
        if (rail != null && rail.isAttachedToWindow() && rail.isShown() && rail.getHeight() > 0) return rail;
        return scanForNavBar(root, 0);
    }

    private static View scanForNavBar(View view, int depth) {
        if (depth > 25 || view == null || view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            int tall = vg.getHeight();
            int wide = vg.getWidth();
            View root = view.getRootView();
            int rootH = root.getHeight();
            int rootW = root.getWidth();
            if (tall > 0 && rootH > 0 && wide >= rootW * 0.8f && tall >= dp(vg, 40) && tall <= rootH * 0.18f) {
                int[] loc = new int[2];
                vg.getLocationOnScreen(loc);
                if (loc[1] + tall >= rootH * 0.9f && vg.getChildCount() >= 3) {
                    return vg;
                }
            }
            for (int i = 0; i < vg.getChildCount(); i++) {
                View found = scanForNavBar(vg.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Locate [ ✕ ] and [ ▶ | 1x ] controls in video player scrubber. */
    static View[] findScrubberControls(View anchor) {
        View[] res = new View[2];
        if (anchor == null) return res;
        View root = anchor.getRootView();
        findControlsIn(root, anchor, res, 0);
        return res;
    }

    private static void findControlsIn(View view, View anchor, View[] out, int depth) {
        if (depth > 22 || view == null || view.getVisibility() != View.VISIBLE) return;
        if (view != anchor) {
            int w = view.getWidth(), h = view.getHeight();
            int screenW = view.getResources().getDisplayMetrics().widthPixels;
            int screenH = view.getResources().getDisplayMetrics().heightPixels;
            if (w > 0 && h > 0) {
                int[] loc = new int[2];
                view.getLocationOnScreen(loc);
                int[] anchorLoc = new int[2];
                anchor.getLocationOnScreen(anchorLoc);
                float dy = Math.abs((loc[1] + h / 2f) - (anchorLoc[1] + anchor.getHeight() / 2f));
                float density = view.getResources().getDisplayMetrics().density;
                if (dy < 65f * density && loc[1] > screenH * 0.6f) {
                    if (out[0] == null && loc[0] < screenW * 0.25f && w <= 64f * density && h <= 64f * density && view.isClickable()) {
                        out[0] = view;
                    }
                    if (out[1] == null && (loc[0] + w) > screenW * 0.70f && w >= 50f * density && w <= 160f * density) {
                        out[1] = view;
                    }
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                findControlsIn(vg.getChildAt(i), anchor, out, depth + 1);
            }
        }
    }

    /** Style [ ✕ ] and [ ▶ | 1x ] as compact, non-bulky opaque Material 3 controls. */
    static void dressScrubberControls(View closeBtn, View controlsBtn) {
        if (closeBtn != null) {
            float density = closeBtn.getResources().getDisplayMetrics().density;
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(0xFF161823); // 100% opaque matte dark surface container
            disc.setStroke(Math.max(1, Math.round(1f * density)), 0x1FFFFFFF); // Subtle 1dp M3 outline
            closeBtn.setBackground(disc);
            closeBtn.setAlpha(1f);
        }
        if (controlsBtn != null) {
            float density = controlsBtn.getResources().getDisplayMetrics().density;
            GradientDrawable pill = new GradientDrawable();
            pill.setShape(GradientDrawable.RECTANGLE);
            pill.setCornerRadius(18f * density);
            pill.setColor(0xFF161823); // 100% opaque matte dark surface container
            pill.setStroke(Math.max(1, Math.round(1f * density)), 0x1FFFFFFF); // Subtle 1dp M3 outline
            controlsBtn.setBackground(pill);
            controlsBtn.setAlpha(1f);
            int padH = Math.round(12f * density);
            int padV = Math.round(6f * density);
            controlsBtn.setPadding(padH, padV, padH, padV);
            if (controlsBtn instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) controlsBtn;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child instanceof android.widget.TextView) {
                        ((android.widget.TextView) child).setTextColor(0xFFFFFFFF);
                    } else if (child.getWidth() <= 3f * density && child.getWidth() > 0) {
                        child.setBackgroundColor(0x26FFFFFF);
                    }
                }
            }
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = View.MeasureSpec.getMode(widthSpec) == View.MeasureSpec.EXACTLY ? View.MeasureSpec.getSize(widthSpec) : 0;
        int height = View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.EXACTLY ? View.MeasureSpec.getSize(heightSpec) : 0;
        setMeasuredDimension(width, height);
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
        if (!MD3Progress.labelled(bar) && bar.getAlpha() != 0f) bar.setAlpha(0f);
        MD3Progress.hideSeekBars();
        if (!scrubOnly && MD3Progress.fingerDown && MD3Progress.scrubbing(getRootView())) {
            postInvalidateOnAnimation();
            return;
        }

        float mid = lineY();

        int screenH = getResources().getDisplayMetrics().heightPixels;
        int[] mine = new int[2];
        getLocationOnScreen(mine);
        float lineScreenY = mine[1] + mid;

        // Default bottom limit: keep strictly above the bottom navigation bar area (at least 64dp from screen bottom)
        float maxAllowedScreenY = screenH - dp(64f);

        // Check if bottom navigation bar is present on screen
        View navBar = findNavBar(getRootView());
        if (navBar != null && navBar.isShown() && navBar.getHeight() > 0) {
            int[] navAt = new int[2];
            navBar.getLocationOnScreen(navAt);
            float navTop = navAt[1];
            if (lineScreenY >= navTop - dp(2f)) {
                mid = navTop - mine[1] - dp(3f);
            }
            maxAllowedScreenY = Math.min(maxAllowedScreenY, (float) navAt[1] - dp(3f));
        }

        // Stop WavyBar from drawing across bottom bar: sit neatly above the navigation bar!
        if (lineScreenY > maxAllowedScreenY) {
            mid = maxAllowedScreenY - mine[1];
        }
        // Clip canvas so no strokes ever bleed down into bottom navigation bar
        float clipBottom = maxAllowedScreenY - mine[1] + dp(4f);
        if (clipBottom > 0 && clipBottom < getHeight()) {
            canvas.clipRect(0, 0, getWidth(), clipBottom);
        }

        if (!scrubOnly) {
            int[] at = new int[2];
            getLocationOnScreen(at);
            MD3Progress.lift(getRootView(), at[1] + mid - dp(5f));
        }

        boolean isScrubber = scrubOnly || (bar != null && bar.getWidth() >= getResources().getDisplayMetrics().widthPixels * 0.85f && navBar == null);
        float left = dp(16);
        float right = getWidth() - dp(16);

        if (isScrubber) {
            View[] controls = findScrubberControls(this);
            dressScrubberControls(controls[0], controls[1]);
            int[] myLoc = new int[2];
            getLocationOnScreen(myLoc);
            if (controls[0] != null && controls[0].getWidth() > 0) {
                int[] closeLoc = new int[2];
                controls[0].getLocationOnScreen(closeLoc);
                left = Math.max(dp(16), (closeLoc[0] - myLoc[0]) + controls[0].getWidth() + dp(12));
            } else {
                left = dp(56);
            }
            if (controls[1] != null && controls[1].getWidth() > 0) {
                int[] ctrlLoc = new int[2];
                controls[1].getLocationOnScreen(ctrlLoc);
                right = Math.min(getWidth() - dp(16), (ctrlLoc[0] - myLoc[0]) - dp(12));
            } else {
                right = getWidth() - dp(112);
            }
        }

        if (right <= left + dp(8)) {
            postInvalidateOnAnimation();
            return;
        }

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

        // Material 3 thin sleek 2dp track
        float line = dp(2f);
        played.setStrokeWidth(line);
        rest.setStrokeWidth(line);
        played.setColor(Accent.colour());
        rest.setColor(0x38FFFFFF);
        thumbPaint.setColor(Accent.colour());

        float x = left + Math.max(0f, Math.min(1f, shown)) * (right - left);
        long now = SystemClock.uptimeMillis();

        float thumbRadius = dp(4.5f);
        float gap = thumbRadius + dp(2f);

        // 1. Draw inactive background track (from thumb gap to right)
        if (right > x + gap) {
            canvas.drawLine(x + gap, mid, right, mid, rest);
        }
        // Stop dot at the end of the track (like in MoltenGram)
        canvas.drawCircle(right, mid, dp(1.5f), rest);

        // 2. Draw active progress (from left to thumb dot)
        float progressEnd = Math.max(left, x - gap);
        if (progressEnd > left) {
            if (!scrubbing && MD3Progress.on()) {
                float amplitude = dp(2f);
                float length = dp(22);
                float phase = (now % 1600L) / 1600f * length;
                wave.reset();
                wave.moveTo(left, mid);
                for (float px = left; px <= progressEnd; px += dp(1.5f)) {
                    float y = mid + (float) Math.sin((px - left + phase) / length * 2 * Math.PI) * amplitude;
                    wave.lineTo(px, y);
                }
                canvas.drawPath(wave, played);
            } else {
                canvas.drawLine(left, mid, progressEnd, mid, played);
            }
        }

        // 3. Draw clean thumb dot (Material 3 dot indicator)
        canvas.drawCircle(x, mid, thumbRadius, thumbPaint);
        canvas.drawCircle(x, mid, thumbRadius, thumbBorder);

        postInvalidateOnAnimation();
    }
}
