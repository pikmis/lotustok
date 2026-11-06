package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * Telling the server that this account is here.
 *
 * The mod keeps two things on the phone -- the id of the account that is signed in and the
 * moment TikTok was last in front -- and sends them to the server: the id, and the version of
 * the mod. The server answers with nothing that matters; it counts who uses LotusTok and when
 * they were last seen, which is what the panel shows.
 *
 * Nothing else leaves the phone with it: no name, no picture, no list of anything. It is heard
 * at most once in {@link #EVERY}, from the moment TikTok comes to the front, and it is always on.
 */
public final class Presence {

    private Presence() {}

    private static final String KEY_UID = "presence_uid";
    private static final String KEY_LAST = "presence_last";
    private static final String KEY_SENT = "presence_sent";

    /** How often the server is told, however often TikTok comes to the front. */
    private static final long EVERY = 30 * 60 * 1000L;

    /** After a failure, how long to leave it before trying again. */
    private static final long RETRY = 5 * 60 * 1000L;

    private static volatile long tried;
    private static volatile boolean sending;

    /** Always on: there is no switch for it, so it cannot be turned off. */
    public static boolean enabled() {
        return true;
    }

    /** The account that was last seen on this phone, or null. */
    public static String uid() {
        SharedPreferences prefs = prefs();
        String known = prefs == null ? "" : prefs.getString(KEY_UID, "");
        return known.length() == 0 ? null : known;
    }

    /** When TikTok was last in front, as the phone's clock says; 0 if never. */
    public static long lastSeen() {
        SharedPreferences prefs = prefs();
        return prefs == null ? 0L : prefs.getLong(KEY_LAST, 0L);
    }

    /** A screen came to the front. */
    public static void resumed() {
        try {
            if (!enabled()) return;
            String uid = Account.id();
            if (uid == null || uid.length() == 0) return;
            long now = System.currentTimeMillis();
            SharedPreferences prefs = prefs();
            if (prefs == null) return;
            prefs.edit().putString(KEY_UID, uid).putLong(KEY_LAST, now).apply();
            tell(uid, now, prefs);
        } catch (Throwable error) {
            Diary.note("presence: " + error);
        }
    }

    /** A screen went away: that is the last the account was seen, for now. */
    public static void paused() {
        try {
            if (!enabled()) return;
            SharedPreferences prefs = prefs();
            if (prefs == null || prefs.getString(KEY_UID, "").length() == 0) return;
            prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply();
        } catch (Throwable ignored) {
        }
    }

    private static void tell(final String uid, long now, final SharedPreferences prefs) {
        if (sending) return;
        long sent = prefs.getLong(KEY_SENT, 0L);
        if (now - sent < EVERY || now - tried < RETRY) return;
        tried = now;
        sending = true;
        Net.away("presence", new Runnable() {
            @Override
            public void run() {
                try {
                    String body = new JSONObject().put("uid", uid)
                            .put("version", Version.MOD).toString();
                    Net.Said said = Net.talk(Badges.SERVER + "/seen", body);
                    if (said.ok()) {
                        prefs.edit().putLong(KEY_SENT, System.currentTimeMillis()).apply();
                    }
                } catch (Throwable error) {
                    Diary.note("presence: " + error);
                } finally {
                    sending = false;
                }
            }
        });
    }

    private static SharedPreferences prefs() {
        try {
            Context context = Margy.context();
            if (context == null) return null;
            return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
