package cat.narezany.margyt;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The head of the settings home in Material 3 Expressive: one large card in the accent, the name
 * of the mod at its foot with the version and the state of the updates under it, and the mark on a
 * slowly turning cookie that comes out over the corner.
 *
 * Every colour is a role of the accent, so the card follows it as the rest of the mod does: the
 * card is the primary, the cookie is the primary a step lighter, the name and the mark are what
 * reads on the primary, and the first chip is its pale container.
 */
final class SettingsHero {

    private SettingsHero() {}

    private static Bitmap petals;
    private static boolean petalsTried;

    /** The card. `update` runs when the chip that says there is an update is touched. */
    static View create(Context context, Runnable update) {
        final float unit = context.getResources().getDisplayMetrics().density;
        final int primary = SettingsHome.card(Accent.colour() | 0xFF000000);
        final int on = SettingsHome.readableOn(primary);
        // the cookie: the primary turned a little toward what is written on it
        final int light = NavBar.mix(primary, on, 0.12f);
        final int container = NavBar.mix(primary, 0xFFFFFFFF, 0.72f);
        final int onContainer = NavBar.readable(NavBar.mix(primary, 0xFF000000, 0.65f), container, 0xFF000000, 4.5f);

        FrameLayout card = new FrameLayout(context);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(primary);
        fill.setCornerRadius(40f * unit);
        card.setBackground(fill);
        card.setClipToOutline(true);
        final float radius = 40f * unit;
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });

        // the cookie, 220dp, out over the top right corner
        final Cookie cookie = new Cookie(context, light);
        FrameLayout.LayoutParams where = new FrameLayout.LayoutParams(Math.round(220f * unit), Math.round(220f * unit),
                Gravity.TOP | Gravity.END);
        where.setMargins(0, Math.round(-14f * unit), Math.round(-24f * unit), 0);
        card.addView(cookie, where);

        // the mark, 96dp, exactly in the middle of the cookie
        Mark mark = new Mark(context, on);
        FrameLayout.LayoutParams markAt = new FrameLayout.LayoutParams(Math.round(96f * unit), Math.round(96f * unit),
                Gravity.TOP | Gravity.END);
        // the cookie's middle is 110dp in from its right edge, which is 24dp past the card's, and 14dp below the card's top
        markAt.setMargins(0, Math.round((-14f + 110f - 48f) * unit), Math.round((110f - 24f - 48f) * unit), 0);
        card.addView(mark, markAt);

        TextView name = new TextView(context);
        name.setText(Version.LITE ? "LotusTok Lite" : "LotusTok");
        name.setTextColor(on);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f);
        name.setSingleLine(true);
        name.setIncludeFontPadding(false);
        name.setLetterSpacing(-1.5f / 44f);
        Fonts.apply(name);
        name.setTypeface(Typeface.create(name.getTypeface(), 800, false));
        FrameLayout.LayoutParams nameAt = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        nameAt.setMargins(Math.round(24f * unit), 0, 0, Math.round(50f * unit));
        card.addView(name, nameAt);

        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip(context, Version.MOD, container, onContainer, null));
        chips.addView(chip(context, "beta", ((on & 0xFFFFFF) | 0x2E000000), on, null));
        chips.addView(versionChip(context, on, update));
        FrameLayout.LayoutParams chipsAt = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        chipsAt.setMargins(Math.round(24f * unit), 0, 0, Math.round(20f * unit));
        card.addView(chips, chipsAt);

        // in with a spring, and the cookie turning: both only where the phone allows animations
        if (Motion.enabled(context)) {
            card.setScaleX(0.92f);
            card.setScaleY(0.92f);
            card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                private ValueAnimator spring;
                @Override public void onViewAttachedToWindow(final View view) {
                    spring = ValueAnimator.ofFloat(0f, 1f);
                    spring.setDuration(650L);
                    spring.addUpdateListener(a -> {
                        float t = (float) a.getAnimatedValue();
                        // a damped spring settling on 1: it overshoots a little and comes back
                        float scale = 0.92f + 0.08f * (1f - (float) (Math.exp(-6.5f * t) * Math.cos(11f * t)));
                        view.setScaleX(scale);
                        view.setScaleY(scale);
                    });
                    spring.start();
                    cookie.turn(true);
                }
                @Override public void onViewDetachedFromWindow(View view) {
                    if (spring != null) spring.cancel();
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    cookie.turn(false);
                }
            });
        }

        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(200f * unit));
        size.setMargins(Math.round(16f * unit), Math.round(8f * unit), Math.round(16f * unit), Math.round(4f * unit));
        card.setLayoutParams(size);
        card.setContentDescription("LotusTok " + Version.MOD);
        return card;
    }

    private static View chip(Context context, String text, int back, int ink, final Runnable tap) {
        float unit = context.getResources().getDisplayMetrics().density;
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(ink);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        Fonts.apply(view);
        view.setTypeface(Typeface.create(view.getTypeface(), 600, false));
        view.setPadding(Math.round(12f * unit), 0, Math.round(12f * unit), 0);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(back);
        shape.setCornerRadius(13f * unit);
        view.setBackground(shape);
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                Math.round(26f * unit));
        at.rightMargin = Math.round(6f * unit);
        view.setLayoutParams(at);
        if (tap != null) {
            view.setClickable(true);
            view.setOnClickListener(v -> tap.run());
        }
        return view;
    }

    /**
     * The version chip: the server's word on whether this build is the one it hands out. The server
     * is asked while the card is on the screen, and a build whose version is not the server's -- the
     * server ahead of it, or it ahead of the server -- is lit red and said to be outdated.
     */
    private static TextView versionChip(final Context context, final int on, final Runnable update) {
        float unit = context.getResources().getDisplayMetrics().density;
        final TextView view = new TextView(context);
        view.setText(Local.of("актуальная"));
        view.setTextColor(on);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        Fonts.apply(view);
        view.setTypeface(Typeface.create(view.getTypeface(), 600, false));
        view.setPadding(Math.round(12f * unit), 0, Math.round(12f * unit), 0);
        final GradientDrawable shape = new GradientDrawable();
        shape.setColor((on & 0xFFFFFF) | 0x2E000000);
        shape.setCornerRadius(13f * unit);
        view.setBackground(shape);
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                Math.round(26f * unit));
        at.rightMargin = Math.round(6f * unit);
        view.setLayoutParams(at);

        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final int[] ticks = {1};
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                // the server is asked again now and then while the card is on the screen: the version
                // it hands out changes when a build is published, and the chip has to follow it
                if (ticks[0]++ % 19 == 0) Updater.check(context, false);
                boolean differs = Updater.differs(), newer = Updater.newer();
                String want = differs ? Local.of("неактуальная")
                        : newer ? Local.of("есть обновление") : Local.of("актуальная");
                if (!want.contentEquals(view.getText())) view.setText(want);
                int back = differs ? 0xFFB3261E : ((on & 0xFFFFFF) | 0x2E000000);
                int ink = differs ? 0xFFFFFFFF : on;
                shape.setColor(back);
                if (view.getCurrentTextColor() != ink) view.setTextColor(ink);
                if (newer && update != null) {
                    view.setClickable(true);
                    view.setOnClickListener(v -> update.run());
                } else {
                    view.setClickable(false);
                    view.setOnClickListener(null);
                }
                handler.postDelayed(this, 800L);
            }
        };
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                handler.removeCallbacks(tick);
                handler.post(tick);
            }
            @Override public void onViewDetachedFromWindow(View v) {
                handler.removeCallbacks(tick);
            }
        });
        // the answer is asked for as soon as the card is built, not waited for until the next round
        Updater.check(context, false);
        return view;
    }

    /** The cookie: nine waves, a fifth of a tenth deep, the way the profile's avatar is cut. It repeats every 40 degrees. */
    private static final class Cookie extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private ValueAnimator turning;
        private int made;

        Cookie(Context context, int colour) {
            super(context);
            paint.setColor(colour);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            int side = Math.min(getWidth(), getHeight());
            if (side == 0) return;
            if (made != side) {
                made = side;
                path.reset();
                float centre = side / 2f, reach = side * 0.46f;
                for (int i = 0; i < 360; i++) {
                    double t = i * 2 * Math.PI / 360;
                    double r = reach * (1 + 0.07 * Math.cos(9 * t));
                    float x = (float) (centre + r * Math.cos(t)), y = (float) (centre + r * Math.sin(t));
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close();
            }
            canvas.drawPath(path, paint);
        }

        void turn(boolean on) {
            if (turning != null) { turning.cancel(); turning = null; }
            if (!on || !Motion.enabled(getContext())) return;
            turning = ValueAnimator.ofFloat(0f, 360f);
            turning.setDuration(40000L);
            turning.setInterpolator(new LinearInterpolator());
            turning.setRepeatCount(ValueAnimator.INFINITE);
            turning.addUpdateListener(a -> setRotation((float) a.getAnimatedValue()));
            turning.start();
        }
    }

    /** The mark, the white petals of the file, drawn in the colour that reads on the card. */
    private static final class Mark extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect from = new Rect(), to = new Rect();

        Mark(Context context, int colour) {
            super(context);
            paint.setColorFilter(new PorterDuffColorFilter(colour, PorterDuff.Mode.SRC_IN));
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            Bitmap art = art(getContext());
            if (art == null) return;
            from.set(0, 0, art.getWidth(), art.getHeight());
            to.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(art, from, to, paint);
        }
    }

    private static synchronized Bitmap art(Context context) {
        if (petals != null || petalsTried) return petals;
        petalsTried = true;
        try (java.io.InputStream in = context.getAssets().open("margyt/hero_petals.png")) {
            petals = BitmapFactory.decodeStream(in);
        } catch (Throwable error) {
            Diary.note("settings hero: petals: " + error);
        }
        return petals;
    }
}
