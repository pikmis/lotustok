package cat.narezany.margyt;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Liquid glass the way Telegram draws it (blur3/LiquidGlassEffect and liquid_glass_shader.agsl,
 * Telegram for Android, GPL): nothing is copied into a bitmap. What lies under the glass is
 * recorded as a display list (the views' own render nodes, a video's texture layer among them),
 * blurred by the GPU with a RenderEffect and bent by a runtime shader, on every frame the window
 * draws. A video behind it moves at the video's own rate, and a list scrolls under it in step.
 *
 * Android 13 and later; 12 gets the live blur without the bending, older ones a plain tint.
 */
final class LiveGlass {

    /** Telegram's shader: a rounded rectangle with a bevel that refracts what is under it. */
    private static final String SHADER = "uniform shader img;\n"
            + "uniform float2 resolution;uniform float2 center;uniform float2 size;uniform float4 radius;\n"
            + "uniform float thickness;uniform float refract_index;uniform float refract_intensity;\n"
            + "uniform float4 foreground_color_premultiplied;\n"
            + "half sdfRect(half2 p, half4 r){r.xy=(p.x>0.0)?r.xy:r.zw;r.x=(p.y>0.0)?r.x:r.y;"
            + "half2 q=abs(p)-size+r.x;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r.x;}\n"
            + "half4 srcOver(half4 src,half4 dst){half3 o=(src.rgb+dst.rgb*(1.0-src.a));"
            + "float a=src.a+(1.0-src.a)*dst.a;return half4(o,a);}\n"
            + "half4 main(in float2 fragCoord){half2 p=fragCoord-center;half sd=sdfRect(p,radius);half2 uv=fragCoord;"
            + "if(sd<0.0){half sdX=sdfRect(p+half2(1.0,0.0),radius);half sdY=sdfRect(p+half2(0.0,1.0),radius);"
            + "half n_cos=max(thickness+sd,0.0)/thickness;half n_cos2=n_cos*n_cos;half n_sin=sqrt(1.0-n_cos2);"
            + "half3 normal=normalize(half3((sdX-sd)*n_cos,(sdY-sd)*n_cos,n_sin));"
            + "half3 refract_vec=refract(half3(0.0,0.0,-1.0),normal,1.0/refract_index);"
            + "half h=sd<-thickness?thickness:sqrt(sd*(-2.0*thickness-sd));"
            + "half refract_length=(h+8.0*thickness)/-refract_vec.z;"
            + "uv+=refract_vec.xy*refract_length*refract_intensity;}"
            + "return srcOver(half4(foreground_color_premultiplied),img.eval(uv));}\n";

    static boolean available() {
        return android.os.Build.VERSION.SDK_INT >= 31;
    }

    private final RenderNode source = new RenderNode("margyt-glass-under");
    private final RenderNode fill = new RenderNode("margyt-glass");
    private final Outline outline = new Outline();
    private final int[] at = new int[2];
    private Object lens;
    private float blurNow = -1;
    private float lastW, lastH, lastTop, lastBottom, lastThick;
    private int lastTint;

    /**
     * Draws the glass of `view` into `canvas`, whose origin is the glass's top left corner,
     * `w` by `h`, at `left`,`top` inside the view. False when it cannot be done here.
     */
    boolean draw(Canvas canvas, View view, float left, float top, float w, float h,
                 float rTop, float rBottom, int tint, boolean bend) {
        if (!canvas.isHardwareAccelerated() || w < 1 || h < 1) return false;
        View root = view.getRootView();
        if (root == null || root == view) return false;
        watch(root, view);
        float density = view.getResources().getDisplayMetrics().density;
        float blur = 3.2f * density;
        int margin = Math.round(blur * 2f + 16f * density);
        int sw = Math.round(w) + 2 * margin, sh = Math.round(h) + 2 * margin;

        view.getLocationInWindow(at);
        float gx = at[0] + left, gy = at[1] + top;
        long now = android.os.SystemClock.uptimeMillis();
        Watcher watcher = ROOTS.get(root);
        int generation = watcher == null ? 0 : watcher.generation;
        // What is under the glass is recorded again only when the screen has moved or been laid
        // out, or twice a second: the pictures in it (a video, a list's rows) are references to
        // the views' own render nodes and stay live without it. Recording it on every frame was
        // the lag of the first version.
        boolean moved = !source.hasDisplayList() || sw != recordedW || sh != recordedH
                || Math.abs(gx - recordedX) > .5f || Math.abs(gy - recordedY) > .5f;
        // Something somewhere in the window scrolled or was laid out. A list scrolling under
        // the comments' field, a carousel of photos, the feed under its buttons: that is every
        // frame, for every piece of glass in the window. Each piece takes it at most every
        // `spacing` ms (longer for a piece whose recordings come out slow), and once more
        // after it has settled, so the last picture is the right one.
        boolean due = generation != recordedGeneration || now - recordedAt > 500L;
        boolean stale = moved || (due && now - recordedAt >= spacing);
        if (!stale && due && !settlePosted) {
            settlePosted = true;
            final View again = view;
            view.postOnAnimationDelayed(() -> { settlePosted = false; again.invalidate(); }, spacing);
        }
        if (recordingDepth > 0) {
            // drawn inside another glass's recording: what it showed last, no new recording
            // (that would be glass recording glass, round and round)
            if (!source.hasDisplayList()) {
                android.graphics.Paint plain = new android.graphics.Paint();
                plain.setColor(tint);
                canvas.drawRect(0, 0, w, h, plain);
                return true;
            }
        } else if (stale) record(root, view, sw, sh, margin, gx, gy, generation, now);
        drawGlass(canvas, w, h, rTop, rBottom, tint, bend, margin, density, blur);
        return true;
    }

    private static int drawn, videosDrawn;
    /** How many recordings of what is under some glass are being made right now. */
    private static int recordingDepth;
    private static long lastLog;
    private int recordedW, recordedH, recordedGeneration;
    private float recordedX = Float.NaN, recordedY = Float.NaN;
    private long recordedAt;

    /** The least time between two recordings of this piece for a change elsewhere, ms. */
    private long spacing = 48L;
    private boolean settlePosted;

    private void record(View root, View view, int sw, int sh, int margin, float gx, float gy, int generation, long now) {
        long started = System.nanoTime();
        try {
            recordInner(root, view, sw, sh, margin, gx, gy, generation, now);
        } finally {
            float took = (System.nanoTime() - started) / 1e6f;
            // a slow one is asked for less often, a quick one back to the usual
            spacing = took > 6f ? Math.min(200L, spacing + 32L) : Math.max(48L, spacing - 8L);
            if (took > 6f && now - slowLog > 2000L) {
                slowLog = now;
                android.util.Log.w("MargyTGlass", "slow glass record " + String.format(java.util.Locale.ROOT, "%.1f", took)
                        + " ms on " + view.getClass().getName() + ", " + drawn + " views, " + videosDrawn + " videos, next in " + spacing + " ms");
            }
        }
    }

    private static long slowLog;

    private void recordInner(View root, View view, int sw, int sh, int margin, float gx, float gy, int generation, long now) {
        recordedW = sw; recordedH = sh; recordedX = gx; recordedY = gy; recordedGeneration = generation; recordedAt = now;
        source.setPosition(0, 0, sw, sh);
        RecordingCanvas under = source.beginRecording(sw, sh);
        // Never a flag that makes other glass draw plain: drawing a view into this recording
        // also records that view's own display list when it is due, and the plain version
        // stayed there and was what the window showed (the top bar, the share sheet and the
        // date window flickered between glass and a flat tint). Glass met inside a recording
        // shows its last picture instead (`recording > 0`).
        recordingDepth++;
        try {
            under.translate(margin - gx, margin - gy);
            areaL = gx - margin; areaT = gy - margin; areaR = gx - margin + sw; areaB = gy - margin + sh;
            videosHeld.clear();
            recording = videosHeld;
            drawn = 0; videosDrawn = 0;
            behind(root, view, under);
            // TikTok reserves the tab row below the feed's video viewport. Keep its
            // layout (captions depend on it), but continue the current frame under glass.
            if (now - lastLog > 3000L) {
                lastLog = now;
                android.util.Log.w("MargyTGlass", "live glass on " + view.getClass().getName() + " " + Math.round(gx) + "," + Math.round(gy)
                        + ": " + drawn + " views under it, " + videosDrawn + " videos");
            }
        } catch (Throwable error) {
            Diary.detail("live glass capture: " + error);
        } finally {
            recording = null;
            recordingDepth--;
            source.endRecording();
        }
    }

    private void drawGlass(Canvas canvas, float w, float h, float rTop, float rBottom, int tint, boolean bend,
                           int margin, float density, float blur) {
        for (VideoCopy copy : videosHeld) copy.want();
        if (blur != blurNow) {
            blurNow = blur;
            source.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
        }

        int iw = Math.round(w), ih = Math.round(h);
        fill.setPosition(0, 0, iw, ih);
        float radius = Math.min(rTop, Math.min(w, h) / 2f);
        outline.setRoundRect(0, 0, iw, ih, radius);
        fill.setOutline(outline);
        fill.setClipToOutline(true);
        float thick = Math.max(1f, Math.min(11f * density, Math.min(w, h) / 5f));
        if (bend && android.os.Build.VERSION.SDK_INT >= 33) {
            Lens shaded = lens instanceof Lens ? (Lens) lens : null;
            if (shaded == null) { shaded = new Lens(); lens = shaded; }
            if (w != lastW || h != lastH || rTop != lastTop || rBottom != lastBottom || thick != lastThick || tint != lastTint) {
                lastW = w; lastH = h; lastTop = rTop; lastBottom = rBottom; lastThick = thick; lastTint = tint;
                float rt = Math.min(rTop, h / 2f), rb = Math.min(rBottom, h / 2f);
                fill.setRenderEffect(shaded.effect(w, h, rt, rb, thick, 0.75f, 1.5f, tint));
            }
        } else {
            fill.setRenderEffect(null);
        }
        RecordingCanvas glass = fill.beginRecording(iw, ih);
        glass.translate(-margin, -margin);
        glass.drawRenderNode(source);
        if (!bend || android.os.Build.VERSION.SDK_INT < 33) {
            glass.translate(margin, margin);
            glass.drawColor(tint);
        }
        fill.endRecording();
        canvas.drawRenderNode(fill);
    }

    // --------------------------------------------------------------- the lens

    /**
     * Apple's selection bubble: clear glass over what is given (the tab bar itself, its icons
     * and names), which it shows a little larger, bent at its rounded edge, with the colours
     * parting there as through a real lens. No tint, no blur.
     */
    private static final String BUBBLE = "uniform shader img;\n"
            + "uniform float2 center;uniform float2 size;uniform float radius;uniform float thickness;\n"
            + "uniform float refract_index;uniform float refract_intensity;uniform float dispersion;\n"
            + "half sdfRect(half2 p,half r){half2 q=abs(p)-size+r;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;}\n"
            + "half4 main(float2 fragCoord){half2 p=fragCoord-center;half sd=sdfRect(p,radius);"
            + "if(sd>=0.0)return img.eval(fragCoord);"
            + "half sdX=sdfRect(p+half2(1.0,0.0),radius);half sdY=sdfRect(p+half2(0.0,1.0),radius);"
            + "half n_cos=max(thickness+sd,0.0)/thickness;half n_sin=sqrt(1.0-n_cos*n_cos);"
            + "half3 normal=normalize(half3((sdX-sd)*n_cos,(sdY-sd)*n_cos,n_sin));"
            + "half3 rv=refract(half3(0.0,0.0,-1.0),normal,1.0/refract_index);"
            + "half h=sd<-thickness?thickness:sqrt(sd*(-2.0*thickness-sd));"
            + "half2 d=rv.xy*((h+8.0*thickness)/-rv.z)*refract_intensity;"
            + "half4 g=img.eval(fragCoord+d);"
            + "half r=img.eval(fragCoord+d*(1.0+dispersion)).r;half b=img.eval(fragCoord+d*(1.0-dispersion)).b;"
            + "half edge=smoothstep(-thickness,0.0,sd);"
            + "half3 c=half3(r,g.g,b)+half3(0.035)*edge;"   // a little light caught at the rim
            + "return half4(c*g.a+half3(0.035)*(1.0-g.a),max(g.a,0.035));}\n";

    private final RenderNode bubble = new RenderNode("margyt-glass-bubble");
    private Object bubbleLens;

    /** Draws the bubble at `box` (in canvas coordinates) over `content`, which is drawn in the same coordinates. */
    boolean bubble(Canvas canvas, RenderNode content, android.graphics.RectF box, float magnify, float density, float strength) {
        if (!canvas.isHardwareAccelerated() || box.width() < 2 || box.height() < 2) return false;
        int w = Math.round(box.width()), h = Math.round(box.height());
        float radius = h / 2f;
        bubble.setPosition(0, 0, w, h);
        outline.setRoundRect(0, 0, w, h, radius);
        bubble.setOutline(outline);
        bubble.setClipToOutline(true);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            BubbleLens lens = bubbleLens instanceof BubbleLens ? (BubbleLens) bubbleLens : null;
            if (lens == null) { lens = new BubbleLens(); bubbleLens = lens; }
            bubble.setRenderEffect(lens.effect(w, h, radius, Math.max(2f, Math.min(h / 5f, 8f * density)), strength));
        }
        RecordingCanvas inside = bubble.beginRecording(w, h);
        // what is under the bubble, larger, about its middle
        inside.translate(w / 2f, h / 2f);
        inside.scale(magnify, magnify);
        inside.translate(-box.centerX(), -box.centerY());
        inside.drawRenderNode(content);
        bubble.endRecording();
        int saved = canvas.save();
        canvas.translate(box.left, box.top);
        canvas.drawRenderNode(bubble);
        canvas.restoreToCount(saved);
        return true;
    }

    private static final class BubbleLens {
        private final RuntimeShader shader = new RuntimeShader(BUBBLE);

        RenderEffect effect(float w, float h, float radius, float thick, float strength) {
            shader.setFloatUniform("center", w / 2f, h / 2f);
            shader.setFloatUniform("size", w / 2f, h / 2f);
            shader.setFloatUniform("radius", radius);
            shader.setFloatUniform("thickness", thick);
            shader.setFloatUniform("refract_index", 1.5f);
            // gentle: the edge bends what is under it a little, the colours barely part
            shader.setFloatUniform("refract_intensity", 0.12f * strength);
            shader.setFloatUniform("dispersion", 0.06f);
            return RenderEffect.createRuntimeShaderEffect(shader, "img");
        }
    }

    /** Kept apart so Android 12 never loads RuntimeShader. */
    private static final class Lens {
        private final RuntimeShader shader = new RuntimeShader(SHADER);

        RenderEffect effect(float w, float h, float rTop, float rBottom, float thick, float intensity, float index, int tint) {
            float a = Color.alpha(tint) / 255f;
            shader.setFloatUniform("resolution", w, h);
            shader.setFloatUniform("center", w / 2f, h / 2f);
            shader.setFloatUniform("size", w / 2f, h / 2f);
            // right bottom, right top, left bottom, left top
            shader.setFloatUniform("radius", rBottom, rTop, rBottom, rTop);
            shader.setFloatUniform("thickness", thick);
            shader.setFloatUniform("refract_intensity", intensity);
            shader.setFloatUniform("refract_index", index);
            shader.setFloatUniform("foreground_color_premultiplied",
                    Color.red(tint) / 255f * a, Color.green(tint) / 255f * a, Color.blue(tint) / 255f * a, a);
            return RenderEffect.createRuntimeShaderEffect(shader, "img");
        }
    }

    // ------------------------------------------------------------ what lies under it

    private static final Matrix SCRATCH = new Matrix();

    /**
     * Everything of the window that is drawn before `target`: at each step down from the root,
     * the parent's background and the children before the one on the way. Views draw through
     * their own render nodes here, so this is a list of references, not a second drawing.
     */
    /** The glass's place in the window being recorded, a little wider for what the edge bends in. */
    private static float areaL, areaT, areaR, areaB;
    /** The video copies the recording being made holds. */
    private static ArrayList<VideoCopy> recording;
    /** The ones this glass's last recording holds: kept coming while the glass is drawn. */
    private final ArrayList<VideoCopy> videosHeld = new ArrayList<>();

    private static void behind(View root, View target, Canvas canvas) {
        // a window of the mod over the app (a sheet, a panel): the app's own window is under it
        View host = activityDecor(target);
        if (host != null && host != root && host.isAttachedToWindow() && host instanceof ViewGroup) {
            int[] a = new int[2], b = new int[2];
            host.getLocationOnScreen(a);
            root.getLocationOnScreen(b);
            int saved = canvas.save();
            canvas.translate(a[0] - b[0], a[1] - b[1]);
            mediaOf(host);   // the app window's videos are known, to be kept out of this one
            manual(canvas, (ViewGroup) host, a[0] - b[0], a[1] - b[1]);
            canvas.restoreToCount(saved);
        }
        ArrayList<View> chain = new ArrayList<>();
        for (View v = target; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            chain.add(0, v);
            if (v == root) break;
        }
        if (chain.isEmpty() || chain.get(0) != root) return;
        int saved = canvas.save();
        float ox = 0, oy = 0;
        for (int level = 0; level < chain.size() - 1; level++) {
            if (!(chain.get(level) instanceof ViewGroup)) break;
            ViewGroup group = (ViewGroup) chain.get(level);
            View next = chain.get(level + 1);
            Drawable background = group.getBackground();
            if (background != null) background.draw(canvas);
            // a group draws its children by Z first and by place second, as Android does: a bar
            // raised above the feed by its elevation has the feed under it even when the feed
            // comes after it among the children (TikTok's top tabs over the video are that)
            int index = group.indexOfChild(next);
            float z = next.getZ();
            for (int i = 0; i < group.getChildCount(); i++) {
                if (i == index) continue;
                View other = group.getChildAt(i);
                float oz = other.getZ();
                if (oz < z || (oz == z && i < index)) child(canvas, group, other, ox, oy);
            }
            float dx = next.getLeft() - group.getScrollX(), dy = next.getTop() - group.getScrollY();
            canvas.translate(dx, dy);
            ox += dx + next.getTranslationX();
            oy += dy + next.getTranslationY();
            Matrix matrix = next.getMatrix();
            if (!matrix.isIdentity()) canvas.concat(matrix);
        }
        canvas.restoreToCount(saved);
    }

    /** The activity's window a view of a dialog or a popup belongs to, or null. */
    private static View activityDecor(View view) {
        android.content.Context context = view.getContext();
        for (int i = 0; i < 12 && context instanceof android.content.ContextWrapper; i++) {
            if (context instanceof android.app.Activity) {
                android.view.Window window = ((android.app.Activity) context).getWindow();
                return window == null ? null : window.getDecorView();
            }
            android.content.Context next = ((android.content.ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return null;
    }

    /** Whether a view at `x`,`y` in the window, as big as it is, reaches the glass. */
    private static boolean reaches(View view, float x, float y) {
        return x < areaR && x + view.getWidth() > areaL && y < areaB && y + view.getHeight() > areaT;
    }

    /** `group` lies at `ox`,`oy` in the window; one of its children is drawn, if it reaches the glass. */
    private static void child(Canvas canvas, ViewGroup group, View child, float ox, float oy) {
        if (child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0.01f
                || child.getWidth() <= 0 || child.getHeight() <= 0) return;
        float cx = ox + child.getLeft() - group.getScrollX() + child.getTranslationX();
        float cy = oy + child.getTop() - group.getScrollY() + child.getTranslationY();
        if (!reaches(child, cx, cy)) return;   // nowhere near the glass: not recorded at all
        int saved = canvas.save();
        canvas.translate(child.getLeft() - group.getScrollX(), child.getTop() - group.getScrollY());
        Matrix matrix = child.getMatrix();
        if (!matrix.isIdentity()) canvas.concat(matrix);
        if (child instanceof android.view.SurfaceView) {
            // never the SurfaceView itself: in an ordinary drawing it is a see-through hole,
            // and drawing its render node a second time moved the video's layer about.
            // Its picture comes from a copy of its frames instead.
            VideoCopy copy = VideoCopy.of((android.view.SurfaceView) child);
            copy.draw(canvas);
            if (recording != null && !recording.contains(copy)) recording.add(copy);
            videosDrawn++;
            canvas.restoreToCount(saved);
            return;
        }
        if (child.getAlpha() < 0.99f) {
            canvas.saveLayerAlpha(0, 0, child.getWidth(), child.getHeight(), Math.round(child.getAlpha() * 255f));
        }
        // a branch with a video in it (looked for here, every time, not taken from a list that
        // can be a moment old) is taken apart by hand; any other is drawn whole, by reference
        if (child instanceof ViewGroup && anyVideo((ViewGroup) child, cx, cy)) manual(canvas, (ViewGroup) child, cx, cy);
        else child.draw(canvas);
        drawn++;
        canvas.restoreToCount(saved);
    }

    /** A group with a video in it, taken apart by hand so the video's own view is never drawn. */
    private static void manual(Canvas canvas, ViewGroup group, float ox, float oy) {
        Drawable background = group.getBackground();
        if (background != null) background.draw(canvas);
        int count = group.getChildCount();
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) order[i] = i;
        final ViewGroup g = group;
        java.util.Arrays.sort(order, (x, y) -> {
            int byZ = Float.compare(g.getChildAt(x).getZ(), g.getChildAt(y).getZ());
            return byZ != 0 ? byZ : Integer.compare(x, y);
        });
        for (Integer i : order) child(canvas, group, group.getChildAt(i), ox, oy);
    }

    /**
     * A SurfaceView anywhere in `group`, under the glass or not. One that is in the branch but
     * not under the glass still must never be drawn here: a SurfaceView places its picture
     * where its view was last drawn, and drawn into another window (a sheet over the feed)
     * the video jumped between the two.
     */
    private static boolean anyVideo(ViewGroup group, float ox, float oy) {
        if (holdsVideo(group, ox, oy, 0)) return true;
        Watcher watcher = ROOTS.get(group.getRootView());
        if (watcher != null) {
            for (android.view.SurfaceView video : watcher.media.videos) {
                if (video != null && video.isAttachedToWindow() && inside(video, group)) return true;
            }
        }
        View playing = GlassVideo.render(group.getRootView());
        return playing != null && inside(playing, group);
    }

    /** A SurfaceView somewhere in the part of `group` that reaches the glass. */
    private static boolean holdsVideo(ViewGroup group, float ox, float oy, int depth) {
        if (depth > 30) return false;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE || child.getWidth() <= 0 || child.getHeight() <= 0) continue;
            float cx = ox + child.getLeft() - group.getScrollX() + child.getTranslationX();
            float cy = oy + child.getTop() - group.getScrollY() + child.getTranslationY();
            if (!reaches(child, cx, cy)) continue;
            if (child instanceof android.view.SurfaceView) return true;
            if (child instanceof ViewGroup && holdsVideo((ViewGroup) child, cx, cy, depth + 1)) return true;
        }
        return false;
    }

    private static Media mediaOf(View root) {
        Watcher watcher = ROOTS.get(root);
        if (watcher == null) {
            watcher = new Watcher();
            ROOTS.put(root, watcher);
            ViewTreeObserver observer = root.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(watcher);
            observer.addOnScrollChangedListener(watcher);
        }
        return watcher.media(root);
    }

    private static boolean inside(View view, View group) {
        for (Object up = view.getParent(); up instanceof View; up = ((View) up).getParent()) if (up == group) return true;
        return false;
    }

    /** What in a window has a picture worth bending: videos, and pictures that are not one colour. */
    static final class Media {
        final ArrayList<android.view.SurfaceView> videos = new ArrayList<>();
        final ArrayList<View> pictures = new ArrayList<>();
        long at;

        void find(View root) {
            videos.clear();
            pictures.clear();
            at = android.os.SystemClock.uptimeMillis();
            // the video TikTok is playing is known by name: it is never missed, however big the screen
            View playing = GlassVideo.render(root);
            if (playing instanceof android.view.SurfaceView) { videos.add((android.view.SurfaceView) playing); pictures.add(playing); }
            long started = System.nanoTime();
            int[] budget = {30000};
            walk(root, budget);
            float took = (System.nanoTime() - started) / 1e6f;
            if (took > 4f) android.util.Log.w("MargyTGlass", "slow media scan " + String.format(java.util.Locale.ROOT, "%.1f", took)
                    + " ms, " + (30000 - budget[0]) + " views, " + pictures.size() + " pictures");
        }

        private void walk(View view, int[] budget) {
            if (budget[0]-- <= 0 || view.getVisibility() != View.VISIBLE || view.getWidth() <= 0 || view.getHeight() <= 0) return;
            if (view instanceof android.view.SurfaceView) {
                if (!videos.contains(view)) { videos.add((android.view.SurfaceView) view); pictures.add(view); }
                return;
            }
            if (view instanceof android.view.TextureView) { pictures.add(view); return; }
            if (view instanceof android.widget.ImageView) {
                Drawable shown = ((android.widget.ImageView) view).getDrawable();
                float d = view.getResources().getDisplayMetrics().density;
                if (shown != null && !(shown instanceof android.graphics.drawable.ColorDrawable)
                        && view.getWidth() >= 40 * d && view.getHeight() >= 40 * d) pictures.add(view);
                return;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) walk(group.getChildAt(i), budget);
            }
        }
    }

    /**
     * Whether anything with a picture is under this piece of glass. Over a page of one colour
     * there is nothing to blur or bend, and the glass is drawn as a tint, at no cost: the
     * comments, a card in a list, a button. A window of the mod over the app always has the
     * app under it. Asked at most twice a second for each piece.
     */
    static boolean worthIt(View view, float left, float top, float w, float h) {
        View root = view.getRootView();
        if (root == null) return false;
        View host = activityDecor(view);
        if (host != null && host != root) return true;
        Media media = mediaOf(root);
        int[] spot = new int[2];
        view.getLocationInWindow(spot);
        float gl = spot[0] + left - 8, gt = spot[1] + top - 8, gr = gl + w + 16, gb = gt + h + 16;
        for (View picture : media.pictures) {
            if (picture == null || picture == view || !picture.isShown() || inside(picture, view)) continue;
            picture.getLocationInWindow(spot);
            if (spot[0] < gr && spot[0] + picture.getWidth() > gl && spot[1] < gb && spot[1] + picture.getHeight() > gt) return true;
        }
        return false;
    }

    /**
     * Copies of one SurfaceView's frames, small (a blur needs little), about twenty a second
     * and only while some glass has drawn it in the last moment. Each copy is put into a
     * render node of its own, which the glass's recording holds: a new frame needs no new
     * recording, only a frame of the window, which is asked for.
     */
    private static final class VideoCopy implements android.view.PixelCopy.OnPixelCopyFinishedListener {
        private static final WeakHashMap<android.view.SurfaceView, VideoCopy> ALL = new WeakHashMap<>();
        private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
        private static final int WIDTH = 270;
        final java.lang.ref.WeakReference<android.view.SurfaceView> view;
        final RenderNode node = new RenderNode("margyt-glass-video");
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        android.graphics.Bitmap bitmap;
        long wanted;
        boolean busy, scheduled;

        VideoCopy(android.view.SurfaceView view) {
            this.view = new java.lang.ref.WeakReference<>(view);
        }

        static VideoCopy of(android.view.SurfaceView view) {
            VideoCopy copy = ALL.get(view);
            if (copy == null) { copy = new VideoCopy(view); ALL.put(view, copy); }
            return copy;
        }

        void want() {
            wanted = android.os.SystemClock.uptimeMillis();
            if (!scheduled) { scheduled = true; MAIN.post(this::take); }
        }

        void draw(Canvas canvas) {
            android.view.SurfaceView source = view.get();
            if (source == null) return;
            wanted = android.os.SystemClock.uptimeMillis();
            node.setPosition(0, 0, source.getWidth(), source.getHeight());
            if (!scheduled) { scheduled = true; MAIN.post(this::take); }
            if (node.hasDisplayList()) canvas.drawRenderNode(node);
        }

        private void take() {
            scheduled = false;
            android.view.SurfaceView source = view.get();
            if (source == null || !source.isAttachedToWindow() || busy) return;
            if (android.os.SystemClock.uptimeMillis() - wanted > 1500L) return;   // no glass over it any more
            android.view.Surface surface = source.getHolder().getSurface();
            if (surface == null || !surface.isValid() || source.getWidth() <= 0) { later(); return; }
            int h = Math.max(1, Math.round(WIDTH * source.getHeight() / (float) source.getWidth()));
            if (bitmap == null || bitmap.getHeight() != h) bitmap = android.graphics.Bitmap.createBitmap(WIDTH, h, android.graphics.Bitmap.Config.ARGB_8888);
            busy = true;
            try {
                android.view.PixelCopy.request(source, bitmap, this, MAIN);
            } catch (Throwable error) {
                busy = false;
                later();
            }
        }

        private long copyLog;

        @Override
        public void onPixelCopyFinished(int result) {
            busy = false;
            long t = android.os.SystemClock.uptimeMillis();
            if (t - copyLog > 3000L) { copyLog = t; android.util.Log.w("MargyTGlass", "video copy: " + result); }
            android.view.SurfaceView source = view.get();
            if (result == android.view.PixelCopy.SUCCESS && source != null) {
                RecordingCanvas canvas = node.beginRecording(source.getWidth(), source.getHeight());
                canvas.drawBitmap(bitmap, null, new android.graphics.Rect(0, 0, source.getWidth(), source.getHeight()), paint);
                node.endRecording();
                // a frame of the window, so the glass shows the new picture
                Watcher watcher = ROOTS.get(source.getRootView());
                if (watcher != null) watcher.redraw();
            }
            later();
        }

        private void later() {
            if (!scheduled) { scheduled = true; MAIN.postDelayed(this::take, 50L); }
        }
    }

    // ----------------------------------------------- drawn again with what is under it

    private static final WeakHashMap<View, Watcher> ROOTS = new WeakHashMap<>();

    /**
     * Whenever the window draws a frame, every piece of glass in it is drawn again: a video
     * frame or a list moving under it is a frame of the window, and the glass follows it.
     * A window that does not draw costs nothing.
     */
    private static void watch(View root, View glass) {
        Watcher watcher = ROOTS.get(root);
        if (watcher == null) {
            watcher = new Watcher();
            ROOTS.put(root, watcher);
            ViewTreeObserver observer = root.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(watcher);
            observer.addOnScrollChangedListener(watcher);
        }
        watcher.glass.put(glass, Boolean.TRUE);
    }

    /**
     * The screen was laid out or scrolled: what is under the glass is recorded again on the
     * next frame. Nothing is asked of a window that is still.
     */
    private static final class Watcher implements ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnScrollChangedListener {
        final WeakHashMap<View, Boolean> glass = new WeakHashMap<>();
        int generation;

        private final Media media = new Media();

        Media media(View root) {
            if (android.os.SystemClock.uptimeMillis() - media.at > 2000L) media.find(root);
            return media;
        }

        @Override public void onGlobalLayout() { changed(); }

        @Override public void onScrollChanged() { changed(); }

        void changed() {
            if (!NavBar.isEnabled() || NavBar.style() != NavBar.GLASS) return;
            generation++;
            redraw();
        }

        void redraw() {
            for (View view : new ArrayList<>(glass.keySet())) {
                if (view != null && view.isShown()) view.invalidate();
            }
        }
    }
}
