package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The heading of the chat list ("Входящие" / "Inbox" / "Вхідні") under a name
 * of the person's own choosing. Empty means TikTok's own word, as it always was.
 *
 * It is changed where the text of a TextView is set, which the mod already
 * watches for badges, so no new hook in TikTok is needed.
 */
final class InboxTitle {

    static final String KEY = "inbox_title";

    /** The words TikTok shows there, in the languages the app has. */
    private static final String[] ORIGINAL = {"Входящие", "Inbox", "Вхідні"};

    private InboxTitle() {}

    static String get(Context context) {
        if (context == null) return "";
        try {
            SharedPreferences prefs = context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
            return prefs.getString(KEY, "");
        } catch (Throwable ignored) {
            return "";
        }
    }

    static void set(Context context, String title) {
        if (context == null) return;
        String clean = title == null ? "" : title.trim();
        if (clean.length() > 40) clean = clean.substring(0, 40);
        context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, clean).apply();
    }

    /** The text to show: the person's own title for the chat list heading, otherwise the text as it was. */
    static CharSequence replace(CharSequence text) {
        if (text == null) return null;
        String own = cached();
        if (own.isEmpty()) return text;
        String now = text.toString().trim();
        for (String word : ORIGINAL) {
            if (word.equalsIgnoreCase(now)) return own;
        }
        return text;
    }

    private static volatile String cachedTitle = "";
    private static volatile boolean loaded;

    private static String cached() {
        if (!loaded) {
            android.content.Context context = Margy.context();
            if (context == null) return "";
            cachedTitle = get(context);
            loaded = true;
        }
        return cachedTitle;
    }

    /** Called when the person changes it, so the new heading shows at once. */
    static void changed(String title) {
        cachedTitle = title == null ? "" : title.trim();
        loaded = true;
    }
}
