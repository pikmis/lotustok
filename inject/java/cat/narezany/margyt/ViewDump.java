package cat.narezany.margyt;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * A diagnostic: writes the class of every view in the lower part of the screen
 * to logcat under LotusDump, so a view can be named by its real class. Off
 * unless asked for by the build (see Watch).
 */
final class ViewDump {

    private static int left = 2;

    private ViewDump() {}

    static void once(Activity activity) {
        if (left <= 0 || activity == null) return;
        left--;
        final View root = activity.getWindow().getDecorView();
        final int screen = activity.getResources().getDisplayMetrics().heightPixels;
        Log.i("LotusDump", "== " + activity.getClass().getName() + " screen " + screen);
        // after the first layout: before it every view is still at the top
        root.postDelayed(new Runnable() {
            @Override
            public void run() {
                walk(root, 0, screen);
            }
        }, 3000L);
    }

    private static void walk(View view, int depth, int screen) {
        if (depth > 30 || view == null) return;
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        if (at[1] > screen / 2) {
            Log.i("LotusDump", depth + " " + view.getClass().getName() + " y=" + at[1]
                    + " " + view.getWidth() + "x" + view.getHeight()
                    + " id=" + (view.getId() == View.NO_ID ? "-" : Integer.toHexString(view.getId())));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) walk(group.getChildAt(i), depth + 1, screen);
        }
    }
}
