package cat.narezany.margyt;

import android.content.Context;

public final class Hashtags {

    static final String KEY = "hashtags_unlimited";

    private static final int MANY = 100;

    private Hashtags() {}

    static boolean on() {
        Context context = Margy.context();
        return context != null && context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY, false);
    }

    static void set(boolean value) {
        Context context = Margy.context();
        if (context != null) context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, value).apply();
    }

    public static int limit(int server) {
        try {
            return on() ? Math.max(server, MANY) : server;
        } catch (Throwable error) {
            return server;
        }
    }
}
