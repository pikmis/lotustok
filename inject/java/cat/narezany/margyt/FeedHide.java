package cat.narezany.margyt;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Panels of the feed that a person can take off the screen: the LIVE and the search
 * buttons, each of the three tabs along the top, and the description under a video.
 * (The four buttons beside a video are {@link FeedRail}'s: they are TikTok's own
 * components, and are switched where TikTok switches them.)
 *
 * A panel is made `GONE`, not just invisible, so what is left closes up: the column
 * beside a video grows from the bottom, and so what was above a removed button comes
 * down to take its place. What a view was before it was hidden is kept on it, and is
 * given back when the panel is switched on again.
 *
 * TikTok shows some of these again when it binds the next video, so what has been
 * found is looked at before each frame. That is a handful of comparisons.
 */
public final class FeedHide {
    public static final int LIVE = 0, SEARCH = 1, FOR_YOU = 2, FOLLOWING = 3, SERIES = 4, DESCRIPTION = 5, NAME = 6;
    public static final String[] KEYS = {"rail_show_live", "rail_show_search", "rail_show_foryou",
            "rail_show_following", "rail_show_series", "rail_show_desc", "rail_show_name"};

    private static final int PRIOR = 0x7e0c03A0;
    private static volatile boolean[] shown;

    private FeedHide() {}

    public static boolean shown(int what) {
        boolean[] current = shown;
        if (current == null) {
            android.content.Context c = Margy.context();
            if (c == null) return true;
            current = new boolean[KEYS.length];
            try {
                android.content.SharedPreferences p = AccountAppearance.prefs(c);
                for (int i = 0; i < current.length; i++) current[i] = p.getBoolean(KEYS[i], true);
            } catch (Throwable error) {
                java.util.Arrays.fill(current, true);
                Diary.note("feed panels settings: " + error);
            }
            shown = current;
        }
        return what < 0 || what >= current.length || current[what];
    }

    public static void setShown(int what, boolean value) {
        if (what < 0 || what >= KEYS.length) return;
        android.content.Context c = Margy.context();
        if (c == null) return;
        AccountAppearance.prefs(c).edit().putBoolean(KEYS[what], value).apply();
        reloadSettings();
    }

    /** The settings changed: read them again, and make what is on the screen follow. */
    static void reloadSettings() {
        shown = null;
        View row = topRow == null ? null : topRow.get();
        if (row != null) row.post(FeedHide::frame);
    }

    // ------------------------------------------------------------------ the top row

    private static final String[][] TAB_WORDS = {
            {"рекоменд", "for you", "для вас", "para ti", "für dich", "pour toi", "para você", "senin için", "dla ciebie", "per te"},
            {"подпис", "following", "підпис", "siguiendo", "abonniert", "abonnements", "seguindo", "takip", "obserwowani", "seguiti"},
            {"сериал", "серіал", "series", "serie", "séries", "dizi", "seriale"}};

    private static int liveId = -1, searchId, rowId, descId, boxId, hostId, titleId;
    private static WeakReference<ViewGroup> topRow;
    private static WeakReference<View> live, search, window;
    private static View[] tabs = new View[0];
    private static int[] tabKinds = new int[0];

    private static void names(View any) {
        if (liveId != -1) return;
        android.content.res.Resources r = any.getResources();
        String pack = any.getContext().getPackageName();
        liveId = r.getIdentifier("k_2", "id", pack);
        searchId = r.getIdentifier("k_5", "id", pack);
        rowId = r.getIdentifier("uvh", "id", pack);
        descId = r.getIdentifier("desc", "id", pack);
        boxId = r.getIdentifier("bqw", "id", pack);
        hostId = r.getIdentifier("bqx", "id", pack);
        titleId = r.getIdentifier("title", "id", pack);
    }

    /** Which tab a view is, by the words on it; -1 for one that is not known. */
    private static int tabKind(View tab) {
        String said = words(tab, 0);
        if (said.length() == 0 && tab.getContentDescription() != null) said = tab.getContentDescription().toString();
        said = said.toLowerCase(java.util.Locale.ROOT);
        for (int kind = 0; kind < TAB_WORDS.length; kind++) {
            for (String word : TAB_WORDS[kind]) if (said.contains(word)) return FOR_YOU + kind;
        }
        return -1;
    }

    private static String words(View view, int depth) {
        if (view == null || depth > 6) return "";
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            return text == null ? "" : text.toString();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String found = words(group.getChildAt(i), depth + 1);
                if (found.length() > 0) return found;
            }
        }
        return "";
    }

    /** The row of buttons and tabs over the feed, as it was found: what is in it is remembered. */
    static void top(ViewGroup row) {
        try {
            names(row);
            topRow = new WeakReference<ViewGroup>(row);
            View a = liveId == 0 ? null : row.findViewById(liveId);
            View b = searchId == 0 ? null : row.findViewById(searchId);
            live = new WeakReference<View>(a);
            search = new WeakReference<View>(b);
            ViewGroup shelf = null;
            for (int i = 0; i < row.getChildCount() && shelf == null; i++) {
                View child = row.getChildAt(i);
                if (child instanceof ViewGroup) shelf = (ViewGroup) child;
            }
            window = new WeakReference<View>(shelf);
            View inner = shelf == null || shelf.getChildCount() == 0 ? null : shelf.getChildAt(0);
            View strip = inner instanceof ViewGroup && ((ViewGroup) inner).getChildCount() > 0
                    ? ((ViewGroup) inner).getChildAt(0) : null;
            if (strip instanceof ViewGroup) {
                ViewGroup list = (ViewGroup) strip;
                tabs = new View[list.getChildCount()];
                tabKinds = new int[tabs.length];
                for (int i = 0; i < tabs.length; i++) {
                    tabs[i] = list.getChildAt(i);
                    tabKinds[i] = tabKind(tabs[i]);
                }
            } else {
                tabs = new View[0];
                tabKinds = new int[0];
            }
            frame();
        } catch (Throwable error) {
            Diary.detail("feed panels: top: " + error);
        }
    }

    // --------------------------------------------------------- the page of a video

    /** One video's caption: the row with the name and the date, the description, and what hangs under it. */
    private static final class Page {
        final WeakReference<View> name, block;
        final WeakReference<View>[] after;

        Page(View name, View block, List<View> after) {
            this.name = new WeakReference<View>(name);
            this.block = new WeakReference<View>(block);
            // weak: the page is held by a table keyed on the page itself
            this.after = new WeakReference[after.size()];
            for (int i = 0; i < this.after.length; i++) this.after[i] = new WeakReference<View>(after.get(i));
        }
    }

    private static final Map<View, Page> pages = new WeakHashMap<View, Page>();

    /**
     * The page a button is on, found from the button, upwards (the pager is not walked).
     * The description is hidden together with what is under it, the translate button,
     * which would be a button for nothing.
     */
    static void page(View slot) {
        try {
            names(slot);
            if (descId == 0) return;
            View host = null;
            Object up = slot.getParent();
            for (int i = 0; i < 12 && up instanceof View; i++) {
                View candidate = (View) up;
                View words = candidate.findViewById(descId);
                if (words != null) {
                    host = candidate;
                    break;
                }
                up = candidate.getParent();
            }
            if (host == null) return;
            synchronized (pages) {
                if (pages.containsKey(host)) {
                    enforce(pages.get(host));
                    return;
                }
            }
            View words = host.findViewById(descId);
            View caption = hostId == 0 ? null : host.findViewById(hostId);
            View title = caption == null || titleId == 0 ? null : caption.findViewById(titleId);
            View block = blockOf(words);
            List<View> after = new ArrayList<View>();
            if (block != null && block.getParent() instanceof ViewGroup) {
                ViewGroup column = (ViewGroup) block.getParent();
                for (int i = column.indexOfChild(block) + 1; i < column.getChildCount(); i++) after.add(column.getChildAt(i));
            } else {
                block = words;
            }
            Page page = new Page(title == null ? null : blockOf(title), block, after);
            synchronized (pages) {
                pages.put(host, page);
            }
            enforce(page);
        } catch (Throwable error) {
            Diary.detail("feed panels: page: " + error);
        }
    }

    /**
     * The row of the caption a view is in: the one that is a child of the caption's column. The
     * words and the name are deep in wrappers, and hiding only them would leave the wrappers' room.
     * Null when it is not found.
     */
    private static View blockOf(View inner) {
        View block = inner;
        for (int i = 0; i < 8; i++) {
            Object above = block.getParent();
            if (!(above instanceof View)) return null;
            Object beyond = ((View) above).getParent();
            if (beyond instanceof View && ((View) beyond).getId() == boxId) return block;
            block = (View) above;
        }
        return null;
    }

    private static void enforce(Page page) {
        boolean hide = !shown(DESCRIPTION);
        set(page.block.get(), hide);
        for (WeakReference<View> v : page.after) set(v.get(), hide);
        // the date is in the same row as the name, and goes with it
        set(page.name.get(), !shown(NAME));
    }

    // ------------------------------------------------------------------ keeping it so

    /** Before each frame: what is to be hidden is hidden, and what is to be shown is given back. */
    static void frame() {
        try {
            View v = live == null ? null : live.get();
            set(v, !shown(LIVE));
            v = search == null ? null : search.get();
            set(v, !shown(SEARCH));
            View[] list = tabs;
            int[] kinds = tabKinds;
            boolean every = list.length > 0;
            for (int i = 0; i < list.length && i < kinds.length; i++) {
                // the tabs that are not known (a community, a city) go with the recommendations they are beside
                boolean hide = !shown(kinds[i] >= 0 ? kinds[i] : FOR_YOU);
                set(list[i], hide);
                if (!hide) every = false;
            }
            // with every tab gone the capsule behind them has nothing in it
            v = window == null ? null : window.get();
            set(v, every);
            synchronized (pages) {
                for (Page page : pages.values()) if (page != null) enforce(page);
            }
        } catch (Throwable ignored) {
            // a view taken apart in the middle of a frame is looked at again on the next
        }
    }

    private static void set(View v, boolean hide) {
        if (v == null) return;
        if (hide) {
            if (v.getVisibility() != View.GONE) {
                v.setTag(PRIOR, Integer.valueOf(v.getVisibility()));
                v.setVisibility(View.GONE);
            }
        } else {
            Object before = v.getTag(PRIOR);
            if (before instanceof Integer) {
                v.setTag(PRIOR, null);
                if (v.getVisibility() == View.GONE) v.setVisibility((Integer) before);
            }
        }
    }
}
