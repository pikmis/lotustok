package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;

/**
 * The feed, the profile and the inbox in the shapes of Material 3.
 *
 * Nothing is moved and nothing is replaced: every view TikTok laid out stays
 * where it was and does what it did. What is added is behind them -- a disc
 * under a button, a card under a row, a capsule under a strip of tabs with a
 * pill under the one that is chosen -- and the corners of the pictures in a
 * grid. Each is a background the mod puts on a view that had none, or a clip
 * on its outline, so each can be taken off again and the screen is TikTok's
 * as it was. There is a switch a screen, and they are taken off when it is
 * turned off.
 *
 * The views are found by what TikTok calls them in this release: a class
 * with a real name where there is one, a resource name where there is not.
 * A name that is not there in some other release finds nothing, and that
 * part of that screen is left as it was.
 *
 * Looking is done when a screen is laid out again, and not more than a few
 * times a second. Lists hand out rows while they scroll without laying the
 * screen out, so the lists that were found are looked at before each frame
 * for rows that have nothing on them yet; that is a handful of views.
 */
final class Md3 {

    private Md3() {}

    static final String KEY_FEED = "md3_feed", KEY_PROFILE = "md3_profile", KEY_INBOX = "md3_inbox", KEY_ICONS = "md3_icons";
    private static final String[] KEYS = {KEY_FEED, KEY_PROFILE, KEY_INBOX, KEY_ICONS};
    private static final int FEED = 0, PROFILE = 1, INBOX = 2, ICONS = 3;
    private static final Boolean[] ON = new Boolean[KEYS.length];

    static boolean isOn(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return on(i);
        return false;
    }

    static void set(String key, boolean value) {
        for (int i = 0; i < KEYS.length; i++) {
            if (!KEYS[i].equals(key)) continue;
            ON[i] = value;
            Context context = Margy.context();
            if (context != null) {
                context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply();
            }
        }
    }

    private static boolean on(int which) {
        if (Version.LITE) return false;
        Boolean known = ON[which];
        if (known != null) return known;
        Context context = Margy.context();
        if (context == null) return which == PROFILE || which == INBOX;  // too early to know; not remembered
        // the profile and the inbox unless switched off; the feed and its icons only when switched on
        boolean value = which == PROFILE || which == INBOX;
        try {
            value = context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getBoolean(KEYS[which], value);
        } catch (Throwable ignored) {
        }
        ON[which] = value;
        return value;
    }

    // ------------------------------------------------- the parts of the feed

    /**
     * The feed's style is several things, and each can be left off while the rest stay: the
     * card behind the caption, the column behind the buttons beside the video, the move of
     * that column (and of the author's picture and the sound's cover with it) in from the
     * edge, the capsule behind the tabs across the top, and the discs behind the buttons there.
     * All of them are on until switched off, and none is drawn when the feed's style is off.
     */
    static final String PART_CAPTION = "md3_feed_caption", PART_COLUMN = "md3_feed_column",
            PART_SHIFT = "md3_feed_shift", PART_TABS = "md3_feed_tabs", PART_DISCS = "md3_feed_discs";
    private static final String[] PARTS = {PART_CAPTION, PART_COLUMN, PART_SHIFT, PART_TABS, PART_DISCS};
    private static final int CAPTION = 0, COLUMN = 1, SHIFT = 2, TABS = 3, DISCS = 4;
    private static final Boolean[] PART_ON = new Boolean[PARTS.length];

    /** What is switched, on its own, for a part of the feed's style. */
    static boolean partOn(String key) {
        for (int i = 0; i < PARTS.length; i++) if (PARTS[i].equals(key)) return stored(i);
        return false;
    }

    static void setPart(String key, boolean value) {
        for (int i = 0; i < PARTS.length; i++) {
            if (!PARTS[i].equals(key)) continue;
            PART_ON[i] = value;
            Context context = Margy.context();
            if (context != null) {
                context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply();
            }
        }
        // the buttons beside a video are dressed when they are handed over; they are asked to look again
        FeedRail.restyle();
    }

    private static boolean stored(int which) {
        Boolean known = PART_ON[which];
        if (known != null) return known;
        Context context = Margy.context();
        if (context == null) return true;  // too early to know; not remembered
        boolean value = true;
        try {
            value = context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getBoolean(PARTS[which], true);
        } catch (Throwable ignored) {
        }
        PART_ON[which] = value;
        return value;
    }

    /** Whether a part of the feed is drawn: the feed's style is on, and that part has not been left off. */
    private static boolean part(int which) {
        return on(FEED) && stored(which);
    }

    // ------------------------------------------------------------ the shapes

    private static final int DISC = 0, CARD = 1, PILL = 2;

    /** A disc, a card or a pill behind a view, in a colour of the theme. */
    private static final class Shape extends Drawable {
        final int kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        /** How far in from each edge of the view it is drawn; less than nothing is further out. */
        float left, top, right, bottom;
        /** The corner of a card, or the radius of a disc. */
        float radius;
        /** How wide a card is, measured from its right edge, when that is not the width of the view. */
        float wide;
        /** The corners at the foot of a card, when they are not those at its head. Less than nothing: the same. */
        float foot = -1f;
        private final android.graphics.Path edge = new android.graphics.Path();
        private final float[] corners = new float[8];
        /** For a card: a view in the one it is behind, which the card is drawn round, `pad` out from each side. */
        java.util.List<WeakReference<View>> parts;
        /** For a caption: the words themselves; with none the card is not drawn, and a name alone is not on one. */
        WeakReference<View> words;
        float pad;
        /** How far below the view the card goes, when that is not `pad`; less than nothing: the same. */
        float padBottom = -1f;
        /** How far above the words the card begins, when that is not `pad`; less than nothing: the same. */
        float padTop = -1f;
        /** For a caption: how far from the screen's right edge it stops, so that it does not go under the column of actions; nought for no limit. */
        float stop;
        /** For a card: the half-width of a rounded-square button drawn over it round the icon in `around`; nought for none. */
        float button;
        /** Whether the rounded square is drawn behind each icon; off, `button` still says how tall the column is round them. */
        boolean squares = true;
        /** How far above the button the card begins, when it is the head of a column and goes further up than its slot; less than nothing: at the slot. */
        float head = -1f;
        /** For a section of a column: the space left between it and the next, in px; less than nothing: the card is not a section. */
        float gap = -1f;
        /** Whether the section is the one of a button that is on (a liked heart, a saved bookmark), and how far it has become a circle. */
        long checked;
        /** How far a section reaches below its slot, in px. */
        float reach;
        private boolean selected;
        private float morph;
        private long morphAt;
        /** The corner of that button at rest, and while it is pressed. */
        float buttonCorner, buttonPressed;
        private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** What a disc is centred on, when that is not the view it is behind. */
        WeakReference<View> around;
        WeakReference<View> host;

        Shape(int kind) {
            this.kind = kind;
        }

        /** What it is for, which is what decides its colour. */
        int role = SHELL;

        void tint(int colour) {
            if (paint.getColor() == colour) return;
            paint.setColor(colour);
            invalidateSelf();
        }

        /** Coloured for what it is and where it is, and kept on the list of what is coloured again. */
        boolean dressed;

        void dress(View view, int as) {
            role = as;
            dressed = true;
            synchronized (WORN) {
                WORN.put(view, this);
            }
            tint(colour(view, as));
        }

        /** The corners of a card in a group: large where the group ends, small where it goes on. */
        void ends(float head, float tail) {
            if (radius == head && foot == tail) return;
            radius = head;
            foot = tail;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect whole = getBounds();
            box.set(whole.left + left, whole.top + top, whole.right - right, whole.bottom - bottom);
            float want = selected ? 1f : 0f;
            if (morph != want) {
                long now = android.os.SystemClock.uptimeMillis();
                float step = Math.min(now - (morphAt == 0L ? now - 16L : morphAt), 48L) / 240f;
                morphAt = now;
                morph = want > morph ? Math.min(want, morph + step) : Math.max(want, morph - step);
                if (morph == want) morphAt = 0L;
                again();
            }
            if (wide > 0f) box.left = box.right - wide;
            if (kind == CARD && parts != null) {
                // round the words themselves: the least box that holds every one of them
                View on = host == null ? null : host.get();
                if (on == null) return;
                if (words != null) {
                    View said = words.get();
                    if (said == null || !said.isShown() || said.getHeight() <= 0) return;
                    if (textOf(said).length() == 0) return;
                }
                float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
                float[] at = new float[2];
                for (WeakReference<View> ref : parts) {
                    View part = ref.get();
                    if (part == null || !part.isShown() || part.getWidth() <= 0) continue;
                    if (!offset(part, on, at)) continue;
                    l = Math.min(l, at[0]);
                    t = Math.min(t, at[1]);
                    r = Math.max(r, at[0] + part.getWidth());
                    b = Math.max(b, at[1] + part.getHeight());
                }
                if (l > r) return;
                box.set(l - pad, t - (padTop >= 0f ? padTop : pad), r + pad, b + (padBottom >= 0f ? padBottom : pad));
                if (stop > 0f) {
                    int[] place = new int[2];
                    on.getLocationOnScreen(place);
                    float edge = on.getResources().getDisplayMetrics().widthPixels - stop - place[0];
                    if (box.right > edge) box.right = Math.max(box.left + pad * 2f, edge);
                }
            }
            if (kind == CARD && button > 0f && wide > 0f) {
                // the column is centred on its icons, which is not the middle of the slot
                float[] at = where();
                if (at != null) {
                    box.left = at[0] - wide / 2f;
                    box.right = at[0] + wide / 2f;
                    if (head >= 0f) box.top = at[1] - button - head;
                    View slot = host == null ? null : host.get();
                    if (gap >= 0f && slot != null) box.bottom = box.top + slot.getHeight() - gap + reach;
                }
            }
            if (kind == DISC) {
                float x = box.centerX(), y = box.centerY();
                boolean morph = pressed && around != null;
                View centre = around == null ? null : around.get(), on = host == null ? null : host.get();
                if (centre != null && on != null && centre.getWidth() > 0) {
                    float offX = centre.getWidth() / 2f, offY = centre.getHeight() / 2f;
                    View step = centre;
                    for (int i = 0; i < 6 && step != null && step != on; i++) {
                        offX += step.getLeft();
                        offY += step.getTop();
                        Object parent = step.getParent();
                        step = parent instanceof View ? (View) parent : null;
                    }
                    if (step == on) {
                        x = offX;
                        y = offY;
                    }
                }
                if (morph) {
                    // Material 3: while pressed, a round icon button becomes squarer
                    float corner = radius * 0.4f;
                    canvas.drawRoundRect(x - radius, y - radius, x + radius, y + radius, corner, corner, paint);
                } else {
                    canvas.drawCircle(x, y, radius, paint);
                }
            } else if (kind == CARD && (foot >= 0f && foot != radius || morph > 0f || button > 0f)) {
                // Material 3: what is on is a circle; the corners run from the group's to full, with a smooth step
                float eased = morph * morph * (3f - 2f * morph);
                float full = Math.min(box.width(), box.height()) / 2f;
                float foots = foot >= 0f ? foot : radius;
                float topCorner = radius + (full - radius) * eased;
                float footCorner = foots + (full - foots) * eased;
                corners[0] = corners[1] = corners[2] = corners[3] = topCorner;
                corners[4] = corners[5] = corners[6] = corners[7] = footCorner;
                edge.rewind();
                edge.addRoundRect(box, corners, android.graphics.Path.Direction.CW);
                canvas.drawPath(edge, paint);
                if (button > 0f && pressed) {
                    // Material 3's pressed state: a layer of the content's colour, over the section and cut to its shape
                    buttonPaint.setColor(0x24FFFFFF);
                    canvas.drawPath(edge, buttonPaint);
                }
            } else {
                float round = kind == PILL ? box.height() / 2f : radius;
                canvas.drawRoundRect(box, round, round, paint);
            }
            if (kind == CARD && button > 0f && squares) drawButton(canvas);
        }

        /** The button over a card: tonal, a rounded square round the middle of the icon, squarer yet while pressed. */
        private void drawButton(Canvas canvas) {
            float[] at = where();
            if (at == null) return;
            View on = host == null ? null : host.get();
            if (on == null) return;
            buttonPaint.setColor(tonal(on.getContext()));
            float corner = pressed ? buttonPressed : buttonCorner;
            canvas.drawRoundRect(at[0] - button, at[1] - button, at[0] + button, at[1] + button, corner, corner, buttonPaint);
        }

        /** The middle of the icon in `around`, in the view the shape is behind; null when it cannot be told. */
        private float[] where() {
            View centre = around == null ? null : around.get(), on = host == null ? null : host.get();
            // TikTok swaps the icon's view when a button is tapped: the one in the slot now is the one
            if (on != null && kind == CARD && button > 0f) {
                View now = pictureOf(on, 0);
                if (now != null) centre = now;
            }
            if (centre == null || on == null || centre.getWidth() <= 0) return null;
            float x = centre.getWidth() / 2f, y = centre.getHeight() / 2f;
            View step = centre;
            for (int i = 0; i < 6 && step != null && step != on; i++) {
                x += step.getLeft();
                y += step.getTop();
                Object parent = step.getParent();
                step = parent instanceof View ? (View) parent : null;
            }
            return step == on ? new float[] {x, y} : null;
        }

        void select(boolean on) {
            if (selected == on) return;
            selected = on;
            again();
        }

        /** Drawn again, and everything round it: a section reaches past the slot it is the background of. */
        private void again() {
            invalidateSelf();
            View slot = host == null ? null : host.get();
            if (slot == null) return;
            slot.invalidate();
            Object up = slot.getParent();
            if (up instanceof View) ((View) up).invalidate();
        }

        /** Whether the view it is behind is being pressed, for a button that changes shape under a finger. */
        private boolean pressed;

        @Override public boolean isStateful() { return kind == DISC || button > 0f; }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean now = false;
            for (int one : state) if (one == android.R.attr.state_pressed) now = true;
            if (now == pressed) return false;
            pressed = now;
            return true;
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final int OURS = 0x4D61724D, WAS = 0x4D61724E, ROUND = 0x4D61724F, FLAT = 0x4D617250, PUT = 0x4D617252;
    private static final Object NONE = new Object();

    /** Puts a shape behind a view, keeping what was there to put back. One a view, made once. */
    private static Shape wear(View view, int kind) {
        return wear(view, kind, false);
    }

    /**
     * The same, and with `under` what the view had stays on it, over the
     * shape: a ripple or a highlight TikTok gave a button goes on working.
     */
    private static Shape wear(View view, int kind, boolean under) {
        Object worn = view.getTag(OURS);
        Shape shape;
        if (worn instanceof Shape) {
            shape = (Shape) worn;
        } else {
            shape = new Shape(kind);
            Drawable before = view.getBackground();
            view.setTag(WAS, before == null ? NONE : before);
            view.setTag(OURS, shape);
            view.setTag(PUT, under && before != null
                    ? new android.graphics.drawable.LayerDrawable(new Drawable[] {shape, before}) : shape);
        }
        Object put = view.getTag(PUT);
        if (put instanceof Drawable && view.getBackground() != put) view.setBackground((Drawable) put);
        return shape;
    }

    /** Takes the mod's shape off a view and gives it back what it had. */
    private static void strip(View view) {
        Object worn = view.getTag(OURS);
        if (!(worn instanceof Shape)) return;
        Object before = view.getTag(WAS);
        if (view.getBackground() == view.getTag(PUT)) view.setBackground(before instanceof Drawable ? (Drawable) before : null);
        view.setTag(OURS, null);
        view.setTag(WAS, null);
        view.setTag(PUT, null);
        synchronized (WORN) {
            WORN.remove(view);
        }
    }

    // ------------------------------------------------------------ the colours

    private static final int SHELL = 0, CHOSEN = 1, OVER_VIDEO = 2, CHOSEN_OVER_VIDEO = 3, TONAL = 4;
    /** Everything the mod has dressed, to be coloured again when what it lies on changes colour. */
    private static final java.util.WeakHashMap<View, Shape> WORN = new java.util.WeakHashMap<View, Shape>();

    /**
     * The colour of the page a view lies on: the nearest thing behind it that
     * is painted one solid colour.
     *
     * Read off the screen and not asked of the theme, because the theme's
     * answer is the answer for the screen on top at the moment it is asked --
     * and with the mod's own settings on top, or nothing, that was the dark
     * one, and a light page came back wearing dark cards. What is painted
     * behind a view is the page it will be seen on, whoever painted it.
     */
    static int behind(View view) {
        Object parent = view.getParent();
        for (int i = 0; i < 28 && parent instanceof View; i++) {
            Drawable back = ((View) parent).getBackground();
            if (back instanceof android.graphics.drawable.ColorDrawable) {
                int colour = ((android.graphics.drawable.ColorDrawable) back).getColor();
                if (android.graphics.Color.alpha(colour) == 255) return colour;
            }
            parent = ((View) parent).getParent();
        }
        if (Themes.isEnabled()) return Themes.backgroundInUse() | 0xFF000000;
        return Themes.isDark() ? 0xFF121212 : 0xFFFFFFFF;
    }

    /**
     * The colour painted under a view that floats over the screen: the
     * bottom bar, which is drawn over whichever page is showing and is no
     * part of it.
     *
     * Found the way the screen is drawn. Whatever was laid down before the
     * view, in its parent and that parent's parent and so on up, is under
     * it; of those, the one drawn last that is a solid colour where the
     * middle of the view is, is the colour it is seen against. Under a video
     * that is TikTok's black strip, on a profile the profile's own page.
     * Only so many views are looked at, and with nothing found the answer is
     * the page the view's own ancestors are painted with.
     */
    static int under(View view) {
        try {
            int[] at = new int[2];
            view.getLocationOnScreen(at);
            int x = at[0] + view.getWidth() / 2, y = at[1] + view.getHeight() / 2;
            // how many views may be looked at, and how wide one has to be to count:
            // a page is as wide as the bar, a picture in a grid passing under it is not
            int[] left = {600, Math.round(view.getWidth() * 0.8f)};
            View child = view;
            Object parent = view.getParent();
            for (int level = 0; level < 28 && parent instanceof ViewGroup; level++) {
                ViewGroup group = (ViewGroup) parent;
                for (int i = group.indexOfChild(child) - 1; i >= 0; i--) {
                    int found = painted(group.getChildAt(i), x, y, 0, left, at);
                    if (found != 0) return found;
                }
                Drawable back = group.getBackground();
                if (back instanceof android.graphics.drawable.ColorDrawable) {
                    int colour = ((android.graphics.drawable.ColorDrawable) back).getColor();
                    if (android.graphics.Color.alpha(colour) == 255) return colour;
                }
                child = group;
                parent = group.getParent();
            }
        } catch (Throwable ignored) {
            // a screen being taken apart: the page will do
        }
        return behind(view);
    }

    /** The solid colour a view or the last-drawn thing inside it paints at a point on the screen, or nought. */
    private static int painted(View view, int x, int y, int depth, int[] left, int[] at) {
        if (view == null || depth > 24 || left[0]-- <= 0) return 0;
        if (view.getVisibility() != View.VISIBLE || view.getAlpha() < 0.9f || view.getWidth() == 0) return 0;
        view.getLocationOnScreen(at);
        if (x < at[0] || y < at[1] || x >= at[0] + view.getWidth() || y >= at[1] + view.getHeight()) return 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                int found = painted(group.getChildAt(i), x, y, depth + 1, left, at);
                if (found != 0) return found;
            }
        }
        Drawable back = view.getBackground();
        if (view.getWidth() >= left[1] && back instanceof android.graphics.drawable.ColorDrawable) {
            int colour = ((android.graphics.drawable.ColorDrawable) back).getColor();
            if (android.graphics.Color.alpha(colour) == 255) return colour;
        }
        return 0;
    }

    /** What is written on a page of this colour: the theme's own, where that can be read on it. */
    static int ink(int ground) {
        boolean dark = NavBar.bright(ground) < 0.5f;
        int ink = dark ? 0xFFFFFFFF : 0xFF161823;
        if (Themes.isEnabled()) {
            int chosen = Themes.text() | 0xFF000000;
            if ((NavBar.bright(chosen) < 0.5f) != dark) ink = chosen;
        }
        return ink;
    }

    /** The colour of a shape behind a view, for what the shape is. */
    private static int colour(View view, int role) {
        if (role == OVER_VIDEO) return overVideo();
        if (role == CHOSEN_OVER_VIDEO) return chosenOverVideo();
        if (role == TONAL) return tonal(view.getContext());
        int ground = behind(view);
        // a surface one step up from the page: a card, a capsule, a disc
        int shell = NavBar.mix(ground, ink(ground), 0.07f);
        return role == CHOSEN ? NavBar.mix(shell, Accent.colour() | 0xFF000000, 0.30f) : shell;
    }

    /** Whether the page a view lies on is a dark one. */
    private static boolean onDark(View view) {
        return NavBar.bright(behind(view)) < 0.5f;
    }

    private static long coloured;

    /** Now and then, before a frame: a theme that has changed is noticed by what it has painted. */
    private static void recolour() {
        long now = SystemClock.uptimeMillis();
        if (now - coloured < 500L) return;
        coloured = now;
        View[] views;
        Shape[] shapes;
        synchronized (WORN) {
            views = WORN.keySet().toArray(new View[0]);
            shapes = new Shape[views.length];
            for (int i = 0; i < views.length; i++) shapes[i] = WORN.get(views[i]);
        }
        for (int i = 0; i < views.length; i++) {
            View view = views[i];
            Shape shape = shapes[i];
            if (view == null || shape == null || !view.isAttachedToWindow()) continue;
            shape.tint(colour(view, shape.role));
        }
    }

    /**
     * The container of a tonal icon button over a video: Material 3's secondary
     * container in its dark scheme, which is the phone's own accent two steps
     * down from its brightest. Where the phone has no such colours, the mod's
     * accent stirred into a dark grey.
     */
    private static int tonal(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                return context.getColor(android.R.color.system_accent2_700) | 0xFF000000;
            } catch (Throwable ignored) {
                // no dynamic colours here
            }
        }
        return NavBar.mix(0xFF1D1B20, Accent.colour() | 0xFF000000, 0.25f) | 0xFF000000;
    }

    /** A disc over a video: dark whatever the theme, and mostly there. */
    private static int overVideo() {
        return (NavBar.mix(0xFF17161B, Accent.colour() | 0xFF000000, 0.10f) & 0x00FFFFFF) | 0x99000000;
    }

    /** The chosen one of a group that lies over a video. */
    private static int chosenOverVideo() {
        return (NavBar.mix(0xFF17161B, Accent.colour() | 0xFF000000, 0.45f) & 0x00FFFFFF) | 0xE6000000;
    }

    // ---------------------------------------------------- the feed's buttons

    /** How far in, in dp, the column of actions and what lines up with it are moved from the screen's edge. */
    private static final float COLUMN_SHIFT = 14f;

    /** What stands in the column's line, the author's picture above it and the sound's cover below, moved with it. */
    private static void shift(View view, boolean on) {
        float want = on ? -COLUMN_SHIFT * view.getResources().getDisplayMetrics().density : 0f;
        if (view.getTranslationX() != want) view.setTranslationX(want);
    }

    /**
     * What else is on the page of the video a button belongs to: the caption, which gets a
     * card, and the author's picture and the sound's cover, which move with the column.
     * The pager is not walked, so the page is found from the button, upwards.
     */
    private static void companions(View slot, boolean on) {
        try {
            names(slot.getContext());
            if (captionHost == 0) return;
            View page = null;
            Object up = slot.getParent();
            for (int i = 0; i < 12 && up instanceof View; i++) {
                View candidate = (View) up;
                if (candidate.findViewById(captionHost) != null) {
                    page = candidate;
                    break;
                }
                up = candidate.getParent();
            }
            if (page == null) return;
            View host = page.findViewById(captionHost);
            if (host != null) {
                if (on && part(CAPTION)) caption(host);
                else strip(host);
            }
            View box = captionBox == 0 ? null : page.findViewById(captionBox);
            if (box != null) room(box, on && part(CAPTION));
            View follow = followColumn == 0 ? null : page.findViewById(followColumn);
            if (follow != null) {
                shift(follow, on && part(SHIFT));
                float lift = on && part(SHIFT) ? -10f * follow.getResources().getDisplayMetrics().density : 0f;
                if (follow.getTranslationY() != lift) follow.setTranslationY(lift);
                unclip(follow);
            }
            View cover = coverButton == 0 ? null : page.findViewById(coverButton);
            if (cover != null) {
                shift(cover, on && part(SHIFT));
                unclip(cover);
            }
        } catch (Throwable error) {
            Diary.detail("md3: page: " + error);
        }
    }

    /** What is moved sideways is cut at the edge of what it is in, unless that lets it draw outside. */
    private static void unclip(View view) {
        if (view instanceof ViewGroup) ((ViewGroup) view).setClipChildren(false);
        Object up = view.getParent();
        for (int i = 0; i < 4 && up instanceof ViewGroup; i++) {
            ((ViewGroup) up).setClipChildren(false);
            ((ViewGroup) up).setClipToPadding(false);
            up = ((ViewGroup) up).getParent();
        }
    }

    private static final int ROOM = 0x4D617260;

    /**
     * The words of a caption stop short of the column of actions, so that the card they
     * are on does not go under it: the box they are in is given room on the right, and
     * gives it back when the style is switched off.
     */
    private static void room(View box, boolean on) {
        float unit = box.getResources().getDisplayMetrics().density;
        int extra = Math.round(16f * unit);
        Object given = box.getTag(ROOM);
        if (on && given == null) {
            box.setTag(ROOM, Boolean.TRUE);
            box.setPadding(box.getPaddingLeft(), box.getPaddingTop(), box.getPaddingRight() + extra, box.getPaddingBottom());
        } else if (!on && given != null) {
            box.setTag(ROOM, null);
            box.setPadding(box.getPaddingLeft(), box.getPaddingTop(), Math.max(0, box.getPaddingRight() - extra), box.getPaddingBottom());
        }
    }

    private static final String[] TRANSLATE = {"Перевод", "Перевести", "Translate", "Переклад", "Перекласти"};

    /** The translate button of a caption: the words on it are all there is to know it by. */
    private static View translateIn(View view, int depth) {
        if (depth > 12 || view == null || view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof android.widget.TextView) {
            CharSequence text = ((android.widget.TextView) view).getText();
            if (text != null) {
                String said = text.toString().trim();
                for (String word : TRANSLATE) if (word.equalsIgnoreCase(said)) return view;
            }
            return null;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = translateIn(group.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * The caption under a video, on a card: the author, the date, the words and the
     * translate button, and nothing else that happens to be in the same container.
     */
    private static void caption(View host) {
        if (!part(CAPTION)) {
            strip(host);
            return;
        }
        java.util.List<WeakReference<View>> parts = new java.util.ArrayList<WeakReference<View>>();
        for (int id : new int[] {captionTitle, captionTime, captionWords}) {
            View part = id == 0 ? null : host.findViewById(id);
            if (part != null) parts.add(new WeakReference<View>(part));
        }
        View translate = translateIn(host, 0);
        if (translate != null) parts.add(new WeakReference<View>(translate));
        if (parts.isEmpty()) {
            strip(host);
            return;
        }
        float unit = host.getResources().getDisplayMetrics().density;
        Shape card = wear(host, CARD, true);
        card.parts = parts;
        View words = captionWords == 0 ? null : host.findViewById(captionWords);
        card.words = words == null ? null : new WeakReference<View>(words);
        card.host = new WeakReference<View>(host);
        card.pad = 8f * unit;
        // the progress line runs just under the caption: the card stops short of it
        card.padBottom = 3f * unit;
        // what stands on top of a caption (who reposted, the friends who liked it) is not covered by its card
        card.padTop = 3f * unit;
        card.radius = 20f * unit;
        card.stop = 92f * unit;
        if (!card.dressed || card.role != OVER_VIDEO) card.dress(host, OVER_VIDEO);
    }

    /** The words a view shows, for one that is not a TextView: what it tells accessibility. */
    private static String textOf(View view) {
        if (view instanceof android.widget.TextView) {
            CharSequence text = ((android.widget.TextView) view).getText();
            return text == null ? "" : text.toString().trim();
        }
        try {
            android.view.accessibility.AccessibilityNodeInfo info = android.view.accessibility.AccessibilityNodeInfo.obtain();
            view.onInitializeAccessibilityNodeInfo(info);
            CharSequence text = info.getText();
            CharSequence said = text == null || text.length() == 0 ? info.getContentDescription() : text;
            info.recycle();
            return said == null ? "" : said.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Where a view is in one of its ancestors, with whatever either has been moved by; false when it is not in it. */
    private static boolean offset(View view, View in, float[] out) {
        float x = 0f, y = 0f;
        View step = view;
        for (int i = 0; i < 10 && step != null && step != in; i++) {
            x += step.getLeft() + step.getTranslationX();
            y += step.getTop() + step.getTranslationY();
            Object parent = step.getParent();
            step = parent instanceof View ? (View) parent : null;
        }
        out[0] = x;
        out[1] = y;
        return step == in;
    }

    private static final int HIGHLIGHT = 0x4D617280;

    /**
     * What TikTok paints inside a button to show it is pressed is a plain rectangle, and it
     * shows through the section's rounded corners. It is taken off while the section is
     * dressed, and given back when it is not.
     */
    private static void flatten(View view, boolean on, boolean top) {
        if (view == null) return;
        if (!top) {
            Object kept = view.getTag(HIGHLIGHT);
            if (on && kept == null && view.getBackground() != null) {
                view.setTag(HIGHLIGHT, view.getBackground());
                view.setBackground(null);
            } else if (!on && kept instanceof Drawable) {
                view.setTag(HIGHLIGHT, null);
                if (view.getBackground() == null) view.setBackground((Drawable) kept);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) flatten(group.getChildAt(i), on, false);
        }
    }

    /** The picture of a button: the first image in it. */
    private static View pictureOf(View view, int depth) {
        if (view == null || depth > 5) return null;
        if (view instanceof android.widget.ImageView) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = pictureOf(group.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * One of the four buttons beside a video, as TikTok hands it over, and which of the
     * four it is. They are dressed as one column: a container behind them all, and in it a
     * tonal icon button (a rounded square) for each, the count left under it. The container
     * is closed at the first that is shown and at the last, whichever have been switched off.
     */
    static void rail(View slot, int which) {
        try {
            if (slot == null) return;
            forgetIcons(slot, 0);
            icons(slot, which);
            boolean feed = on(FEED);
            companions(slot, feed);
            if (!part(COLUMN)) {
                strip(slot);
                flatten(slot, false, true);
                boolean moved = part(SHIFT);
                slot.setTranslationX(moved ? -COLUMN_SHIFT * slot.getResources().getDisplayMetrics().density : 0f);
                if (moved) unclip(slot);
                return;
            }
            flatten(slot, true, true);
            // the column is wider than the button it is the background of, and a view's
            // background is cut at its edge unless what holds it lets it draw outside
            Object holder = slot.getParent();
            for (int i = 0; i < 3 && holder instanceof ViewGroup; i++) {
                ((ViewGroup) holder).setClipChildren(false);
                ((ViewGroup) holder).setClipToPadding(false);
                holder = ((ViewGroup) holder).getParent();
            }
            // TikTok sets the icons 32 dp in from the edge; the column needs more room than that on each side
            slot.setTranslationX(part(SHIFT) ? -COLUMN_SHIFT * slot.getResources().getDisplayMetrics().density : 0f);
            boolean first = true, last = true;
            for (int i = 0; i < which; i++) if (FeedRail.shown(i)) first = false;
            for (int i = which + 1; i < FeedRail.KEYS.length; i++) if (FeedRail.shown(i)) last = false;
            float unit = slot.getResources().getDisplayMetrics().density;
            View picture = pictureOf(slot, 0);
            // what TikTok lays over a button when it is pressed is a square; the section has its own
            Shape card = wear(slot, CARD, false);
            // the slot is as wide as a touch counts and flush with the screen: the column is drawn
            // round the icon's own place, which is what the button is centred on too
            card.right = 8f * unit;
            card.wide = 72f * unit;
            card.top = 0f;
            card.head = 4f * unit;
            card.gap = 2f * unit;
            card.reach = last ? 5f * unit : 0f;
            card.bottom = last ? 4f * unit : 0f;
            card.ends(first ? 27f * unit : 8f * unit, last ? 22f * unit : 8f * unit);
            card.around = picture == null ? null : new WeakReference<View>(picture);
            card.host = new WeakReference<View>(slot);
            card.button = 21f * unit;
            card.squares = false;
            card.buttonCorner = 15f * unit;
            card.buttonPressed = 9f * unit;
            card.dress(slot, OVER_VIDEO);
        } catch (Throwable error) {
            Diary.detail("md3: rail: " + error);
        }
    }

    // ------------------------------------------------- the feed's icons

    // In a square of twenty-four. The heart and the arrow are Google's Material Icons,
    // the rounded set, which is filled and as heavy as TikTok's own; the bubble and the
    // bookmark are drawn to go with them, rounder than Google's, which are nearly square.
    private static final String LIKE = "M13.35 20.13c-.76.69-1.93.69-2.69-.01l-.11-.1C5.3 15.27 1.87 12.16 2 8.28"
            + "c.06-1.7.93-3.33 2.34-4.29 2.64-1.8 5.9-.96 7.66 1.1 1.76-2.06 5.02-2.91 7.66-1.1 1.41.96 2.28 2.59 2.34 4.29"
            + ".14 3.88-3.3 6.99-8.55 11.76l-.1.09z";
    private static final String COMMENT = "M12 3C6.48 3 2 6.8 2 11.5c0 2.6 1.38 4.93 3.55 6.5-.2 1.2-.83 2.4-1.75 3.3"
            + "-.3.3-.08.8.34.76 2.1-.2 3.9-1 5.2-2.03.85.18 1.74.27 2.66.27 5.52 0 10-3.8 10-8.5S17.52 3 12 3z"
            + "M7.4 10.1c.77 0 1.4.63 1.4 1.4s-.63 1.4-1.4 1.4S6 12.27 6 11.5s.63-1.4 1.4-1.4z"
            + "M12 10.1c.77 0 1.4.63 1.4 1.4s-.63 1.4-1.4 1.4-1.4-.63-1.4-1.4.63-1.4 1.4-1.4z"
            + "M16.6 10.1c.77 0 1.4.63 1.4 1.4s-.63 1.4-1.4 1.4-1.4-.63-1.4-1.4.63-1.4 1.4-1.4z";
    private static final String SAVE = "M7.5 3h9q2.5 0 2.5 2.5v14.1q0 1.4-1.3.85L12 18.2l-5.7 2.25Q5 21 5 19.6V5.5Q5 3 7.5 3z";
    private static final String SHARE = "M10 9V7.41c0-.89-1.08-1.34-1.71-.71L3.7 11.29c-.39.39-.39 1.02 0 1.41l4.59 4.59"
            + "c.63.63 1.71.19 1.71-.7V14.9c5 0 8.5 1.6 11 5.1-1-5-4-10-11-11z";
    private static final String[] GLYPHS = {LIKE, COMMENT, SAVE, SHARE};
    private static final android.graphics.Path[] DRAWN = new android.graphics.Path[GLYPHS.length];

    private static android.graphics.Path glyph(int which) {
        android.graphics.Path known = DRAWN[which];
        if (known != null) return known;
        final android.graphics.Path path = new android.graphics.Path();
        // the dots of the bubble are holes in it
        path.setFillType(android.graphics.Path.FillType.EVEN_ODD);
        NavPlan.trace(GLYPHS[which], new NavPlan.Pen() {
            @Override public void move(float x, float y) { path.moveTo(x, y); }
            @Override public void line(float x, float y) { path.lineTo(x, y); }
            @Override public void curve(float x1, float y1, float x2, float y2, float x, float y) {
                path.cubicTo(x1, y1, x2, y2, x, y);
            }
            @Override public void close() { path.close(); }
        });
        DRAWN[which] = path;
        return path;
    }

    /**
     * One of those icons as a picture TikTok's own icon can hold in place of
     * its own. TikTok colours what it holds by handing it a filter and an
     * alpha, so this takes both and is white until told otherwise: a liked
     * heart comes out red the way TikTok's does.
     */
    private static final class Glyph extends Drawable {
        final int which;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Glyph(int which) {
            this.which = which;
            paint.setColor(0xFFFFFFFF);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect box = getBounds();
            if (box.isEmpty()) return;
            sample();
            int saved = canvas.save();
            // Google leaves more room round an icon than TikTok does
            float grown = 1.14f, side = Math.min(box.width(), box.height()) * grown;
            canvas.translate(box.exactCenterX() - side / 2f, box.exactCenterY() - side / 2f);
            // the share arrow is Google's reply arrow, turned to point the other way
            if (which == 3) canvas.scale(-1f, 1f, side / 2f, 0f);
            canvas.scale(side / 24f, side / 24f);
            canvas.drawPath(glyph(which), paint);
            canvas.restoreToCount(saved);
        }

        /** Whether what it is painted with is a colour and not white, found by painting one pixel with it. */
        boolean vivid;
        private static final android.graphics.Bitmap PIXEL = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
        private static final Canvas SAMPLER = new Canvas(PIXEL);

        private void sample() {
            PIXEL.eraseColor(0);
            SAMPLER.drawRect(0f, 0f, 1f, 1f, paint);
            int seen = PIXEL.getPixel(0, 0);
            int high = Math.max(android.graphics.Color.red(seen), Math.max(android.graphics.Color.green(seen), android.graphics.Color.blue(seen)));
            int low = Math.min(android.graphics.Color.red(seen), Math.min(android.graphics.Color.green(seen), android.graphics.Color.blue(seen)));
            vivid = android.graphics.Color.alpha(seen) > 40 && high - low > 70;
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); sample(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); sample(); }
        @Override public void setTint(int colour) { paint.setColor(colour); sample(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final String TUX_ICON = "com.bytedance.tux.drawable.TuxIconDrawable";
    private static java.lang.reflect.Field tuxPicture;
    private static boolean tuxAsked;
    /** What each of TikTok's icons held before it was given one of these, to give back. */
    private static final java.util.WeakHashMap<Drawable, Drawable> HELD = new java.util.WeakHashMap<Drawable, Drawable>();
    private static final int PICTURE = 0x4D617254;

    /**
     * The icon of one of the buttons beside a video, made Google's. TikTok's
     * icon is a holder with the picture inside it, and it is the picture that
     * is changed: the holder goes on sizing it, colouring it and animating it
     * as before. Looked at again before every frame, because a tap on the
     * heart swaps the holder for another.
     */
    private static void icon(View view, int which, int depth) {
        if (view == null || depth > 5 || which < 0 || which >= GLYPHS.length) return;
        if (view instanceof android.widget.ImageView) {
            android.widget.ImageView image = (android.widget.ImageView) view;
            Drawable shown = image.getDrawable();
            if (shown == null || shown == view.getTag(PICTURE)) return;
            view.setTag(PICTURE, shown);
            if (!shown.getClass().getName().equals(TUX_ICON)) return;
            try {
                if (!tuxAsked) {
                    tuxAsked = true;
                    java.lang.reflect.Field first = null;
                    for (java.lang.reflect.Field field : shown.getClass().getDeclaredFields()) {
                        if (field.getType() != Drawable.class || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                        if (first == null || field.getName().compareTo(first.getName()) < 0) first = field;
                    }
                    if (first != null) first.setAccessible(true);
                    tuxPicture = first;
                }
                if (tuxPicture == null) return;
                Object inside = tuxPicture.get(shown);
                boolean on = on(ICONS);
                if (on && inside instanceof Drawable && !(inside instanceof Glyph)) {
                    synchronized (HELD) {
                        HELD.put(shown, (Drawable) inside);
                    }
                    Glyph ours = new Glyph(which);
                    ours.setBounds(((Drawable) inside).getBounds());
                    tuxPicture.set(shown, ours);
                    shown.invalidateSelf();
                } else if (!on && inside instanceof Glyph) {
                    Drawable before;
                    synchronized (HELD) {
                        before = HELD.remove(shown);
                    }
                    if (before != null) {
                        tuxPicture.set(shown, before);
                        shown.invalidateSelf();
                    }
                }
            } catch (Throwable error) {
                Diary.detail("md3: icon: " + error);
                tuxPicture = null;
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) icon(group.getChildAt(i), which, depth + 1);
        }
    }

    /** TikTok lays a rectangle over a button while it is pressed, as the view's foreground; the section is what shows it. */
    private static void noForeground(View view, int depth) {
        if (view == null || depth > 4) return;
        if (view.getForeground() != null) view.setForeground(null);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) noForeground(group.getChildAt(i), depth + 1);
        }
    }

    /** Whether the icon of a button wears a colour of its own (TikTok's red heart, its saved bookmark) and not the white of one at rest. */
    private static boolean vivid(View slot) {
        View picture = pictureOf(slot, 0);
        if (!(picture instanceof android.widget.ImageView)) return false;
        Drawable shown = ((android.widget.ImageView) picture).getDrawable();
        if (shown == null) return false;
        Rect at = shown.getBounds();
        if (at.isEmpty()) return false;
        // the icon is painted small, off the screen, and looked at: a colour of its own is a button that is on
        if (SAMPLE == null) {
            SAMPLE = android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888);
            SAMPLER = new Canvas(SAMPLE);
        }
        SAMPLE.eraseColor(0);
        int saved = SAMPLER.save();
        SAMPLER.scale(24f / at.width(), 24f / at.height());
        SAMPLER.translate(-at.left, -at.top);
        try {
            shown.draw(SAMPLER);
        } finally {
            SAMPLER.restoreToCount(saved);
        }
        for (int y = 0; y < 24; y += 2) {
            for (int x = 0; x < 24; x += 2) {
                int seen = SAMPLE.getPixel(x, y);
                if (android.graphics.Color.alpha(seen) < 160) continue;
                int high = Math.max(android.graphics.Color.red(seen), Math.max(android.graphics.Color.green(seen), android.graphics.Color.blue(seen)));
                int low = Math.min(android.graphics.Color.red(seen), Math.min(android.graphics.Color.green(seen), android.graphics.Color.blue(seen)));
                if (high - low > 70) return true;
            }
        }
        return false;
    }

    private static android.graphics.Bitmap SAMPLE;
    private static Canvas SAMPLER;

    /** The icons of one button looked at again: which button it is, is what the mod was told when handed it. */
    static void icons(View slot, int which) {
        try {
            if (slot != null && slot.getVisibility() == View.VISIBLE) {
                icon(slot, which, 0);
                // TikTok sets its own background on a button while it is pressed: the section goes back behind it
                Object put = slot.getTag(PUT);
                if (put instanceof Drawable && slot.getBackground() != put) slot.setBackground((Drawable) put);
                noForeground(slot, 0);
                // a pressed button is dimmed by TikTok (eased down to 0.6 and back up), and what is dimmed is cut to its edges: the section,
                // which reaches past them, came out a rectangle. It is lit by its own pressed layer instead.
                if (slot.getAlpha() < 1f && slot.getAlpha() > 0.55f) slot.setAlpha(1f);
                // the heart and the bookmark: on, their section is a circle
                if (which == 0 || which == 2) {
                    Object card = slot.getTag(OURS);
                    if (card instanceof Shape) {
                        Shape section = (Shape) card;
                        long now = android.os.SystemClock.uptimeMillis();
                        if (now - section.checked > 120L) {
                            section.checked = now;
                            section.select(vivid(slot));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // a button taken apart in the middle of a frame is looked at again on the next
        }
    }

    /** The switch was turned: every icon is looked at afresh, whatever it was the last time. */
    private static void forgetIcons(View view, int depth) {
        if (view == null || depth > 5) return;
        view.setTag(PICTURE, null);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) forgetIcons(group.getChildAt(i), depth + 1);
        }
    }

    // ------------------------------------------------------- finding things

    private static final String NAV_BAR = "com.bytedance.tux.navigation.TuxNavBar";
    private static final String ICON = "com.bytedance.tux.icon.TuxIconView";
    private static final String ROW = "com.ss.android.ugc.aweme.notice.api.view.MultiSelectConstraintLayout";
    private static final String CELL = "com.ss.android.ugc.aweme.base.widget.FixedRatioFrameLayout";
    private static final String PAGER = "com.ss.android.ugc.aweme.common.widget.VerticalViewPager";
    private static final int K_OTHER = 0, K_NAV = 1, K_ICON = 2, K_ROW = 3, K_CELL = 4, K_PAGER = 5;
    private static final IdentityHashMap<Class<?>, Integer> KINDS = new IdentityHashMap<Class<?>, Integer>();

    private static int kind(View view) {
        Class<?> type = view.getClass();
        Integer known = KINDS.get(type);
        if (known != null) return known;
        String name = type.getName();
        int kind = name.equals(NAV_BAR) ? K_NAV : name.equals(ICON) ? K_ICON : name.equals(ROW) ? K_ROW
                : name.equals(CELL) ? K_CELL : name.equals(PAGER) ? K_PAGER : K_OTHER;
        KINDS.put(type, kind);
        return kind;
    }

    /** What TikTok 47.2 calls the views that have no class of their own to be known by. */
    private static int feedLeft, feedSearch, feedTop, profileRoot, profileTabs, statNumber;
    private static int captionBox, captionHost, captionTitle, captionTime, captionWords, followColumn, coverButton;
    private static boolean named;

    private static void names(Context context) {
        if (named) return;
        named = true;
        try {
            android.content.res.Resources resources = context.getResources();
            String pack = context.getPackageName();
            feedLeft = resources.getIdentifier("k_2", "id", pack);
            feedSearch = resources.getIdentifier("k_5", "id", pack);
            feedTop = resources.getIdentifier("uvh", "id", pack);
            profileRoot = resources.getIdentifier("t7i", "id", pack);
            profileTabs = resources.getIdentifier("z2h", "id", pack);
            statNumber = resources.getIdentifier("t7t", "id", pack);
            captionHost = resources.getIdentifier("bqx", "id", pack);
            captionBox = resources.getIdentifier("bqw", "id", pack);
            captionTitle = resources.getIdentifier("title", "id", pack);
            captionTime = resources.getIdentifier("tv_post_time", "id", pack);
            captionWords = resources.getIdentifier("desc", "id", pack);
            followColumn = resources.getIdentifier("i_a", "id", pack);
            coverButton = resources.getIdentifier("pnm", "id", pack);
            Diary.detail("md3: names " + (feedLeft != 0 ? 1 : 0) + (feedSearch != 0 ? 1 : 0) + (profileRoot != 0 ? 1 : 0)
                    + (profileTabs != 0 ? 1 : 0) + (statNumber != 0 ? 1 : 0));
        } catch (Throwable error) {
            Diary.detail("md3: names: " + error);
        }
    }

    // ---------------------------------------------------------- one screen

    private static final int WATCHED = 0x4D617251;
    private static final long QUIET = 350L;

    private static final class Screen implements ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> root;
        long looked;
        boolean owed;
        /** Lists whose rows come and go without the screen being laid out. */
        WeakReference<ViewGroup> rows = new WeakReference<ViewGroup>(null), cells = new WeakReference<ViewGroup>(null);
        WeakReference<ViewGroup> tabs = new WeakReference<ViewGroup>(null), feedTabs = new WeakReference<ViewGroup>(null);
        int rowsSeen, cellsSeen, tabChosen = -2, feedChosen = -2;
        final Runnable later = () -> {
            owed = false;
            look();
        };

        Screen(View root) {
            this.root = new WeakReference<View>(root);
        }

        @Override
        public void onGlobalLayout() {
            View view = root.get();
            if (view == null) return;
            long now = SystemClock.uptimeMillis();
            if (now - looked < QUIET) {
                if (!owed) {
                    owed = true;
                    view.postDelayed(later, QUIET - (now - looked) + 20);
                }
                return;
            }
            look();
        }

        void look() {
            View view = root.get();
            if (view == null || !view.isAttachedToWindow()) return;
            looked = SystemClock.uptimeMillis();
            try {
                long probe = Probe.start();
                names(view.getContext());
                visit(view, 0, false);
                FeedRail.restyle();
                Probe.stop(Probe.LOOK, probe);
            } catch (Throwable error) {
                Diary.detail("md3: " + error);
            }
        }

        @Override
        public boolean onPreDraw() {
            long whole = Probe.start();
            try {
                long probe = Probe.start();
                FeedRail.icons();
                Probe.stop(Probe.RAIL_ICONS, probe);
                probe = Probe.start();
                FeedHide.frame();
                Probe.stop(Probe.HIDE_FRAME, probe);
                recolour();
                // Every row there is, every frame: which of them begins a group
                // and which ends one changes as the list is scrolled, and there
                // are a dozen of them.
                ViewGroup list = rows.get();
                if (list != null && list.isAttachedToWindow()) {
                    int many = list.getChildCount();
                    for (int i = 0; i < many; i++) {
                        View child = list.getChildAt(i);
                        if (kind(child) != K_ROW) {
                            row(child, 0, true, true);
                            continue;
                        }
                        boolean head = i == 0 ? !list.canScrollVertically(-1) : kind(list.getChildAt(i - 1)) != K_ROW;
                        boolean tail = i == many - 1 ? !list.canScrollVertically(1) : kind(list.getChildAt(i + 1)) != K_ROW;
                        row(child, 0, head, tail);
                    }
                }
                ViewGroup grid = cells.get();
                if (grid != null && grid.isAttachedToWindow() && grid.getChildCount() > 0) {
                    if (grid.getChildCount() != cellsSeen) {
                        cellsSeen = grid.getChildCount();
                        for (int i = 0; i < grid.getChildCount(); i++) cell(grid.getChildAt(i));
                    }
                    for (int i = 0; i < 3 && i < grid.getChildCount(); i++) {
                        cell(grid.getChildAt(i));
                        cell(grid.getChildAt(grid.getChildCount() - 1 - i));
                    }
                }
                ViewGroup strip = tabs.get();
                if (strip != null && strip.isAttachedToWindow()) {
                    int chosen = chosenIn(strip);
                    if (chosen != tabChosen) {
                        tabChosen = chosen;
                        pills(strip, on(PROFILE), 3f, 3f, CHOSEN);
                    }
                }
                strip = feedTabs.get();
                if (strip != null && strip.isAttachedToWindow()) {
                    int chosen = chosenIn(strip);
                    if (chosen != feedChosen) {
                        feedChosen = chosen;
                        pills(strip, part(TABS), 2f, 13f, CHOSEN_OVER_VIDEO);
                    }
                    // TikTok gives the line its colour back when the feed under it changes
                    underline(strip, false);
                }
            } catch (Throwable ignored) {
                // a list taken apart in the middle of a frame is looked at again on the next
            }
            Probe.stop(Probe.PRE_DRAW, whole);
            return true;
        }

        private void visit(View view, int depth, boolean inProfile) {
            if (view == null || depth > 45 || view.getVisibility() != View.VISIBLE) return;
            int id = view.getId();
            if (id != View.NO_ID && id != 0) {
                if (id == feedLeft || id == feedSearch) {
                    disc(view, part(DISCS), OVER_VIDEO, 19f);
                    return;
                }
                if (id == feedTop && view instanceof ViewGroup) {
                    FeedHide.top((ViewGroup) view);
                    feedTop((ViewGroup) view);
                }
                if (id == profileRoot) inProfile = true;
                if (id == profileTabs && view instanceof ViewGroup) {
                    tabStrip((ViewGroup) view);
                    return;
                }
                if (id == statNumber) {
                    stats(view);
                    return;
                }
            }
            if (BurnEye.eye(view)) {
                disc(view, part(DISCS), OVER_VIDEO, 19f);
                return;
            }
            int kind = kind(view);
            if (kind == K_PAGER) return;  // a video and everything on it: the buttons beside it come another way
            if (kind == K_ROW) {
                Object parent = view.getParent();
                if (parent instanceof ViewGroup) {
                    ViewGroup list = (ViewGroup) parent;
                    // the first row is wrapped in a header; the list is what holds the plain ones
                    if (list.getChildCount() > 2 || rows.get() == null) {
                        if (rows.get() != list) rowsSeen = -1;
                        rows = new WeakReference<ViewGroup>(list);
                    }
                }
                row(view, 0, true, true);
                return;
            }
            if (kind == K_CELL) {
                if (!inProfile) return;
                Object parent = view.getParent();
                if (parent instanceof ViewGroup && cells.get() != parent) {
                    cells = new WeakReference<ViewGroup>((ViewGroup) parent);
                    cellsSeen = -1;
                }
                cell(view);
                return;
            }
            if (kind == K_NAV) {
                // Over a profile's picture the buttons are white, and a disc the
                // colour of a light page would hide them; there they go without.
                icons(view, 0, inProfile ? on(PROFILE) && onDark(view) : on(INBOX));
                return;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) visit(group.getChildAt(i), depth + 1, inProfile);
            }
        }

        /**
         * The words across the top of the feed: a capsule behind them, a pill
         * on the one that is chosen, and no line under it.
         */
        private void feedTop(ViewGroup row) {
            ViewGroup window = null;
            for (int i = 0; i < row.getChildCount() && window == null; i++) {
                View child = row.getChildAt(i);
                if (child instanceof ViewGroup && kind(child) != K_ICON && child.getVisibility() == View.VISIBLE) {
                    window = (ViewGroup) child;
                }
            }
            // the window, what scrolls in it, and the strip the words are on
            View inner = window == null || window.getChildCount() == 0 ? null : window.getChildAt(0);
            View words = inner instanceof ViewGroup && ((ViewGroup) inner).getChildCount() > 0
                    ? ((ViewGroup) inner).getChildAt(0) : null;
            if (!(words instanceof ViewGroup) || ((ViewGroup) words).getChildCount() < 2) return;
            ViewGroup strip = (ViewGroup) words;
            if (!part(TABS)) {
                Md3.strip(window);
                for (int i = 0; i < strip.getChildCount(); i++) Md3.strip(strip.getChildAt(i));
                underline(strip, true);
                feedTabs = new WeakReference<ViewGroup>(null);
                return;
            }
            float unit = row.getResources().getDisplayMetrics().density;
            Shape capsule = wear(window, PILL);
            capsule.top = capsule.bottom = 9f * unit;
            capsule.dress(window, OVER_VIDEO);
            if (feedTabs.get() != strip) {
                feedTabs = new WeakReference<ViewGroup>(strip);
                feedChosen = -2;
            }
        }

        /** The strip of tabs under a profile: a capsule, a pill on the chosen tab, and no line under it. */
        private void tabStrip(ViewGroup holder) {
            ViewGroup strip = holder.getChildCount() == 1 && holder.getChildAt(0) instanceof ViewGroup
                    ? (ViewGroup) holder.getChildAt(0) : null;
            if (strip == null) return;
            if (!on(PROFILE)) {
                Md3.strip(holder);
                for (int i = 0; i < strip.getChildCount(); i++) Md3.strip(strip.getChildAt(i));
                tabs = new WeakReference<ViewGroup>(null);
                return;
            }
            wear(holder, PILL).dress(holder, SHELL);
            if (strip.getTag(FLAT) == null) {
                strip.setTag(FLAT, Boolean.TRUE);
                try {
                    strip.getClass().getMethod("setSelectedIndicatorHeight", int.class).invoke(strip, 0);
                } catch (Throwable error) {
                    Diary.detail("md3: the line under the tabs stays: " + error);
                }
                // and the hairline TikTok draws under the whole strip
                Object parent = holder.getParent();
                if (parent instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) parent;
                    int at = group.indexOfChild(holder);
                    View next = at >= 0 && at + 1 < group.getChildCount() ? group.getChildAt(at + 1) : null;
                    if (next != null && !(next instanceof ViewGroup) && next.getHeight() > 0
                            && next.getHeight() <= 3 * next.getResources().getDisplayMetrics().density) {
                        next.setAlpha(0f);
                    }
                }
            }
            tabs = new WeakReference<ViewGroup>(strip);
            tabChosen = -2;
        }
    }

    private static int chosenIn(ViewGroup strip) {
        for (int i = 0; i < strip.getChildCount(); i++) if (strip.getChildAt(i).isSelected()) return i;
        return -1;
    }

    /** A pill on the tab that is chosen, and nothing on the others. */
    private static void pills(ViewGroup strip, boolean on, float sides, float ends, int role) {
        float unit = strip.getResources().getDisplayMetrics().density;
        for (int i = 0; i < strip.getChildCount(); i++) {
            View tab = strip.getChildAt(i);
            if (tab.isSelected() && on) {
                Shape pill = wear(tab, PILL);
                pill.left = pill.right = sides * unit;
                pill.top = pill.bottom = ends * unit;
                pill.dress(tab, role);
            } else {
                strip(tab);
            }
        }
    }

    private static java.lang.reflect.Method linePaint;
    private static boolean lineAsked;

    /** The line TikTok draws under the chosen word of the feed: out of sight, or back. */
    private static void underline(ViewGroup strip, boolean shown) {
        try {
            if (!lineAsked) {
                lineAsked = true;
                linePaint = strip.getClass().getMethod("getMSelectedIndicatorPaint");
            }
            if (linePaint == null) return;
            Object paint = linePaint.invoke(strip);
            if (!(paint instanceof Paint)) return;
            int wanted = shown ? 255 : 0;
            if (((Paint) paint).getAlpha() != wanted) {
                ((Paint) paint).setAlpha(wanted);
                // the line has a shadow, and a shadow is drawn however faint the line
                if (!shown) ((Paint) paint).clearShadowLayer();
                strip.invalidate();
            }
        } catch (Throwable error) {
            if (linePaint != null) Diary.detail("md3: the line under the feed's tabs stays: " + error);
            linePaint = null;
        }
    }

    /** A disc behind a button that is only an icon. */
    private static void disc(View view, boolean on, int role, float radius) {
        if (!on) {
            strip(view);
            return;
        }
        if (view.getBackground() != null && !(view.getTag(OURS) instanceof Shape)) return;
        Shape disc = wear(view, DISC);
        disc.radius = radius * view.getResources().getDisplayMetrics().density;
        disc.dress(view, role);
    }

    /** The buttons of a bar across the top of a screen. */
    private static void icons(View view, int depth, boolean on) {
        if (depth > 6 || view.getVisibility() != View.VISIBLE) return;
        if (kind(view) == K_ICON) {
            float unit = view.getResources().getDisplayMetrics().density;
            if (view.isFocusable() && view.getWidth() >= 32 * unit) disc(view, on, SHELL, 19f);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) icons(group.getChildAt(i), depth + 1, on);
        }
    }

    /**
     * A row of the inbox, on a card. The cards of rows that follow one
     * another lie close and are square where they meet, so a run of rows
     * reads as one container; `head` and `tail` say whether this one begins
     * the run or ends it.
     */
    private static void row(View view, int depth, boolean head, boolean tail) {
        if (view == null) return;
        if (kind(view) != K_ROW) {
            // the header at the top of the list wraps a row, which stands by itself
            if (depth == 0 && view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount() && i < 4; i++) row(group.getChildAt(i), 1, true, true);
            }
            return;
        }
        if (!on(INBOX)) {
            strip(view);
            return;
        }
        float unit = view.getResources().getDisplayMetrics().density;
        Shape card = wear(view, CARD);
        card.left = card.right = 8f * unit;
        card.top = card.bottom = unit;
        card.ends(head ? 22f * unit : 5f * unit, tail ? 22f * unit : 5f * unit);
        // this is asked of every row before every frame; the colour is worked
        // out once, and after that by what looks at every shape twice a second
        if (!card.dressed || card.role != SHELL) card.dress(view, SHELL);
    }

    private static final ViewOutlineProvider ROUNDED = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            float unit = view.getResources().getDisplayMetrics().density;
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 12f * unit);
        }
    };

    /** A picture in a profile's grid, with its corners taken off. */
    private static void cell(View view) {
        if (view == null || kind(view) != K_CELL) return;
        boolean on = on(PROFILE);
        if (on == (view.getTag(ROUND) != null)) return;
        if (on) {
            view.setTag(ROUND, Boolean.TRUE);
            view.setOutlineProvider(ROUNDED);
            view.setClipToOutline(true);
        } else {
            view.setTag(ROUND, null);
            view.setClipToOutline(false);
            view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }
    }

    /** The three numbers of a profile, on one card. Given one of the numbers. */
    private static void stats(View number) {
        Object stat = number.getParent();
        Object line = stat instanceof View ? ((View) stat).getParent() : null;
        if (!(line instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) line;
        if (!on(PROFILE)) {
            strip(row);
            return;
        }
        if (row.getTag(OURS) instanceof Shape && row.getBackground() == row.getTag(OURS)) {
            ((Shape) row.getTag(OURS)).dress(row, SHELL);
            return;
        }
        if (row.getBackground() != null && !(row.getTag(OURS) instanceof Shape)) return;
        float unit = row.getResources().getDisplayMetrics().density;
        Shape card = wear(row, CARD);
        // wider than the numbers it is behind, which start at its very edge
        card.left = -10f * unit;
        card.top = -6f * unit;
        card.bottom = -4f * unit;
        card.right = 0f;
        card.radius = 18f * unit;
        card.dress(row, SHELL);
        // and so drawn past the row, which its parents would otherwise cut off
        Object above = row.getParent();
        for (int i = 0; i < 2 && above instanceof ViewGroup; i++) {
            ((ViewGroup) above).setClipChildren(false);
            above = ((ViewGroup) above).getParent();
        }
    }

    // ------------------------------------------------------------ attaching

    /** A screen came up. Only the one the feed, the profile and the inbox are on is watched. */
    static void attach(Activity activity) {
        try {
            String name = activity.getClass().getName();
            if (!name.endsWith(".SplashActivity") && !name.endsWith(".MainActivity")) return;
            View root = activity.getWindow().getDecorView();
            Object known = root.getTag(WATCHED);
            Screen screen;
            if (known instanceof Screen) {
                screen = (Screen) known;
            } else {
                screen = new Screen(root);
                root.setTag(WATCHED, screen);
                root.getViewTreeObserver().addOnGlobalLayoutListener(screen);
                root.getViewTreeObserver().addOnPreDrawListener(screen);
            }
            // a switch may have been turned while another screen was up
            screen.rowsSeen = screen.cellsSeen = -1;
            screen.tabChosen = screen.feedChosen = -2;
            root.post(screen.later);
        } catch (Throwable error) {
            Diary.detail("md3: " + error);
        }
    }
}
