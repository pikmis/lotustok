package cat.narezany.margyt;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/** The largest visible video layer in the current window, for live glass capture. */
final class GlassVideo {
    private static WeakReference<View> cached = new WeakReference<View>(null);
    private static long checked;

    private GlassVideo() {}

    static View render(View root) {
        if (root == null) return null;
        long now = SystemClock.uptimeMillis();
        View value = cached.get();
        if (now - checked < 400L && value != null && value.getRootView() == root
                && value.isAttachedToWindow() && value.isShown()) return value;
        if (now - checked < 400L && value == null) return null;
        checked = now;
        Candidate best = new Candidate();
        visit(root, root, best, new int[]{2000}, 0);
        cached = new WeakReference<View>(best.view);
        return best.view;
    }

    private static final class Candidate { View view; long area; }

    private static void visit(View view, View root, Candidate best, int[] budget, int depth) {
        if (view == null || budget[0]-- <= 0 || depth > 35 || !view.isShown()) return;
        if (view instanceof SurfaceView || view instanceof TextureView) {
            Rect visible = new Rect();
            if (view.getGlobalVisibleRect(visible)) {
                long area = (long) visible.width() * visible.height();
                long minimum = (long) root.getWidth() * root.getHeight() / 8L;
                if (area > best.area && area >= minimum) { best.view = view; best.area = area; }
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) visit(group.getChildAt(i), root, best, budget, depth + 1);
        }
    }
}
