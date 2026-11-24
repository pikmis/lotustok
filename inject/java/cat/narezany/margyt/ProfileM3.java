package cat.narezany.margyt;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The signed-in profile in Material 3 Expressive's shapes.
 *
 * TikTok's own profile is left as it is and its views are rearranged where they stand: the
 * picture goes to the left of the name, in a wavy shape, with the description under the name; the
 * three numbers leave their one slab and become three cards across the width; below them come the
 * two buttons of the design, a large filled one that opens the editor the pencil opens and a tonal
 * one that shares the profile, joined into one group; the chip, the tabs in a capsule with a pill
 * on the chosen one, and a grid with a gap between its pictures; and the icons of the bar at the
 * top lose the discs they lay on. The photograph the page keeps behind the head is put away.
 *
 * Colours are Material 3's roles, taken from the page the profile is on and from the accent:
 * the accent itself only on the main button, a pale container of it on the share button and the
 * chosen tab, a step up from the page for the cards and the capsule.
 *
 * Nothing here is known by a name of TikTok's code. The views are found by the ids of this version
 * of the app, one at a time, and a piece that is not where it was is left alone.
 */
final class ProfileM3 {

    private ProfileM3() {}

    static final String KEY = "profile_m3";
    private static final int MINE = 0x7e0c0337, CARD = 0x7e0c0338, ICON = 0x7e0c0339, SKIN = 0x7e0c033a,
            SKIN_AT = 0x7e0c033b, GROUP = 0x7e0c033c, MOVED = 0x7e0c033d;
    private static final int STAT = 0x7e0c0370, NAME_AT = 0x7e0c0371, MINE_TAG = 0x7e0c0372,
            GRID = 0x7e0c0373;

    // Caches for the work that used to be redone on every pass. All are touched on the UI thread only.
    // Resource ids and entry names are stable for the life of the install.
    private static final java.util.HashMap<String, Integer> IDS = new java.util.HashMap<String, Integer>();
    private static final java.util.HashMap<Integer, String> NAMES = new java.util.HashMap<Integer, String>();
    // The palette depends only on the page colour and the accent, both stable while scrolling: a small
    // LRU keyed on them removes the mixes, the readable loops and the HSV turn from every pass.
    private static final java.util.LinkedHashMap<Long, Roles> ROLES_CACHE =
            new java.util.LinkedHashMap<Long, Roles>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long, Roles> eldest) {
                    return size() > 8;
                }
            };
    // Discovered views, so the ancestor climbs and the full-page walk are not repeated every pass.
    private static final java.util.WeakHashMap<View, ViewGroup> PAGES = new java.util.WeakHashMap<View, ViewGroup>();
    private static final java.util.WeakHashMap<View, java.util.HashMap<String, java.lang.ref.WeakReference<View>>> ROOTS =
            new java.util.WeakHashMap<View, java.util.HashMap<String, java.lang.ref.WeakReference<View>>>();
    // Reflection looked up once per class instead of on every time-gated re-tint.
    private static final java.util.HashMap<Class<?>, java.lang.reflect.Method> TINT_METHODS =
            new java.util.HashMap<Class<?>, java.lang.reflect.Method>();
    private static final java.util.HashMap<String, java.lang.reflect.Method> TAB_METHODS =
            new java.util.HashMap<String, java.lang.reflect.Method>();
    private static final android.graphics.drawable.ColorDrawable NO_INDICATOR = new android.graphics.drawable.ColorDrawable(0);
    // One coalesced apply per head: a burst of layout/pre-draw events gives a leading and a trailing
    // pass, not a full dress every frame (the same shape as Md3.Screen's quiet window).
    private static final long APPLY_WINDOW = 150L;
    private static final android.os.Handler APPLY_HANDLER = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final java.util.WeakHashMap<View, long[]> APPLY_GATE = new java.util.WeakHashMap<View, long[]>();
    private static Boolean enabledCache;

    static boolean isEnabled() {
        if (Version.LITE) return false;
        if (enabledCache != null) return enabledCache;
        SharedPreferences prefs = prefs();
        boolean on = prefs == null || prefs.getBoolean(KEY, true);
        enabledCache = on;
        return on;
    }

    static void setEnabled(boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY, on).apply();
        enabledCache = on;
    }

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        if (context == null) return null;
        try {
            return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ the colours

    /** Material 3's roles, for the page a view lies on. */
    static final class Roles {
        int ground, ink, primary, onPrimary, container, high, secondary, onSecondary, variant, outline;
        int tertiaryContainer, error, primaryContainer;
        boolean dark;
    }

    static Roles roles(View view) {
        return rolesFor(Md3.behind(view) | 0xFF000000);
    }

    /** The same roles for a page of this colour. */
    static Roles rolesFor(int ground) {
        long key = ((long) ground << 32) | (Accent.colour() & 0xFFFFFFFFL);
        Roles cached = ROLES_CACHE.get(key);
        if (cached != null) return cached;
        Roles r = new Roles();
        r.ground = ground | 0xFF000000;
        r.ink = Md3.ink(r.ground);
        r.dark = NavBar.bright(r.ground) < 0.5f;
        r.primary = Accent.colour() | 0xFF000000;
        r.onPrimary = NavBar.contrast(0xFFFFFFFF, r.primary) >= NavBar.contrast(0xFF161823, r.primary)
                ? 0xFFFFFFFF : 0xFF161823;
        r.container = NavBar.mix(r.ground, r.ink, r.dark ? 0.055f : 0.045f);
        r.high = NavBar.mix(r.ground, r.ink, r.dark ? 0.10f : 0.07f);
        r.secondary = NavBar.mix(r.ground, r.primary, r.dark ? 0.30f : 0.17f);
        r.primaryContainer = NavBar.mix(r.ground, r.primary, r.dark ? 0.38f : 0.26f);
        r.onSecondary = NavBar.readable(NavBar.mix(r.ink, r.primary, 0.20f), r.secondary, r.ink, 4.5f);
        r.variant = NavBar.readable(NavBar.mix(r.ground, r.ink, r.dark ? 0.72f : 0.74f), r.ground, r.ink, 4.5f);
        r.outline = NavBar.mix(r.ground, r.ink, r.dark ? 0.26f : 0.20f);
        // the tertiary of the accent: its hue turned a fifth of the way round the wheel, as Material 3 derives it
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(r.primary, hsv);
        hsv[0] = (hsv[0] + 60f) % 360f;
        int tertiary = android.graphics.Color.HSVToColor(hsv) | 0xFF000000;
        r.tertiaryContainer = NavBar.mix(r.ground, tertiary, r.dark ? 0.32f : 0.24f);
        r.error = r.dark ? 0xFFF2B8B5 : 0xFFB3261E;
        ROLES_CACHE.put(key, r);
        return r;
    }

    // ------------------------------------------------------------------ finding views

    private static int id(View view, String name) {
        Integer cached = IDS.get(name);
        if (cached != null) return cached;
        int found = view.getResources().getIdentifier(name, "id", view.getContext().getPackageName());
        IDS.put(name, found);
        return found;
    }

    /** The nearest ancestor that holds the head of this profile: the same profile's views, not another page's. */
    private static ViewGroup page(View anchor) {
        ViewGroup cached = PAGES.get(anchor);
        if (cached != null && cached.isAttachedToWindow() && above(anchor, cached)) return cached;
        int head = id(anchor, "t7l");
        if (head == 0) return null;
        View up = anchor;
        for (int depth = 0; up != null && depth < 24; depth++) {
            if (up.getId() == head && up instanceof ViewGroup) {
                PAGES.put(anchor, (ViewGroup) up);
                return (ViewGroup) up;
            }
            View found = up.findViewById(head);
            if (found instanceof ViewGroup) {
                PAGES.put(anchor, (ViewGroup) found);
                return (ViewGroup) found;
            }
            android.view.ViewParent next = up.getParent();
            up = next instanceof View ? (View) next : null;
        }
        return null;
    }

    /** Whether maybe is an ancestor of view: a cheap climb, no subtree search. */
    private static boolean above(View view, View maybe) {
        View up = view;
        for (int depth = 0; up != null && depth < 24; depth++) {
            if (up == maybe) return true;
            android.view.ViewParent next = up.getParent();
            up = next instanceof View ? (View) next : null;
        }
        return false;
    }

    private static View root(View head, String name) {
        int want = id(head, name);
        if (want == 0) return null;
        java.util.HashMap<String, java.lang.ref.WeakReference<View>> byName = ROOTS.get(head);
        if (byName != null) {
            java.lang.ref.WeakReference<View> ref = byName.get(name);
            View cached = ref == null ? null : ref.get();
            if (cached != null && cached.isAttachedToWindow() && cached.getId() == want) return cached;
        }
        View up = head;
        for (int depth = 0; up != null && depth < 24; depth++) {
            View found = up.findViewById(want);
            if (found != null) {
                if (byName == null) {
                    byName = new java.util.HashMap<String, java.lang.ref.WeakReference<View>>();
                    ROOTS.put(head, byName);
                }
                byName.put(name, new java.lang.ref.WeakReference<View>(found));
                return found;
            }
            android.view.ViewParent next = up.getParent();
            up = next instanceof View ? (View) next : null;
        }
        return null;
    }

    private static int dp(float unit, float value) {
        return Math.round(value * unit);
    }

    // ------------------------------------------------------------------ small helpers

    /** An icon of TikTok's Tux set takes its colour through its own method; any other image through a filter. */
    private static void tint(View view, int colour) {
        long now = SystemClock.uptimeMillis();
        Object known = view.getTag(SKIN), at = view.getTag(SKIN_AT);
        if (known instanceof Integer && ((Integer) known).intValue() == colour
                && at instanceof Long && now - (Long) at < 1000L) return;
        view.setTag(SKIN, Integer.valueOf(colour));
        view.setTag(SKIN_AT, Long.valueOf(now));
        Class<?> cls = view.getClass();
        java.lang.reflect.Method m = TINT_METHODS.get(cls);
        if (m == null) {
            try {
                m = cls.getMethod("setTintColor", int.class);
            } catch (Throwable noMethod) {
                m = null;
            }
            TINT_METHODS.put(cls, m);
        }
        if (m != null) {
            try {
                m.invoke(view, colour);
            } catch (Throwable ignored) {
                if (view instanceof ImageView) ((ImageView) view).setColorFilter(colour);
            }
        } else if (view instanceof ImageView) {
            ((ImageView) view).setColorFilter(colour);
        }
    }

    private static void write(TextView text, float sp, int colour, boolean bold) {
        if (text == null) return;
        float want = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, text.getResources().getDisplayMetrics());
        if (Math.abs(text.getTextSize() - want) > 0.5f) text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (colour != 0 && text.getCurrentTextColor() != colour) text.setTextColor(colour);
        if (bold && (text.getTypeface() == null || !text.getTypeface().isBold())) {
            text.setTypeface(Typeface.create(text.getTypeface(), Typeface.BOLD));
        }
    }

    private static void size(View view, int width, int height) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null && (lp.width != width || lp.height != height)) {
            lp.width = width;
            lp.height = height;
            view.setLayoutParams(lp);
        }
    }

    private static void margins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
        if (m.leftMargin != left || m.topMargin != top || m.rightMargin != right || m.bottomMargin != bottom) {
            m.setMargins(left, top, right, bottom);
            view.setLayoutParams(m);
        }
    }

    private static void pad(View view, int left, int top, int right, int bottom) {
        if (view.getPaddingLeft() != left || view.getPaddingTop() != top
                || view.getPaddingRight() != right || view.getPaddingBottom() != bottom) {
            view.setPadding(left, top, right, bottom);
        }
    }

    private static GradientDrawable fill(int colour, float[] radii) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(colour);
        shape.setCornerRadii(radii);
        return shape;
    }

    private static float[] corners(float unit, float topLeft, float topRight, float bottomRight, float bottomLeft) {
        return new float[]{topLeft * unit, topLeft * unit, topRight * unit, topRight * unit,
                bottomRight * unit, bottomRight * unit, bottomLeft * unit, bottomLeft * unit};
    }

    private static android.graphics.drawable.Drawable ripple(int colour, android.graphics.drawable.Drawable content, boolean round) {
        android.graphics.drawable.Drawable mask = round ? new ShapeDrawable(new OvalShape()) : null;
        return new RippleDrawable(ColorStateList.valueOf((colour & 0x00FFFFFF) | 0x2A000000), content,
                content == null ? mask : null);
    }

    // ------------------------------------------------------------------ the wavy shape

    /**
     * A shape drawn by a radius that goes round: `waves` of them, `depth` deep, the first turned by
     * `turn`. Smooth at any size, being a path of the picture's own size and not a picture.
     */
    static ViewOutlineProvider polar(final int waves, final double depth, final double turn) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth(), h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                Path path = polarPath(waves, depth, turn, w, h);
                if (android.os.Build.VERSION.SDK_INT >= 30) outline.setPath(path);
                else outline.setOval(0, 0, w, h);
            }
        };
    }

    /** The path of a polar shape in a box of this size. */
    static Path polarPath(int waves, double depth, double turn, int w, int h) {
        Path path = new Path();
        for (int i = 0; i <= 240; i++) {
            double t = i * 2 * Math.PI / 240;
            double r = 0.5 * (1 + depth * Math.cos(waves * (t + turn))) / (1 + depth);
            float x = (float) (w * (0.5 + r * Math.cos(t))), y = (float) (h * (0.5 + r * Math.sin(t)));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        return path;
    }

    /** The shape the person has chosen for avatars in the settings, whatever it is. */
    static final ViewOutlineProvider AVATAR = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            int w = view.getWidth(), h = view.getHeight();
            if (w <= 0 || h <= 0) return;
            android.graphics.Matrix fit = new android.graphics.Matrix();
            fit.setScale(w / 2f, h / 2f);
            fit.postTranslate(w / 2f, h / 2f);
            Path path = new Path();
            AvatarShape.chosenUnit().transform(fit, path);
            if (android.os.Build.VERSION.SDK_INT >= 30) outline.setPath(path);
            else outline.setOval(0, 0, w, h);
        }
    };
    private static final int SHAPE_OF = 0x7e0c033e;

    /** A picture of a person is cut to the chosen shape, and cut again when the choice is changed. */
    static void avatarShape(View photo) {
        if (photo.getOutlineProvider() != AVATAR) {
            photo.setOutlineProvider(AVATAR);
            photo.setClipToOutline(true);
        }
        String now = AvatarShape.chosen();
        if (!now.equals(photo.getTag(SHAPE_OF))) {
            photo.setTag(SHAPE_OF, now);
            photo.invalidateOutline();
        }
    }
    /** Eight waves, 4.5 per cent: the sunny. */
    static final ViewOutlineProvider SUNNY = polar(8, 0.045, 0);

    // ------------------------------------------------------------------ watching

    private static final java.util.WeakHashMap<View, Boolean> WATCHED = new java.util.WeakHashMap<View, Boolean>();

    /**
     * Dress the profile at most once per quiet window, with a trailing pass so the last state is
     * always applied. Both the layout listener and the pre-draw watcher come through here, keyed on
     * the head, so a burst of scroll/layout events is one leading and one trailing dress, not a full
     * pass every frame.
     */
    static void applyCoalesced(View anchor) {
        if (anchor == null || !isEnabled()) return;
        final ViewGroup head = page(anchor);
        if (head == null) return;
        long[] gate = APPLY_GATE.get(head);
        if (gate == null) {
            gate = new long[]{0L, 0L};
            APPLY_GATE.put(head, gate);
        }
        long now = SystemClock.uptimeMillis();
        if (now - gate[0] >= APPLY_WINDOW) {
            gate[0] = now;
            apply(head);
        } else if (gate[1] == 0L) {
            gate[1] = 1L;
            final long[] g = gate;
            final java.lang.ref.WeakReference<ViewGroup> ref = new java.lang.ref.WeakReference<ViewGroup>(head);
            APPLY_HANDLER.postDelayed(new Runnable() {
                @Override public void run() {
                    g[1] = 0L;
                    ViewGroup h = ref.get();
                    if (h == null || !h.isAttachedToWindow()) return;
                    g[0] = SystemClock.uptimeMillis();
                    apply(h);
                }
            }, APPLY_WINDOW - (now - gate[0]) + 1);
        }
    }

    /**
     * TikTok paints the numbers' slab again each time they are bound, and puts its other rows into
     * the head after this has been laid out, so the head is looked at again a few times a second for as
     * long as it is on the screen.
     */
    private static void watch(final ViewGroup head) {
        if (WATCHED.put(head, Boolean.TRUE) != null) return;
        final java.lang.ref.WeakReference<ViewGroup> ref = new java.lang.ref.WeakReference<ViewGroup>(head);
        head.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            long last;

            @Override
            public boolean onPreDraw() {
                ViewGroup now = ref.get();
                if (now == null || !now.isAttachedToWindow()) {
                    try {
                        if (now != null) now.getViewTreeObserver().removeOnPreDrawListener(this);
                    } catch (Throwable ignored) {
                    }
                    WATCHED.remove(now);
                    return true;
                }
                applyCoalesced(now);
                return true;
            }
        });
    }

    // ------------------------------------------------------------------ the whole page

    static void apply(View anchor) {
        long probe = Probe.start();
        try {
            if (anchor == null || !isEnabled()) return;
            ViewGroup head = page(anchor);
            if (head == null || !(head instanceof LinearLayout)) return;
            watch(head);
            float unit = head.getResources().getDisplayMetrics().density;
            Roles r = roles(head);
            boolean mine = mine(head);

            try {
                View cover = root(head, "bte");
                if (cover != null && cover.getVisibility() != View.GONE) cover.setVisibility(View.GONE);
            } catch (Throwable error) {
                Diary.detail("profile m3: cover: " + error);
            }
            try { topBar(head, r, unit); } catch (Throwable error) { Diary.detail("profile m3: top: " + error); }
            try { avatar(head, r, unit); } catch (Throwable error) { Diary.detail("profile m3: avatar: " + error); }
            View slab = null;
            try { slab = numbers(head, r, unit); } catch (Throwable error) { Diary.detail("profile m3: numbers: " + error); }
            try { names(head, slab, r, unit); } catch (Throwable error) { Diary.detail("profile m3: names: " + error); }
            try {
                View pencil = pencil(head);
                if (pencil != null && mine) buttons(head, pencil, slab, unit, r);
            } catch (Throwable error) { Diary.detail("profile m3: buttons: " + error); }
            try { order(head, slab); } catch (Throwable error) { Diary.detail("profile m3: order: " + error); }
            try { chip(head, r, unit); } catch (Throwable error) { Diary.detail("profile m3: chip: " + error); }
            try { tabs(head, r, unit); } catch (Throwable error) { Diary.detail("profile m3: tabs: " + error); }
            try { grids(head, unit); } catch (Throwable error) { Diary.detail("profile m3: grid: " + error); }
        } catch (Throwable error) {
            Diary.detail("profile m3: " + error);
        } finally {
            Probe.stop(Probe.PROFILE, probe);
        }
    }

    // ------------------------------------------------------------------ the bar at the top

    /** Icon buttons as Material 3 draws them: 48dp to touch, a 24dp icon, and no disc under it. */
    private static void topBar(ViewGroup head, Roles r, float unit) {
        for (String name : new String[]{"ptp", "pte"}) {
            View bar = root(head, name);
            if (!(bar instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) bar;
            for (int i = 0; i < group.getChildCount(); i++) {
                View v = group.getChildAt(i);
                if (!(v instanceof ImageView) || v.getVisibility() == View.GONE) continue;
                boolean ours = v.getTag(ICON) != null;
                if (!ours && !(v.getBackground() instanceof android.graphics.drawable.InsetDrawable)) continue;
                v.setTag(ICON, Boolean.TRUE);
                if (!(v.getBackground() instanceof RippleDrawable)) v.setBackground(ripple(r.ink, null, true));
                size(v, dp(unit, 48f), dp(unit, 48f));
                pad(v, dp(unit, 12f), dp(unit, 12f), dp(unit, 12f), dp(unit, 12f));
                tint(v, r.variant);
            }
        }
    }

    // ------------------------------------------------------------------ the picture

    /** The box the profile's avatar is in, whatever the layout: a frame is hung on it. */
    static View avatarBox(View anchor) {
        ViewGroup head = page(anchor);
        if (head == null) return null;
        View found = head.findViewById(id(head, "bnt"));
        return found instanceof ViewGroup ? found : null;
    }

    private static void avatar(ViewGroup head, Roles r, float unit) {
        View found = head.findViewById(id(head, "bnt"));
        if (!(found instanceof ViewGroup)) return;
        ViewGroup box = (ViewGroup) found;
        View photo = box.getChildCount() > 0 && box.getChildAt(0) instanceof ImageView ? box.getChildAt(0) : null;
        if (photo == null) return;
        int side = dp(unit, 116f);
        // the note over the picture ("what is on your mind") is put away
        View bubble = head.findViewById(id(head, "q4n"));
        if (bubble != null && bubble.getVisibility() != View.GONE) bubble.setVisibility(View.GONE);
        View noteHolder = head.findViewById(id(head, "u66"));
        if (noteHolder != null && noteHolder.getVisibility() != View.GONE) noteHolder.setVisibility(View.GONE);
        // room over the picture for a frame that rises above it, a crown for one
        int top = Math.max(dp(unit, 6f), Frames.headroom(box) + dp(unit, 4f));
        size(box, side, side);
        // a drop to the left, so that the wavy edge is in line with the cards and away from the name
        margins(box, -dp(unit, 3f), top, 0, 0);
        size(photo, side, side);
        ViewGroup.LayoutParams lp = photo.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams && ((FrameLayout.LayoutParams) lp).gravity != (Gravity.TOP | Gravity.START)) {
            ((FrameLayout.LayoutParams) lp).gravity = Gravity.TOP | Gravity.START;
            photo.setLayoutParams(lp);
        }
        margins(photo, 0, 0, 0, 0);
        avatarShape(photo);
        // the round button that adds a story is put away
        for (int i = 0; i < box.getChildCount(); i++) {
            View plus = box.getChildAt(i);
            if (plus != photo && plus instanceof ViewGroup && plus.isClickable()
                    && plus.getVisibility() != View.GONE) {
                plus.setVisibility(View.GONE);
            }
        }
        // the whole header sits to the left, 16dp from the edge, with 16dp between the picture and the name
        View row = head.findViewById(id(head, "t58"));
        if (row instanceof LinearLayout && ((LinearLayout) row).getGravity() != Gravity.CENTER_VERTICAL) {
            ((LinearLayout) row).setGravity(Gravity.CENTER_VERTICAL);
        }
        if (row != null) {
            // a long description makes the column taller than TikTok first laid it out: a fixed height
            // would cut it off at the foot and let it run over the cards under the head
            ViewGroup.LayoutParams rowParams = row.getLayoutParams();
            if (rowParams != null && rowParams.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                rowParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                row.setLayoutParams(rowParams);
            }
        }
        View frame = picture(head);
        if (row instanceof ViewGroup && frame != null && frame.getParent() == row
                && ((ViewGroup) row).indexOfChild(frame) > 0) {
            ViewGroup group = (ViewGroup) row;
            group.removeView(frame);
            group.addView(frame, 0);
        }
        if (frame != null) margins(frame, 0, 0, 0, 0);
        // the round button reaches past the box the picture is in
        View up = box;
        for (int i = 0; i < 4 && up instanceof ViewGroup && up != head; i++) {
            if (((ViewGroup) up).getClipChildren()) ((ViewGroup) up).setClipChildren(false);
            android.view.ViewParent next = up.getParent();
            up = next instanceof View ? (View) next : null;
        }
        // the name's column is centred on the picture, not on the note over it
        View column = head.findViewById(id(head, "t5_"));
        if (column != null) {
            ViewGroup.LayoutParams params = column.getLayoutParams();
            if (params instanceof LinearLayout.LayoutParams
                    && ((LinearLayout.LayoutParams) params).gravity != Gravity.CENTER_VERTICAL) {
                ((LinearLayout.LayoutParams) params).gravity = Gravity.CENTER_VERTICAL;
                column.setLayoutParams(params);
            }
            // 16dp between the picture and the name; as padding, because TikTok sets the margins again at each bind
            pad(column, dp(unit, 16f), 0, 0, 0);
        }
    }

    private static View picture(ViewGroup head) {
        return head.findViewById(id(head, "t45"));
    }

    // ------------------------------------------------------------------ the name, the handle, the description

    private static void names(ViewGroup head, View slab, Roles r, float unit) {
        View column = head.findViewById(id(head, "t5_"));
        if (!(column instanceof LinearLayout)) return;
        LinearLayout stack = (LinearLayout) column;
        View name = head.findViewById(id(head, "t75"));
        if (name instanceof TextView) {
            write((TextView) name, 32f, 0, true);
            tidyName((TextView) name, unit);
        }
        View handle = head.findViewById(id(head, "t3h"));
        if (handle instanceof TextView) write((TextView) handle, 14f, r.variant, false);

        // the description moves under the handle, 8dp below it
        View bio = null;
        for (int i = 0; i < head.getChildCount(); i++) {
            View one = head.getChildAt(i);
            if (one instanceof LinearLayout && one != slab && !Boolean.TRUE.equals(one.getTag(MINE))
                    && one.getTag(MOVED) == null && !containsId(one, id(head, "t58")) && !isSlab(one, head)) {
                bio = one;
                break;
            }
        }
        if (bio == null) {
            for (int i = 0; i < stack.getChildCount(); i++) if (stack.getChildAt(i).getTag(MOVED) != null) bio = stack.getChildAt(i);
        }
        if (bio != null) {
            if (bio.getParent() != stack) {
                bio.setTag(MOVED, Boolean.TRUE);
                ((ViewGroup) bio.getParent()).removeView(bio);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = dp(unit, 8f);
                stack.addView(bio, params);
            }
            margins(bio, 0, dp(unit, 8f), 0, 0);
            restyle(bio, r);
        }
    }

    private static boolean containsId(View view, int id) {
        return id != 0 && view.findViewById(id) != null;
    }

    private static boolean isSlab(View view, ViewGroup head) {
        return view instanceof LinearLayout && ((LinearLayout) view).getChildCount() == 3
                && view.findViewById(id(head, "t7t")) != null;
    }

    private static void restyle(View view, Roles r) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (!"p4x".equals(entry(view)) && text.getVisibility() == View.VISIBLE
                    && text.getText() != null && text.getText().length() > 0) {
                write(text, 14f, r.ink, false);
                // the description sits in the column beside the picture, narrower than the screen:
                // a long one is cut after four lines with an ellipsis, not left to run the head out
                if (text.getMaxLines() <= 0 || text.getMaxLines() > 4) text.setMaxLines(4);
                text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) restyle(g.getChildAt(i), r);
        }
    }

    private static String entry(View view) {
        int id = view.getId();
        if (id == View.NO_ID) return "";
        String cached = NAMES.get(id);
        if (cached != null) return cached;
        String name;
        try {
            name = view.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {
            name = "";
        }
        NAMES.put(id, name);
        return name;
    }

    /** The badges 28dp tall with 6dp between them, and no arrow after the name. */
    private static void tidyName(TextView name, float unit) {
        CharSequence text = name.getText();
        if (!(text instanceof Spannable)) return;
        // skip the span scan while the name is unchanged, but look again now and then so badges
        // TikTok adds to the same spannable after this pass are still caught
        long now = SystemClock.uptimeMillis();
        Object kept = name.getTag(NAME_AT);
        if (kept instanceof Object[]) {
            Object[] c = (Object[]) kept;
            if (c[0] == text && now - ((Long) c[1]).longValue() < 400L) return;
        }
        name.setTag(NAME_AT, new Object[]{text, Long.valueOf(now)});
        Spannable spans = (Spannable) text;
        ImageSpan[] images = spans.getSpans(0, spans.length(), ImageSpan.class);
        boolean changed = false;
        int tall = dp(unit, 28f), gap = dp(unit, 6f);
        // the arrow of the account list is a span of TikTok's own on the last character: it is made no wider than nothing
        Object[] others = spans.getSpans(0, spans.length(), android.text.style.ReplacementSpan.class);
        for (int i = 0; i < others.length; i++) {
            Object span = others[i];
            if (span instanceof ImageSpan || span instanceof ZeroSpan) continue;
            int start = spans.getSpanStart(span), end = spans.getSpanEnd(span);
            if (end != spans.length() || end - start != 1) continue;
            spans.removeSpan(span);
            spans.setSpan(new ZeroSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            changed = true;
        }
        for (int i = 0; i < images.length; i++) {
            ImageSpan span = images[i];
            android.graphics.drawable.Drawable drawable = span.getDrawable();
            if (drawable == null) continue;
            int start = spans.getSpanStart(span), end = spans.getSpanEnd(span);
            boolean badge = spans.getSpans(start, end, android.text.style.ClickableSpan.class).length > 0;
            if (!badge) {
                // what TikTok puts after the name, the arrow of the account list, is made no wider than nothing
                if (drawable.getBounds().width() != 0) {
                    android.graphics.drawable.ColorDrawable none = new android.graphics.drawable.ColorDrawable(0);
                    none.setBounds(0, 0, 0, 0);
                    spans.removeSpan(span);
                    spans.setSpan(new ImageSpan(none, ImageSpan.ALIGN_BOTTOM), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    changed = true;
                }
                continue;
            }
            if (drawable instanceof android.graphics.drawable.InsetDrawable) continue;  // done already
            int wide = drawable.getIntrinsicWidth() > 0 && drawable.getIntrinsicHeight() > 0
                    ? Math.round(tall * (float) drawable.getIntrinsicWidth() / drawable.getIntrinsicHeight()) : tall;
            drawable.setBounds(0, 0, wide, tall);
            android.graphics.drawable.InsetDrawable spaced = new android.graphics.drawable.InsetDrawable(drawable, 0, 0, gap, 0);
            spaced.setBounds(0, 0, wide + gap, tall);
            spans.removeSpan(span);
            spans.setSpan(new ImageSpan(spaced, ImageSpan.ALIGN_BOTTOM), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            changed = true;
        }
        if (changed) {
            name.requestLayout();
            name.invalidate();
        }
    }

    /** A character that takes no room and draws nothing. */
    private static final class ZeroSpan extends android.text.style.ReplacementSpan {
        @Override
        public int getSize(android.graphics.Paint paint, CharSequence text, int start, int end,
                           android.graphics.Paint.FontMetricsInt metrics) {
            return 0;
        }

        @Override
        public void draw(android.graphics.Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, android.graphics.Paint paint) {
        }
    }

    // ------------------------------------------------------------------ the three numbers

    private static View numbers(ViewGroup head, Roles r, float unit) {
        View number = head.findViewById(id(head, "t7t"));
        ViewGroup stats = number == null || !(number.getParent() instanceof ViewGroup) ? null
                : (ViewGroup) number.getParent();
        ViewGroup slab = stats == null || !(stats.getParent() instanceof ViewGroup) ? null
                : (ViewGroup) stats.getParent();
        if (!(slab instanceof LinearLayout) || slab.getChildCount() != 3) return null;
        if (slab.getParent() != head && slab.getParent() instanceof ViewGroup) {
            ((ViewGroup) slab.getParent()).removeView(slab);
            LinearLayout.LayoutParams across = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            head.addView(slab, beforeChip(head), across);
        }
        margins(slab, dp(unit, 16f), dp(unit, 16f), dp(unit, 16f), 0);
        slab.setBackground(null);
        pad(slab, 0, 0, 0, 0);
        ((LinearLayout) slab).setGravity(Gravity.CENTER);
        for (int i = 0; i < slab.getChildCount(); i++) {
            View one = slab.getChildAt(i);
            ViewGroup.LayoutParams lp = one.getLayoutParams();
            if (!(lp instanceof LinearLayout.LayoutParams)) continue;
            LinearLayout.LayoutParams share = (LinearLayout.LayoutParams) lp;
            int left = i == 0 ? 0 : dp(unit, 4f), right = i == 2 ? 0 : dp(unit, 4f);
            if (share.width != 0 || share.weight != 1f || share.leftMargin != left || share.rightMargin != right
                    || share.topMargin != 0 || share.bottomMargin != 0) {
                share.width = 0;
                share.weight = 1f;
                share.setMargins(left, 0, right, 0);
                one.setLayoutParams(share);
            }
            Object done = one.getTag(CARD);
            if (!Integer.valueOf(r.high).equals(done)) {
                one.setTag(CARD, Integer.valueOf(r.high));
                one.setBackground(fill(r.high, corners(unit, 24f, 24f, 24f, 24f)));
            }
            pad(one, dp(unit, 16f), dp(unit, 14f), dp(unit, 16f), dp(unit, 14f));
            restyleStat(one, r);
        }
        return slab;
    }

    /** The number 26sp bold, its caption 12sp in the quieter colour. */
    private static void restyleStat(View view, Roles r) {
        if (view instanceof TextView) {
            String entry = entry(view);
            if ("t7t".equals(entry)) statNumber((TextView) view, r);
            else if ("t7s".equals(entry)) {
                TextView caption = (TextView) view;
                write(caption, 12f, r.variant, false);
                // a third of the width is narrow: the caption is one line, cut with an ellipsis
                caption.setSingleLine(true);
                caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) restyleStat(g.getChildAt(i), r);
        }
    }

    /**
     * A number that can be as long as a billion ("1,2 млрд") in a card a third of the width wide:
     * one line, and the size given up until it fits, so that the three cards keep one height.
     * The size is worked out again at each pass, because TikTok sets the number's own size again
     * every time it binds the head.
     */
    private static void statNumber(TextView number, Roles r) {
        if (number.getCurrentTextColor() != r.ink) number.setTextColor(r.ink);
        if (number.getTypeface() == null || !number.getTypeface().isBold()) {
            number.setTypeface(Typeface.create(number.getTypeface(), Typeface.BOLD));
        }
        number.setSingleLine(true);
        number.setEllipsize(android.text.TextUtils.TruncateAt.END);
        CharSequence text = number.getText();
        int avail = number.getWidth() - number.getPaddingLeft() - number.getPaddingRight();
        if (text == null || text.length() == 0 || avail <= 0) return;
        // the measure loop is only needed when the number, the width or the size TikTok reset changed
        Object kept = number.getTag(STAT);
        if (kept instanceof Object[]) {
            Object[] c = (Object[]) kept;
            if (c[0] == text && ((Integer) c[1]).intValue() == avail
                    && Math.abs(number.getTextSize() - ((Float) c[2]).floatValue()) <= 0.5f) {
                return;
            }
        }
        float step = number.getResources().getDisplayMetrics().scaledDensity;
        float least = 14f * step, size = 26f * step;
        android.text.TextPaint probe = new android.text.TextPaint(number.getPaint());
        probe.setTextSize(size);
        while (size > least && probe.measureText(text, 0, text.length()) > avail) {
            size -= step;
            probe.setTextSize(size);
        }
        number.setTag(STAT, new Object[]{text, Integer.valueOf(avail), Float.valueOf(size)});
        if (Math.abs(number.getTextSize() - size) > 0.5f) {
            number.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        }
    }

    // ------------------------------------------------------------------ the two buttons

    /** The place after the bio and before TikTok's row of chips: the end of the head when there is none. */
    private static int beforeChip(ViewGroup head) {
        for (int i = 0; i < head.getChildCount(); i++) {
            if (head.getChildAt(i) instanceof HorizontalScrollView) return i;
        }
        return head.getChildCount();
    }

    private static View pencil(View head) {
        View bar = root(head, "ptp");
        if (bar instanceof ViewGroup && ((ViewGroup) bar).getChildCount() > 0) {
            return ((ViewGroup) bar).getChildAt(0);
        }
        return null;
    }

    /** Whether the name on this page is the signed-in account's own. */
    private static boolean mine(View head) {
        String account = Account.profileHandle();
        Object kept = head.getTag(MINE_TAG);
        Object[] c = kept instanceof Object[] ? (Object[]) kept : null;
        View view = c != null && c[0] instanceof View && ((View) c[0]).isAttachedToWindow() ? (View) c[0] : null;
        if (view == null) view = head.findViewById(id(head, "t3h"));
        if (!(view instanceof TextView) || account == null || account.length() == 0) {
            head.setTag(MINE_TAG, new Object[]{view, account, null, Boolean.FALSE});
            return false;
        }
        CharSequence said = ((TextView) view).getText();
        if (c != null && account.equals(c[1]) && c[2] == said) {
            return ((Boolean) c[3]).booleanValue();
        }
        boolean result = false;
        if (said != null) {
            String shown = said.toString().trim();
            if (shown.startsWith("@")) shown = shown.substring(1);
            result = shown.equalsIgnoreCase(account.startsWith("@") ? account.substring(1) : account);
        }
        head.setTag(MINE_TAG, new Object[]{view, account, said, Boolean.valueOf(result)});
        return result;
    }

    /** One group of two buttons, 4dp apart: the large one filled with the accent, the small one in its pale container. */
    private static void buttons(final ViewGroup head, final View pencil, View after, float unit, Roles r) {
        LinearLayout row = null;
        for (int i = 0; i < head.getChildCount(); i++) {
            if (Boolean.TRUE.equals(head.getChildAt(i).getTag(MINE))) row = (LinearLayout) head.getChildAt(i);
        }
        if (row == null) {
            row = new LinearLayout(head.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setTag(MINE, Boolean.TRUE);
            LinearLayout.LayoutParams across = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int at = after != null && after.getParent() == head ? head.indexOfChild(after) + 1 : beforeChip(head);
            head.addView(row, Math.min(at, head.getChildCount()), across);

            TextView edit = new TextView(head.getContext());
            edit.setText(Local.of("Изменить профиль"));
            edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            edit.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            edit.setGravity(Gravity.CENTER);
            edit.setSingleLine(true);
            row.addView(edit, new LinearLayout.LayoutParams(0, dp(unit, 56f), 1f));
            edit.setOnClickListener(v -> press(pencil));

            ImageView share = new ImageView(head.getContext());
            share.setScaleType(ImageView.ScaleType.FIT_CENTER);
            share.setContentDescription(Local.of("Поделиться"));
            LinearLayout.LayoutParams square = new LinearLayout.LayoutParams(dp(unit, 64f), dp(unit, 56f));
            square.leftMargin = dp(unit, 4f);
            row.addView(share, square);
            share.setOnClickListener(v -> {
                try {
                    String handle = Account.profileHandle();
                    if (handle == null || handle.length() == 0) return;
                    if (handle.startsWith("@")) handle = handle.substring(1);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, "https://www.tiktok.com/@" + handle);
                    v.getContext().startActivity(Intent.createChooser(send, null));
                } catch (Throwable ignored) {
                }
            });
        }
        margins(row, dp(unit, 16f), dp(unit, 16f), dp(unit, 16f), 0);
        int key = r.primary ^ (r.secondary * 31) ^ r.ink;
        if (!Integer.valueOf(key).equals(row.getTag(GROUP))) {
            row.setTag(GROUP, Integer.valueOf(key));
            TextView edit = (TextView) row.getChildAt(0);
            ImageView share = (ImageView) row.getChildAt(1);
            edit.setTextColor(r.onPrimary);
            edit.setBackground(ripple(r.onPrimary, fill(r.primary, corners(unit, 28f, 8f, 8f, 28f)), false));
            share.setImageDrawable(SettingsGlyph.make("share", r.onSecondary));
            int inset = dp(unit, 20f);
            share.setPadding(inset, dp(unit, 16f), inset, dp(unit, 16f));
            share.setBackground(ripple(r.onSecondary, fill(r.secondary, corners(unit, 8f, 28f, 28f, 8f)), false));
        }
    }

    /** What the pencil does: the click, or a touch where it is when nothing there listens for clicks. */
    private static void press(View pencil) {
        try {
            View target = pencil;
            for (int up = 0; target != null && up < 4; up++) {
                if (target.performClick()) return;
                android.view.ViewParent next = target.getParent();
                target = next instanceof View ? (View) next : null;
            }
            int[] at = new int[2];
            pencil.getLocationInWindow(at);
            float x = at[0] + pencil.getWidth() / 2f, y = at[1] + pencil.getHeight() / 2f;
            long now = SystemClock.uptimeMillis();
            View host = pencil.getRootView();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
            host.dispatchTouchEvent(down);
            MotionEvent lift = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0);
            host.dispatchTouchEvent(lift);
            down.recycle();
            lift.recycle();
        } catch (Throwable ignored) {
        }
    }

    /** The numbers, then the buttons, then TikTok's chips: whatever was put into the head since is kept above them. */
    private static void order(ViewGroup head, View slab) {
        if (slab == null || slab.getParent() != head) return;
        View buttons = null;
        for (int i = 0; i < head.getChildCount(); i++) {
            if (Boolean.TRUE.equals(head.getChildAt(i).getTag(MINE))) buttons = head.getChildAt(i);
        }
        int chip = beforeChip(head);
        int wanted = chip - (buttons == null ? 1 : 2);
        if (wanted < 0 || head.indexOfChild(slab) == wanted
                && (buttons == null || head.indexOfChild(buttons) == wanted + 1)) return;
        ViewGroup.LayoutParams slabParams = slab.getLayoutParams();
        head.removeView(slab);
        if (buttons != null) {
            ViewGroup.LayoutParams buttonParams = buttons.getLayoutParams();
            head.removeView(buttons);
            int end = beforeChip(head);
            head.addView(slab, end, slabParams);
            head.addView(buttons, end + 1, buttonParams);
        } else {
            head.addView(slab, beforeChip(head), slabParams);
        }
    }

    // ------------------------------------------------------------------ the chip

    /** TikTok's row with the TikTok Studio chip is put away. */
    private static void chip(ViewGroup head, Roles r, float unit) {
        for (int i = 0; i < head.getChildCount(); i++) {
            View one = head.getChildAt(i);
            if (one instanceof HorizontalScrollView && one.getVisibility() != View.GONE) one.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------ the tabs

    /** The capsule is 56dp with 4dp inside it, and each of the five tabs is 48dp: Md3 draws the capsule and the pill. */
    private static void tabs(ViewGroup head, Roles r, float unit) {
        View holder = root(head, "z2h");
        if (!(holder instanceof ViewGroup)) return;
        size(holder, ViewGroup.LayoutParams.MATCH_PARENT, dp(unit, 56f));
        margins(holder, dp(unit, 16f), dp(unit, 16f), dp(unit, 16f), 0);
        pad(holder, dp(unit, 4f), dp(unit, 4f), dp(unit, 4f), dp(unit, 4f));
        ViewGroup strip = ((ViewGroup) holder).getChildCount() == 1 && ((ViewGroup) holder).getChildAt(0) instanceof ViewGroup
                ? (ViewGroup) ((ViewGroup) holder).getChildAt(0) : null;
        if (strip == null) return;
        long now = SystemClock.uptimeMillis();
        Object kept = holder.getTag(GROUP);
        if (!(kept instanceof Long) || now - (Long) kept > 700L) {
            holder.setTag(GROUP, Long.valueOf(now));
            // the line TikTok draws under the chosen tab: no height, and nothing to draw it with
            try {
                tabMethod(holder, "setSelectedTabIndicatorHeight", int.class).invoke(holder, 0);
                tabMethod(holder, "setSelectedTabIndicator", android.graphics.drawable.Drawable.class).invoke(holder, NO_INDICATOR);
                tabMethod(holder, "setSelectedTabIndicatorColor", int.class).invoke(holder, 0);
            } catch (Throwable error) {
                Diary.detail("profile m3: indicator: " + error);
            }
        }
        for (int i = 0; i < strip.getChildCount(); i++) {
            View tab = strip.getChildAt(i);
            View icon = tab.findViewById(id(head, "kfx"));
            if (icon != null) {
                size(icon, dp(unit, 24f), dp(unit, 24f));
                tint(icon, tab.isSelected() ? r.onSecondary : r.variant);
            }
            View arrow = tab.findViewById(id(head, "y00"));
            if (arrow != null) {
                if (arrow.getVisibility() != View.GONE) arrow.setVisibility(View.GONE);
                View content = tab.findViewById(id(head, "p3z")), cell = tab.findViewById(id(head, "knf"));
                if (content != null) size(content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
                if (cell != null) size(cell, dp(unit, 24f), ViewGroup.LayoutParams.MATCH_PARENT);
                if (tab.getTag(MOVED) == null) {
                    tab.setTag(MOVED, Boolean.TRUE);
                    final View sort = arrow;
                    final View inside = tab.findViewById(id(head, "aib"));
                    tab.setOnLongClickListener(v -> {
                        boolean done = sort.performClick();
                        if (!done && inside != null) done = inside.performClick();
                        return done;
                    });
                }
            }
        }
    }

    /** A tab-strip method looked up once per class, not on every gated re-hide of the indicator. */
    private static java.lang.reflect.Method tabMethod(View holder, String name, Class<?> param) throws NoSuchMethodException {
        String key = holder.getClass().getName() + "#" + name;
        java.lang.reflect.Method m = TAB_METHODS.get(key);
        if (m == null) {
            m = holder.getClass().getMethod(name, param);
            TAB_METHODS.put(key, m);
        }
        return m;
    }

    // ------------------------------------------------------------------ the pictures

    /** 16dp from the edge of the screen and 4dp between pictures. */
    private static void grids(ViewGroup head, float unit) {
        int id = id(head, "ial");
        if (id == 0) return;
        Object kept = head.getTag(GRID);
        ViewGroup grid = kept instanceof ViewGroup ? (ViewGroup) kept : null;
        if (grid == null || !grid.isAttachedToWindow() || grid.getId() != id) {
            View page = head.getParent() instanceof View ? (View) head.getParent() : null;
            grid = findGrid(page, id, 0);
            if (grid != null) head.setTag(GRID, grid);
        }
        if (grid != null) dressGrid(grid, unit);
    }

    /** The grid once found is kept on the head, so the page is not walked again every pass. */
    private static ViewGroup findGrid(View view, int id, int depth) {
        if (view == null || depth > 14) return null;
        if (view.getId() == id && view instanceof ViewGroup) return (ViewGroup) view;
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                ViewGroup found = findGrid(g.getChildAt(i), id, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void dressGrid(ViewGroup grid, float unit) {
        // 16dp from the edge: 14 here and 2 on each picture, which are 4dp from one another
        pad(grid, dp(unit, 14f), grid.getPaddingTop(), dp(unit, 14f), grid.getPaddingBottom());
        if (grid instanceof android.widget.GridView) {
            android.widget.GridView list = (android.widget.GridView) grid;
            if (list.getHorizontalSpacing() != 0) list.setHorizontalSpacing(0);
            if (list.getVerticalSpacing() != 0) list.setVerticalSpacing(0);
        }
        for (int i = 0; i < grid.getChildCount(); i++) {
            margins(grid.getChildAt(i), dp(unit, 2f), dp(unit, 2f), dp(unit, 2f), dp(unit, 2f));
        }
    }
}
