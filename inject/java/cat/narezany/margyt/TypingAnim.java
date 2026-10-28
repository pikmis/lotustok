package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;

import java.util.ArrayList;

public final class TypingAnim {

    static final String ON = "typing_anim", BLUR = "typing_anim_blur", SLIDE = "typing_anim_slide",
            SCALE = "typing_anim_scale", ROTATE = "typing_anim_rotate", GHOST = "typing_anim_ghost",
            CURSOR = "typing_anim_cursor", SPEED = "typing_anim_speed", CURVE = "typing_anim_curve";

    private static final long[] DURATIONS = {220L, 380L, 620L};
    private static final float[] TAU = {40f, 75f, 120f};
    private static final int TAG_MINE = 0x7f5a0121, TAG_WATCHED = 0x7f5a0122, TAG_CARET = 0x7f5a0123;
    private static long lastLook;

    private TypingAnim() {}

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        return context == null ? null : context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }

    static boolean on(String key) {
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.getBoolean(key, false);
    }

    static void set(String key, boolean value) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(key, value).apply();
    }

    private static int number(String key, int fallback, int count) {
        SharedPreferences prefs = prefs();
        int at = prefs == null ? fallback : prefs.getInt(key, fallback);
        return Math.max(0, Math.min(count - 1, at));
    }

    static int speed() {
        return number(SPEED, 1, DURATIONS.length);
    }

    static void nextSpeed() {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(SPEED, (speed() + 1) % DURATIONS.length).apply();
    }

    static String speedName() {
        return new String[] {Local.of("быстро"), Local.of("плавно"), Local.of("медленно")}[speed()];
    }

    static int curve() {
        return number(CURVE, 1, 3);
    }

    static void nextCurve() {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(CURVE, (curve() + 1) % 3).apply();
    }

    static String curveName() {
        return new String[] {Local.of("мягкая"), Local.of("пружина"), Local.of("резкая")}[curve()];
    }

    private static float ease(float t) {
        float left = 1f - t;
        switch (curve()) {
            case 1: {
                float c = 2.2f, u = t - 1f;
                return 1f + (c + 1f) * u * u * u + c * u * u;
            }
            case 2:
                return 1f - left * left * left * left * left;
            default:
                return 1f - left * left * left;
        }
    }

    static void attach(final Activity activity) {
        if (Version.LITE) return;
        try {
            final View decor = activity.getWindow().getDecorView();
            if (decor.getTag(TAG_MINE) != null) return;
            decor.setTag(TAG_MINE, Boolean.TRUE);
            decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    try {
                        long now = SystemClock.uptimeMillis();
                        if (now - lastLook < 300L) return;
                        lastLook = now;
                        if (on(ON) || on(CURSOR) || on(GHOST)) find(decor, 0);
                        else release(decor, 0);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable error) {
            Diary.note("typing animation attach: " + error);
        }
    }

    private static void find(View view, int depth) {
        if (depth > 60 || view == null) return;
        if (view instanceof EditText) {
            EditText edit = (EditText) view;
            if (view.getTag(TAG_WATCHED) == null) {
                Watcher watcher = new Watcher(edit);
                view.setTag(TAG_WATCHED, watcher);
                edit.addTextChangedListener(watcher);
                edit.getOverlay().add(watcher.layer);
            }
            boolean glide = on(ON) && on(CURSOR);
            if (glide != (view.getTag(TAG_CARET) != null)) {
                view.setTag(TAG_CARET, glide ? Boolean.TRUE : null);
                edit.setCursorVisible(!glide);
            }
            if (glide || ((Watcher) view.getTag(TAG_WATCHED)).layer.busy()) view.postInvalidateOnAnimation();
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) find(group.getChildAt(i), depth + 1);
        }
    }

    private static void release(View view, int depth) {
        if (depth > 60 || view == null) return;
        if (view instanceof EditText) {
            if (view.getTag(TAG_CARET) != null) {
                view.setTag(TAG_CARET, null);
                ((EditText) view).setCursorVisible(true);
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) release(group.getChildAt(i), depth + 1);
        }
    }

    private static final class Watcher implements TextWatcher {
        private final EditText view;
        final Layer layer;
        private int start, before, count;

        Watcher(EditText view) {
            this.view = view;
            this.layer = new Layer(view);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int st, int c, int after) {
            if (!on(ON) || !on(GHOST) || c <= after || c - after > 24) return;
            try {
                layer.leave(s.subSequence(st + after, st + c).toString(), st + after);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int st, int b, int c) {
            start = st;
            before = b;
            count = c;
        }

        @Override
        public void afterTextChanged(Editable text) {
            view.postInvalidateOnAnimation();
            if (!on(ON) || count <= before || count - before > 24) return;
            int from = start + before, to = Math.min(text.length(), start + count);
            if (from >= to) return;
            text.setSpan(new Pop(SystemClock.uptimeMillis(), DURATIONS[speed()], view.getTextSize(), view.getPaint()),
                    from, to, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
            view.removeCallbacks(tick);
            view.postOnAnimation(tick);
        }

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                Editable text = view.getText();
                if (text == null) return;
                Pop[] spans = text.getSpans(0, text.length(), Pop.class);
                if (spans.length == 0) return;
                long now = SystemClock.uptimeMillis();
                for (Pop span : spans) if (span.done(now)) text.removeSpan(span);
                view.invalidate();
                view.postOnAnimation(this);
            }
        };
    }

    private static final class Pop extends ReplacementSpan {
        private final long began, length;
        private final float size;
        private final Paint draw = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float tilt;
        private BlurMaskFilter filter;
        private int filterRadius = -1;

        Pop(long began, long length, float size, Paint like) {
            this.began = began;
            this.length = length;
            this.size = size;
            this.tilt = (began % 2 == 0 ? 1f : -1f) * (6f + (began % 7));
        }

        boolean done(long now) {
            return now - began >= length;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) paint.getFontMetricsInt(fm);
            return Math.round(paint.measureText(text, start, end));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            float t = Math.max(0f, Math.min(1f, (SystemClock.uptimeMillis() - began) / (float) length));
            float e = ease(t);
            float calm = Math.max(0f, Math.min(1f, t * 1.6f));
            float width = paint.measureText(text, start, end);
            draw.set(paint);
            draw.setAlpha(Math.round(paint.getAlpha() * (1f - (1f - calm) * (1f - calm))));
            if (on(BLUR)) {
                int radius = Math.round(size * 0.2f * (1f - calm));
                if (radius >= 1) {
                    if (filter == null || filterRadius != radius) {
                        filter = new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL);
                        filterRadius = radius;
                    }
                    draw.setMaskFilter(filter);
                } else {
                    draw.setMaskFilter(null);
                }
            }
            canvas.save();
            canvas.translate(x + width / 2f, y);
            if (on(SLIDE)) canvas.translate(0f, size * 0.55f * (1f - e));
            if (on(SCALE)) {
                float s = 0.4f + 0.6f * e;
                canvas.scale(s, s);
            }
            if (on(ROTATE)) canvas.rotate(tilt * (1f - e));
            canvas.drawText(text, start, end, -width / 2f, 0f, draw);
            canvas.restore();
        }
    }

    private static final class Layer extends Drawable {
        private final EditText view;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final ArrayList<Gone> gone = new ArrayList<Gone>();
        private float x, y, h;
        private boolean placed;
        private long last, moved;
        private float tx = -1, ty = -1;

        Layer(EditText view) {
            this.view = view;
            setBounds(0, 0, 20000, 20000);
        }

        boolean busy() {
            return !gone.isEmpty();
        }

        private static final class Gone {
            final String text;
            final float x, y, size;
            final long began = SystemClock.uptimeMillis();

            Gone(String text, float x, float y, float size) {
                this.text = text;
                this.x = x;
                this.y = y;
                this.size = size;
            }
        }

        void leave(String text, int at) {
            Layout layout = view.getLayout();
            if (layout == null || text.length() == 0 || at < 0 || at > view.length()) return;
            int line = layout.getLineForOffset(at);
            float gx = layout.getPrimaryHorizontal(at) + view.getCompoundPaddingLeft() - view.getScrollX();
            float gy = layout.getLineBaseline(line) + view.getExtendedPaddingTop() - view.getScrollY();
            gone.add(new Gone(text, gx, gy, view.getTextSize()));
            if (gone.size() > 40) gone.remove(0);
            view.postInvalidateOnAnimation();
        }

        @Override
        public void draw(Canvas canvas) {
            long now = SystemClock.uptimeMillis();
            boolean again = false;
            for (int i = gone.size() - 1; i >= 0; i--) {
                Gone g = gone.get(i);
                float t = (now - g.began) / (float) DURATIONS[speed()];
                if (t >= 1f) {
                    gone.remove(i);
                    continue;
                }
                again = true;
                float e = 1f - (1f - t) * (1f - t);
                paint.reset();
                paint.setAntiAlias(true);
                paint.set(view.getPaint());
                paint.setColor(view.getCurrentTextColor());
                paint.setAlpha(Math.round(255 * (1f - t) * (1f - t)));
                if (on(BLUR)) paint.setMaskFilter(new BlurMaskFilter(Math.max(1f, g.size * 0.2f * e), BlurMaskFilter.Blur.NORMAL));
                float width = paint.measureText(g.text);
                canvas.save();
                canvas.translate(g.x + width / 2f, g.y - g.size * 0.4f * e);
                float s = 1f - 0.4f * e;
                canvas.scale(s, s);
                canvas.drawText(g.text, -width / 2f, 0f, paint);
                canvas.restore();
            }
            if (on(ON) && on(CURSOR) && view.hasFocus() && view.getLayout() != null) {
                cursor(canvas, now);
                again = true;
            } else {
                placed = false;
            }
            if (again) view.postInvalidateOnAnimation();
        }

        private void cursor(Canvas canvas, long now) {
            Layout layout = view.getLayout();
            int from = view.getSelectionStart(), to = view.getSelectionEnd();
            if (to < 0 || from != to) return;
            int line = layout.getLineForOffset(to);
            float goalX = layout.getPrimaryHorizontal(to) + view.getCompoundPaddingLeft() - view.getScrollX();
            float goalY = layout.getLineTop(line) + view.getExtendedPaddingTop() - view.getScrollY();
            float goalH = layout.getLineBottom(line) - layout.getLineTop(line);
            long dt = Math.max(1L, Math.min(50L, now - last));
            last = now;
            if (!placed) {
                x = goalX;
                y = goalY;
                h = goalH;
                placed = true;
                moved = now;
            }
            float k = 1f - (float) Math.exp(-dt / TAU[speed()]);
            x += (goalX - x) * k;
            y += (goalY - y) * k;
            h += (goalH - h) * k;
            if (Math.abs(goalX - tx) > 0.5f || Math.abs(goalY - ty) > 0.5f) {
                tx = goalX;
                ty = goalY;
                moved = now;
            }
            float idle = (now - moved) / 1000f;
            float alpha = idle < 0.5f ? 1f : 0.5f + 0.5f * (float) Math.cos((idle - 0.5f) * 2.0 * Math.PI / 1.1);
            paint.reset();
            paint.setAntiAlias(true);
            paint.setColor(Accent.colour() | 0xFF000000);
            paint.setAlpha(Math.round(255 * Math.max(0.15f, alpha)));
            float w = view.getResources().getDisplayMetrics().density * 2.2f;
            rect.set(x - w / 2f, y + h * 0.08f, x + w / 2f, y + h * 0.92f);
            canvas.drawRoundRect(rect, w, w, paint);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
