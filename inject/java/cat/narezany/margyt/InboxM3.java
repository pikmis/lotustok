package cat.narezany.margyt;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * The inbox in the shapes of Material 3 Expressive.
 *
 * TikTok's own views are kept, and they still do what they did: the heading is put over the bar at the top,
 * which stays under it with its buttons, and the new buttons press the old ones; the stories lose the ring
 * round them and are cut to the chosen shape of avatars; the chips of the folders are pills with the chosen
 * one in the accent; the pictures of the chats are cut to that shape too, and the number of what is unread is
 * set in a cookie. The colours are the page's, as TikTok has it, and the accent.
 *
 * Nothing here is known by a name of TikTok's code: the views are found by the ids of this version of the app,
 * one at a time, and a piece that is not where it was is left alone.
 */
final class InboxM3 {

    private InboxM3() {}

    private static final int HEADER = 0x7e0c0350, HIDDEN = 0x7e0c0351, STYLED = 0x7e0c0352, OURS = 0x7e0c0353, CHOSEN = 0x7e0c0354;

    private static WeakReference<Page> current = new WeakReference<>(null);

    static boolean isEnabled() {
        return !Version.LITE && Md3.isOn(Md3.KEY_INBOX);
    }

    /** Once a frame, from the screen that watches the rows: with the views the rows are in. */
    static void frame(ViewGroup fromRows) {
        try {
            Page page = current.get();
            if (page == null || !page.alive()) {
                page = Page.find(fromRows);
                if (page == null) return;
                current = new WeakReference<>(page);
            }
            if (isEnabled()) page.tick(); else page.off();
        } catch (Throwable error) {
            Diary.detail("inbox m3: " + error);
        }
    }

    // ---------------------------------------------------------------------------------------- the page

    private static final class Page {
        final LinearLayout root;
        final ViewGroup bar;
        final ViewGroup list;
        final int idRings1, idRings2, idAvatar, idSmall, idBig, idSelect, idBarRow, idTitle, idAdd, idSearch, idMore;
        int ground, accent;
        ProfileM3.Roles roles;
        Header header;
        int barHeight = Integer.MIN_VALUE;

        Page(LinearLayout root, ViewGroup bar, ViewGroup list) {
            this.root = root;
            this.bar = bar;
            this.list = list;
            Resources r = root.getResources();
            String pack = root.getContext().getPackageName();
            idRings1 = r.getIdentifier("wuz", "id", pack);
            idRings2 = r.getIdentifier("wuo", "id", pack);
            idAvatar = r.getIdentifier("wup", "id", pack);
            idSmall = r.getIdentifier("sti", "id", pack);
            idBig = r.getIdentifier("sth", "id", pack);
            idSelect = r.getIdentifier("l7f", "id", pack);
            idBarRow = r.getIdentifier("l8s", "id", pack);
            idTitle = r.getIdentifier("title", "id", pack);
            idAdd = r.getIdentifier("fha", "id", pack);
            idSearch = r.getIdentifier("kou", "id", pack);
            idMore = r.getIdentifier("gcv", "id", pack);
        }

        boolean alive() {
            return root.isAttachedToWindow() && list.isAttachedToWindow();
        }

        /** The page the rows are on: the column that has the status bar over the bar, the bar, and the list. */
        static Page find(View from) {
            View up = from;
            for (int level = 0; level < 14 && up != null; level++) {
                if (up instanceof LinearLayout && ((LinearLayout) up).getChildCount() >= 3
                        && "v_status_bar".equals(idName(((LinearLayout) up).getChildAt(0)))
                        && ((LinearLayout) up).getChildAt(1) instanceof ViewGroup) {
                    LinearLayout column = (LinearLayout) up;
                    ViewGroup bar = (ViewGroup) column.getChildAt(1);
                    int id = column.getResources().getIdentifier("l78", "id", column.getContext().getPackageName());
                    View list = id == 0 ? null : column.findViewById(id);
                    if (list instanceof ViewGroup) return new Page(column, bar, (ViewGroup) list);
                    return null;
                }
                android.view.ViewParent next = up.getParent();
                up = next instanceof View ? (View) next : null;
            }
            return null;
        }

        void tick() {
            int now = Md3.behind(list) | 0xFF000000;
            int tint = Accent.colour() | 0xFF000000;
            if (roles == null || now != ground || tint != accent) {
                ground = now;
                accent = tint;
                roles = ProfileM3.rolesFor(ground);
                if (header != null) header.dress(roles);
            }
            heading();
            stories();
            chips();
            rows();
        }

        // ------------------------------------------------------------------------ the heading

        void heading() {
            Object known = bar.getTag(HEADER);
            if (!(known instanceof Header)) {
                header = new Header(bar.getContext(), this);
                header.dress(roles);
                bar.setTag(HEADER, header);
                if (barHeight == Integer.MIN_VALUE) {
                    ViewGroup.LayoutParams params = bar.getLayoutParams();
                    if (params != null) {
                        barHeight = params.height;
                        // a generous start: the heading fits inside it from the first pass and can still
                        // grow when the count of the unread appears; a tight start would cut its foot
                        params.height = Math.round(300f * bar.getResources().getDisplayMetrics().density);
                        bar.setLayoutParams(params);
                    }
                }
                bar.addView(header, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                bar.setClipChildren(false);
                // the bar is as tall as the heading is, once that is known
                header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                    ViewGroup.LayoutParams params = bar.getLayoutParams();
                    if (params != null && header != null && header.getHeight() > 0 && params.height != header.getHeight()
                            && bar.getTag(HEADER) == header) {
                        params.height = header.getHeight();
                        bar.setLayoutParams(params);
                    }
                });
            } else {
                header = (Header) known;
            }
            // TikTok's own bar stays under the new one, clear, so that its buttons are still there to be pressed;
            // when the chats are being chosen it has its own words, and those are shown
            View selecting = idSelect == 0 ? null : bar.findViewById(idSelect);
            boolean chosen = selecting != null && selecting.getVisibility() == View.VISIBLE && selecting.getWidth() > 0;
            View barRow = idBarRow == 0 ? null : bar.findViewById(idBarRow);
            if (barRow != null) {
                float want = chosen ? 1f : 0f;
                if (barRow.getAlpha() != want) barRow.setAlpha(want);
            }
            header.setVisibility(chosen ? View.GONE : View.VISIBLE);
            View title = idTitle == 0 ? null : bar.findViewById(idTitle);
            CharSequence name = title instanceof TextView ? ((TextView) title).getText() : "";
            header.show(name, unread());
        }

        /** A little height for the heading to grow into: the next layout sets the bar to the heading's own height. */
        void room() {
            ViewGroup.LayoutParams params = bar.getLayoutParams();
            if (params == null || params.height <= 0) return;
            params.height = params.height + Math.round(28f * bar.getResources().getDisplayMetrics().density);
            bar.setLayoutParams(params);
        }

        /** What the chip of the chosen folder says it holds, when it says a number. */
        int unread() {
            ViewGroup chips = chipsHolder();
            if (chips == null) return -1;
            int chosen = chosenChip(chips);
            if (chosen < 0) return -1;
            View chip = chips.getChildAt(chosen);
            TextView label = labelOf(chip);
            if (label == null) return -1;
            // the name, and the number beside it when it is a view of its own
            StringBuilder said = new StringBuilder(label.getText());
            if (chip instanceof ViewGroup) {
                ViewGroup parts = (ViewGroup) chip;
                for (int part = 0; part < parts.getChildCount(); part++) {
                    View one = parts.getChildAt(part);
                    if (one != label && one instanceof TextView && one.getVisibility() == View.VISIBLE) said.append(' ').append(((TextView) one).getText());
                }
            }
            String text = said.toString().trim();
            int end = -1;
            for (int i = text.length() - 1; i >= 0; i--) {
                if (Character.isDigit(text.charAt(i))) {
                    if (end < 0) end = i;
                } else if (end >= 0) {
                    return parse(text.substring(i + 1, end + 1));
                }
            }
            return end >= 0 ? parse(text.substring(0, end + 1)) : -1;
        }

        private static int parse(String digits) {
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException error) {
                return -1;
            }
        }

        // ------------------------------------------------------------------------ the stories

        void stories() {
            if (list.getChildCount() == 0) return;
            walkStories(list.getChildAt(0), 0);
        }

        private void walkStories(View view, int depth) {
            if (depth > 9 || view == null) return;
            int id = view.getId();
            // the ring round a story: the view over the avatar that has a class of its own
            if (view.getClass().getName().endsWith(".11Wv") && view.getVisibility() == View.VISIBLE) {
                view.setTag(HIDDEN, Boolean.TRUE);
                view.setVisibility(View.INVISIBLE);
                return;
            }
            if (id != View.NO_ID && (id == idRings1 || id == idRings2)) {
                // the ring round a story is not wanted
                if (view.getVisibility() == View.VISIBLE) {
                    view.setTag(HIDDEN, Boolean.TRUE);
                    view.setVisibility(View.INVISIBLE);
                }
                return;
            }
            if (id != View.NO_ID && id == idAvatar) {
                ProfileM3.avatarShape(view);
                if (view.getParent() instanceof ViewGroup) {
                    ViewGroup holder = (ViewGroup) view.getParent();
                    for (int i = 0; i < holder.getChildCount(); i++) {
                        View sibling = holder.getChildAt(i);
                        if (sibling != view && sibling.getWidth() == view.getWidth() && !(sibling instanceof TextView)
                                && sibling.getId() != idRings1 && sibling.getId() != idRings2) ProfileM3.avatarShape(sibling);
                    }
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) walkStories(group.getChildAt(i), depth + 1);
            }
        }

        // ------------------------------------------------------------------------ the chips

        /** The row of folders: the group inside the horizontal scroll in the second child of the list. */
        ViewGroup chipsHolder() {
            if (list.getChildCount() < 2 || !(list.getChildAt(1) instanceof ViewGroup)) return null;
            ViewGroup line = (ViewGroup) list.getChildAt(1);
            for (int i = 0; i < line.getChildCount(); i++) {
                View one = line.getChildAt(i);
                if (one instanceof android.widget.HorizontalScrollView && ((ViewGroup) one).getChildCount() > 0
                        && ((ViewGroup) one).getChildAt(0) instanceof ViewGroup) return (ViewGroup) ((ViewGroup) one).getChildAt(0);
            }
            return null;
        }

        static TextView labelOf(View chip) {
            if (!(chip instanceof ViewGroup)) return null;
            ViewGroup group = (ViewGroup) chip;
            for (int i = 0; i < group.getChildCount(); i++) {
                View one = group.getChildAt(i);
                if (one instanceof TextView && one.getVisibility() == View.VISIBLE && ((TextView) one).getText().length() > 0) return (TextView) one;
            }
            return null;
        }

        /**
         * The chosen chip is the one TikTok painted with a tint of the accent, where the rest have a faint grey.
         * What TikTok painted is read each time it paints it again, before it is covered.
         */
        static boolean chosen(View chip) {
            Drawable back = chip.getBackground();
            if (back != chip.getTag(OURS)) {
                Boolean read = readChosen(back);
                if (read != null) chip.setTag(CHOSEN, read);
            }
            return Boolean.TRUE.equals(chip.getTag(CHOSEN));
        }

        private static Boolean readChosen(Drawable back) {
            try {
                if (back instanceof android.graphics.drawable.ColorDrawable)
                    return android.graphics.Color.alpha(((android.graphics.drawable.ColorDrawable) back).getColor()) >= 0x20;
                if (back instanceof GradientDrawable && android.os.Build.VERSION.SDK_INT >= 24) {
                    android.content.res.ColorStateList fill = ((GradientDrawable) back).getColor();
                    if (fill != null) return android.graphics.Color.alpha(fill.getDefaultColor()) >= 0x20;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        static int chosenChip(ViewGroup chips) {
            for (int i = 0; i < chips.getChildCount(); i++) if (chosen(chips.getChildAt(i))) return i;
            return -1;
        }

        void chips() {
            if (list.getChildCount() < 2 || !(list.getChildAt(1) instanceof ViewGroup)) return;
            ViewGroup line = (ViewGroup) list.getChildAt(1);
            ViewGroup chips = chipsHolder();
            if (chips == null) return;
            // TikTok's own row stays where it is, clear, to be pressed through the new one, which lies over it
            // from edge to edge: it is one group of buttons, the chosen one round and the others square where they meet
            for (int i = 0; i < line.getChildCount(); i++) {
                View one = line.getChildAt(i);
                if (one instanceof ChipScroll) continue;
                if (one.getAlpha() != 0f) one.setAlpha(0f);
            }
            ChipScroll scroll = null;
            for (int i = 0; i < line.getChildCount(); i++) if (line.getChildAt(i) instanceof ChipScroll) scroll = (ChipScroll) line.getChildAt(i);
            if (scroll == null) {
                scroll = new ChipScroll(line.getContext());
                float unit = line.getResources().getDisplayMetrics().density;
                ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(36f * unit));
                params.topMargin = Math.round(4f * unit);
                line.addView(scroll, params);
            }
            scroll.row.sync(chips, roles);
        }

        /** The filter at the end of the chips: the thing to press for the new filter button. */
        View filter() {
            if (list.getChildCount() < 2 || !(list.getChildAt(1) instanceof ViewGroup)) return null;
            ViewGroup line = (ViewGroup) list.getChildAt(1);
            View found = null;
            for (int i = 0; i < line.getChildCount(); i++) {
                View one = line.getChildAt(i);
                if (one instanceof ViewGroup && !(one instanceof android.widget.HorizontalScrollView) && one.getWidth() > 0
                        && one.hasOnClickListeners()) found = one;
            }
            return found;
        }

        // ------------------------------------------------------------------------ the rows

        void rows() {
            for (int i = 0; i < list.getChildCount(); i++) {
                View child = list.getChildAt(i);
                if (isRow(child)) {
                    dressRow(child);
                } else if (child instanceof LinearLayout) {
                    // the head of the list holds a row of its own, and a line under it that is not wanted
                    ViewGroup group = (ViewGroup) child;
                    for (int j = 0; j < group.getChildCount(); j++) {
                        View inner = group.getChildAt(j);
                        if (isRow(inner)) dressRow(inner);
                        else if (inner instanceof ImageView && inner.getHeight() < 24 * inner.getResources().getDisplayMetrics().density
                                && inner.getAlpha() != 0f) inner.setAlpha(0f);
                    }
                }
            }
        }

        private boolean isRow(View view) {
            return view.getClass().getName().endsWith("MultiSelectConstraintLayout");
        }

        private void dressRow(View row) {
            // the two findViewById are only needed once per row, and again if the chosen shape changes;
            // the rows are dressed every frame otherwise, which is a search per row per frame for nothing
            String shape = AvatarShape.chosen();
            if (shape.equals(row.getTag(STYLED))) return;
            row.setTag(STYLED, shape);
            View big = idBig == 0 ? null : row.findViewById(idBig);
            View small = idSmall == 0 ? null : row.findViewById(idSmall);
            if (big != null) ProfileM3.avatarShape(big);
            if (small != null) ProfileM3.avatarShape(small);
        }

        void off() {
            if (header != null) {
                bar.removeView(header);
                bar.setTag(HEADER, null);
                bar.setClipChildren(true);
                header = null;
            }
            View barRow = idBarRow == 0 ? null : bar.findViewById(idBarRow);
            if (barRow != null && barRow.getAlpha() != 1f) barRow.setAlpha(1f);
            if (barHeight != Integer.MIN_VALUE) {
                ViewGroup.LayoutParams params = bar.getLayoutParams();
                if (params != null) {
                    params.height = barHeight;
                    bar.setLayoutParams(params);
                }
                barHeight = Integer.MIN_VALUE;
            }
        }
    }

    // ---------------------------------------------------------------------------------------- the chips

    /** The folders in a scroll that has nothing to scroll while they fit. */
    private static final class ChipScroll extends android.widget.HorizontalScrollView {
        final ChipRow row;

        ChipScroll(Context context) {
            super(context);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setFillViewport(true);
            row = new ChipRow(context);
            addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    /** The folders as one group of buttons across the width. */
    private static final class ChipRow extends LinearLayout {
        private String shown = "";

        ChipRow(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            int side = Math.round(16f * context.getResources().getDisplayMetrics().density);
            setPadding(side, 0, side, 0);
        }

        void sync(ViewGroup chips, ProfileM3.Roles r) {
            float unit = getResources().getDisplayMetrics().density;
            int many = chips.getChildCount();
            int lit = SettingsHome.card(r.primary);
            // what each says, and which is chosen
            StringBuilder sig = new StringBuilder();
            String[] words = new String[many];
            boolean[] more = new boolean[many];
            int chosen = Page.chosenChip(chips);
            for (int i = 0; i < many; i++) {
                View chip = chips.getChildAt(i);
                TextView label = Page.labelOf(chip);
                StringBuilder text = new StringBuilder(label == null ? "" : label.getText());
                if (chip instanceof ViewGroup) {
                    ViewGroup parts = (ViewGroup) chip;
                    for (int part = 0; part < parts.getChildCount(); part++) {
                        View one = parts.getChildAt(part);
                        if (one != label && one instanceof TextView && one.getVisibility() == View.VISIBLE
                                && ((TextView) one).getText().length() > 0) text.append(' ').append(((TextView) one).getText());
                        if (one.getClass().getName().endsWith("TuxIconView")) more[i] = one.getVisibility() == View.VISIBLE;
                    }
                }
                words[i] = text.toString();
                sig.append(words[i]).append('|').append(more[i]).append('|');
            }
            sig.append(chosen).append(r.primary).append(r.ground).append(many);
            if (sig.toString().equals(shown) && getChildCount() == many) return;
            shown = sig.toString();
            removeAllViews();
            // the words as large as they go with every chip in the width, from 15sp down to 13sp; and if they do not
            // go in at 13sp, each chip is as wide as it needs to be, and the row is scrolled
            TextView sample = new TextView(getContext());
            Fonts.apply(sample);
            sample.setTypeface(Typeface.create(sample.getTypeface(), Typeface.BOLD));
            android.text.TextPaint paint = sample.getPaint();
            float room = getResources().getDisplayMetrics().widthPixels - 32f * unit - 3f * unit * (many - 1);
            float size = 13f;
            boolean fits = false;
            for (float tryOne = 15f; tryOne >= 13f; tryOne -= 0.5f) {
                paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, tryOne, getResources().getDisplayMetrics()));
                float need = 0f;
                for (int i = 0; i < many; i++) {
                    need += paint.measureText(words[i]) + 24f * unit + (i == chosen ? 22f * unit : 0f) + (more[i] ? 22f * unit : 0f);
                }
                if (need * 1.04f <= room) {
                    size = tryOne;
                    fits = true;
                    break;
                }
            }
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, getResources().getDisplayMetrics()));
            ViewGroup.LayoutParams own = getLayoutParams();
            if (own != null) {
                int want = fits ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
                if (own.width != want) {
                    own.width = want;
                    setLayoutParams(own);
                }
            }
            for (int i = 0; i < many; i++) {
                final View original = chips.getChildAt(i);
                boolean on = i == chosen;
                TextView button = new TextView(getContext());
                button.setText(words[i]);
                button.setGravity(Gravity.CENTER);
                button.setSingleLine(true);
                button.setEllipsize(android.text.TextUtils.TruncateAt.END);
                button.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
                Fonts.apply(button);
                button.setTypeface(Typeface.create("sans-serif-medium", on ? Typeface.BOLD : Typeface.NORMAL));
                int ink = on ? SettingsHome.readableOn(lit) : r.ink;
                button.setTextColor(ink);
                float full = 18f * unit, small = 5f * unit;
                float left = on || i == 0 ? full : small, right = on || i == many - 1 ? full : small;
                GradientDrawable back = new GradientDrawable();
                back.setColor(on ? lit : r.high);
                back.setCornerRadii(new float[] {left, left, right, right, right, right, left, left});
                button.setBackground(back);
                Drawable first = on ? SettingsGlyph.make("check", ink) : null;
                Drawable last = more[i] ? SettingsGlyph.make("chevron_right", ink) : null;
                if (first != null) first.setBounds(0, 0, Math.round(18f * unit), Math.round(18f * unit));
                if (last != null) last.setBounds(0, 0, Math.round(18f * unit), Math.round(18f * unit));
                button.setCompoundDrawables(first, null, last, null);
                button.setCompoundDrawablePadding(Math.round(4f * unit));
                button.setPadding(Math.round(12f * unit), 0, Math.round(12f * unit), 0);
                button.setClickable(true);
                button.setOnClickListener(v -> {
                    TextView real = Page.labelOf(original);
                    if (!original.performClick() && real != null) real.performClick();
                });
                float wide = paint.measureText(words[i]) * 1.1f + 28f * unit + (on ? 24f * unit : 0f) + (more[i] ? 24f * unit : 0f);
                LinearLayout.LayoutParams at = fits
                        ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1f, wide))
                        : new LinearLayout.LayoutParams(Math.round(wide), ViewGroup.LayoutParams.MATCH_PARENT);
                if (i > 0) at.leftMargin = Math.round(3f * unit);
                addView(button, at);
            }
            // the chosen one is in sight
            if (!fits && chosen >= 0 && getParent() instanceof android.widget.HorizontalScrollView) {
                final View chosenButton = getChildAt(chosen);
                final android.widget.HorizontalScrollView scroll = (android.widget.HorizontalScrollView) getParent();
                post(() -> scroll.scrollTo(Math.max(0, chosenButton.getLeft() - Math.round(16f * unit)), 0));
            }
        }
    }

    // ---------------------------------------------------------------------------------------- the heading

    /** The heading: the name large and how many are new under it, two round buttons, and the search with its filter. */
    private static final class Header extends LinearLayout {
        private final Page page;
        private final TextView title, subtitle;
        private final FrameLayout add, more, search, filter;
        private final TextView hint;
        private final LinearLayout capsule;
        private final ImageView addGlyph, moreGlyph, filterGlyph;
        private final ImageView magnifier;
        private ProfileM3.Roles roles;
        private String shown = "";

        Header(Context context, final Page page) {
            super(context);
            this.page = page;
            float unit = context.getResources().getDisplayMetrics().density;
            setOrientation(VERTICAL);
            setPadding(Math.round(16f * unit), Math.round(8f * unit), Math.round(16f * unit), Math.round(8f * unit));
            setClickable(true);

            LinearLayout top = new LinearLayout(context);
            top.setOrientation(HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout words = new LinearLayout(context);
            words.setOrientation(VERTICAL);
            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setIncludeFontPadding(false);
            title.setLetterSpacing(-1.5f / 40f);
            Fonts.apply(title);
            title.setTypeface(Typeface.create(title.getTypeface(), 800, false));
            words.addView(title);
            subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            subtitle.setSingleLine(true);
            Fonts.apply(subtitle);
            LinearLayout.LayoutParams subtitleAt = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            subtitleAt.topMargin = Math.round(4f * unit);
            words.addView(subtitle, subtitleAt);
            top.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            addGlyph = new ImageView(context);
            add = button(context, addGlyph, unit);
            add.setContentDescription(Local.of("Найти друзей"));
            add.setOnClickListener(v -> press(page.idAdd));
            moreGlyph = new ImageView(context);
            more = button(context, moreGlyph, unit);
            more.setContentDescription(Local.of("Ещё"));
            more.setOnClickListener(v -> press(page.idMore));
            LinearLayout.LayoutParams buttonAt = new LinearLayout.LayoutParams(Math.round(48f * unit), Math.round(48f * unit));
            buttonAt.leftMargin = Math.round(8f * unit);
            top.addView(add, buttonAt);
            top.addView(more, buttonAt);
            addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            capsule = new LinearLayout(context);
            capsule.setOrientation(HORIZONTAL);
            capsule.setGravity(Gravity.CENTER_VERTICAL);
            capsule.setPadding(Math.round(20f * unit), 0, Math.round(8f * unit), 0);
            capsule.setClickable(true);
            magnifier = new ImageView(context);
            LinearLayout.LayoutParams magnifierAt = new LinearLayout.LayoutParams(Math.round(24f * unit), Math.round(24f * unit));
            magnifierAt.rightMargin = Math.round(12f * unit);
            capsule.addView(magnifier, magnifierAt);
            hint = new TextView(context);
            hint.setText(Local.of("Поиск по чатам"));
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            hint.setSingleLine(true);
            Fonts.apply(hint);
            capsule.addView(hint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            filterGlyph = new ImageView(context);
            filter = new FrameLayout(context);
            filter.addView(filterGlyph, new FrameLayout.LayoutParams(Math.round(22f * unit), Math.round(22f * unit), Gravity.CENTER));
            filter.setClickable(true);
            filter.setContentDescription(Local.of("Фильтр"));
            filter.setOnClickListener(v -> {
                View real = page.filter();
                if (real != null) real.performClick();
            });
            capsule.addView(filter, new LinearLayout.LayoutParams(Math.round(40f * unit), Math.round(40f * unit)));
            search = new FrameLayout(context);
            search.addView(capsule, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(56f * unit)));
            capsule.setOnClickListener(v -> press(page.idSearch));
            LinearLayout.LayoutParams searchAt = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            searchAt.topMargin = Math.round(12f * unit);
            addView(search, searchAt);
        }

        private FrameLayout button(Context context, ImageView glyph, float unit) {
            FrameLayout box = new FrameLayout(context);
            box.addView(glyph, new FrameLayout.LayoutParams(Math.round(24f * unit), Math.round(24f * unit), Gravity.CENTER));
            box.setClickable(true);
            return box;
        }

        /** Press what TikTok's own bar has under this one. */
        private void press(int id) {
            View real = id == 0 ? null : page.bar.findViewById(id);
            if (real != null) real.performClick();
        }

        void show(CharSequence name, int unread) {
            String text = name == null ? "" : name.toString();
            String note = unread > 0 ? unread + " " + Local.of("новых") : "";
            String sig = text + "|" + note;
            if (sig.equals(shown)) return;
            shown = sig;
            title.setText(text);
            room(text);
            subtitle.setText(note);
            boolean had = subtitle.getVisibility() == View.VISIBLE;
            subtitle.setVisibility(note.length() == 0 ? View.GONE : View.VISIBLE);
            // the count comes back: the bar has just shrunk to the heading without it, and the heading
            // measured inside that height could never grow into the line again -- room is made for it
            if (!had && note.length() > 0 && getHeight() > 0) page.room();
            if (roles != null) dress(roles);
        }

        /**
         * How much lower than the line's own descent the letters reach. The font padding is off for
         * the tight look, so the line is only as tall as the font's ascent and descent say — and a
         * font whose descent is short of the ink (the tails of «д» and «щ» reach farther) has those
         * tails cut off at the view's edge. The room the ink asks for is given to the bottom.
         */
        private void room(String text) {
            int bottom = 0;
            if (text.length() > 0) {
                android.graphics.Rect ink = new android.graphics.Rect();
                android.graphics.Paint paint = title.getPaint();
                paint.getTextBounds(text, 0, text.length(), ink);
                int over = (int) Math.ceil(ink.bottom - paint.getFontMetrics().descent);
                if (over > 0) bottom = over + 1;
            }
            if (title.getPaddingBottom() != bottom) title.setPadding(0, 0, 0, bottom);
        }

        void dress(ProfileM3.Roles r) {
            if (r == null) return;
            roles = r;
            float unit = getResources().getDisplayMetrics().density;
            setBackgroundColor(r.ground);
            title.setTextColor(r.ink);
            int lit = NavBar.readable(r.primary, r.ground, r.ink, 3f);
            subtitle.setTextColor(r.variant);
            String note = subtitle.getText().toString();
            if (note.length() > 0) {
                android.text.SpannableString coloured = new android.text.SpannableString(note);
                int space = note.indexOf(' ');
                if (space > 0) coloured.setSpan(new android.text.style.ForegroundColorSpan(lit), 0, space, 0);
                subtitle.setText(coloured);
            }
            for (FrameLayout round : new FrameLayout[] {add, more}) {
                GradientDrawable disc = new GradientDrawable();
                disc.setShape(GradientDrawable.OVAL);
                disc.setColor(r.container);
                round.setBackground(disc);
            }
            addGlyph.setImageDrawable(new PersonAdd(r.ink));
            moreGlyph.setImageDrawable(new Dots(r.ink));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(r.high);
            pill.setCornerRadius(28f * unit);
            capsule.setBackground(pill);
            magnifier.setImageDrawable(new Expressive.Magnifier(r.variant));
            hint.setTextColor(r.variant);
            GradientDrawable square = new GradientDrawable();
            square.setColor(r.secondary);
            square.setCornerRadius(14f * unit);
            filter.setBackground(square);
            Drawable tune = SettingsGlyph.make("tune", r.onSecondary);
            if (tune != null) filterGlyph.setImageDrawable(tune);
        }
    }

    /** A person with a plus: add friends. */
    private static final class PersonAdd extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        PersonAdd(int colour) {
            paint.setColor(colour);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override public void draw(Canvas canvas) {
            float unit = Math.min(getBounds().width(), getBounds().height()) / 24f;
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(unit, unit);
            paint.setStrokeWidth(2f);
            canvas.drawCircle(9.5f, 8f, 3.6f, paint);
            Path body = new Path();
            body.moveTo(3f, 20f);
            body.cubicTo(3f, 15.5f, 6f, 13.5f, 9.5f, 13.5f);
            body.cubicTo(11f, 13.5f, 12.4f, 13.8f, 13.5f, 14.4f);
            canvas.drawPath(body, paint);
            canvas.drawLine(18.5f, 7f, 18.5f, 13f, paint);
            canvas.drawLine(15.5f, 10f, 21.5f, 10f, paint);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
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

    private static String idName(View view) {
        try {
            if (view.getId() != View.NO_ID) return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
        }
        return "";
    }
}
