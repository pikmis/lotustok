package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * The like and dislike under a comment, as two chevrons: up for the like, down
 * for the dislike, and the count of likes beside them. Off until it is switched
 * on in the settings, under the accent.
 *
 * TikTok's own heart and thumb stay where they are, but are made see-through
 * and whatever TikTok puts in them later (the blue ones, once a vote is cast)
 * stays out of sight too. The chevron is the icon's foreground, drawn over
 * them. Its colour is that of TikTok's like count beside it, which turns blue
 * when the comment is liked.
 */
final class CommentVotes {

    static final String KEY = "comment_chevrons";

    /** TikTok's own like and dislike buttons, by the names of their resources, and the count beside the like. */
    private static final String LIKE_BUTTON = "nd7", DISLIKE_BUTTON = "k0p", COUNT = "tv_like_count";
    private static final int INK = 0xFF8A8A8A;

    private static volatile Boolean on;
    private static long swept;

    private CommentVotes() {}

    static boolean isEnabled() {
        Boolean known = on;
        if (known != null) return known.booleanValue();
        Context context = Margy.context();
        boolean value = context != null && context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY, false);
        on = Boolean.valueOf(value);
        return value;
    }

    static void setEnabled(boolean value) {
        on = Boolean.valueOf(value);
        Context context = Margy.context();
        if (context != null) context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, value).apply();
    }

    /** Keeps watching the screen the comments are on, so each comment that comes up gets its chevrons. */
    static void attach(Activity activity) {
        if (activity == null) return;
        final View root = activity.getWindow().getDecorView();
        // every frame, not only on layout: TikTok puts its own icon back when a vote is cast, and nothing lays out then
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                try {
                    if (isEnabled()) sweep(root, 0);
                } catch (Throwable ignored) {
                }
                return true;
            }
        });
    }

    private static void sweep(View view, int depth) {
        if (view == null || depth > 60) return;
        if (depth == 0) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - swept < 200L) return;
            swept = now;
        }
        if (view instanceof ImageView) {
            // TikTok gives the icon another id once a vote is cast, but the button around it keeps its own
            String button = view.getParent() instanceof View ? name((View) view.getParent()) : null;
            boolean like = LIKE_BUTTON.equals(button);
            if (like || DISLIKE_BUTTON.equals(button)) {
                ImageView icon = (ImageView) view;
                // whatever TikTok puts in the icon, now or later (the blue one after a vote too), is swapped for nothing
                if (!(icon.getDrawable() instanceof android.graphics.drawable.ColorDrawable)) {
                    icon.setImageDrawable(new android.graphics.drawable.ColorDrawable(0));
                }
                if (!(icon.getForeground() instanceof Chevron)) icon.setForeground(new Chevron(icon, like));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) sweep(group.getChildAt(i), depth + 1);
        }
    }

    /** The colour of TikTok's own like count beside the icon, so the chevron reads in light and dark and turns when voted. */
    private static int ink(View icon) {
        if (icon.getParent() instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) icon.getParent();
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof TextView && COUNT.equals(name(child))) {
                    return ((TextView) child).getCurrentTextColor();
                }
            }
        }
        return INK;
    }

    /** The ids TikTok gives the heart and the thumb once the vote is cast: the like and the dislike. */
    private static final String LIKED = "maq", DISLIKED = "m8g";

    private static boolean voted(View icon) {
        String name = name(icon);
        return LIKED.equals(name) || DISLIKED.equals(name);
    }

    /** Whether TikTok has turned the icon over: its dislike is its thumb-up the other way up. */
    private static boolean flipped(View icon) {
        boolean flip = false;
        View at = icon;
        for (int i = 0; i < 3 && at != null; i++) {
            float turn = ((at.getRotation() % 360f) + 360f) % 360f;
            if (Math.abs(turn - 180f) < 1f) flip = !flip;
            if (at.getScaleY() < 0f) flip = !flip;
            at = at.getParent() instanceof View ? (View) at.getParent() : null;
        }
        return flip;
    }

    private static String name(View view) {
        int id = view.getId();
        if (id == View.NO_ID || view.getResources() == null) return null;
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (Throwable error) {
            return null;
        }
    }

    /** A chevron as a foreground: up for the like, down for the dislike, whichever way TikTok has turned the icon. */
    private static final class Chevron extends Drawable {
        private final View icon;
        private final boolean up;
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);

        Chevron(View icon, boolean up) {
            this.icon = icon;
            this.up = up;
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            // the size comes from the icon's height, not its width: the like and the dislike are not the same width
            float unit = icon.getHeight() / 32f;
            if (unit <= 0f) return;
            boolean pointsUp = up != flipped(icon);
            ink.setColor(voted(icon) ? Accent.colour() : ink(icon));
            ink.setStrokeWidth(2.8f * unit);
            int saved = canvas.save();
            canvas.translate(getBounds().exactCenterX() - 16f * unit, getBounds().exactCenterY() - 16f * unit);
            // up: the point is at the top; down: the point is at the bottom
            float near = pointsUp ? 19f : 12f, far = pointsUp ? 12f : 19f;
            // one outline, not two lines: where they meet, two translucent strokes would show a darker dot
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(9 * unit, near * unit);
            path.lineTo(16 * unit, far * unit);
            path.lineTo(23 * unit, near * unit);
            canvas.drawPath(path, ink);
            canvas.restoreToCount(saved);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
