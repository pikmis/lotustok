package cat.narezany.margyt;

import android.app.Activity;
import android.app.Dialog;
import android.os.SystemClock;
import android.widget.Toast;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Takes back every like the person has given, one after another -- the same way
 * {@link RepostPurge} takes back reposts.
 *
 * Nothing here talks to TikTok's servers by itself. The list of liked videos is the
 * call the profile's likes tab makes, and taking a like back is the call the like
 * button makes (a "digg" of type 0); both are called the way the app calls them, so
 * they are signed and sent by the app's own network stack. What the mod adds is the
 * loop, and the manners of a loop: a pause between two calls, a stop when the server
 * starts refusing, and a button that ends it.
 *
 * TikTok does not say when a like was given, so unlike the reposts there is no
 * period to choose: it is all of them.
 */
final class LikePurge {

    private LikePurge() {}

    private static final int PAGE = 20, PAGES = 800, NOTE = 4712;
    /** The wait between two likes taken back, and how long one is waited for. */
    private static final long PAUSE = 1200L, PATIENCE = 20000L;
    private static final int REFUSALS = 3;

    private static volatile boolean counting, running, cancelled;
    private static volatile int doneNow, totalNow;
    private static volatile String phase = "";

    private static final Runnable STOP = new Runnable() {
        @Override public void run() {
            cancelled = true;
        }
    };

    private static final String NOTE_WARNING = "Иногда удаляются не все лайки: TikTok может отказать или пропустить часть. "
            + "Если что-то осталось, запустите удаление ещё раз.";

    private static String title() {
        return Local.of("Удаление лайков");
    }

    private static void status(String text, int at, int of, boolean going) {
        RepostPurge.status(title(), NOTE, text, at, of, going);
    }

    // ------------------------------------------------------------ the question

    /** The settings row: count them, ask, and only on a yes take them back. */
    static void begin(final Activity activity) {
        if (counting || running) {
            Popup.ask(activity, Local.of("Лайки"),
                    Local.of(phase) + (totalNow > 0 ? "\n" + doneNow + " / " + totalNow : ""),
                    Local.of("Остановить"), new Runnable() {
                        @Override public void run() {
                            cancelled = true;
                        }
                    },
                    Local.of("Закрыть"), null, null, null);
            return;
        }
        Popup.ask(activity, Local.of("Удалить все мои лайки?"), Local.of(NOTE_WARNING),
                Local.of("Посчитать"), new Runnable() {
                    @Override public void run() {
                        count(activity);
                    }
                },
                Local.of("Нет"), null, null, null);
    }

    private static void count(final Activity activity) {
        final String uid = Account.id();
        if (uid == null || uid.length() == 0) {
            Toast.makeText(activity, Local.of("Не удалось определить аккаунт"), Toast.LENGTH_LONG).show();
            return;
        }
        counting = true;
        cancelled = false;
        phase = "Считаем ваши лайки...";
        doneNow = 0;
        totalNow = 0;
        final RepostPurge.Waiting waiting = RepostPurge.screen(activity, Local.of("Считаем ваши лайки..."), STOP);
        waiting.dialog.show();
        status(Local.of("Считаем ваши лайки..."), 0, 0, true);
        new Thread(new Runnable() {
            @Override public void run() {
                List<String> found = null;
                String failure = null;
                try {
                    found = list(uid, Account.secId(), activity, waiting);
                } catch (Throwable error) {
                    failure = String.valueOf(error);
                    Diary.note("like purge: list failed, " + error);
                }
                final List<String> items = found == null ? new ArrayList<String>() : found;
                final String failed = failure;
                counting = false;
                final Activity here = Watch.here() != null ? Watch.here() : activity;
                here.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        try {
                            if (waiting.dialog.isShowing()) waiting.dialog.dismiss();
                        } catch (Throwable ignored) {
                        }
                        if (cancelled) {
                            status(Local.of("Остановлено"), 0, 0, false);
                            return;
                        }
                        if (failed != null) {
                            status(Local.of("Не удалось получить список лайков"), 0, 0, false);
                            Popup.show(here, Local.of("Лайки"), Local.of("Не удалось получить список лайков. Откройте TikTok, зайдите в свой профиль и попробуйте ещё раз."));
                        } else if (items.isEmpty()) {
                            status(Local.of("Лайков нет"), 0, 0, false);
                            Popup.show(here, Local.of("Лайки"), Local.of("У вас нет лайков"));
                        } else {
                            confirm(here, items);
                        }
                    }
                });
            }
        }, "margyt-like-count").start();
    }

    private static void confirm(final Activity activity, final List<String> items) {
        Popup.ask(activity, Local.of("Вы точно согласны на удаление всех лайков?"),
                Local.of("Лайков к удалению: ") + items.size()
                        + "\n\n" + Local.of(NOTE_WARNING)
                        + "\n" + Local.of("Удаление идёт в фоне: экран можно свернуть, прогресс виден в уведомлении."),
                Local.of("Да"), new Runnable() {
                    @Override public void run() {
                        remove(activity, items);
                    }
                },
                Local.of("Нет"), null,
                null, null);
    }

    // ------------------------------------------------------------ TikTok's calls

    /**
     * Every liked video of the account, a page after a page, by the call of the likes tab.
     *
     * The next page starts where the last one's cursor says; a video seen twice is kept once, and
     * the count ends on a page with nothing in it, or when pages stop bringing anything new.
     */
    private static List<String> list(String uid, String sec, Activity activity, RepostPurge.Waiting waiting) throws Throwable {
        Method fetch = null;
        for (Method method : Class.forName("X.0st9").getMethods()) {
            if (method.getName().equals("LJIIIIZZ") && method.getParameterTypes().length == 10) fetch = method;
        }
        if (fetch == null) throw new NoSuchMethodException("the likes call is not here");
        fetch.setAccessible(true);
        Set<String> out = new LinkedHashSet<String>();
        long cursor = 0;
        int pages = 0, barren = 0;
        while (pages < PAGES && !cancelled) {
            Object answer;
            try {
                // (cached, user id, sec id, type, cursor, count, invalid, hiding, mine, source)
                answer = RepostPurge.call(fetch, null, false, uid, sec == null ? "" : sec, 0, cursor, PAGE, 0, 0, true, "");
            } catch (Throwable error) {
                if (pages == 0) throw error;
                Diary.note("like purge: page " + pages + " failed, " + error);
                break;
            }
            pages++;
            Object items = answer == null ? null : NativeRead.get(answer, "getItems");
            if (!(items instanceof List) || ((List<?>) items).isEmpty()) break;
            int before = out.size();
            for (Object post : (List<?>) items) {
                String aid = NativeRead.aid(post);
                if (aid.length() > 0) out.add(aid);
            }
            Object next = NativeRead.field(answer, "maxCursor");
            if (!(next instanceof Number)) next = NativeRead.get(answer, "getMaxCursor");
            long moved = next instanceof Number ? ((Number) next).longValue() : 0;
            cursor = moved > 0 ? moved : cursor + ((List<?>) items).size();
            barren = out.size() == before ? barren + 1 : 0;
            if (barren >= 3) break;
            shown(activity, waiting, out.size());
        }
        Diary.note("like purge: " + out.size() + " likes found in " + pages + " pages");
        return new ArrayList<String>(out);
    }

    private static void shown(Activity activity, final RepostPurge.Waiting waiting, final int counted) {
        doneNow = counted;
        status(Local.of("Считаем ваши лайки...") + " " + counted, 0, 0, true);
        try {
            activity.runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (!cancelled) waiting.words.setText(Local.of("Считаем ваши лайки...") + "\n" + counted);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** One like taken back: TikTok's own call, waited for until it has answered. */
    private static boolean take(String aid, Object repository, Method send, Class<?> good, Class<?> bad) throws Throwable {
        HashMap<String, String> request = new HashMap<String, String>();
        request.put("aweme_id", aid);
        request.put("type", "0");
        request.put("channel_id", "0");
        request.put("enter_from", "personal_homepage");
        request.put("friends_upvote", "false");
        final CountDownLatch answered = new CountDownLatch(1);
        final boolean[] fine = new boolean[1];
        send.invoke(repository, request, callback(good, answered, fine, true), callback(bad, answered, fine, false));
        boolean in = answered.await(PATIENCE, TimeUnit.MILLISECONDS);
        Diary.note("like purge: " + aid + " -> " + (in ? (fine[0] ? "done" : "refused") : "no answer"));
        return in && fine[0];
    }

    /** A function of TikTok's kinds that does nothing but say which of the two answered. */
    private static Object callback(Class<?> type, final CountDownLatch answered, final boolean[] fine, final boolean good) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] arguments) {
                String name = method.getName();
                if (name.equals("invoke")) {
                    if (answered.getCount() > 0) {
                        fine[0] = good;
                        answered.countDown();
                    }
                    return unit();
                }
                if (name.equals("hashCode")) return System.identityHashCode(proxy);
                if (name.equals("equals")) return proxy == arguments[0];
                if (name.equals("toString")) return "like-callback";
                return null;
            }
        });
    }

    private static Object unit() {
        try {
            return Class.forName("kotlin.Unit").getField("LIZ").get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------ taking back

    private static void remove(final Activity activity, final List<String> items) {
        if (running) return;
        running = true;
        cancelled = false;

        phase = "Удаляем ваши лайки...";
        doneNow = 0;
        totalNow = items.size();
        final RepostPurge.Waiting waiting = RepostPurge.screen(activity, Local.of("Удаляем ваши лайки..."), STOP);
        final Dialog dialog = waiting.dialog;
        dialog.show();
        status(Local.of("Удаляем ваши лайки..."), 0, items.size(), true);

        new Thread(new Runnable() {
            @Override public void run() {
                int done = 0, refused = 0;
                String why = null;
                try {
                    Object origin = Class.forName("X.0p8g").getConstructor().newInstance();
                    java.lang.reflect.Field held = origin.getClass().getDeclaredField("LLJJIII");
                    held.setAccessible(true);
                    Object repository = held.get(origin);
                    Class<?> good = Class.forName("X.14yX");
                    Class<?> bad = Class.forName("kotlin.jvm.functions.Function2");
                    Method send = null;
                    for (Method method : repository.getClass().getMethods()) {
                        if (method.getName().equals("D") && method.getParameterTypes().length == 3) send = method;
                    }
                    if (send == null) throw new NoSuchMethodException("the like call is not here");
                    send.setAccessible(true);
                    for (String aid : items) {
                        if (cancelled) break;
                        boolean fine = false;
                        try {
                            fine = take(aid, repository, send, good, bad);
                        } catch (Throwable error) {
                            Diary.note("like purge: " + error);
                        }
                        if (fine) {
                            done++;
                            doneNow = done;
                            refused = 0;
                            status(done + " / " + items.size(), done, items.size(), true);
                            final int shownDone = done;
                            try {
                                activity.runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        waiting.words.setText(Local.of("Удаляем ваши лайки...") + "\n" + shownDone + " / " + items.size());
                                    }
                                });
                            } catch (Throwable ignored) {
                            }
                        } else {
                            refused++;
                            if (refused >= REFUSALS) {
                                why = "refused";
                                break;
                            }
                        }
                        long until = SystemClock.uptimeMillis() + PAUSE;
                        while (!cancelled && SystemClock.uptimeMillis() < until) Thread.sleep(50);
                    }
                } catch (Throwable error) {
                    why = String.valueOf(error);
                    Diary.note("like purge: " + error);
                }
                final int removed = done;
                final boolean stopped = why != null;
                final boolean cancelledNow = cancelled;
                running = false;
                String message = Local.of("Удалено лайков: ") + removed + " / " + items.size();
                if (stopped) message += "\n" + Local.of("TikTok перестал отвечать на запросы. Подождите немного и запустите ещё раз.");
                else if (cancelledNow) message += "\n" + Local.of("Остановлено");
                else if (removed < items.size()) message += "\n" + Local.of("Часть лайков TikTok не удалил.");
                message += "\n\n" + Local.of(NOTE_WARNING);
                status(Local.of("Удалено лайков: ") + removed + " / " + items.size(), 0, 0, false);
                final String result = message;
                final Activity here = Watch.here() != null ? Watch.here() : activity;
                here.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        try {
                            if (dialog.isShowing()) dialog.dismiss();
                        } catch (Throwable ignored) {
                        }
                        if (here.isFinishing()) return;
                        Popup.show(here, Local.of("Лайки"), result);
                    }
                });
            }
        }, "margyt-like-purge").start();
    }
}
