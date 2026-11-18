package cat.narezany.margyt;

import android.view.ViewOutlineProvider;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings home in Material 3 Expressive, below the hero card: the round buttons of the bar, the
 * four quick switches, the list of sections as one connected group, and the two buttons at the foot.
 *
 * Nothing here is a setting of its own. A quick switch is a switch the sections already have, written
 * the same way, and which four are on the grid is the person's choice, kept in the mod's preferences.
 * Every colour is a role taken from the page and the accent (see {@link ProfileM3#rolesFor}).
 */
final class SettingsHome {

    private SettingsHome() {}

    // ------------------------------------------------------------------ what can be pinned

    interface Get { boolean get(); }
    interface Put { void put(boolean on); }

    /** A switch the sections have, so that it can be put on the grid. */
    static final class Pin {
        final String id, icon, title;
        final Get get;
        final Put put;
        Pin(String id, String icon, String title, Get get, Put put) {
            this.id = id; this.icon = icon; this.title = title; this.get = get; this.put = put;
        }
    }

    private static List<Pin> all;

    /** The boolean settings of the mod, the ones with a switch in the sections. */
    static synchronized List<Pin> pins() {
        if (all != null) return all;
        List<Pin> out = new ArrayList<>();
        out.add(new Pin("ghost", "visibility_off", Local.of("Режим призрака"), () -> Ghost.on(Ghost.KEY), on -> Ghost.set(Ghost.KEY, on)));
        out.add(new Pin("ads", "block", Text.HIDE_ADS, Feed::isEnabled, Feed::setEnabled));
        out.add(new Pin("watermark", "download", Text.NO_WATERMARK, Download::isEnabled, Download::setEnabled));
        out.add(new Pin("md3profile", "tune", Text.MD3_PROFILE, () -> Md3.isOn(Md3.KEY_PROFILE), on -> Md3.set(Md3.KEY_PROFILE, on)));
        out.add(new Pin("md3feed", "tune", Text.MD3_FEED, () -> Md3.isOn(Md3.KEY_FEED), on -> Md3.set(Md3.KEY_FEED, on)));
        out.add(new Pin("md3icons", "tune", Text.MD3_ICONS, () -> Md3.isOn(Md3.KEY_ICONS), on -> Md3.set(Md3.KEY_ICONS, on)));
        out.add(new Pin("md3inbox", "tune", Text.MD3_INBOX, () -> Md3.isOn(Md3.KEY_INBOX), on -> Md3.set(Md3.KEY_INBOX, on)));
        out.add(new Pin("chatm3", "chat", Local.of("MD3 чат"), ChatM3::isEnabled, ChatM3::setEnabled));
        out.add(new Pin("profilem3", "tune", Local.of("Раскладка профиля: аватар слева, карточки, кнопки"), ProfileM3::isEnabled, ProfileM3::setEnabled));
        out.add(new Pin("folders", "chat", Local.of("Папки в личных сообщениях"), ChatFolders::enabled, ChatFolders::setEnabled));
        out.add(new Pin("times", "schedule", Local.of("Время под сообщениями"), MessageTimes::enabled, MessageTimes::setEnabled));
        out.add(new Pin("hidelive", "visibility_off", Text.HIDE_LIVE, () -> Feed.hides(Feed.KEY_LIVE), on -> Feed.setHides(Feed.KEY_LIVE, on)));
        out.add(new Pin("hidephotos", "image", Text.HIDE_PHOTOS, () -> Feed.hides(Feed.KEY_PHOTOS), on -> Feed.setHides(Feed.KEY_PHOTOS, on)));
        out.add(new Pin("hidestories", "visibility_off", Text.HIDE_STORIES, () -> Feed.hides(Feed.KEY_STORIES), on -> Feed.setHides(Feed.KEY_STORIES, on)));
        out.add(new Pin("hidesuggest", "visibility_off", Text.HIDE_SUGGESTIONS, () -> Feed.hides(Feed.KEY_SUGGESTIONS), on -> Feed.setHides(Feed.KEY_SUGGESTIONS, on)));
        out.add(new Pin("sound", "volume_up", Text.SOUND, Sound::isEnabled, Sound::setEnabled));
        out.add(new Pin("seekbar", "timeline", Text.SEEKBAR, Seekbar::isEnabled, Seekbar::setEnabled));
        out.add(new Pin("dates", "info", Text.ALWAYS_DATE, Dates::isEnabled, Dates::setEnabled));
        out.add(new Pin("regionflag", "language", Local.of("Страна видео у ника"), RegionFlag::isEnabled, RegionFlag::setEnabled));
        out.add(new Pin("always", "download", Text.DOWNLOAD_ALWAYS, Download::isAlways, Download::setAlways));
        out.add(new Pin("avatars", "place", Text.SAVE_AVATARS_ON, Avatars::isEnabled, Avatars::setEnabled));
        out.add(new Pin("stickers", "star", Text.SAVE_STICKERS_ON, Stickers::isEnabled, Stickers::setEnabled));
        out.add(new Pin("loading", "palette", Text.LOADING_SHAPES, Loading::isEnabled, Loading::setEnabled));
        out.add(new Pin("corners", "image", Text.ROUND_CARDS, Corners::isEnabled, Corners::setEnabled));
        out.add(new Pin("navcapsule", "tune", Text.NAV_CAPSULE, NavBar::isEnabled, NavBar::setEnabled));
        all = out;
        return out;
    }

    private static final String KEY_PINS = "settings_quick", KEY_RECENT = "settings_recent";
    private static final int PLACES = 4, KEEP = 12;

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }

    private static List<String> list(String saved) {
        List<String> out = new ArrayList<>();
        if (saved != null && saved.length() > 0) for (String part : saved.split(",", -1)) out.add(part);
        return out;
    }

    /**
     * The four on the grid, in order. A place the person has chosen by hand keeps what they put there;
     * the others are the switches they turned on most lately, most recent first. With nothing turned on
     * yet (a new install) it is the switches that are on now, and after those the rest of the list.
     */
    static String[] chosen(Context context) {
        SharedPreferences prefs = prefs(context);
        List<String> manual = list(prefs.getString(KEY_PINS, ""));
        List<String> recent = list(prefs.getString(KEY_RECENT, ""));
        List<String> order = new ArrayList<>();
        for (String id : recent) if (find(id) != null && !order.contains(id)) order.add(id);
        if (order.size() < PLACES) {
            for (Pin pin : pins()) if (!order.contains(pin.id) && pin.get.get()) order.add(pin.id);
            for (Pin pin : pins()) if (!order.contains(pin.id)) order.add(pin.id);
        }
        String[] out = new String[PLACES];
        List<String> used = new ArrayList<>();
        for (int i = 0; i < PLACES; i++) {
            String id = i < manual.size() ? manual.get(i) : "";
            if (id.length() > 0 && find(id) != null && !used.contains(id)) { out[i] = id; used.add(id); }
        }
        int next = 0;
        for (int i = 0; i < PLACES; i++) {
            if (out[i] != null) continue;
            while (next < order.size() && used.contains(order.get(next))) next++;
            out[i] = next < order.size() ? order.get(next) : pins().get(0).id;
            used.add(out[i]);
        }
        return out;
    }

    static Pin find(String id) {
        for (Pin pin : pins()) if (pin.id.equals(id)) return pin;
        return null;
    }

    /** A switch was turned on, in a section or on the grid: it is the latest. */
    static void turnedOn(Context context, String title) {
        Pin pin = null;
        for (Pin one : pins()) if (one.title.equals(title)) { pin = one; break; }
        if (pin == null) return;
        SharedPreferences prefs = prefs(context);
        List<String> recent = list(prefs.getString(KEY_RECENT, ""));
        recent.remove(pin.id);
        recent.add(0, pin.id);
        while (recent.size() > KEEP) recent.remove(recent.size() - 1);
        prefs.edit().putString(KEY_RECENT, TextUtils.join(",", recent)).apply();
    }

    /** Puts this switch in one place of the grid by hand; it keeps the place, and leaves any other it was on. */
    static void pin(Context context, int place, String id) {
        SharedPreferences prefs = prefs(context);
        List<String> manual = list(prefs.getString(KEY_PINS, ""));
        while (manual.size() < PLACES) manual.add("");
        for (int i = 0; i < manual.size(); i++) if (manual.get(i).equals(id)) manual.set(i, "");
        manual.set(place, id);
        prefs.edit().putString(KEY_PINS, TextUtils.join(",", manual)).apply();
    }

    /** Back to the switches turned on most lately. */
    static void reset(Context context) {
        prefs(context).edit().remove(KEY_PINS).apply();
    }

    // ------------------------------------------------------------------ the bar

    /** The two round buttons: back on the left, the menu on the right, 48dp, on the container colour. */
    static View bar(Context context, ProfileM3.Roles r, Runnable back, Runnable menu) {
        return bar(context, r, back, new Dots(r.ink), Local.of("Ещё"), menu);
    }

    /** The same bar with another button on the right. */
    static View bar(Context context, ProfileM3.Roles r, Runnable back, Drawable glyph, String description, Runnable right) {
        float unit = context.getResources().getDisplayMetrics().density;
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Math.round(16f * unit), Math.round(12f * unit), Math.round(16f * unit), 0);
        View left = Expressive.roundButton(context, SettingsGlyph.make("arrow_forward", r.ink),
                r.container, (r.primary & 0xFFFFFF) | 0x33000000, r.ink, 48);
        ((ViewGroup) left).getChildAt(0).setScaleX(-1f);
        left.setContentDescription(Local.of("Назад"));
        left.setOnClickListener(v -> back.run());
        bar.addView(left);
        View gap = new View(context);
        bar.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
        View end = Expressive.roundButton(context, glyph, r.container,
                (r.primary & 0xFFFFFF) | 0x33000000, r.ink, 48);
        end.setContentDescription(description);
        end.setOnClickListener(v -> right.run());
        bar.addView(end);
        return bar;
    }

    /** Three dots one over another. */
    private static final class Dots extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Dots(int colour) { paint.setColor(colour); }
        @Override public void draw(Canvas canvas) {
            float w = getBounds().width(), h = getBounds().height(), r = Math.min(w, h) * 0.085f;
            float x = getBounds().exactCenterX(), y = getBounds().exactCenterY(), step = Math.min(w, h) * 0.30f;
            canvas.drawCircle(x, y - step, r, paint);
            canvas.drawCircle(x, y, r, paint);
            canvas.drawCircle(x, y + step, r, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ------------------------------------------------------------------ the quick switches

    /** What the grid asks of the activity. */
    interface Host {
        void changed();
        void choose(int place);
    }

    static View quick(final Context context, final ProfileM3.Roles r, final Host host) {
        float unit = context.getResources().getDisplayMetrics().density;
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        String[] ids = chosen(context);
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 2; column++) {
                final int place = row * 2 + column;
                Pin pin = find(ids[place]);
                if (pin == null) continue;
                LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(0, Math.round(112f * unit), 1f);
                if (column == 0) at.rightMargin = Math.round(4f * unit); else at.leftMargin = Math.round(4f * unit);
                line.addView(new Tile(context, pin, r, host, place), at);
            }
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (row == 1) at.topMargin = Math.round(8f * unit);
            grid.addView(line, at);
        }
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        size.setMargins(Math.round(16f * unit), Math.round(8f * unit), Math.round(16f * unit), 0);
        grid.setLayoutParams(size);
        return grid;
    }

    /** One tile: the icon and the switch above, the name below. The colours change over 200 ms. */
    private static final class Tile extends LinearLayout {
        private final Pin pin;
        private final ProfileM3.Roles r;
        private final GradientDrawable back = new GradientDrawable();
        private final ImageView icon;
        private final TextView name;
        private final Switch toggle;
        private final int onBack, onInk, offBack, offInk;
        private boolean on;
        private float shown;
        private ValueAnimator animator;

        Tile(final Context context, final Pin pin, final ProfileM3.Roles r, final Host host, final int place) {
            super(context);
            this.pin = pin;
            this.r = r;
            float unit = context.getResources().getDisplayMetrics().density;
            onBack = card(r.primary);
            onInk = readableOn(onBack);
            offBack = r.high;
            offInk = r.ink;
            setOrientation(VERTICAL);
            int pad = Math.round(16f * unit);
            setPadding(pad, pad, pad, pad);
            back.setCornerRadius(28f * unit);
            setBackground(back);
            setClickable(true);
            setContentDescription(pin.title);

            LinearLayout top = new LinearLayout(context);
            top.setOrientation(HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            icon = new ImageView(context);
            Drawable glyph = SettingsGlyph.make(pin.icon, offInk);
            if (glyph != null) icon.setImageDrawable(glyph);
            top.addView(icon, new LinearLayout.LayoutParams(Math.round(28f * unit), Math.round(28f * unit)));
            top.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));
            toggle = new Switch(context, unit);
            top.addView(toggle, new LinearLayout.LayoutParams(Math.round(52f * unit), Math.round(32f * unit)));
            addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addView(new View(context), new LinearLayout.LayoutParams(1, 0, 1f));

            name = new TextView(context);
            name.setText(pin.title);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            Fonts.apply(name);
            name.setTypeface(Typeface.create(name.getTypeface(), 700, false));
            addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            on = pin.get.get();
            shown = on ? 1f : 0f;
            paint(shown);

            setOnClickListener(v -> {
                on = !on;
                pin.put.put(on);
                if (on) turnedOn(context, pin.title);
                host.changed();
                glide(on ? 1f : 0f);
            });
            setOnLongClickListener(v -> { host.choose(place); return true; });
            Motion.touch(this);
        }

        private void glide(final float to) {
            if (animator != null) animator.cancel();
            if (!Motion.enabled(getContext())) { shown = to; paint(to); return; }
            animator = ValueAnimator.ofFloat(shown, to);
            animator.setDuration(200L);
            animator.addUpdateListener(a -> { shown = (float) a.getAnimatedValue(); paint(shown); });
            animator.start();
        }

        private void paint(float t) {
            ArgbEvaluator blend = new ArgbEvaluator();
            back.setColor((Integer) blend.evaluate(t, offBack, onBack));
            int ink = (Integer) blend.evaluate(t, offInk, onInk);
            name.setTextColor(ink);
            Drawable glyph = icon.getDrawable();
            if (glyph != null) glyph.setColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN);
            toggle.set(t, (Integer) blend.evaluate(t, offInk, onInk), (Integer) blend.evaluate(t, r.outline, onBack), onBack);
            icon.invalidate();
        }
    }

    /** The switch of the design: a white track and a dot of the card's colour when on, an outlined track when off. */
    private static final class Switch extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float unit;
        private float t;
        private int track, outline, thumbOn, thumbOff;

        Switch(Context context, float unit) {
            super(context);
            this.unit = unit;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        /** Where it is (0 off, 1 on), the track when on, the outline and thumb when off, the thumb when on. */
        void set(float t, int trackOn, int outlineOff, int thumb) {
            this.t = t;
            this.track = trackOn;
            this.outline = outlineOff;
            this.thumbOn = thumb;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            // the track: filled when on, a line round it when off
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(blend(0x00000000 | (track & 0xFFFFFF), track, t));
            paint.setAlpha(Math.round(255 * t));
            canvas.drawRoundRect(0, 0, w, h, h / 2, h / 2, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * unit);
            paint.setColor(outline);
            paint.setAlpha(Math.round(255 * (1f - t)));
            canvas.drawRoundRect(unit, unit, w - unit, h - unit, h / 2 - unit, h / 2 - unit, paint);
            // the thumb: 16dp when off, 24dp when on
            float radius = (8f + 4f * t) * unit;
            float x = (14f + (36f - 14f) * t) * unit;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(blend(outline | 0xFF000000, thumbOn | 0xFF000000, t));
            paint.setAlpha(255);
            canvas.drawCircle(x, h / 2, radius, paint);
        }

        private static int blend(int a, int b, float t) {
            return (Integer) new ArgbEvaluator().evaluate(t, a, b);
        }
    }

    /** The arrow in a disc: 36dp, on the page's colour. */
    static View disc(Context context, ProfileM3.Roles r, boolean open) {
        float unit = context.getResources().getDisplayMetrics().density;
        FrameLayout disc = new FrameLayout(context);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(r.ground);
        disc.setBackground(shape);
        ImageView arrow = new ImageView(context);
        Drawable more = SettingsGlyph.make("chevron_right", r.variant);
        if (more != null) arrow.setImageDrawable(more);
        disc.addView(arrow, new FrameLayout.LayoutParams(Math.round(24f * unit), Math.round(24f * unit), Gravity.CENTER));
        return disc;
    }

    // ------------------------------------------------------------------ the sections

    /** A section of the settings: what is on the row and what touching it does. */
    static final class Section {
        final String icon, title, note;
        final Runnable open;
        Section(String icon, String title, String note, Runnable open) {
            this.icon = icon; this.title = title; this.note = note; this.open = open;
        }
    }

    /** n waves, how deep, how turned: cookie, clover, sunny, gem, pentagon, burst. */
    private static final double[][] SHAPES = {
            {9, 0.07, 0}, {4, 0.13, Math.PI / 4}, {8, 0.045, 0}, {6, 0.05, 0}, {5, 0.06, -Math.PI / 2}, {12, 0.06, 0}};

    /** The label of the list and the list itself, as one block. */
    static View sections(Context context, ProfileM3.Roles r, List<Section> list) {
        float unit = context.getResources().getDisplayMetrics().density;
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(context);
        label.setText(Local.of("РАЗДЕЛЫ"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        label.setLetterSpacing(0.02f);
        label.setTextColor(NavBar.readable(r.primary, r.ground, r.ink, 3f));
        Fonts.apply(label);
        label.setTypeface(Typeface.create(label.getTypeface(), 700, false));
        label.setPadding(Math.round(28f * unit), Math.round(22f * unit), Math.round(28f * unit), Math.round(8f * unit));
        block.addView(label);

        int[][] pairs = {{containerOf(r, 0), onOf(r, 0)}, {containerOf(r, 1), onOf(r, 1)}, {containerOf(r, 2), onOf(r, 2)}};
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(Math.round(12f * unit), 0, Math.round(12f * unit), 0);
        for (int i = 0; i < list.size(); i++) {
            final Section section = list.get(i);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Math.round(80f * unit));
            row.setPadding(Math.round(12f * unit), Math.round(12f * unit), Math.round(16f * unit), Math.round(12f * unit));
            GradientDrawable back = new GradientDrawable();
            back.setColor(r.container);
            float big = 28f * unit, small = 8f * unit;
            boolean first = i == 0, last = i == list.size() - 1;
            float top = first ? big : small, bottom = last ? big : small;
            back.setCornerRadii(new float[] {top, top, top, top, bottom, bottom, bottom, bottom});
            row.setBackground(back);
            row.setClickable(true);
            row.setContentDescription(section.title + ". " + section.note);

            int[] pair = pairs[i % 3];
            double[] shape = SHAPES[i % SHAPES.length];
            FrameLayout box = new FrameLayout(context);
            box.setBackground(new Blob(shape, pair[0]));
            ImageView glyph = new ImageView(context);
            Drawable drawing = SettingsGlyph.make(section.icon, pair[1]);
            if (drawing != null) glyph.setImageDrawable(drawing);
            box.addView(glyph, new FrameLayout.LayoutParams(Math.round(26f * unit), Math.round(26f * unit), Gravity.CENTER));
            row.addView(box, new LinearLayout.LayoutParams(Math.round(56f * unit), Math.round(56f * unit)));

            LinearLayout words = new LinearLayout(context);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(context);
            title.setText(section.title);
            title.setTextColor(r.ink);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            Fonts.apply(title);
            title.setTypeface(Typeface.create(title.getTypeface(), 700, false));
            words.addView(title);
            TextView note = new TextView(context);
            note.setText(section.note);
            note.setTextColor(r.variant);
            note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            note.setSingleLine(true);
            note.setEllipsize(TextUtils.TruncateAt.END);
            Fonts.apply(note);
            LinearLayout.LayoutParams noteAt = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            noteAt.topMargin = Math.round(2f * unit);
            words.addView(note, noteAt);
            LinearLayout.LayoutParams wordsAt = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            wordsAt.leftMargin = Math.round(16f * unit);
            row.addView(words, wordsAt);

            View chevron = disc(context, r, false);
            LinearLayout.LayoutParams chevronAt = new LinearLayout.LayoutParams(Math.round(36f * unit), Math.round(36f * unit));
            chevronAt.leftMargin = Math.round(16f * unit);
            row.addView(chevron, chevronAt);

            row.setOnClickListener(v -> section.open.run());
            Motion.touch(row);
            LinearLayout.LayoutParams rowAt = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rowAt.topMargin = Math.round(3f * unit);
            group.addView(row, rowAt);
        }
        block.addView(group);
        return block;
    }

    /** A shape of the sections: r = R (1 + a cos(n t)), in a square. */
    private static final class Blob extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final double[] shape;
        private int made;

        Blob(double[] shape, int colour) {
            this.shape = shape;
            paint.setColor(colour);
        }

        @Override public void draw(Canvas canvas) {
            int side = Math.min(getBounds().width(), getBounds().height());
            if (side == 0) return;
            if (made != side) {
                made = side;
                path.reset();
                for (int i = 0; i <= 240; i++) {
                    double t = i * 2 * Math.PI / 240;
                    double reach = side * 0.46 * (1 + shape[1] * Math.cos(shape[0] * (t + shape[2])));
                    float x = (float) (side / 2.0 + reach * Math.cos(t)), y = (float) (side / 2.0 + reach * Math.sin(t));
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close();
            }
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static int containerOf(ProfileM3.Roles r, int which) {
        return which == 0 ? r.primaryContainer : which == 1 ? r.secondary : r.tertiaryContainer;
    }

    private static int onOf(ProfileM3.Roles r, int which) {
        int container = containerOf(r, which);
        int tone = which == 0 ? r.primary : which == 1 ? NavBar.mix(r.ink, r.primary, 0.20f) : r.error;
        return NavBar.readable(which == 2 ? NavBar.mix(r.ink, r.tertiaryContainer, 0.10f) : tone, container, r.ink, 4.5f);
    }

    // ------------------------------------------------------------------ the foot

    /** Two buttons in one group: 56dp, 4dp apart, on the secondary container. */
    static View foot(Context context, ProfileM3.Roles r, Runnable telegram, Runnable about) {
        float unit = context.getResources().getDisplayMetrics().density;
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.addView(button(context, r, new Plane(r.onSecondary), Local.of("Telegram"), telegram, true),
                new LinearLayout.LayoutParams(0, Math.round(56f * unit), 1f));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, Math.round(56f * unit), 1f);
        second.leftMargin = Math.round(4f * unit);
        group.addView(button(context, r, SettingsGlyph.make("info", r.onSecondary), Local.of("О моде"), about, false), second);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        size.setMargins(Math.round(16f * unit), Math.round(12f * unit), Math.round(16f * unit), 0);
        group.setLayoutParams(size);
        return group;
    }

    private static View button(Context context, ProfileM3.Roles r, Drawable glyph, String text, Runnable tap, boolean first) {
        float unit = context.getResources().getDisplayMetrics().density;
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        GradientDrawable back = new GradientDrawable();
        back.setColor(r.secondary);
        float big = 28f * unit, small = 8f * unit;
        back.setCornerRadii(first ? new float[] {big, big, small, small, small, small, big, big}
                : new float[] {small, small, big, big, big, big, small, small});
        button.setBackground(back);
        button.setClickable(true);
        ImageView icon = new ImageView(context);
        if (glyph != null) icon.setImageDrawable(glyph);
        button.addView(icon, new LinearLayout.LayoutParams(Math.round(20f * unit), Math.round(20f * unit)));
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(r.onSecondary);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        Fonts.apply(label);
        label.setTypeface(Typeface.create(label.getTypeface(), 700, false));
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        at.leftMargin = Math.round(8f * unit);
        button.addView(label, at);
        button.setContentDescription(text);
        button.setOnClickListener(v -> tap.run());
        Motion.touch(button);
        return button;
    }

    /** A paper plane, for the Telegram button. */
    private static final class Plane extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        Plane(int colour) { paint.setColor(colour); }
        @Override public void draw(Canvas canvas) {
            float w = getBounds().width(), h = getBounds().height();
            path.reset();
            // the outline of the "send" glyph: a triangle with a notch, in a 24 by 24 box
            path.moveTo(2.01f, 21f); path.lineTo(23f, 12f); path.lineTo(2.01f, 3f); path.lineTo(2f, 10f);
            path.lineTo(17f, 12f); path.lineTo(2f, 14f); path.close();
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(w / 24f, h / 24f);
            canvas.drawPath(path, paint);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ------------------------------------------------------------------ colours

    /** The accent as a card's colour, darkened only as far as it takes for what is written on it to be read. */
    static int card(int accent) {
        int use = accent | 0xFF000000;
        for (int step = 0; step < 12 && best(use) < 4.5f; step++) use = NavBar.mix(use, 0xFF000000, 0.06f);
        return use;
    }

    private static float best(int on) {
        return Math.max(NavBar.contrast(0xFFFFFFFF, on), NavBar.contrast(0xFF161823, on));
    }

    static int readableOn(int primary) {
        return NavBar.contrast(0xFFFFFFFF, primary) >= NavBar.contrast(0xFF161823, primary) ? 0xFFFFFFFF : 0xFF161823;
    }
}
