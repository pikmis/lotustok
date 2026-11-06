package cat.narezany.margyt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

/**
 * A piece of TikTok's own screen drawn small, standing next to the setting that changes it.
 *
 * Every one is laid out in the measures of the real screen -- a phone 360 across, the rail of
 * buttons where the feed has it, the author's card, the line under the video, the five pieces of the
 * bottom bar, a profile with its numbers -- and then scaled to the width it is given. It wears what
 * is switched on right now: the accent, the shape of avatars, the capsule bar, Material 3 in the
 * feed and its round icons, the wavy line, round covers, the loading shapes. A switch that is moved
 * is seen to move: each is eased to its new state instead of jumping to it.
 *
 * The profile and the avatar are the signed-in account's own, as far as the mod has seen them.
 *
 * Nothing is stored here. The state is read again on every frame, which is a handful of flags,
 * so a switch anywhere on the page shows up without being wired to this view.
 */
final class DesignPreview extends View {

    static final int RAIL = 0, NAV = 1, GRID = 2, AVATARS = 3, LOADING = 4, ACCENT = 5, TOPBAR = 7,
            DIM = 8, AUTHOR = 9, INBOX = 10, CHAT = 11;

    private static final int F_FEED = 0, F_ICONS = 1, F_CAPSULE = 2, F_ROUND = 3, F_SHAPES = 4, F_WAVE = 5, F_DIM = 6,
            F_THEME = 7, F_FLAG = 8, F_DATES = 9, F_BADGES = 10, F_INBOX = 11, F_TIMES = 12, F_PROFILE = 13, F_COUNT = 14;

    /** The phone everything is drawn on, in dp. */
    private static final float PHONE_W = 360f;

    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final Path clip = new Path();
    private final Path wave = new Path();
    private final Matrix place = new Matrix();
    private final RectF box = new RectF();
    private final float[] eased = new float[F_COUNT];
    private final Drawable[] glyphs = new Drawable[8];
    private final float density;
    private Skin skin;
    private long last;
    private boolean fresh = true;

    DesignPreview(Context context, Skin skin, int kind) {
        super(context);
        this.skin = skin;
        this.kind = kind;
        density = context.getResources().getDisplayMetrics().density;
        // the icons of TikTok's rail and bar, as nearly as the mod's own set has them
        String[] names = {"favorite_border", "chat", "star", "share", "people", "chat", "account_circle", "add"};
        for (int i = 0; i < glyphs.length; i++) glyphs[i] = SettingsGlyph.make(names[i], 0xFFFFFFFF);
        loadAvatar(context);
    }

    void setSkin(Skin skin) {
        this.skin = skin;
        invalidate();
    }

    // ------------------------------------------------------------ the account

    private static volatile Bitmap face;
    private static volatile String faceFrom;

    /** The signed-in account's picture, fetched once; until it is here a plain one is drawn. */
    private void loadAvatar(final Context context) {
        final String address = Account.profileAvatar();
        if (address == null || address.length() == 0 || address.equals(faceFrom)) return;
        faceFrom = address;
        Net.away("preview avatar", new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] raw = Net.bytes(address);
                    if (raw == null) return;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                    if (bitmap == null) return;
                    face = bitmap;
                    post(new Runnable() {
                        @Override
                        public void run() {
                            invalidate();
                        }
                    });
                } catch (Throwable ignored) {
                    faceFrom = null;
                }
            }
        });
    }

    private static String name() {
        String name = Account.profileName();
        return name == null || name.length() == 0 ? "Nickname" : name;
    }

    private static String handle() {
        String handle = Account.profileHandle();
        return "@" + (handle == null || handle.length() == 0 ? "nickname" : handle);
    }

    /** 10 800 as "10,8 тыс.", the way the profile says it. */
    private static String count(long n) {
        if (n < 0) return "—";
        if (n < 10000) return Long.toString(n);
        if (n < 1000000) return String.format(java.util.Locale.forLanguageTag("ru"), "%.1f", n / 1000.0) + " " + Local.of("тыс.");
        return String.format(java.util.Locale.forLanguageTag("ru"), "%.1f", n / 1000000.0) + " " + Local.of("млн");
    }

    // ------------------------------------------------------------------ sizes

    /** Which stretch of the phone this piece shows: top and bottom, in dp. */
    private float top() {
        switch (kind) {
            case RAIL: return 400f;
            case DIM: return 560f;
            case NAV: return 636f;
            case GRID: return 0f;
            default: return 0f;
        }
    }

    private float span() {
        switch (kind) {
            case RAIL: return 410f;
            case DIM: return 250f;
            case NAV: return 176f;
            case GRID: return 300f;
            case AVATARS: return 118f;
            case ACCENT: return 132f;
            case TOPBAR: return 92f;
            case AUTHOR: return 104f;
            case INBOX: return 214f;
            case CHAT: return 136f;
            default: return 110f;
        }
    }

    private static boolean wanted(int which) {
        try {
            switch (which) {
                case F_FEED: return Md3.isOn(Md3.KEY_FEED);
                case F_ICONS: return Md3.isOn(Md3.KEY_ICONS);
                case F_CAPSULE: return NavBar.isEnabled();
                case F_ROUND: return Corners.isEnabled();
                case F_WAVE: return MD3Progress.on();
                case F_DIM: return Dim.isConfigured();
                case F_THEME: return Themes.isEnabled();
                case F_FLAG: return RegionFlag.isEnabled();
                case F_DATES: return Dates.isEnabled();
                case F_BADGES: return false;
                case F_INBOX: return Md3.isOn(Md3.KEY_INBOX);
                case F_TIMES: return MessageTimes.enabled();
                case F_PROFILE: return Flags.isOn(Flags.KEY_NEW_PROFILE);
                default: return Loading.isEnabled();
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, Math.round(span() * w / (PHONE_W * density) * density));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float step = last == 0 ? 1f : Math.min(1f, (now - last) / 1000f * 7f);
        last = now;
        for (int i = 0; i < F_COUNT; i++) {
            float target = wanted(i) ? 1f : 0f;
            if (fresh) eased[i] = target;
            else {
                eased[i] += (target - eased[i]) * step;
                if (Math.abs(target - eased[i]) < 0.01f) eased[i] = target;
            }
        }
        fresh = false;
        int accent = Accent.colour() | 0xFF000000;

        float scale = getWidth() / PHONE_W;
        canvas.save();
        box.set(0, 0, getWidth(), getHeight());
        clip.rewind();
        clip.addRoundRect(box, 14f * density, 14f * density, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.scale(scale, scale);
        canvas.translate(0, -top());
        switch (kind) {
            case RAIL: rail(canvas, accent); break;
            case NAV: nav(canvas, accent); break;
            case GRID: grid(canvas, accent); break;
            case AVATARS: avatars(canvas, accent); break;
            case ACCENT: accentSample(canvas, accent); break;
            case TOPBAR: topbar(canvas, accent); break;
            case DIM: rail(canvas, accent); break;
            case AUTHOR: author(canvas, accent); break;
            case INBOX: inbox(canvas, accent); break;
            case CHAT: chat(canvas, accent); break;
            default: loading(canvas, accent); break;
        }
        canvas.restore();
        // the spinner and the changing shapes never stop, so neither does this while on screen
        if (kind == LOADING || kind == DIM || moving()) postInvalidateOnAnimation();
        else postInvalidateDelayed(250L);
    }

    private boolean moving() {
        for (int i = 0; i < F_COUNT; i++) {
            if (Math.abs((wanted(i) ? 1f : 0f) - eased[i]) > 0.005f) return true;
        }
        return false;
    }

    // ------------------------------------------------------------- small helpers

    private static int alpha(int colour, float factor) {
        int a = Math.round(((colour >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (colour & 0xFFFFFF);
    }

    private void fill(int colour) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
    }

    private float text(Canvas canvas, String words, float x, float y, float size, int colour, boolean bold, Paint.Align align) {
        ink.setTextAlign(align);
        ink.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        ink.setTextSize(size);
        ink.setColor(colour);
        canvas.drawText(words, x, y, ink);
        return ink.measureText(words);
    }

    private String fit(String words, float size, boolean bold, float width) {
        ink.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        ink.setTextSize(size);
        if (ink.measureText(words) <= width) return words;
        int n = ink.breakText(words, true, width - ink.measureText("…"), null);
        return words.substring(0, Math.max(0, n)) + "…";
    }

    private void video(Canvas canvas, float from, float to, float w) {
        fill(0xFF000000);   // a colour that was left in the paint would dim the gradient with its alpha
        paint.setShader(new LinearGradient(0, from, 0, to, 0xFF6A5A4A, 0xFF171310, Shader.TileMode.CLAMP));
        canvas.drawRect(0, from, w, to, paint);
        paint.setShader(null);
    }

    private void glyph(Canvas canvas, int which, float cx, float cy, float size, int colour) {
        Drawable d = glyphs[which];
        if (d == null) return;
        int half = Math.round(size / 2f);
        d.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
        d.setColorFilter(colour, android.graphics.PorterDuff.Mode.SRC_IN);
        d.draw(canvas);
    }

    /** The signed-in account's picture in the chosen shape, with a rim if one is asked for. */
    private void avatar(Canvas canvas, float cx, float cy, float radius, int ring, float rim) {
        shape.rewind();
        place.setScale(radius, radius);
        place.postTranslate(cx, cy);
        shape.addPath(AvatarShape.chosenUnit(), place);
        canvas.save();
        canvas.clipPath(shape);
        Bitmap picture = face;
        if (picture != null) {
            float side = Math.min(picture.getWidth(), picture.getHeight());
            android.graphics.Rect from = new android.graphics.Rect(
                    (int) ((picture.getWidth() - side) / 2), (int) ((picture.getHeight() - side) / 2),
                    (int) ((picture.getWidth() + side) / 2), (int) ((picture.getHeight() + side) / 2));
            box.set(cx - radius, cy - radius, cx + radius, cy + radius);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setFilterBitmap(true);
            canvas.drawBitmap(picture, from, box, paint);
        } else {
            // not seen yet: a face-coloured disc with hair, so it reads as a person
            fill(0xFFB48B6A);
            canvas.drawCircle(cx, cy, radius * 1.5f, paint);
            fill(0xFF3A2A22);
            canvas.drawCircle(cx, cy - radius * 0.35f, radius * 0.78f, paint);
            fill(0xFFF3DCC0);
            canvas.drawCircle(cx, cy - radius * 0.05f, radius * 0.5f, paint);
        }
        canvas.restore();
        if (rim > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(rim);
            paint.setColor(ring);
            canvas.drawPath(shape, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    // ------------------------------------------------------------------ the feed

    /** The rail, the author's card, the line and the bar: the lower right and left of the feed. */
    private void rail(Canvas canvas, int accent) {
        float w = PHONE_W;
        video(canvas, top(), top() + span(), w);
        float rx = 327f;
        // the screen saver: everything over the video, dimmed by the strength that is set
        int layer = -1;
        if (kind == DIM) {
            float dim = eased[F_DIM] * Math.min(90, Dim.strength()) / 100f;
            layer = canvas.saveLayerAlpha(0, top(), w, top() + span(), Math.round(255 * (1f - dim)));
        }

        // the author, with a plus under the picture
        avatar(canvas, rx, 437f, 19.5f, 0xFFFFFFFF, 1.5f);
        fill(accent);
        canvas.drawCircle(rx, 457f, 9f, paint);
        glyph(canvas, 7, rx, 457f, 12f, 0xFFFFFFFF);

        String[] counts = {"499,7 " + Local.of("тыс."), "5260", "29,4 " + Local.of("тыс."), "22,2 " + Local.of("тыс.")};
        float c = eased[F_FEED], t = eased[F_ICONS];
        int placed = 0;
        for (int i = 0; i < 4; i++) {
            // a button that is switched off leaves no gap: the ones below move up
            if (!FeedRail.shown(i)) continue;
            float y0 = 482f + placed * 55f;
            placed++;
            if (c > 0.01f) {
                // the card behind a button: a column of them, the first and the last round at their ends
                float big = 26f, small = 8f;
                float rt = placed == 1 ? big : small, rb = placed == shownCount() ? big : small;
                box.set(rx - 32.5f, y0, rx + 32.5f, y0 + 52f);
                clip.rewind();
                clip.addRoundRect(box, new float[]{rt, rt, rt, rt, rb, rb, rb, rb}, Path.Direction.CW);
                fill(alpha(0xB3202328, c));
                canvas.drawPath(clip, paint);
            }
            if (t > 0.01f) {
                // the round icon: a tonal circle in the accent
                fill(alpha((accent & 0xFFFFFF) | 0xFF000000, 0.42f * t));
                canvas.drawCircle(rx, y0 + 18f, 17f, paint);
            }
            glyph(canvas, i, rx, y0 + 18f, 26f, 0xFFFFFFFF);
            text(canvas, counts[i], rx, y0 + 44f, 12f, 0xFFFFFFFF, true, Paint.Align.CENTER);
        }

        // the sound's disc
        fill(0xFF22201E);
        canvas.drawCircle(rx, 734f, 18f, paint);
        fill(0xFFB89B72);
        canvas.drawCircle(rx, 734f, 10f, paint);
        fill(0xFF22201E);
        canvas.drawCircle(rx, 734f, 2.5f, paint);

        bottom(canvas, accent, true);
        if (layer >= 0) canvas.restoreToCount(layer);
    }

    private static int shownCount() {
        int n = 0;
        for (int i = 0; i < 4; i++) if (FeedRail.shown(i)) n++;
        return n;
    }

    private void nav(Canvas canvas, int accent) {
        video(canvas, top(), top() + span(), PHONE_W);
        bottom(canvas, accent, true);
    }

    /** The author's card, the sound, the line under the video and the bar, in the measures of the real feed. */
    private void bottom(Canvas canvas, int accent, boolean card) {
        float w = PHONE_W;
        if (card) {
            box.set(4f, 653f, 275f, 722f);
            fill(0x99121620);
            canvas.drawRoundRect(box, 16f, 16f, paint);
            if (FeedHide.shown(6)) {
                float used = text(canvas, fit(name(), 16f, true, 130f), 14f, 677f, 16f, 0xFFFFFFFF, true, Paint.Align.LEFT);
                text(canvas, "24 " + Local.of("дня назад"), 14f + used + 10f, 677f, 14f, 0xBBFFFFFF, false, Paint.Align.LEFT);
            }
            if (FeedHide.shown(5)) {
                text(canvas, fit(Local.of("Описание видео и #хештеги"), 14f, false, 250f), 14f, 697f, 14f, 0xFFFFFFFF, false, Paint.Align.LEFT);
                text(canvas, Local.of("ещё") + "  |  " + Local.of("Перевод"), 14f, 714f, 14f, 0xFFFFFFFF, true, Paint.Align.LEFT);
            }
        }
        text(canvas, "♫ " + Local.of("Содержит: оригинальный звук"), 14f, 740f, 14f, 0xFFFFFFFF, false, Paint.Align.LEFT);

        // the line: a wave in the accent for what has played, and the rest in grey
        float y = 754f, cut = w * 0.68f, wv = eased[F_WAVE];
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3.2f);
        paint.setColor(0x66FFFFFF);
        canvas.drawLine(cut + 8f, y, w - 12f, y, paint);
        wave.rewind();
        wave.moveTo(8f, y);
        for (float x = 8f; x <= cut; x += 2f) wave.lineTo(x, y + (float) Math.sin(x / 5.2f) * 3.2f * wv);
        paint.setColor(accent);
        canvas.drawPath(wave, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);

        float n = eased[F_CAPSULE];
        float barTop = 766f, barBottom = 800f;
        // flat: TikTok's own bar, black across the bottom, a name under every icon
        if (n < 0.99f) {
            float f = 1f - n;
            fill(alpha(0xFF000000, f));
            canvas.drawRect(0, 762f, w, 820f, paint);
            String[] names = {Local.of("Главная"), Local.of("Друзья"), "", Local.of("Входящие"), Local.of("Профиль")};
            float cell = w / 5f;
            for (int i = 0; i < 5; i++) {
                float cx = cell * (i + 0.5f);
                if (i == 2) {
                    fill(alpha(accent, f));
                    box.set(cx - 22f, 770f, cx + 22f, 798f);
                    canvas.drawRoundRect(box, 9f, 9f, paint);
                    glyph(canvas, 7, cx, 784f, 20f, alpha(0xFFFFFFFF, f) | 0xFF000000);
                    continue;
                }
                int on = i == 0 ? 0xFFFFFFFF : 0x99FFFFFF;
                if (i == 0) house(canvas, cx, 779f, 22f, alpha(on, f));
                else if (i == 4) avatar(canvas, cx, 779f, 11f, 0xFFFFFFFF, 0f);
                else glyph(canvas, i == 1 ? 4 : 5, cx, 779f, 22f, alpha(on, f) | 0xFF000000);
                text(canvas, names[i], cx, 806f, 10f, alpha(on, f), i == 0, Paint.Align.CENTER);
            }
        }
        // the capsule: separate pieces over the video, the open one wide with its name
        if (n > 0.01f) {
            float[] weight = {2.4f, 1f, 1.3f, 1.3f, 1.3f};
            float sum = 0;
            for (float v : weight) sum += v;
            float gap = 4f, left = 15f, room = w - 2 * left - gap * 4;
            float x = left;
            for (int i = 0; i < 5; i++) {
                float pw = room * weight[i] / sum;
                box.set(x, barTop, x + pw, barBottom);
                float cx = x + pw / 2f, cy = (barTop + barBottom) / 2f;
                if (i == 0) fill(alpha((accent & 0xFFFFFF) | 0xFF000000, 0.38f * n));
                else if (i == 2) fill(alpha(accent, n));
                else fill(alpha(0xCC1F2327, n));
                canvas.drawRoundRect(box, 17f, 17f, paint);
                if (i == 0) {
                    house(canvas, x + 20f, cy, 20f, alpha(0xFFFFFFFF, n));
                    text(canvas, Local.of("Главная"), x + 38f, cy + 4.5f, 13f, alpha(0xFFFFFFFF, n), true, Paint.Align.LEFT);
                } else if (i == 2) glyph(canvas, 7, cx, cy, 20f, alpha(0xFFFFFFFF, n) | 0xFF000000);
                else if (i == 4) avatar(canvas, cx, cy, 11f, 0xFFFFFFFF, 0f);
                else glyph(canvas, i == 1 ? 4 : 5, cx, cy, 20f, alpha(0xFFFFFFFF, n) | 0xFF000000);
                x += pw + gap;
            }
        }
    }

    private void house(Canvas canvas, float cx, float cy, float size, int colour) {
        float s = size / 2f;
        shape.rewind();
        shape.moveTo(cx, cy - s);
        shape.lineTo(cx + s, cy - s * 0.05f);
        shape.lineTo(cx + s * 0.72f, cy - s * 0.05f);
        shape.lineTo(cx + s * 0.72f, cy + s * 0.85f);
        shape.lineTo(cx + s * 0.22f, cy + s * 0.85f);
        shape.lineTo(cx + s * 0.22f, cy + s * 0.28f);
        shape.lineTo(cx - s * 0.22f, cy + s * 0.28f);
        shape.lineTo(cx - s * 0.22f, cy + s * 0.85f);
        shape.lineTo(cx - s * 0.72f, cy + s * 0.85f);
        shape.lineTo(cx - s * 0.72f, cy - s * 0.05f);
        shape.lineTo(cx - s, cy - s * 0.05f);
        shape.close();
        fill(colour);
        canvas.drawPath(shape, paint);
    }

    // --------------------------------------------------------------- the profile

    private void grid(Canvas canvas, int accent) {
        float w = PHONE_W;
        fill(skin.page);
        canvas.drawRect(0, 0, w, span(), paint);
        int body = skin.text;

        if (eased[F_PROFILE] < 0.5f) {
            oldGrid(canvas, accent);
            return;
        }
        // the head: the name and the numbers on the left, the picture on the right
        float margin = 14f;
        text(canvas, fit(name(), 26f, true, 240f), margin, 40f, 26f, body, true, Paint.Align.LEFT);
        text(canvas, fit(handle(), 15f, false, 240f), margin, 64f, 15f, alpha(body, 0.55f), false, Paint.Align.LEFT);
        long following = Account.profileFollowing(), followers = Account.profileFollowers(), likes = Account.profileLikes();
        String[] number = {count(following), count(followers), count(likes)};
        String[] caption = {Local.of("Подписки"), Local.of("Подписчиков"), Local.of("Лайки")};
        float x = margin;
        for (int i = 0; i < 3; i++) {
            text(canvas, number[i], x, 106f, 20f, body, true, Paint.Align.LEFT);
            text(canvas, caption[i], x, 124f, 13f, alpha(body, 0.55f), false, Paint.Align.LEFT);
            x += 92f;
        }
        avatar(canvas, w - 62f, 62f, 43f, accent, 0f);

        // the buttons
        fill(alpha(body, 0.09f));
        box.set(margin, 144f, 214f, 180f);
        canvas.drawRoundRect(box, 18f, 18f, paint);
        text(canvas, Local.of("Изменить профиль"), (box.left + box.right) / 2f, 167f, 14f, body, true, Paint.Align.CENTER);
        box.set(222f, 144f, w - margin, 180f);
        canvas.drawRoundRect(box, 18f, 18f, paint);
        text(canvas, Local.of("Поделиться"), (box.left + box.right) / 2f, 167f, 14f, body, true, Paint.Align.CENTER);

        // the covers, rounder when the switch is on
        float radius = 0.5f + eased[F_ROUND] * 12f;
        int[] tones = {0xFF7A5C9E, 0xFF4E8F8B, 0xFFB5764E};
        String[] plays = {"8296", "1482", "2424"};
        float cell = (w - 2f * 4f) / 3f;
        for (int i = 0; i < 3; i++) {
            float cx = 2f + i * (cell + 2f);
            box.set(cx, 200f, cx + cell, 440f);
            fill(0xFF000000);
            paint.setShader(new LinearGradient(0, 200f, 0, 330f, tones[i], alpha(tones[i], 0.5f), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(box, radius, radius, paint);
            paint.setShader(null);
            text(canvas, "▷ " + plays[i], cx + 10f, 280f, 14f, 0xFFFFFFFF, true, Paint.Align.LEFT);
        }
    }

    /** The profile as it was before the new one: the picture in the middle, the name under it, the numbers in a row. */
    private void oldGrid(Canvas canvas, int accent) {
        float w = PHONE_W;
        int body = skin.text;
        avatar(canvas, w / 2f, 58f, 40f, accent, 0f);
        text(canvas, fit(handle(), 17f, true, 300f), w / 2f, 124f, 17f, body, true, Paint.Align.CENTER);
        long following = Account.profileFollowing(), followers = Account.profileFollowers(), likes = Account.profileLikes();
        String[] number = {count(following), count(followers), count(likes)};
        String[] caption = {Local.of("Подписки"), Local.of("Подписчиков"), Local.of("Лайки")};
        for (int i = 0; i < 3; i++) {
            float cx = w * (0.2f + 0.3f * i);
            text(canvas, number[i], cx, 158f, 18f, body, true, Paint.Align.CENTER);
            text(canvas, caption[i], cx, 176f, 12f, alpha(body, 0.55f), false, Paint.Align.CENTER);
        }
        fill(alpha(body, 0.09f));
        box.set(48f, 194f, w - 48f, 226f);
        canvas.drawRoundRect(box, 3f, 3f, paint);
        text(canvas, Local.of("Изменить профиль"), w / 2f, 215f, 14f, body, true, Paint.Align.CENTER);
        float radius = 0.5f + eased[F_ROUND] * 12f;
        int[] tones = {0xFF7A5C9E, 0xFF4E8F8B, 0xFFB5764E};
        float cell = (w - 2f * 4f) / 3f;
        for (int i = 0; i < 3; i++) {
            float cx = 2f + i * (cell + 2f);
            box.set(cx, 246f, cx + cell, 440f);
            fill(0xFF000000);
            paint.setShader(new LinearGradient(0, 246f, 0, 350f, tones[i], alpha(tones[i], 0.5f), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(box, radius, radius, paint);
            paint.setShader(null);
        }
    }

    // ----------------------------------------------------------------- avatars

    private void avatars(Canvas canvas, int accent) {
        float w = PHONE_W;
        fill(skin.page);
        canvas.drawRect(0, 0, w, span(), paint);
        int body = skin.text;
        float cy = span() / 2f;

        // beside a video, with its plus
        float x1 = w * 0.16f;
        avatar(canvas, x1, cy - 4f, 26f, 0xFFFFFFFF, 2f);
        fill(accent);
        canvas.drawCircle(x1, cy - 4f + 26f, 10f, paint);
        glyph(canvas, 7, x1, cy - 4f + 26f, 13f, 0xFFFFFFFF);

        // on a profile, in the ring of a live stream
        float x2 = w * 0.45f;
        avatar(canvas, x2, cy - 2f, 34f, 0xFFFF2D6A, 3.5f);
        fill(0xFF1F6FEB);
        box.set(x2 - 20f, cy + 24f, x2 + 20f, cy + 42f);
        canvas.drawRoundRect(box, 4f, 4f, paint);
        text(canvas, Local.of("Эфир"), x2, cy + 38f, 12f, 0xFFFFFFFF, true, Paint.Align.CENTER);

        // in a list of chats
        float x3 = w * 0.66f;
        avatar(canvas, x3, cy, 24f, 0xFFFFFFFF, 0f);
        text(canvas, fit(name(), 15f, true, 100f), x3 + 34f, cy - 3f, 15f, body, true, Paint.Align.LEFT);
        text(canvas, Local.of("Привет!"), x3 + 34f, cy + 15f, 13f, alpha(body, 0.55f), false, Paint.Align.LEFT);
    }

    // ------------------------------------------------------------ the accent

    /** What the accent colours: a button, the open tab, a switch, the line under a video, a link. */
    private void accentSample(Canvas canvas, int accent) {
        float w = PHONE_W;
        fill(skin.page);
        canvas.drawRect(0, 0, w, span(), paint);
        int body = skin.text;

        fill(accent);
        box.set(14f, 16f, 160f, 52f);
        canvas.drawRoundRect(box, 18f, 18f, paint);
        text(canvas, Local.of("Подписаться"), 87f, 39f, 14f, 0xFFFFFFFF, true, Paint.Align.CENTER);

        // two tabs, the open one underlined
        text(canvas, Local.of("Подписки"), 190f, 38f, 15f, alpha(body, 0.55f), false, Paint.Align.LEFT);
        float used = text(canvas, Local.of("Для вас"), 270f, 38f, 15f, body, true, Paint.Align.LEFT);
        fill(accent);
        box.set(270f, 46f, 270f + used, 49f);
        canvas.drawRoundRect(box, 1.5f, 1.5f, paint);

        // a switch that is on
        fill(accent);
        box.set(14f, 70f, 66f, 96f);
        canvas.drawRoundRect(box, 13f, 13f, paint);
        fill(0xFFFFFFFF);
        canvas.drawCircle(53f, 83f, 9f, paint);
        text(canvas, Local.of("Включено"), 78f, 88f, 14f, body, false, Paint.Align.LEFT);

        // a link and a number that is liked
        text(canvas, "#hashtag", 190f, 88f, 15f, accent, true, Paint.Align.LEFT);
        glyph(canvas, 0, 282f, 82f, 22f, accent);
        text(canvas, "12,4 " + Local.of("тыс."), 297f, 87f, 11f, body, true, Paint.Align.LEFT);

        // the line under a video
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3.2f);
        paint.setColor(alpha(body, 0.25f));
        canvas.drawLine(w * 0.66f, 116f, w - 14f, 116f, paint);
        wave.rewind();
        wave.moveTo(14f, 116f);
        for (float x = 14f; x <= w * 0.64f; x += 2f) wave.lineTo(x, 116f + (float) Math.sin(x / 5.2f) * 3.2f * eased[F_WAVE]);
        paint.setColor(accent);
        canvas.drawPath(wave, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    // ---------------------------------------------------------- colour mixing

    private static int mix(int from, int to, float how) {
        int r = Math.round(((from >> 16) & 0xFF) * (1f - how) + ((to >> 16) & 0xFF) * how);
        int g = Math.round(((from >> 8) & 0xFF) * (1f - how) + ((to >> 8) & 0xFF) * how);
        int b = Math.round((from & 0xFF) * (1f - how) + (to & 0xFF) * how);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------ the top of the feed

    private void topbar(Canvas canvas, int accent) {
        float w = PHONE_W;
        video(canvas, 0f, span(), w);
        // the LIVE button, top left
        if (FeedHide.shown(0)) {
            fill(0x66000000);
            box.set(14f, 22f, 66f, 48f);
            canvas.drawRoundRect(box, 13f, 13f, paint);
            fill(0xFFFF3B5C);
            canvas.drawCircle(27f, 35f, 3.5f, paint);
            text(canvas, "LIVE", 35f, 40f, 12f, 0xFFFFFFFF, true, Paint.Align.LEFT);
        }
        // the tabs, the open one white and underlined
        String[] tab = {Local.of("Сериалы"), Local.of("Подписки"), Local.of("Для вас")};
        boolean[] on = {FeedHide.shown(4), FeedHide.shown(3), FeedHide.shown(2)};
        float x = 96f;
        for (int i = 0; i < 3; i++) {
            if (!on[i]) continue;
            boolean open = i == 2;
            float used = text(canvas, tab[i], x, 42f, 16f, open ? 0xFFFFFFFF : 0xB3FFFFFF, open, Paint.Align.LEFT);
            if (open) {
                fill(0xFFFFFFFF);
                box.set(x + used / 2f - 12f, 50f, x + used / 2f + 12f, 53f);
                canvas.drawRoundRect(box, 1.5f, 1.5f, paint);
            }
            x += used + 16f;
        }
        // the search, top right
        if (FeedHide.shown(1)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.4f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(w - 33f, 34f, 7.5f, paint);
            canvas.drawLine(w - 27.5f, 39.5f, w - 21f, 46f, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    // ------------------------------------------------------------ the author

    private void author(Canvas canvas, int accent) {
        float w = PHONE_W;
        video(canvas, 0f, span(), w);
        box.set(8f, 12f, w - 8f, span() - 12f);
        fill(0x99121620);
        canvas.drawRoundRect(box, 16f, 16f, paint);
        float x = 20f;
        x += text(canvas, fit(name(), 17f, true, 120f), x, 42f, 17f, 0xFFFFFFFF, true, Paint.Align.LEFT) + 6f;

        // the country, a dark square with its flag
        float flag = eased[F_FLAG];
        if (flag > 0.01f) {
            fill(alpha(0xFF161616, flag));
            box.set(x, 24f, x + 26f, 50f);
            canvas.drawRoundRect(box, 8f, 8f, paint);
            ink.setTypeface(Fonts.twemoji(getContext()));
            ink.setTextSize(14f);
            ink.setTextAlign(Paint.Align.CENTER);
            ink.setColor(alpha(0xFFFFFFFF, flag));
            String emoji = RegionFlag.flag(java.util.Locale.getDefault().getCountry());
            canvas.drawText(emoji == null ? "\uD83C\uDDFA\uD83C\uDDE6" : emoji, x + 13f, 42f, ink);
            x += 32f;
        }
        // the date: the day it was posted, or how long ago
        String[] styles = {"2 " + Local.of("мес.") + " 3 " + Local.of("дн.") + " " + Local.of("назад"),
                "3 " + Local.of("дн.") + " " + Local.of("назад"), "15.10.2026"};
        String date = eased[F_DATES] > 0.5f ? styles[Math.max(0, Math.min(2, Dates.style()))] : "24 " + Local.of("дня назад");
        text(canvas, date, x, 42f, 14f, 0xBBFFFFFF, false, Paint.Align.LEFT);
        text(canvas, fit(Local.of("Описание видео и #хештеги"), 14f, false, w - 56f), 20f, 68f, 14f, 0xFFFFFFFF, false, Paint.Align.LEFT);
        text(canvas, Local.of("ещё") + "  |  " + Local.of("Перевод"), 20f, 86f, 14f, 0xFFFFFFFF, true, Paint.Align.LEFT);
    }

    // ------------------------------------------------------------- the inbox

    private void inbox(Canvas canvas, int accent) {
        float w = PHONE_W;
        float md = eased[F_INBOX];
        int body = skin.text;
        int page = mix(skin.card, skin.page, md);
        fill(page);
        canvas.drawRect(0, 0, w, span(), paint);
        String[] who = {name(), "LotusTok", "Maria"};
        String[] said = {Local.of("Привет!"), Local.of("LotusTok лучший мод!"), Local.of("да, да, LotusTok лучший мод!")};
        String[] when = {"15:42", "14:08", Local.of("Вчера")};
        for (int i = 0; i < 3; i++) {
            float y = 8f + i * (md > 0.5f ? 68f : 66f);
            float inset = 10f * md;
            // Material 3: every chat a card, the first and the last rounder
            float rt = i == 0 ? 22f : 6f, rb = i == 2 ? 22f : 6f;
            box.set(inset, y, w - inset, y + 64f);
            clip.rewind();
            clip.addRoundRect(box, new float[]{rt, rt, rt, rt, rb, rb, rb, rb}, Path.Direction.CW);
            fill(alpha(skin.card, md));
            canvas.drawPath(clip, paint);
            if (md < 0.5f && i < 2) {
                fill(alpha(body, 0.10f));
                canvas.drawRect(76f, y + 65f, w, y + 66f, paint);
            }
            if (i == 0) avatar(canvas, 40f, y + 32f, 22f, 0xFFFFFFFF, 0f);
            else if (i == 1) roundPicture(canvas, Launcher.preview("bloom"), 40f, y + 32f, 22f);
            else initial(canvas, "M", 40f, y + 32f, 22f, 0xFF9C6ADE);
            text(canvas, fit(who[i], 16f, true, 190f), 76f, y + 28f, 16f, body, true, Paint.Align.LEFT);
            text(canvas, fit(said[i], 14f, false, 200f), 76f, y + 48f, 14f, alpha(body, 0.55f), false, Paint.Align.LEFT);
            text(canvas, when[i], w - 20f - inset, y + 26f, 12f, alpha(body, 0.45f), false, Paint.Align.RIGHT);
            if (i == 2) {
                fill(0xFFFF3B5C);
                canvas.drawCircle(w - 28f - inset, y + 46f, 9f, paint);
                text(canvas, "2", w - 28f - inset, y + 50f, 11f, 0xFFFFFFFF, true, Paint.Align.CENTER);
            }
        }
    }

    /** A picture cut to a circle, for the chats of the inbox. */
    private void roundPicture(Canvas canvas, Bitmap picture, float cx, float cy, float radius) {
        if (picture == null) {
            initial(canvas, "L", cx, cy, radius, 0xFFB57BEE);
            return;
        }
        clip.rewind();
        clip.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        box.set(cx - radius, cy - radius, cx + radius, cy + radius);
        fill(0xFFFFFFFF);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(picture, null, box, paint);
        canvas.restore();
    }

    /** A person with no picture of their own: a colour and the first letter of the name. */
    private void initial(Canvas canvas, String letter, float cx, float cy, float radius, int colour) {
        fill(colour);
        canvas.drawCircle(cx, cy, radius, paint);
        text(canvas, letter, cx, cy + radius * 0.36f, radius * 0.95f, 0xFFFFFFFF, true, Paint.Align.CENTER);
    }

    // --------------------------------------------------------------- a chat

    private void chat(Canvas canvas, int accent) {
        float w = PHONE_W;
        fill(skin.page);
        canvas.drawRect(0, 0, w, span(), paint);
        int body = skin.text;
        float times = eased[F_TIMES];
        String clock = MessageTimes.formatName();

        // theirs
        box.set(14f, 14f, 200f, 52f);
        fill(alpha(body, 0.09f));
        canvas.drawRoundRect(box, 16f, 16f, paint);
        text(canvas, Local.of("Привет! Как дела?"), 28f, 38f, 15f, body, false, Paint.Align.LEFT);
        text(canvas, clock, 24f, 66f, 11f, alpha(alpha(body, 0.5f), times), false, Paint.Align.LEFT);
        // mine
        float shift = 66f * times;
        box.set(w - 200f, 66f + shift - 66f * times + 10f * times, w - 14f, 104f + 10f * times);
        fill(accent);
        canvas.drawRoundRect(box, 16f, 16f, paint);
        text(canvas, Local.of("Всё отлично, а у тебя?"), w - 186f, 90f + 10f * times, 15f, 0xFFFFFFFF, false, Paint.Align.LEFT);
        text(canvas, clock, w - 24f, 120f + 10f * times, 11f, alpha(alpha(body, 0.5f), times), false, Paint.Align.RIGHT);
    }

    // ---------------------------------------------------------------- loading

    private void loading(Canvas canvas, int accent) {
        float w = PHONE_W;
        fill(skin.page);
        canvas.drawRect(0, 0, w, span(), paint);
        float cx = w / 2f, cy = span() / 2f;
        long now = SystemClock.uptimeMillis();
        float s = eased[F_SHAPES];

        // the ordinary indicator: an arc going round
        if (s < 0.99f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(alpha((skin.text & 0xFFFFFF) | 0xFF000000, (1f - s) * 0.85f));
            float r = 22f;
            box.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(box, (now % 900) / 900f * 360f, 100f, false, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }
        // the shapes: one that turns and becomes the next
        if (s > 0.01f) {
            int count = AvatarShape.ALL.length - 1;
            float phase = (now % 6000) / 6000f * count;
            int which = 1 + (int) phase % count;
            float into = phase - (float) Math.floor(phase);
            float pulse = 0.85f + 0.15f * (float) Math.sin(into * Math.PI);
            shape.rewind();
            place.setRotate((now % 3000) / 3000f * 360f);
            place.postScale(22f * pulse, 22f * pulse);
            place.postTranslate(cx, cy);
            shape.addPath(AvatarShape.unit(which), place);
            fill(alpha(accent, s));
            canvas.drawPath(shape, paint);
        }
    }
}
