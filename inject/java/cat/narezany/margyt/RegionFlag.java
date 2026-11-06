package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

/**
 * The country a video comes from, as a round flag in a dark chip right after the author's name.
 *
 * TikTok's own post model carries it -- `Aweme.getRegion()`, a two-letter country code -- and the
 * caption row already hands over its post when it is bound, so nothing is asked of the network.
 * The flag is the emoji of that code, drawn by the phone's own emoji font and cut to a circle.
 * Off until asked for.
 */
final class RegionFlag {

    private RegionFlag() {}

    static final String KEY = "region_flag";

    private static volatile Boolean on;

    static boolean isEnabled() {
        Boolean known = on;
        if (known != null) return known.booleanValue();
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        boolean value = prefs.getBoolean(KEY, false);
        on = Boolean.valueOf(value);
        return value;
    }

    static void setEnabled(boolean enabled) {
        on = Boolean.valueOf(enabled);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY, enabled).apply();
    }

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        return context == null ? null : context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }

    private static final int CHIP_TAG = 0x7e0d0341;

    /**
     * Called when a caption row is bound to its post: puts the chip after the name, or takes it off.
     * `cell` is the row, `post` the video it is for.
     */
    static void bind(final View cell, final Object post) {
        try {
            if (cell == null) return;
            Object had = cell.getTag(CHIP_TAG);
            if (!isEnabled()) {
                if (had instanceof Chip) ((Chip) had).setVisibility(View.GONE);
                return;
            }
            String region = NativeRead.string(NativeRead.get(post, "getRegion")).trim().toUpperCase(java.util.Locale.ROOT);
            String flag = flag(region);
            Chip chip = had instanceof Chip ? (Chip) had : null;
            if (flag == null) {
                if (chip != null) chip.setVisibility(View.GONE);
                return;
            }
            if (chip == null || chip.getParent() == null) {
                chip = place(cell);
                if (chip == null) return;
                cell.setTag(CHIP_TAG, chip);
            }
            chip.show(flag, region);
        } catch (Throwable error) {
            Diary.note("region flag: " + error);
        }
    }

    /** The chip put after the author's name, in the line the name is in. */
    private static Chip place(View cell) {
        Context context = cell.getContext();
        String pack = context.getPackageName();
        int rowId = cell.getResources().getIdentifier("uxm", "id", pack);
        int titleId = cell.getResources().getIdentifier("title", "id", pack);
        if (rowId == 0 || titleId == 0) return null;
        View row = cell.getId() == rowId ? cell : cell.findViewById(rowId);
        if (!(row instanceof LinearLayout)) return null;
        LinearLayout line = (LinearLayout) row;
        View title = line.findViewById(titleId);
        if (title == null || title.getParent() != line) return null;
        float unit = context.getResources().getDisplayMetrics().density;
        Chip chip = new Chip(context);
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(Math.round(28f * unit), Math.round(28f * unit));
        at.gravity = Gravity.CENTER_VERTICAL;
        at.leftMargin = Math.round(6f * unit);
        line.addView(chip, line.indexOfChild(title) + 1, at);
        line.setClipChildren(false);
        return chip;
    }

    /** Two letters of the alphabet as the two regional indicators a flag is made of; null for anything else. */
    static String flag(String region) {
        if (region == null || region.length() != 2) return null;
        char a = region.charAt(0), b = region.charAt(1);
        if (a < 'A' || a > 'Z' || b < 'A' || b > 'Z') return null;
        return new String(Character.toChars(0x1F1E6 + (a - 'A'))) + new String(Character.toChars(0x1F1E6 + (b - 'A')));
    }

    /** A dark rounded square with the flag, cut to a circle, in the middle of it. */
    private static final class Chip extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final Path circle = new Path();
        private final Rect bounds = new Rect();
        private String flag = "";

        Chip(Context context) {
            super(context);
            fill.setColor(0xFF161616);
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(0x66FFFFFF);
            ring.setStrokeWidth(getResources().getDisplayMetrics().density);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            // Twemoji's flags are flat and fill their whole box; the phone's own wave and leave a margin
            android.graphics.Typeface flags = Fonts.twemoji(context);
            if (flags != null) ink.setTypeface(flags);
        }

        void show(String flag, String region) {
            this.flag = flag;
            setContentDescription(region);
            setVisibility(View.VISIBLE);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float unit = getResources().getDisplayMetrics().density;
            float w = getWidth(), h = getHeight();
            box.set(0, 0, w, h);
            canvas.drawRoundRect(box, 8f * unit, 8f * unit, fill);
            float d = Math.min(w, h) * 0.72f, cx = w / 2f, cy = h / 2f;
            circle.rewind();
            circle.addCircle(cx, cy, d / 2f, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(circle);
            ink.setTextSize(d);
            ink.getTextBounds(flag, 0, flag.length(), bounds);
            if (bounds.width() > 0 && bounds.height() > 0) {
                // the glyph is scaled until it covers the circle, whatever shape the font gave it
                float scale = d / Math.min(bounds.width(), bounds.height());
                canvas.translate(cx, cy);
                canvas.scale(scale, scale);
                canvas.drawText(flag, -bounds.exactCenterX(), -bounds.exactCenterY(), ink);
            }
            canvas.restore();
            canvas.drawCircle(cx, cy, d / 2f - ring.getStrokeWidth() / 2f, ring);
        }
    }
}
