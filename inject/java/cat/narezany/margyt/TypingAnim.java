package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BlurMaskFilter;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;

public final class TypingAnim {

    static final String ON = "typing_anim", BLUR = "typing_anim_blur", SLIDE = "typing_anim_slide", SPEED = "typing_anim_speed";

    private static final long[] DURATIONS = {180L, 300L, 480L};
    private static final int TAG_MINE = 0x7f5a0121, TAG_WATCHED = 0x7f5a0122;
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

    static int speed() {
        SharedPreferences prefs = prefs();
        int at = prefs == null ? 1 : prefs.getInt(SPEED, 1);
        return Math.max(0, Math.min(DURATIONS.length - 1, at));
    }

    static void nextSpeed() {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(SPEED, (speed() + 1) % DURATIONS.length).apply();
    }

    static String speedName() {
        return new String[] {Local.of("быстро"), Local.of("плавно"), Local.of("медленно")}[speed()];
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
                        if (!on(ON) || now - lastLook < 400L) return;
                        lastLook = now;
                        find(decor, 0);
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
            if (view.getTag(TAG_WATCHED) == null) {
                view.setTag(TAG_WATCHED, Boolean.TRUE);
                ((EditText) view).addTextChangedListener(new Watcher((EditText) view));
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) find(group.getChildAt(i), depth + 1);
        }
    }

    private static final class Watcher implements TextWatcher {
        private final EditText view;
        private int start, before, count;

        Watcher(EditText view) {
            this.view = view;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int st, int c, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int st, int b, int c) {
            start = st;
            before = b;
            count = c;
        }

        @Override
        public void afterTextChanged(Editable text) {
            if (!on(ON) || count <= before || count - before > 24) return;
            int from = start + before, to = Math.min(text.length(), start + count);
            if (from >= to) return;
            long length = DURATIONS[speed()];
            text.setSpan(new Appear(SystemClock.uptimeMillis(), length, on(BLUR), on(SLIDE), view.getTextSize()),
                    from, to, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
            view.removeCallbacks(tick);
            view.postOnAnimation(tick);
        }

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                Editable text = view.getText();
                if (text == null) return;
                Appear[] spans = text.getSpans(0, text.length(), Appear.class);
                if (spans.length == 0) return;
                long now = SystemClock.uptimeMillis();
                for (Appear span : spans) if (span.done(now)) text.removeSpan(span);
                view.invalidate();
                view.postOnAnimation(this);
            }
        };
    }

    private static final class Appear extends CharacterStyle implements UpdateAppearance {
        private final long began, length;
        private final boolean blur, slide;
        private final float size;
        private BlurMaskFilter filter;
        private int filterRadius = -1;

        Appear(long began, long length, boolean blur, boolean slide, float size) {
            this.began = began;
            this.length = length;
            this.blur = blur;
            this.slide = slide;
            this.size = size;
        }

        boolean done(long now) {
            return now - began >= length;
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            float t = Math.max(0f, Math.min(1f, (SystemClock.uptimeMillis() - began) / (float) length));
            float left = 1f - t;
            float ease = 1f - left * left * left;
            paint.setAlpha(Math.round(paint.getAlpha() * ease));
            if (slide) paint.baselineShift += Math.round(size * 0.45f * left * left);
            if (blur) {
                int radius = Math.round(size * 0.22f * left);
                if (radius >= 1) {
                    if (filter == null || filterRadius != radius) {
                        filter = new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL);
                        filterRadius = radius;
                    }
                    paint.setMaskFilter(filter);
                } else {
                    paint.setMaskFilter(null);
                }
            }
        }
    }
}
