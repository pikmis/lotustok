package cat.narezany.margyt;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

/**
 * A direct message chat in Material 3 Expressive's shapes.
 *
 * The chat is not a screen of its own that comes up through `onResume`: it is put into the one the
 * feed is in. So the screen is watched, a few times a second, for the page of a chat, and the views
 * of TikTok's own are rearranged where they stand: the bar at the top is rebuilt out of them in one
 * row, the call, the favourite and the menu are TikTok's buttons that are clicked from there, and a
 * piece that is not where it was is left alone.
 *
 * Colours are Material 3's roles, as `ProfileM3` has them for the page the chat is on.
 */
final class ChatM3 {

    private ChatM3() {}

    static final String KEY = "chat_m3";
    private static final int BAR = 0x7e0c0340, SKIN = 0x7e0c0341, CARD = 0x7e0c0342, DONE = 0x7e0c0343,
            HOLD = 0x7e0c0344, WATCH = 0x7e0c0345;
    static final int SENDER = 0x7e0c0346, INCOMING = 0x7e0c0347;

    static boolean isEnabled() {
        if (Version.LITE) return false;
        SharedPreferences prefs = prefs();
        return prefs == null || prefs.getBoolean(KEY, true);
    }

    static void setEnabled(boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY, on).apply();
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

    // ------------------------------------------------------------------ watching

    private static final java.util.WeakHashMap<View, Boolean> WATCHED = new java.util.WeakHashMap<View, Boolean>();

    static void attach(final Activity activity) {
        try {
            final View decor = activity.getWindow().getDecorView();
            if (WATCHED.put(decor, Boolean.TRUE) != null) return;
            decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                long last;
                int chatId = -1;

                @Override
                public boolean onPreDraw() {
                    long now = SystemClock.uptimeMillis();
                    if (now - last < 200L) return true;
                    last = now;
                    try {
                        if (!isEnabled()) return true;
                        if (chatId < 0) {
                            chatId = decor.getResources().getIdentifier("dy_", "id", decor.getContext().getPackageName());
                        }
                        if (chatId == 0) return true;
                        View chat = decor.findViewById(chatId);
                        if (chat == null || !chat.isShown()) return true;
                        apply(chat);
                    } catch (Throwable error) {
                        Diary.detail("chat m3: " + error);
                    }
                    return true;
                }
            });
        } catch (Throwable error) {
            Diary.detail("chat m3: " + error);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static int id(View view, String name) {
        return view.getResources().getIdentifier(name, "id", view.getContext().getPackageName());
    }

    private static View find(View scope, String name) {
        int want = id(scope, name);
        return want == 0 ? null : scope.findViewById(want);
    }

    private static int dp(float unit, float value) {
        return Math.round(value * unit);
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

    private static void gone(View view) {
        if (view != null && view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
    }

    private static void write(TextView text, float sp, int colour, int weight, float spacingDp) {
        if (text == null) return;
        float density = text.getResources().getDisplayMetrics().density;
        float want = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sp,
                text.getResources().getDisplayMetrics());
        if (Math.abs(text.getTextSize() - want) > 0.5f) text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        if (colour != 0 && text.getCurrentTextColor() != colour) text.setTextColor(colour);
        if (weight > 0) {
            Object kept = text.getTag(HOLD);
            if (!Integer.valueOf(weight).equals(kept)) {
                text.setTag(HOLD, Integer.valueOf(weight));
                text.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
            }
        }
        float spacing = spacingDp * density / want;
        if (Math.abs(text.getLetterSpacing() - spacing) > 0.0005f) text.setLetterSpacing(spacing);
    }

    private static void tint(View view, int colour) {
        long now = SystemClock.uptimeMillis();
        Object known = view.getTag(SKIN), at = view.getTag(SKIN + 100);
        if (Integer.valueOf(colour).equals(known) && at instanceof Long && now - (Long) at < 1000L) return;
        view.setTag(SKIN, Integer.valueOf(colour));
        view.setTag(SKIN + 100, Long.valueOf(now));
        try {
            view.getClass().getMethod("setTintColor", int.class).invoke(view, colour);
        } catch (Throwable noMethod) {
            if (view instanceof ImageView) ((ImageView) view).setColorFilter(colour, PorterDuff.Mode.SRC_IN);
        }
    }

    /**
     * Every picture in the tree is painted in one colour, and a glyph set as a compound drawable
     * of a text view as well: the camera's glyph is not always where its button keeps its children.
     */
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

    private static Drawable pressed(int on, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf((on & 0x00FFFFFF) | 0x2A000000), content, null);
    }

    /** Set a background again only when what it is made of has changed. */
    private static void dress(View view, int key, Drawable look) {
        if (!Integer.valueOf(key).equals(view.getTag(CARD))) {
            view.setTag(CARD, Integer.valueOf(key));
            view.setBackground(look);
        }
    }

    // ------------------------------------------------------------------ icons of Material Symbols

    private static final String CALL = "M795-120q-116 0-236.5-56T335-335Q232-438 176-558.5T120-795q0-19.29 12.86-32.14Q145.71-840 165-840h140q14 0 24 10t14 25l26.93 125.64Q372-665 369.5-653.5t-10.73 19.73L259-533q26 44 55 82t64 72q37 38 78 69.5t86 55.5l95-98q10-11 23.15-15 13.15-4 25.85-2l119 26q15 4 25 16.04 10 12.05 10 26.96v135q0 19.29-12.86 32.14Q814.29-120 795-120ZM229-588l81-82-23-110H180q2 42 13.5 88.5T229-588Zm369 363q41 19 89 31t93 14v-107l-103-21-79 83ZM229-588Zm369 363Z";
    private static final String MORE = "M479.86-160Q460-160 446-174.14t-14-34Q432-228 446.14-242t34-14Q500-256 514-241.86t14 34Q528-188 513.86-174t-34 14Zm0-272Q460-432 446-446.14t-14-34Q432-500 446.14-514t34-14Q500-528 514-513.86t14 34Q528-460 513.86-446t-34 14Zm0-272Q460-704 446-718.14t-14-34Q432-772 446.14-786t34-14Q500-800 514-785.86t14 34Q528-732 513.86-718t-34 14Z";
    private static final String PERSON = "M372-523q-42-42-42-108t42-108q42-42 108-42t108 42q42 42 42 108t-42 108q-42 42-108 42t-108-42ZM160-220v-34q0-38 19-65t49-41q67-30 128.5-45T480-420q62 0 123 15.5T731-360q31 14 50 41t19 65v34q0 25-17.5 42.5T740-160H220q-25 0-42.5-17.5T160-220Zm60 0h520v-34q0-16-9.5-30.5T707-306q-64-31-117-42.5T480-360q-57 0-111 11.5T252-306q-14 7-23 21.5t-9 30.5v34Zm324.5-346.5Q570-592 570-631t-25.5-64.5Q519-721 480-721t-64.5 25.5Q390-670 390-631t25.5 64.5Q441-541 480-541t64.5-25.5ZM480-631Zm0 411Z";

    /** A Material Symbols icon, drawn from its path at the size and colour asked. */
    private static final class Glyph extends Drawable {
        private final Path path = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;

        Glyph(String data, int size, int colour) {
            this.size = size;
            paint.setColor(colour);
            NavPlan.trace(data, new NavPlan.Pen() {
                @Override public void move(float x, float y) { path.moveTo(x, y); }
                @Override public void line(float x, float y) { path.lineTo(x, y); }
                @Override public void curve(float x1, float y1, float x2, float y2, float x, float y) {
                    path.cubicTo(x1, y1, x2, y2, x, y);
                }
                @Override public void close() { path.close(); }
            });
        }

        @Override
        public void draw(Canvas canvas) {
            int saved = canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 960f, getBounds().height() / 960f);
            canvas.translate(0f, 960f);
            canvas.drawPath(path, paint);
            canvas.restoreToCount(saved);
        }

        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** The box a message is written in: a round-ended bar as far as `edge`, and nothing after it, where the microphone is. */
    private static final class FieldBack extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float edge = -1f, radius;
        private final android.graphics.RectF box = new android.graphics.RectF();

        void set(int colour, float radius, float edge) {
            if (paint.getColor() != colour || this.radius != radius || this.edge != edge) {
                paint.setColor(colour);
                this.radius = radius;
                this.edge = edge;
                invalidateSelf();
            }
        }

        @Override
        public void draw(Canvas canvas) {
            float right = edge < 0f ? getBounds().right : Math.min(edge, getBounds().right);
            box.set(getBounds().left, getBounds().top, right, getBounds().bottom);
            canvas.drawRoundRect(box, radius, radius, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ------------------------------------------------------------------ the shape that is a microphone and a send button

    /**
     * The clover of the microphone that turns into the round button of sending, and back, in about
     * 250 ms, with the overshoot of a spring. Drawn as a path of the radius going round, so it is
     * smooth at any size.
     */
    private static final class Morph extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private float turn;        // 0: clover, 1: circle
        private float target;
        private ValueAnimator running;

        Morph(int colour) {
            paint.setColor(colour);
        }

        void colour(int colour) {
            if (paint.getColor() != colour) {
                paint.setColor(colour);
                invalidateSelf();
            }
        }

        void to(float next) {
            if (next == target) return;
            target = next;
            if (running != null) running.cancel();
            running = ValueAnimator.ofFloat(turn, next);
            running.setDuration(250L);
            running.setInterpolator(new OvershootInterpolator(2.2f));
            running.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    turn = (Float) animation.getAnimatedValue();
                    invalidateSelf();
                }
            });
            running.start();
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width(), h = getBounds().height();
            if (w <= 0 || h <= 0) return;
            path.rewind();
            // four petals, 13 per cent deep, turned an eighth of the way, into a circle
            float depth = 0.13f * (1f - turn);
            for (int i = 0; i <= 240; i++) {
                double t = i * 2 * Math.PI / 240;
                double r = 0.5 * (1 + depth * Math.cos(4 * (t + Math.PI / 4))) / (1 + 0.13);
                // the circle reaches as far as the clover's petals do: the radius grows to meet it
                r = r + (0.5 - 0.5 / 1.13) * turn;
                float x = (float) (w * (0.5 + r * Math.cos(t))) + getBounds().left;
                float y = (float) (h * (0.5 + r * Math.sin(t))) + getBounds().top;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            path.close();
            canvas.drawPath(path, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ------------------------------------------------------------------ the whole page

    static void apply(View chat) {
        float unit = chat.getResources().getDisplayMetrics().density;
        ProfileM3.Roles r = ProfileM3.roles(chat);
        try { header(chat, r, unit); } catch (Throwable error) { Diary.detail("chat m3: header: " + error); }
        try { hero(chat, r, unit); } catch (Throwable error) { Diary.detail("chat m3: hero: " + error); }
        try { hint(chat, r, unit); } catch (Throwable error) { Diary.detail("chat m3: hint: " + error); }
        try { chips(chat, r, unit); } catch (Throwable error) { Diary.detail("chat m3: chips: " + error); }
        try { input(chat, r, unit); } catch (Throwable error) { Diary.detail("chat m3: input: " + error); }
        try { messages(chat, r, unit); } catch (Throwable error) { Diary.detail("chat m3: messages: " + error); }
    }

    // ------------------------------------------------------------------ the bar at the top

    /** 80dp: the way back, the person, and the group of the call and the menu. TikTok's own bar is put away. */
    private static void header(View chat, final ProfileM3.Roles r, float unit) {
        View holder = find(chat, "dyq");
        if (!(holder instanceof ViewGroup) || ((ViewGroup) holder).getChildCount() == 0) return;
        View inner = ((ViewGroup) holder).getChildAt(0);
        if (!(inner instanceof LinearLayout)) return;
        LinearLayout row = (LinearLayout) inner;
        LinearLayout bar = null;
        for (int i = 0; i < row.getChildCount(); i++) {
            if (Boolean.TRUE.equals(row.getChildAt(i).getTag(BAR))) bar = (LinearLayout) row.getChildAt(i);
        }
        final View back = holder.findViewById(id(chat, "n9k"));
        final View who = holder.findViewById(id(chat, "dva"));
        final View actions = holder.findViewById(id(chat, "uql"));
        if (bar == null) {
            if (back == null || who == null || actions == null) return;
            ViewGroup old = (ViewGroup) back.getParent();
            bar = new LinearLayout(chat.getContext());
            bar.setTag(BAR, Boolean.TRUE);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            old.removeView(back);
            ((ViewGroup) who.getParent()).removeView(who);
            ((ViewGroup) actions.getParent()).removeView(actions);
            row.addView(bar, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(unit, 80f)));
            bar.addView(back, new LinearLayout.LayoutParams(dp(unit, 48f), dp(unit, 48f)));
            LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, dp(unit, 56f), 1f);
            middle.leftMargin = dp(unit, 10f);
            bar.addView(who, middle);
            bar.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(unit, 56f)));
            // TikTok's bar, still there for its views to be found by, and not seen
            ViewGroup oldBar = old;
            oldBar.setVisibility(View.GONE);
        }
        pad(bar, dp(unit, 8f), 0, dp(unit, 12f), 0);
        size(bar, ViewGroup.LayoutParams.MATCH_PARENT, dp(unit, 80f));

        // the way back: a disc of 48dp on the container, the arrow on it
        if (back != null) {
            size(back, dp(unit, 48f), dp(unit, 48f));
            dress(back, r.container, fill(r.container, corners(unit, 24f, 24f, 24f, 24f)));
            View arrow = back instanceof ViewGroup && ((ViewGroup) back).getChildCount() > 0 ? ((ViewGroup) back).getChildAt(0) : null;
            if (arrow instanceof ViewGroup) {
                pad(arrow, 0, 0, 0, 0);
                ((ViewGroup) arrow).setClipChildren(false);
                if (((ViewGroup) arrow).getChildCount() > 0) {
                    View icon = ((ViewGroup) arrow).getChildAt(0);
                    ViewGroup.LayoutParams ilp = icon.getLayoutParams();
                    if (ilp instanceof android.widget.FrameLayout.LayoutParams
                            && ((android.widget.FrameLayout.LayoutParams) ilp).gravity != Gravity.CENTER) {
                        ((android.widget.FrameLayout.LayoutParams) ilp).gravity = Gravity.CENTER;
                        icon.setLayoutParams(ilp);
                    }
                    tint(icon, r.ink);
                }
                ViewGroup.LayoutParams alp = arrow.getLayoutParams();
                if (alp != null && (alp.width != ViewGroup.LayoutParams.MATCH_PARENT || alp.height != ViewGroup.LayoutParams.MATCH_PARENT)) {
                    alp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    alp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    arrow.setLayoutParams(alp);
                }
            }
        }

        // the person: a picture of 46dp in the cookie, with no ring and no dot, the name over the status
        if (who != null) {
            View avatar = who.findViewById(id(chat, "lxd"));
            final View name = who.findViewById(id(chat, "yyi"));
            if (avatar != null) {
                size(avatar, dp(unit, 46f), dp(unit, 46f));
                View photo = avatar instanceof ViewGroup && ((ViewGroup) avatar).getChildCount() > 0
                        ? ((ViewGroup) avatar).getChildAt(0) : null;
                if (photo != null) {
                    size(photo, dp(unit, 46f), dp(unit, 46f));
                    ProfileM3.avatarShape(photo);
                }
                if (avatar instanceof ViewGroup) {
                    ViewGroup parts = (ViewGroup) avatar;
                    for (int i = 0; i < parts.getChildCount(); i++) {
                        if (parts.getChildAt(i) != photo) gone(parts.getChildAt(i));
                    }
                }
                if (avatar.getTag(DONE) == null) {
                    avatar.setTag(DONE, Boolean.TRUE);
                    avatar.setClickable(true);
                    avatar.setOnClickListener(v -> {
                        if (name != null) name.performClick();
                    });
                }
            }
            View title = who.findViewById(id(chat, "ihm"));
            if (title instanceof TextView) write((TextView) title, 17f, r.ink, 700, 0f);
            if (title != null) margins(title, dp(unit, 10f), 0, 0, 0);
            View status = who.findViewById(id(chat, "vn5"));
            if (status instanceof ViewGroup) {
                margins(status, dp(unit, 10f), 0, 0, 0);
                ViewGroup switcher = (ViewGroup) status;
                for (int i = 0; i < switcher.getChildCount(); i++) {
                    if (switcher.getChildAt(i) instanceof TextView) write((TextView) switcher.getChildAt(i), 12f, r.variant, 0, 0f);
                }
            }
        }

        // the call and the menu, joined: 48 by 56dp each, 2dp apart, the ends of the group round
        if (actions instanceof ViewGroup && ((ViewGroup) actions).getChildCount() > 0
                && ((ViewGroup) actions).getChildAt(0) instanceof LinearLayout) {
            LinearLayout strip = (LinearLayout) ((ViewGroup) actions).getChildAt(0);
            pad(strip, 0, 0, 0, 0);
            final View call = strip.findViewById(id(chat, "kns")), star = strip.findViewById(id(chat, "kmm")),
                    more = strip.findViewById(id(chat, "kik"));
            gone(call);
            gone(star);
            gone(more);
            gone(strip.findViewById(id(chat, "zcu")));
            ImageView first = null, second = null;
            for (int i = 0; i < strip.getChildCount(); i++) {
                View one = strip.getChildAt(i);
                if (Boolean.TRUE.equals(one.getTag(BAR))) {
                    if (first == null) first = (ImageView) one;
                    else second = (ImageView) one;
                }
            }
            if (first == null) {
                first = new ImageView(chat.getContext());
                first.setTag(BAR, Boolean.TRUE);
                first.setScaleType(ImageView.ScaleType.CENTER);
                first.setContentDescription(Local.of("Звонок"));
                LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(dp(unit, 48f), dp(unit, 56f));
                a.rightMargin = dp(unit, 2f);
                strip.addView(first, a);
                first.setOnClickListener(v -> {
                    if (call != null) call.performClick();
                });
                second = new ImageView(chat.getContext());
                second.setTag(BAR, Boolean.TRUE);
                second.setScaleType(ImageView.ScaleType.CENTER);
                second.setContentDescription(Local.of("Ещё"));
                strip.addView(second, new LinearLayout.LayoutParams(dp(unit, 48f), dp(unit, 56f)));
                second.setOnClickListener(v -> {
                    // the favourite lives in this menu now, beside what TikTok's own menu holds
                    PopupMenu menu = new PopupMenu(v.getContext(), v);
                    menu.getMenu().add(0, 1, 0, Local.of("В избранное"));
                    menu.getMenu().add(0, 2, 1, Local.of("Ещё"));
                    menu.setOnMenuItemClickListener(item -> {
                        View target = item.getItemId() == 1 ? star : more;
                        if (target != null) target.performClick();
                        return true;
                    });
                    menu.show();
                });
            }
            int key = r.secondary ^ (r.onSecondary * 31);
            if (!Integer.valueOf(key).equals(first.getTag(CARD))) {
                first.setTag(CARD, Integer.valueOf(key));
                first.setImageDrawable(new Glyph(CALL, dp(unit, 24f), r.onSecondary));
                first.setBackground(pressed(r.onSecondary, fill(r.secondary, corners(unit, 28f, 8f, 8f, 28f))));
                second.setImageDrawable(new Glyph(MORE, dp(unit, 24f), r.onSecondary));
                second.setBackground(pressed(r.onSecondary, fill(r.secondary, corners(unit, 8f, 28f, 28f, 8f))));
            }
        }
    }

    // ------------------------------------------------------------------ an empty chat

    private static void hero(View chat, ProfileM3.Roles r, float unit) {
        View photo = find(chat, "t3o"), name = find(chat, "t74"), counts = find(chat, "t82"), card = find(chat, "t8v");
        if (photo == null || name == null || !(card instanceof LinearLayout)) return;
        // 168dp, in the cookie, with no ring and no line round it
        size(photo, dp(unit, 168f), dp(unit, 168f));
        margins(photo, 0, dp(unit, 56f), 0, 0);
        ProfileM3.avatarShape(photo);
        photo.setBackground(null);
        if (name instanceof TextView) {
            write((TextView) name, 36f, r.ink, 800, -1f);
            margins(name, 0, dp(unit, 18f), 0, 0);
        }
        if (!(counts instanceof LinearLayout) || ((LinearLayout) counts).getChildCount() == 0) return;
        LinearLayout line = (LinearLayout) counts;
        margins(line, 0, dp(unit, 18f), 0, 0);
        TextView original = null;
        LinearLayout cards = null;
        for (int i = 0; i < line.getChildCount(); i++) {
            View one = line.getChildAt(i);
            if (Boolean.TRUE.equals(one.getTag(BAR))) cards = (LinearLayout) one;
            else if (one instanceof TextView) original = (TextView) one;
        }
        if (original != null) {
            gone(original);
            String said = String.valueOf(original.getText());
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d[\\d\\s\\u00a0.,]*\\S?)\\s+([^\\d·]+?)\\s*·\\s*(\\d[\\d\\s\\u00a0.,]*\\S?)\\s+(.+)").matcher(said.trim());
            if (m.matches()) {
                if (cards == null) {
                    cards = new LinearLayout(chat.getContext());
                    cards.setTag(BAR, Boolean.TRUE);
                    cards.setOrientation(LinearLayout.HORIZONTAL);
                    cards.setGravity(Gravity.CENTER);
                    line.addView(cards, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                String key = said + r.high;
                if (!key.equals(cards.getTag(DONE))) {
                    cards.setTag(DONE, key);
                    cards.removeAllViews();
                    cards.addView(card(chat, m.group(1).trim(), caption(m.group(2)), r, unit, false));
                    cards.addView(card(chat, m.group(3).trim(), caption(m.group(4)), r, unit, true));
                }
            }
        }
        // the button that opens the profile: 48dp, the container of the secondary colour
        LinearLayout button = null;
        LinearLayout holder = (LinearLayout) card;
        for (int i = 0; i < holder.getChildCount(); i++) {
            if (Boolean.TRUE.equals(holder.getChildAt(i).getTag(BAR))) button = (LinearLayout) holder.getChildAt(i);
        }
        if (button == null) {
            final LinearLayout opener = (LinearLayout) card;
            button = new LinearLayout(chat.getContext());
            button.setTag(BAR, Boolean.TRUE);
            button.setOrientation(LinearLayout.HORIZONTAL);
            button.setGravity(Gravity.CENTER);
            button.setClickable(true);
            ImageView person = new ImageView(chat.getContext());
            person.setScaleType(ImageView.ScaleType.CENTER);
            button.addView(person, new LinearLayout.LayoutParams(dp(unit, 20f), dp(unit, 20f)));
            TextView label = new TextView(chat.getContext());
            label.setText(Local.of("Профиль"));
            label.setSingleLine(true);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            l.leftMargin = dp(unit, 8f);
            button.addView(label, l);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(unit, 48f));
            p.topMargin = dp(unit, 18f);
            p.gravity = Gravity.CENTER_HORIZONTAL;
            opener.addView(button, p);
            button.setOnClickListener(v -> opener.performClick());
        }
        int key = r.secondary ^ (r.onSecondary * 31);
        pad(button, dp(unit, 24f), 0, dp(unit, 24f), 0);
        if (!Integer.valueOf(key).equals(button.getTag(CARD))) {
            button.setTag(CARD, Integer.valueOf(key));
            button.setBackground(pressed(r.onSecondary, fill(r.secondary, corners(unit, 24f, 24f, 24f, 24f))));
            ((ImageView) button.getChildAt(0)).setImageDrawable(new Glyph(PERSON, dp(unit, 20f), r.onSecondary));
            TextView label = (TextView) button.getChildAt(1);
            label.setTextColor(r.onSecondary);
            label.setTextSize(15f);
            label.setTypeface(Typeface.create(Typeface.DEFAULT, 700, false));
        }
    }

    /** "подписчика(-ов)" is written for any number; the card says it the short way. */
    private static String caption(String said) {
        String text = said.trim().replaceAll("\\(-[^)]*\\)", "").trim();
        if (text.startsWith("подписчика")) return "подписчиков";
        return text;
    }

    private static View card(View chat, String number, String caption, ProfileM3.Roles r, float unit, boolean second) {
        LinearLayout one = new LinearLayout(chat.getContext());
        one.setOrientation(LinearLayout.VERTICAL);
        one.setPadding(dp(unit, 18f), dp(unit, 10f), dp(unit, 18f), dp(unit, 10f));
        one.setBackground(fill(r.high, corners(unit, 20f, 20f, 20f, 20f)));
        TextView n = new TextView(chat.getContext());
        n.setText(number);
        n.setTextSize(20f);
        n.setTextColor(r.ink);
        n.setTypeface(Typeface.create(Typeface.DEFAULT, 700, false));
        n.setIncludeFontPadding(false);
        one.addView(n);
        TextView c = new TextView(chat.getContext());
        c.setText(caption);
        c.setTextSize(12f);
        c.setTextColor(r.variant);
        c.setIncludeFontPadding(false);
        one.addView(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (second) lp.leftMargin = dp(unit, 8f);
        one.setLayoutParams(lp);
        return one;
    }

    // ------------------------------------------------------------------ the greeting, while nothing has been said

    private static void hint(View chat, ProfileM3.Roles r, float unit) {
        View block = find(chat, "xd3");
        if (!(block instanceof LinearLayout)) return;
        LinearLayout stack = (LinearLayout) block;
        View words = find(stack, "xd4"), close = find(stack, "eb4"), text = find(stack, "xd6");
        View sticker = find(stack, "xd5");
        gone(close);
        if (words instanceof LinearLayout) {
            words.setBackground(null);
            pad(words, 0, 0, 0, 0);
            ((LinearLayout) words).setGravity(Gravity.CENTER);
            ViewGroup.LayoutParams lp = words.getLayoutParams();
            if (lp instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams l = (LinearLayout.LayoutParams) lp;
                if (l.width != ViewGroup.LayoutParams.MATCH_PARENT || l.topMargin != dp(unit, 10f)) {
                    l.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    l.topMargin = dp(unit, 10f);
                    words.setLayoutParams(l);
                }
            }
            // under the picture and not over it
            if (stack.indexOfChild(words) == 0 && stack.getChildCount() > 1) {
                stack.removeView(words);
                stack.addView(words);
            }
        }
        if (text instanceof TextView) {
            View title = find(chat, "ihm");
            String nick = title instanceof TextView ? String.valueOf(((TextView) title).getText()).trim() : "";
            String want = Local.of("Поздоровайтесь с ") + nick;
            TextView t = (TextView) text;
            if (!want.contentEquals(t.getText())) t.setText(want);
            write(t, 14f, r.variant, 0, 0f);
            t.setGravity(Gravity.CENTER);
            ViewGroup.LayoutParams lp = t.getLayoutParams();
            if (lp != null && lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                t.setLayoutParams(lp);
            }
        }
        // the wave, 96dp, in the sunny, on the container of the tertiary colour
        if (sticker != null) {
            size(sticker, dp(unit, 96f), dp(unit, 96f));
            dress(sticker, r.tertiaryContainer, new android.graphics.drawable.ColorDrawable(r.tertiaryContainer));
            if (sticker.getOutlineProvider() != ProfileM3.SUNNY) {
                sticker.setOutlineProvider(ProfileM3.SUNNY);
                sticker.setClipToOutline(true);
            }
            if (sticker instanceof ViewGroup) {
                for (int i = 0; i < ((ViewGroup) sticker).getChildCount(); i++) {
                    size(((ViewGroup) sticker).getChildAt(i), dp(unit, 96f), dp(unit, 96f));
                }
            }
        }
    }

    // ------------------------------------------------------------------ quick actions

    private static void chips(View chat, ProfileM3.Roles r, float unit) {
        View list = find(chat, "a3n");
        if (!(list instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) list;
        size(row, ViewGroup.LayoutParams.MATCH_PARENT, dp(unit, 66f));
        pad(row, dp(unit, 12f), 0, dp(unit, 12f), 0);
        for (int i = 0; i < row.getChildCount(); i++) {
            View chip = row.getChildAt(i);
            if (!(chip instanceof LinearLayout)) continue;
            LinearLayout box = (LinearLayout) chip;
            View label = find(box, "a3y"), icon = find(box, "a3a");
            boolean round = label == null || label.getVisibility() == View.GONE;
            ViewGroup.LayoutParams lp = box.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                int width = round ? dp(unit, 44f) : ViewGroup.LayoutParams.WRAP_CONTENT;
                if (m.height != dp(unit, 44f) || m.width != width || m.rightMargin != dp(unit, 8f)
                        || m.topMargin != dp(unit, 6f) || m.bottomMargin != dp(unit, 16f) || m.leftMargin != 0) {
                    m.height = dp(unit, 44f);
                    m.width = width;
                    m.setMargins(0, dp(unit, 6f), dp(unit, 8f), dp(unit, 16f));
                    box.setLayoutParams(m);
                }
            }
            box.setGravity(Gravity.CENTER);
            int key = round ? r.tertiaryContainer ^ 1 : r.container;
            dress(box, key, round ? fill(r.tertiaryContainer, corners(unit, 22f, 22f, 22f, 22f))
                    : fill(r.container, corners(unit, 14f, 14f, 14f, 14f)));
            for (int k = 0; k < box.getChildCount(); k++) {
                View part = box.getChildAt(k);
                if (part instanceof android.widget.Space) {
                    ViewGroup.LayoutParams sp = part.getLayoutParams();
                    boolean middle = part == find(box, "x0u");
                    int want = round ? 0 : (middle ? dp(unit, 8f) : dp(unit, 14f));
                    if (sp != null && sp.width != want) {
                        sp.width = want;
                        part.setLayoutParams(sp);
                    }
                }
            }
            if (icon instanceof ImageView) {
                int px = round ? dp(unit, 22f) : dp(unit, 20f);
                size(icon, px, px);
                margins(icon, 0, 0, 0, 0);
                ((ImageView) icon).setColorFilter(round ? r.error : r.primary, PorterDuff.Mode.SRC_IN);
            }
            if (label instanceof TextView) write((TextView) label, 14f, r.ink, 600, 0f);
        }
    }

    // ------------------------------------------------------------------ the box a message is written in

    private static void input(View chat, final ProfileM3.Roles r, float unit) {
        View slot = find(chat, "zn");
        if (!(slot instanceof ViewGroup)) return;
        ViewGroup zone = (ViewGroup) slot;
        View cameraBox = zone.findViewById(id(chat, "n9k"));
        View camera = zone.findViewById(id(chat, "dhg"));
        View field = zone.findViewById(id(chat, "lf9"));
        View mic = zone.findViewById(id(chat, "lfm"));
        View edit = zone.findViewById(id(chat, "lf_"));

        // the camera: 48dp, a disc, the secondary container
        if (cameraBox != null) {
            size(cameraBox, dp(unit, 48f), dp(unit, 48f));
            margins(cameraBox, dp(unit, 12f), 0, 0, dp(unit, 2f));
        }
        if (camera != null) {
            size(camera, dp(unit, 48f), dp(unit, 48f));
            dress(camera, r.secondary, pressed(r.onSecondary, fill(r.secondary, corners(unit, 24f, 24f, 24f, 24f))));
            View icon = camera instanceof ViewGroup && ((ViewGroup) camera).getChildCount() > 0 ? ((ViewGroup) camera).getChildAt(0) : null;
            if (icon != null) {
                pad(icon, dp(unit, 16f), dp(unit, 16f), dp(unit, 16f), dp(unit, 16f));
                tint(icon, r.onSecondary);
            }
        }

        // the field: 48dp, a round end on each side, the high container; the icons on its right
        if (field != null) {
            ViewGroup.LayoutParams lp = field.getLayoutParams();
            if (lp != null && lp.height != dp(unit, 48f)) {
                lp.height = dp(unit, 48f);
                field.setLayoutParams(lp);
            }
            margins(field, dp(unit, 8f), 0, dp(unit, 8f), dp(unit, 2f));
            FieldBack back;
            if (field.getBackground() instanceof FieldBack) {
                back = (FieldBack) field.getBackground();
            } else {
                back = new FieldBack();
                field.setBackground(back);
            }
            View icons0 = field.findViewById(id(chat, "lfc"));
            View sticker0 = icons0 == null ? null : icons0.findViewById(id(chat, "lff"));
            float edge = icons0 != null && sticker0 != null && sticker0.getRight() > 0
                    ? icons0.getLeft() + sticker0.getRight() + dp(unit, 4f) : -1f;
            back.set(r.high, 24f * unit, edge);
            if (icons0 != null) {
                margins(icons0, 0, 0, 0, 0);
                if (icons0 instanceof LinearLayout) ((LinearLayout) icons0).setGravity(Gravity.CENTER_VERTICAL);
            }
            EditText typed = edit instanceof ViewGroup && ((ViewGroup) edit).getChildCount() > 0
                    && ((ViewGroup) edit).getChildAt(0) instanceof EditText ? (EditText) ((ViewGroup) edit).getChildAt(0) : null;
            if (typed != null) {
                write(typed, 16f, r.ink, 0, 0f);
                typed.setHintTextColor(r.variant);
                pad(typed, dp(unit, 20f), typed.getPaddingTop(), typed.getPaddingRight(), typed.getPaddingBottom());
                if (typed.getTag(WATCH) == null) {
                    typed.setTag(WATCH, Boolean.TRUE);
                    typed.addTextChangedListener(new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                        @Override public void afterTextChanged(Editable s) {
                            // the microphone's shape follows the words: the next apply sets it, and this moves it now
                            Object morph = typed.getTag(DONE);
                            if (morph instanceof Morph) ((Morph) morph).to(s.length() > 0 ? 1f : 0f);
                            try { input(chat, r, unit); } catch (Throwable ignored) {}
                        }
                    });
                }
                Object known = typed.getTag(DONE);
                if (!(known instanceof Morph) && mic != null && mic.getBackground() instanceof Morph) {
                    typed.setTag(DONE, mic.getBackground());
                }
            }
            View icons = field.findViewById(id(chat, "lfc"));
            if (icons instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) icons;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View one = group.getChildAt(i);
                    if (one instanceof ImageView) tint(one, r.variant);
                    else if (one != mic && one instanceof ViewGroup && ((ViewGroup) one).getChildCount() > 0
                            && ((ViewGroup) one).getChildAt(0) instanceof ImageView) {
                        tint(((ViewGroup) one).getChildAt(0), r.variant);
                    }
                }
            }
        }

        // the microphone: out of the field, 48dp, the clover on the primary colour; it turns round when there are words
        if (mic != null && field != null) {
            {
                size(mic, dp(unit, 48f), dp(unit, 48f));
                margins(mic, dp(unit, 8f), 0, 0, 0);
                Morph morph;
                if (mic.getBackground() instanceof Morph) {
                    morph = (Morph) mic.getBackground();
                    morph.colour(r.primary);
                } else {
                    morph = new Morph(r.primary);
                    mic.setBackground(morph);
                }
                for (View one : new View[]{find(mic, "cvv")}) {
                    if (one != null) size(one, dp(unit, 48f), dp(unit, 48f));
                }
                for (int i = 0; i < ((ViewGroup) mic).getChildCount(); i++) {
                    View part = ((ViewGroup) mic).getChildAt(i);
                    size(part, dp(unit, 48f), dp(unit, 48f));
                    tintAll(part, r.onPrimary, unit);
                }
                EditText typed = edit instanceof ViewGroup && ((ViewGroup) edit).getChildCount() > 0
                        && ((ViewGroup) edit).getChildAt(0) instanceof EditText ? (EditText) ((ViewGroup) edit).getChildAt(0) : null;
                if (typed != null) {
                    typed.setTag(DONE, morph);
                    morph.to(typed.getText().length() > 0 ? 1f : 0f);
                }
            }
        }

        // the send button: solid primary Monet color and onPrimary tint, 48dp circle
        for (int i = 0; i < zone.getChildCount(); i++) {
            View child = zone.getChildAt(i);
            if (child == null || child == cameraBox || child == camera || child == field || child == mic) continue;
            size(child, dp(unit, 48f), dp(unit, 48f));
            margins(child, dp(unit, 8f), 0, 0, 0);
            dress(child, r.primary, pressed(r.onPrimary, fill(r.primary, corners(unit, 24f, 24f, 24f, 24f))));
            if (child instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) child;
                for (int j = 0; j < vg.getChildCount(); j++) {
                    View inner = vg.getChildAt(j);
                    if (!(inner instanceof ImageView)) inner.setBackground(null);
                }
            }
            tintAll(child, r.onPrimary, unit);
        }

        // TikTok's own buttons of the row (the stickers, the plus, the send) are cut down to the same
        // 48dp: the whole row is one height then, and does not push into the messages above it
        shrink(zone, dp(unit, 48f), 0, cameraBox, camera, field, mic);
    }

    /**
     * Square buttons larger than `size` are cut down to it. The parts of the row that are already
     * drawn by the mod are left as they are, and so is everything that is not a square button.
     */
    private static void shrink(ViewGroup group, int size, int depth, View... skip) {
        if (depth > 4) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            boolean known = false;
            for (View one : skip) if (one == child) known = true;
            if (known) continue;
            int side = child.getWidth();
            if (side > size && side == child.getHeight()) {
                size(child, size, size);
                if (child instanceof ViewGroup) {
                    ViewGroup box = (ViewGroup) child;
                    for (int j = 0; j < box.getChildCount(); j++) {
                        View inner = box.getChildAt(j);
                        int inside = inner.getWidth();
                        if (inside > size && inside == inner.getHeight()) size(inner, size, size);
                    }
                }
            }
            if (child instanceof ViewGroup) shrink((ViewGroup) child, size, depth + 1, skip);
        }
    }

    private static void tintAll(View view, int colour, float unit) {
        if (view instanceof ImageView) {
            size(view, dp(unit, 48f), dp(unit, 48f));
            pad(view, dp(unit, 14f), dp(unit, 14f), dp(unit, 14f), dp(unit, 14f));
            tint(view, colour);
        } else if (view instanceof ViewGroup || view.getClass().getSimpleName().length() > 0) {
            size(view, dp(unit, 48f), dp(unit, 48f));
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) tintAll(g.getChildAt(i), colour, unit);
        }
    }

    // ------------------------------------------------------------------ message bubbles & MD3 grouping

    static final class BubbleSlot {
        final View row;
        final View bubble;
        final boolean incoming;
        final long sender;

        BubbleSlot(View row, View bubble, boolean incoming, long sender) {
            this.row = row;
            this.bubble = bubble;
            this.incoming = incoming;
            this.sender = sender;
        }
    }

    private static void messages(View chat, ProfileM3.Roles r, float unit) {
        ViewGroup list = findMessageList(chat);
        if (list == null || list.getChildCount() == 0) return;
        styleGroupedBubbles(list, r, unit);
    }

    private static ViewGroup findMessageList(View chat) {
        if (!(chat instanceof ViewGroup)) return null;
        ViewGroup rv = findRecyclerView((ViewGroup) chat, 0);
        if (rv != null) return rv;
        return findListFallback((ViewGroup) chat, 0);
    }

    private static ViewGroup findRecyclerView(ViewGroup parent, int depth) {
        if (depth > 8) return null;
        if (parent.getClass().getName().contains("RecyclerView")) return parent;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup) {
                if (child.getClass().getName().contains("RecyclerView")) return (ViewGroup) child;
                ViewGroup found = findRecyclerView((ViewGroup) child, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static ViewGroup findListFallback(ViewGroup parent, int depth) {
        if (depth > 6) return null;
        int withBubbles = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup && findBubble(child) != null) withBubbles++;
        }
        if (withBubbles >= 2) return parent;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup found = findListFallback((ViewGroup) child, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    static View findBubble(View root) {
        if (!(root instanceof ViewGroup)) return null;
        View node = root;
        View candidate = null;
        for (int depth = 0; depth < 5 && node instanceof ViewGroup; depth++) {
            ViewGroup g = (ViewGroup) node;
            View best = null;
            int maxArea = 0;
            for (int i = 0; i < g.getChildCount(); i++) {
                View child = g.getChildAt(i);
                if (child == null || child.getVisibility() != View.VISIBLE) continue;
                if (Integer.valueOf(MessageTimes.TAG).equals(child.getTag(MessageTimes.TAG))) continue;
                if (child instanceof TextView && child.getTag(MessageTimes.TAG) != null) continue;
                int w = child.getWidth(), h = child.getHeight();
                if (w > 0 && w == h && w <= 160 && child.getLeft() < 60 && g.getChildCount() > 1) {
                    continue;
                }
                int area = w * h;
                if (area > maxArea) {
                    maxArea = area;
                    best = child;
                }
            }
            if (best == null) break;
            if (best.getBackground() != null && best != root) {
                candidate = best;
            }
            node = best;
        }
        return candidate != null ? candidate : (node != root && !(node instanceof TextView && node.getTag(MessageTimes.TAG) != null) ? node : null);
    }

    static void styleGroupedBubbles(ViewGroup list, ProfileM3.Roles r, float unit) {
        java.util.List<BubbleSlot> slots = new java.util.ArrayList<BubbleSlot>();
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            View bubble = findBubble(child);
            if (bubble == null) continue;

            boolean incoming;
            Object incTag = child.getTag(INCOMING);
            if (incTag instanceof Boolean) {
                incoming = (Boolean) incTag;
            } else {
                int rowWidth = child.getWidth();
                if (rowWidth <= 0) rowWidth = list.getWidth();
                int bubbleCenter = bubble.getLeft() + bubble.getWidth() / 2;
                incoming = bubbleCenter < rowWidth / 2;
            }

            long sender;
            Object sTag = child.getTag(SENDER);
            if (sTag instanceof Long) {
                sender = (Long) sTag;
            } else {
                sender = incoming ? 1L : -1L;
            }

            slots.add(new BubbleSlot(child, bubble, incoming, sender));
        }

        if (slots.isEmpty()) return;

        java.util.Collections.sort(slots, new java.util.Comparator<BubbleSlot>() {
            @Override
            public int compare(BubbleSlot a, BubbleSlot b) {
                return Integer.compare(a.row.getTop(), b.row.getTop());
            }
        });

        int outColor = r.tertiaryContainer != 0 ? r.tertiaryContainer : r.primaryContainer;
        int inColor = r.high != 0 ? r.high : r.container;

        for (int i = 0; i < slots.size(); i++) {
            BubbleSlot curr = slots.get(i);
            boolean hasPrev = (i > 0) && sameGroup(curr, slots.get(i - 1));
            boolean hasNext = (i < slots.size() - 1) && sameGroup(curr, slots.get(i + 1));

            float tl, tr, br, bl;
            if (curr.incoming) {
                tl = hasPrev ? 4f : 18f;
                tr = 18f;
                br = 18f;
                bl = hasNext ? 4f : 18f;
            } else {
                tl = 18f;
                tr = hasPrev ? 4f : 18f;
                br = hasNext ? 4f : 18f;
                bl = 18f;
            }

            int color = curr.incoming ? inColor : outColor;
            float[] radii = corners(unit, tl, tr, br, bl);
            int cornerKey = ((int) tl) | (((int) tr) << 6) | (((int) br) << 12) | (((int) bl) << 18);
            int key = color ^ cornerKey;

            dress(curr.bubble, key, fill(color, radii));
            tintTexts(curr.bubble, r.ink);
        }
    }

    private static boolean sameGroup(BubbleSlot a, BubbleSlot b) {
        if (a.incoming != b.incoming) return false;
        if (a.sender != 0 && b.sender != 0 && a.sender != b.sender) return false;
        return true;
    }

    static void onBindMessage(ViewGroup root, boolean incoming, long sender) {
        if (!isEnabled()) return;
        root.setTag(INCOMING, Boolean.valueOf(incoming));
        root.setTag(SENDER, Long.valueOf(sender));
        try {
            float unit = root.getResources().getDisplayMetrics().density;
            ProfileM3.Roles r = ProfileM3.roles(root);
            View bubble = findBubble(root);
            if (bubble != null) {
                int color = incoming ? (r.high != 0 ? r.high : r.container)
                        : (r.tertiaryContainer != 0 ? r.tertiaryContainer : r.primaryContainer);
                float[] radii = corners(unit, 18f, 18f, 18f, 18f);
                int key = color ^ (18 | (18 << 6) | (18 << 12) | (18 << 18));
                dress(bubble, key, fill(color, radii));
                tintTexts(bubble, r.ink);
            }
        } catch (Throwable ignored) {}
    }

    private static void tintTexts(View view, int colour) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (Integer.valueOf(MessageTimes.TAG).equals(tv.getTag(MessageTimes.TAG))) return;
            if (tv.getTag(MessageTimes.TAG) != null) return;
            if (tv.getCurrentTextColor() != colour) tv.setTextColor(colour);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) tintTexts(vg.getChildAt(i), colour);
        }
    }
}
