package cat.narezany.margyt;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The window that says what supporting the project gives, and how to get it.
 *
 * It shows the badge the way it will look -- beside the name of the person who is looking, with
 * their own picture -- and the three things to do in order: pay, send the screenshot, wait for
 * the badge. It is opened from the settings and from the supporter badge itself.
 */
final class SupportCard {

    private SupportCard() {}

    /** Who the screenshot goes to. */
    static final String WRITE = "https://t.me/saintcursed";

    /** The badges that make somebody a supporter, the first one that exists is the one shown. */
    static final String[] BADGES = {"supporter_lotustok", "supporter"};

    /** Whether this badge is the supporter's. */
    static boolean isSupporter(Badges.Badge badge) {
        if (badge == null) return false;
        for (String id : BADGES) if (id.equals(badge.id)) return true;
        return false;
    }

    /** The picture of the signed-in account, kept once it has been fetched. */
    private static volatile Bitmap face;
    private static volatile String faceFrom;

    static void show(final Context context) {
        try {
            Skin skin = Skin.remembered(context);
            final Dialog dialog = new SheetDialog(context, true);
            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(context, 24), dp(context, 28), dp(context, 24), dp(context, 18));
            GradientDrawable background = new GradientDrawable();
            background.setColor(skin.card);
            background.setCornerRadius(Math.max(skin.radius, dp(context, 28)));
            card.setBackground(background);

            // the mark, in the accent
            FrameLayout mark = new FrameLayout(context);
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(Accent.colour());
            mark.setBackground(disc);
            ImageView star = new ImageView(context);
            star.setImageBitmap(icon("star"));
            star.setColorFilter(onAccent(), PorterDuff.Mode.SRC_IN);
            mark.addView(star, new FrameLayout.LayoutParams(dp(context, 40), dp(context, 40), Gravity.CENTER));
            LinearLayout.LayoutParams markSize = new LinearLayout.LayoutParams(dp(context, 76), dp(context, 76));
            markSize.gravity = Gravity.CENTER_HORIZONTAL;
            card.addView(mark, markSize);

            TextView head = new TextView(context);
            head.setText(Local.of("Поддержать разработку"));
            head.setTextColor(skin.text);
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setGravity(Gravity.CENTER);
            head.setPadding(0, dp(context, 16), 0, dp(context, 6));
            card.addView(head);

            TextView sub = new TextView(context);
            sub.setText(Local.of("Поддержка проекта и личный бейдж рядом с именем."));
            sub.setTextColor(skin.muted());
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, 0, 0, dp(context, 14));
            card.addView(sub);

            card.addView(step(context, skin, "favorite_border", Local.of("Пожертвование"),
                    Local.of("Перевод от 20 рублей и больше на реквизиты из раздела «Поддержать проект» в настройках LotusTok.")));
            card.addView(step(context, skin, "image", Local.of("Подтверждение"),
                    Local.of("Скриншот перевода отправляется @saintcursed. На нём должны быть видны сумма, дата и время.")));
            card.addView(step(context, skin, "star", Local.of("Получение бейджа"),
                    Local.of("После проверки перевода бейдж появляется рядом с именем у всех пользователей LotusTok.")));

            card.addView(chip(context, skin));

            TextView write = new TextView(context);
            write.setText(Local.of("Написать разработчику"));
            write.setTextColor(onAccent());
            write.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            write.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            write.setGravity(Gravity.CENTER);
            write.setPadding(0, dp(context, 13), 0, dp(context, 13));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Accent.colour());
            pill.setCornerRadius(dp(context, 24));
            write.setBackground(pill);
            write.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent go = new Intent(Intent.ACTION_VIEW, Uri.parse(WRITE));
                        go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        v.getContext().startActivity(go);
                    } catch (Throwable error) {
                        Diary.note("support: " + error);
                    }
                    try {
                        dialog.dismiss();
                    } catch (Throwable ignored) {
                    }
                }
            });
            LinearLayout.LayoutParams writeSize = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            writeSize.topMargin = dp(context, 16);
            card.addView(write, writeSize);

            android.widget.ScrollView scroll = new android.widget.ScrollView(context);
            scroll.addView(card);
            dialog.setContentView(scroll);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 380),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92f));
                params.gravity = Gravity.CENTER;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("support: " + error);
        }
    }

    /** One of the three things to do: a small picture, what it is called, and what to do. */
    private static View step(Context context, Skin skin, String picture, String title, String words) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(context, 8), 0, dp(context, 8));

        ImageView lead = new ImageView(context);
        lead.setImageBitmap(icon(picture));
        lead.setColorFilter(skin.text, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams leadSize = new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24));
        leadSize.rightMargin = dp(context, 16);
        leadSize.topMargin = dp(context, 2);
        row.addView(lead, leadSize);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setText(title);
        name.setTextColor(skin.text);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        text.addView(name);
        TextView body = new TextView(context);
        body.setText(words);
        body.setTextColor(skin.muted());
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        text.addView(body);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** How the badge looks: the person's own picture and name, with the badge beside it. */
    private static View chip(final Context context, Skin skin) {
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(context, 12), dp(context, 12), dp(context, 16), dp(context, 12));
        GradientDrawable back = new GradientDrawable();
        back.setColor(skin.page);
        back.setCornerRadius(dp(context, 20));
        chip.setBackground(back);

        final ImageView avatar = new ImageView(context);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable plain = new GradientDrawable();
        plain.setShape(GradientDrawable.OVAL);
        plain.setColor(skin.muted());
        avatar.setBackground(plain);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        if (face != null) avatar.setImageBitmap(face);
        else loadFace(avatar);
        LinearLayout.LayoutParams avatarSize = new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44));
        avatarSize.rightMargin = dp(context, 12);
        chip.addView(avatar, avatarSize);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);

        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(context);
        String who = Account.profileName();
        name.setText(who == null || who.length() == 0 ? Local.of("Ник") : who);
        name.setTextColor(Accent.colour());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        View badge = badge(context);
        if (badge != null) {
            LinearLayout.LayoutParams badgeSize = new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18));
            badgeSize.leftMargin = dp(context, 6);
            line.addView(badge, badgeSize);
        }
        text.addView(line);

        TextView says = new TextView(context);
        says.setText(Local.of("Так выглядит бейдж."));
        says.setTextColor(skin.text);
        says.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        says.setPadding(0, dp(context, 2), 0, 0);
        text.addView(says);
        chip.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams chipSize = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipSize.topMargin = dp(context, 14);
        chip.setLayoutParams(chipSize);
        return chip;
    }

    /** The supporter's badge as a small picture, or the mod's own note until it has arrived. */
    private static View badge(Context context) {
        Badges.Badge found = null;
        for (String id : BADGES) {
            found = Badges.byId(id);
            if (found != null) break;
        }
        ImageView view = new ImageView(context);
        Bitmap picture = found == null || found.image.length() == 0
                ? null : Badges.picture(context, found.image);
        view.setImageBitmap(picture != null ? picture : Badges.note());
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (found != null && found.colour != 0) {
            view.setColorFilter(found.colour, PorterDuff.Mode.SRC_IN);
        }
        return view;
    }

    private static void loadFace(final ImageView into) {
        final String address = Account.profileAvatar();
        if (address == null || address.length() == 0 || address.equals(faceFrom)) return;
        faceFrom = address;
        Net.away("support avatar", new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] raw = Net.bytes(address);
                    if (raw == null) return;
                    final Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                    if (bitmap == null) return;
                    face = bitmap;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            into.setImageBitmap(bitmap);
                        }
                    });
                } catch (Throwable ignored) {
                    faceFrom = null;
                }
            }
        });
    }

    private static Bitmap icon(String name) {
        try {
            String data = Icons.PNG.get(name);
            if (data == null) return null;
            byte[] png = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(png, 0, png.length);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Dark letters on a light accent, white on a dark one. */
    private static int onAccent() {
        int colour = Accent.colour();
        int red = (colour >> 16) & 0xFF, green = (colour >> 8) & 0xFF, blue = colour & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000 > 150 ? 0xFF1C2C24 : 0xFFFFFFFF;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
