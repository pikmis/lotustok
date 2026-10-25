package cat.narezany.margyt;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.reflect.Field;

/**
 * The line along the bottom of a video, as Material 3's wavy progress
 * indicator. Off until it is switched on in the settings, under the accent.
 *
 * TikTok's own line is a FrameLayout three pixels high with two one-pixel
 * views inside. They are found by their fields, hidden, and a WavyBar is put
 * beside them that draws the wave from the video's progress. Switched off
 * again, the bar takes itself away and TikTok's own line is as it was.
 */
final class MD3Progress {

    static final String KEY = "md3_progress";

    private static final String BAR = "com.ss.android.ugc.aweme.feed.widget.LineProgressBar";

    /** The lines already done: a view is remembered for as long as it lives, and no longer. */
    private static final java.util.WeakHashMap<View, Boolean> done = new java.util.WeakHashMap<View, Boolean>();

    private MD3Progress() {}

    static void set(boolean value) {
        android.content.Context context = Margy.context();
        if (context != null) context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, value).apply();
    }

    static boolean on() {
        android.content.Context context = Margy.context();
        return context != null && context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY, false);
    }

    /** A bar that took itself away: it may be put in again if the switch comes back on. */
    static void forget(View bar) {
        synchronized (done) {
            done.remove(bar);
        }
    }

    static void attach(Activity activity) {
        if (activity == null) return;
        final View root = activity.getWindow().getDecorView();
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                try {
                    if (on()) style(root, 0);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void style(View view, int depth) {
        if (depth > 50 || view == null) return;
        if (BAR.equals(view.getClass().getName())) {
            // a story has a line of this class too, and it says how far the story has got: it is left alone
            if (!inStory(view)) apply(view);
            return;
        }
        if (isSeekBar(view)) {
            if (!inStory(view)) {
                synchronized (seekBars) {
                    seekBars.put(view, Boolean.TRUE);
                }
                if (view.getAlpha() != 0f) view.setAlpha(0f);
                // in the feed the wave is on each page's thin line; elsewhere there is no thin line,
                // and the wave goes over the seek bar
                if (!under(view, TAB_HOST)) applyOver(view);
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) style(group.getChildAt(i), depth + 1);
        }
    }

    private static void apply(View bar) {
        synchronized (done) {
            if (done.containsKey(bar)) return;
            done.put(bar, Boolean.TRUE);
        }
        try {
            View track = (View) field(bar, "LL");
            View fill = (View) field(bar, "LLJJIII");
            if (track == null || fill == null || !(bar instanceof FrameLayout)) {
                forget(bar);
                return;
            }
            FrameLayout frame = (FrameLayout) bar;
            // TikTok's own two views are not drawn; they stay where they are
            track.setAlpha(0f);
            fill.setAlpha(0f);
            // the seek bar beside it holds the progress, and only moves when it is shown
            if (!Seekbar.isEnabled()) Seekbar.setEnabled(true);
            // the wave is taller than the line it replaces, so nothing above may clip it
            frame.setClipChildren(false);
            frame.setClipToPadding(false);
            android.view.ViewParent parent = frame.getParent();
            for (int i = 0; i < 4 && parent instanceof ViewGroup; i++) {
                ((ViewGroup) parent).setClipChildren(false);
                parent = parent.getParent();
            }
            ViewGroup home = bar.getParent() instanceof FrameLayout ? (ViewGroup) bar.getParent() : frame;
            WavyBar wavy = new WavyBar(bar.getContext(), bar, track, fill);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.round(dp(bar, 20)));
            lp.gravity = android.view.Gravity.CENTER_VERTICAL;
            home.setClipChildren(false);
            home.addView(wavy, lp);
            wavy.setTranslationY(-dp(bar, 5));
        } catch (Throwable error) {
            forget(bar);
        }
    }

    /** A video opened from a profile, the favourites, the reposts or a chat: the wave over its seek bar. */
    private static void applyOver(View seek) {
        synchronized (done) {
            if (done.containsKey(seek)) return;
            done.put(seek, Boolean.TRUE);
        }
        try {
            if (!(seek.getParent() instanceof FrameLayout)) {
                forget(seek);
                return;
            }
            if (!Seekbar.isEnabled()) Seekbar.setEnabled(true);
            FrameLayout home = (FrameLayout) seek.getParent();
            home.setClipChildren(false);
            home.setClipToPadding(false);
            android.view.ViewParent parent = home.getParent();
            for (int i = 0; i < 4 && parent instanceof ViewGroup; i++) {
                ((ViewGroup) parent).setClipChildren(false);
                parent = parent.getParent();
            }
            WavyBar wavy = new WavyBar(seek.getContext(), seek, null, null);
            home.addView(wavy, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable error) {
            forget(seek);
        }
    }

    /** TikTok's seek bars, hidden while the wave is on: the view that answers `getMProgress`. */
    private static final java.util.WeakHashMap<View, Boolean> seekBars = new java.util.WeakHashMap<View, Boolean>();
    private static final java.util.HashMap<Class<?>, Boolean> seekClasses = new java.util.HashMap<Class<?>, Boolean>();
    private static final String TAB_HOST = "com.ss.android.ugc.aweme.ui.FragmentTabHost";

    private static boolean isSeekBar(View view) {
        Class<?> type = view.getClass();
        Boolean known = seekClasses.get(type);
        if (known == null) {
            boolean answers = false;
            try {
                answers = type.getMethod("getMProgress") != null;
            } catch (Throwable ignored) {
            }
            known = answers;
            seekClasses.put(type, known);
        }
        return known;
    }

    /** TikTok fades its seek bars in again by itself: asked every frame by a wave. */
    static void hideSeekBars() {
        synchronized (seekBars) {
            for (View seek : seekBars.keySet()) if (seek.getAlpha() != 0f) seek.setAlpha(0f);
        }
    }

    /** The wave is switched off: the seek bars are TikTok's again. */
    static void showSeekBars() {
        synchronized (seekBars) {
            for (View seek : seekBars.keySet()) seek.setAlpha(1f);
            seekBars.clear();
        }
    }

    /** A seek bar on the screen of this wave, for how far the video has got when the player cannot say. */
    static View seekBar(View wave) {
        synchronized (seekBars) {
            for (View seek : seekBars.keySet()) {
                if (seek.getRootView() == wave.getRootView() && seek.getVisibility() == View.VISIBLE && seek.getWidth() > 0) return seek;
            }
        }
        return null;
    }

    private static boolean under(View view, String type) {
        android.view.ViewParent parent = view.getParent();
        for (int i = 0; i < 30 && parent != null; i++) {
            if (type.equals(parent.getClass().getName())) return true;
            parent = parent.getParent();
        }
        return false;
    }

    /** Whether a view is part of a story: its parents are TikTok's story classes or have story ids. */
    private static boolean inStory(View view) {
        android.view.ViewParent parent = view.getParent();
        for (int i = 0; i < 30 && parent != null; i++) {
            if (parent.getClass().getName().contains(".story.")) return true;
            if (parent instanceof View && ((View) parent).getId() != View.NO_ID) {
                try {
                    if (((View) parent).getResources().getResourceEntryName(((View) parent).getId()).contains("story")) return true;
                } catch (Throwable ignored) {
                }
            }
            parent = parent.getParent();
        }
        return false;
    }

    /** The caption lifted: the container under a video that holds the author, the words, the translate button and the sound. */
    private static java.util.ArrayList<View> lifted = new java.util.ArrayList<View>();
    /** When each wave last had the caption above it looked at, by where the wave is: every page has one, and they are not asked in turn. */
    private static final java.util.HashMap<Integer, Long> liftedAt = new java.util.HashMap<Integer, Long>();
    private static int captionId = -1, descId = -1;

    /**
     * Lifts the caption just far enough that nothing of it is over the wave.
     * `waveTop` is where the top of the wave is on the screen. The text of a
     * caption grows upward from the same place whatever its length, so how far
     * it has to go is read off the text, every time, and no more than that:
     * a short caption is not lifted at all. Asked a few times a second.
     */
    static void lift(View root, float waveTop) {
        long now = android.os.SystemClock.uptimeMillis();
        if (root == null) return;
        Integer slot = Math.round(waveTop);
        Long before = liftedAt.get(slot);
        if (before != null && now - before < 200L) return;
        if (liftedAt.size() > 16) liftedAt.clear();
        liftedAt.put(slot, now);
        if (captionId == -1) {
            captionId = root.getResources().getIdentifier("bqx", "id", root.getContext().getPackageName());
        }
        if (captionId == 0) return;
        java.util.ArrayList<View> hosts = new java.util.ArrayList<View>();
        collect(root, 0, hosts);
        float unit = root.getResources().getDisplayMetrics().density;
        for (View host : hosts) {
            float bottom = textBottom(host, 0) - host.getTranslationY();
            if (bottom <= 0f) {
                if (host.getTranslationY() != 0f) host.setTranslationY(0f);
                continue;
            }
            // each wave is for the caption of its own page: the others (the next video's, a screen
            // below) are theirs to look after, and are left as they are
            if (Math.abs(bottom - waveTop) > 160f * unit) continue;
            // the card is a little below the words, and a little more is left between it and the wave
            float need = bottom - (waveTop - 9f * unit);
            float shift = Math.max(0f, Math.min(need, 48f * unit));
            if (host.getTranslationY() != -shift) host.setTranslationY(-shift);
            if (!lifted.contains(host)) {
                if (lifted.size() > 6) lifted.remove(0);
                lifted.add(host);
            }
        }
    }

    /** The wave is switched off, or gone: the lifted caption is put back where it was. */
    static void unlift() {
        for (View old : lifted) old.setTranslationY(0f);
        lifted = new java.util.ArrayList<View>();
    }

    private static void collect(View view, int depth, java.util.ArrayList<View> into) {
        if (depth > 40 || view == null || view.getVisibility() != View.VISIBLE) return;
        if (view.getId() == captionId) {
            into.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), depth + 1, into);
        }
    }

    /** The lowest edge, on the screen, of any words in a view; nought when there are none. */
    private static float textBottom(View view, int depth) {
        if (depth > 12 || view == null || view.getVisibility() != View.VISIBLE) return 0f;
        float low = 0f;
        // the words of a caption are drawn by a view of TikTok's own, which is not a TextView
        if (!(view instanceof ViewGroup) && !(view instanceof android.widget.TextView) && view.getId() != View.NO_ID && view.getHeight() > 0) {
            if (descId == -1) descId = view.getResources().getIdentifier("desc", "id", view.getContext().getPackageName());
            if (view.getId() == descId) {
                int[] at = new int[2];
                view.getLocationOnScreen(at);
                low = at[1] + view.getHeight();
            }
        }
        if (view instanceof android.widget.TextView) {
            CharSequence text = ((android.widget.TextView) view).getText();
            if (text != null && text.toString().trim().length() > 0 && view.getHeight() > 0) {
                int[] at = new int[2];
                view.getLocationOnScreen(at);
                low = at[1] + view.getHeight();
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) low = Math.max(low, textBottom(group.getChildAt(i), depth + 1));
        }
        return low;
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static float dp(View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }
}
