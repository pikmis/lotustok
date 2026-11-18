package cat.narezany.margyt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ss.android.ugc.aweme.base.model.UrlModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Frames round an avatar, bigger than the avatar.
 *
 * A frame comes with a badge or is given by hand, and the server says who wears which. It goes in the
 * overlay of the view the avatar is in, which moves with the view, and the parents up the chain stop
 * clipping it, because a frame is meant to stick out. Nothing about an avatar view says whose it is, but
 * every avatar passes through two places on its way to the screen: a read of its address off the account
 * (which remembers the pictures of accounts with a frame), and the image loader's display call (which,
 * finding one of those pictures, hangs the frame on the view). A profile page knows whose it is.
 */
public final class Frames {

    private Frames() {}

    private static final int TAG = 0x7e0c0371, WATCHED = 0x7e0c0375;
    /** TikTok's picture for somebody without one: shared by everybody, so never a sign of who it is. */
    private static final String NOBODY = "1594805258216454";
    private static final int MOST_PICTURES = 2000;

    public static final class Frame {
        public final String id, image, title, text;
        public final float scale, dy;
        /** A picture for each avatar shape, when the frame follows the shape (the angel's glow). */
        final Map<String, String> shapes;
        Frame(String id, String image, float scale, float dy, String title, String text, Map<String, String> shapes) {
            this.id = id; this.image = image; this.scale = scale; this.dy = dy; this.title = title; this.text = text;
            this.shapes = shapes;
        }

        /** The picture to draw: the one for the avatar shape chosen in the settings, if there is one. */
        String picture() {
            String own = shapes.get(AvatarShape.chosen());
            return own != null && own.length() > 0 ? own : image;
        }
    }

    private static volatile Map<String, Frame> frames = new HashMap<>();
    private static volatile Map<String, String> worn = new HashMap<>();
    /** The picture's own name (what stays the same across sizes) -> whose it is. */
    private static final Map<String, String> pictures = new ConcurrentHashMap<>();
    /** Every view with a frame on, so that a change shows at once. Main thread only. */
    private static final java.util.Set<View> hanging = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());

    /**
     * What this account just chose. The list of who wears what comes from the server, cached for half
     * a minute on the way and asked for every two: going by that alone, a new frame took up to a couple
     * of minutes to show on your own avatar. So your own choice counts here until the server agrees.
     */
    private static volatile String ownChoice;
    private static volatile String ownUid;
    private static volatile long ownUntil;

    private static volatile List<String> mine = new ArrayList<>();
    private static volatile String mineWorn = "";

    // ------------------------------------------------------- what the server says

    static void learn(JSONObject root) {
        JSONArray list = root.optJSONArray("frames");
        if (list == null) return;
        Map<String, Frame> built = new HashMap<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject one = list.optJSONObject(i);
            if (one == null) continue;
            String id = one.optString("id", "");
            if (id.length() == 0) continue;
            Map<String, String> shapes = new HashMap<>();
            JSONObject byShape = one.optJSONObject("shapes");
            if (byShape != null) {
                java.util.Iterator<String> names = byShape.keys();
                while (names.hasNext()) { String shape = names.next(); shapes.put(shape, byShape.optString(shape, "")); }
            }
            built.put(id, new Frame(id, one.optString("image", ""), (float) one.optDouble("scale", 1.5),
                    (float) one.optDouble("dy", 0), localised(one, "title"), localised(one, "text"), shapes));
        }
        Map<String, String> who = new HashMap<>();
        JSONObject on = root.optJSONObject("worn");
        if (on != null) {
            java.util.Iterator<String> keys = on.keys();
            while (keys.hasNext()) { String uid = keys.next(); who.put(uid, on.optString(uid, "")); }
        }
        if (ownChoice != null) {
            String said = who.get(ownUid);
            if (ownChoice.equals(said == null ? "" : said) || android.os.SystemClock.elapsedRealtime() > ownUntil) ownChoice = null;
        }
        frames = built;
        worn = who;
        Context context = Margy.context();
        if (context != null) for (Frame frame : built.values()) picture(context, frame.picture());
        new android.os.Handler(android.os.Looper.getMainLooper()).post(Frames::refresh);
    }

    /** What's on the screen goes by what's known now. */
    private static void refresh() {
        for (View view : new ArrayList<>(hanging)) {
            Object tag = view.getTag(TAG);
            if (!(tag instanceof Hung)) continue;
            Hung hung = (Hung) tag;
            Frame now = of(hung.uid);
            if (now != hung.frame) show(view, hung.uid, now, hung.pinned);
        }
    }

    private static String localised(JSONObject one, String field) {
        String translated = one.optString(field + "_" + Locale.getDefault().getLanguage(), "");
        return translated.length() > 0 ? translated : one.optString(field, "");
    }

    /** The frame an account shows, or null. */
    public static Frame of(String uid) {
        if (uid == null) return null;
        String id = ownChoice != null && uid.equals(ownUid) ? ownChoice : worn.get(uid);
        return id == null || id.length() == 0 ? null : frames.get(id);
    }

    static Frame byId(String id) { return frames.get(id); }

    /** What this account holds, from the same answer as its badges. */
    static void readMine(JSONObject answer) {
        JSONArray list = answer.optJSONArray("frames");
        if (list == null) return;
        List<String> built = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) built.add(list.optString(i, ""));
        mine = built;
        mineWorn = answer.optString("worn", "");
    }

    static List<String> mine() { return new ArrayList<>(mine); }
    static String mineWorn() { return mineWorn; }

    private static volatile String wanted;
    private static boolean saving;

    /**
     * Put on one of this account's frames, or none with an empty id. It shows at once; the server is
     * told a moment later, and only the last choice: somebody trying frames on goes through several
     * in as many seconds, and the server takes one write in a while. When it says "too often" this
     * waits as long as it asks and sends what's wanted by then.
     */
    static void wear(final String id, final Mine.Said then) {
        mineWorn = id;
        wanted = id;
        ownUid = Account.id();
        ownChoice = id;
        ownUntil = android.os.SystemClock.elapsedRealtime() + 5 * 60 * 1000L;
        refresh();
        if (saving) return;
        saving = true;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> send(then), 800);
    }

    private static void send(final Mine.Said then) {
        final String uid = Account.id();
        final String token = Mine.token();
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        if (uid == null || token.length() == 0) {
            saving = false;
            if (then != null) then.said(false, Mine.PROVE);
            return;
        }
        final String id = wanted;
        Net.away("frames: wear", () -> {
            boolean ok = false;
            String trouble = "";
            try {
                Net.Said said = Net.talk(Badges.SERVER + "/frame",
                        new JSONObject().put("uid", uid).put("token", token).put("frame", id).toString());
                if (said.code == 429) {
                    int wait = new JSONObject(said.body).optInt("wait", 5);
                    main.postDelayed(() -> send(then), (wait + 1) * 1000L);
                    return;
                }
                if (said.ok()) {
                    ok = true;
                    if (id.equals(wanted)) { readMine(new JSONObject(said.body)); Badges.refresh(); }
                } else if (Mine.asksForProof(said)) {
                    trouble = Mine.PROVE;
                }
            } catch (Throwable error) {
                trouble = String.valueOf(error);
                Diary.note("frames: wear -- " + error);
            }
            final boolean done = ok;
            final String why = trouble;
            main.post(() -> {
                // changed its mind while that was on the way
                if (done && !id.equals(wanted)) { send(then); return; }
                saving = false;
                if (then != null) then.said(done, why);
            });
        });
    }

    // ------------------------------------------------------ whose picture this is

    /** An account's avatar address was read. */
    static void saw(String uid, UrlModel url) {
        try {
            if (uid == null || url == null) return;
            // your own pictures are kept whatever you wear: you may put a frame on in a moment
            if (of(uid) == null && !uid.equals(Account.id())) return;
            List list = url.getUrlList();
            if (list == null) return;
            if (pictures.size() > MOST_PICTURES) pictures.clear();
            for (Object one : list) {
                String key = key(one instanceof String ? (String) one : null);
                if (key != null) pictures.put(key, uid);
            }
        } catch (Throwable ignored) {}
    }

    /** The same for a model with no type of ours: a contact in the messages. */
    public static UrlModel contact(Object model) {
        Object url = NativeRead.get(model, "getAvatarThumb");
        UrlModel found = url instanceof UrlModel ? (UrlModel) url : null;
        if (found != null && !frames.isEmpty()) saw(NativeRead.string(NativeRead.get(model, "getUid")), found);
        return found;
    }

    /** The part of an address that names the picture: what's between the last slash and the size. */
    private static String key(String url) {
        if (url == null) return null;
        int end = url.indexOf('?');
        if (end < 0) end = url.length();
        int start = url.lastIndexOf('/', end - 1) + 1;
        for (int i = start; i < end; i++) {
            char c = url.charAt(i);
            if (c == '~' || c == '.') { end = i; break; }
        }
        if (end - start < 8) return null;
        String key = url.substring(start, end);
        return key.equals(NOBODY) ? null : key;
    }

    // ----------------------------------------------------------- where it's drawn

    private static Field requestView, requestImage, requestUrls, urlsList, requestUri;
    private static boolean fieldsTried;

    private static void fields(Object request) {
        if (fieldsTried) return;
        fieldsTried = true;
        try {
            Class<?> type = request.getClass();
            // 47.2.41: X.1FSL, which is what FrescoImageLoaderImpl.display is given
            requestView = type.getField("LJJIJ");
            requestImage = type.getField("LJJIJIIJI");
            requestUrls = type.getField("LJJJI");
            requestUri = type.getField("LIZ");
        } catch (Throwable error) {
            Diary.note("frames: request: " + error);
        }
    }

    /** The image loader is about to show a picture in a view. Runs for every picture: mostly a look-up. */
    public static void display(Object request) {
        try {
            if (frames.isEmpty()) return;
            fields(request);
            if (requestView == null) return;
            Object target = requestView.get(request);
            if (!(target instanceof View) && requestImage != null) target = requestImage.get(request);
            if (!(target instanceof View)) return;
            final View view = (View) target;
            Object tag = view.getTag(TAG);
            // a frame pinned by the profile page isn't the loader's to take off
            if (tag instanceof Hung && ((Hung) tag).pinned) return;
            final String uid = pictures.isEmpty() ? null : find(request);
            final Frame frame = of(uid);
            if (frame == null && tag == null) return;
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) show(view, uid, frame, false);
            else view.post(() -> show(view, uid, frame, false));
        } catch (Throwable error) {
            Diary.note("frames: display: " + error);
        }
    }

    private static String find(Object request) throws Exception {
        Object urls = requestUrls == null ? null : requestUrls.get(request);
        if (urls != null) {
            if (urlsList == null) urlsList = urls.getClass().getField("LIZ");
            Object list = urlsList.get(urls);
            if (list instanceof List) for (Object one : (List) list) {
                String key = key(one instanceof String ? (String) one : null);
                String uid = key == null ? null : pictures.get(key);
                if (uid != null) return uid;
            }
        }
        Object uri = requestUri == null ? null : requestUri.get(request);
        String key = uri == null ? null : key(uri.toString());
        return key == null ? null : pictures.get(key);
    }

    // ---------------------------------------------------------------- on a profile

    private static final Map<View, String> profiles = new java.util.WeakHashMap<>();
    private static java.lang.reflect.Method[] road;

    /**
     * A profile was bound or laid out: the frame goes on the box its avatar is in, which isn't clipped to
     * the avatar's shape. The profile page knows whose it is, so it needs no look-up by picture.
     */
    static void onProfile(View anchor, Object component) {
        try {
            if (component != null) {
                String uid = uid(component);
                if (uid != null) profiles.put(anchor, uid);
            }
            String uid = profiles.get(anchor);
            if (uid == null) return;
            View box = ProfileM3.avatarBox(anchor);
            if (box == null) return;
            Frame frame = of(uid);
            Object tag = box.getTag(TAG);
            if (frame == null && tag == null) return;
            if (tag instanceof Hung && ((Hung) tag).frame == frame) { place(box); return; }
            show(box, uid, frame, frame != null);
        } catch (Throwable error) {
            Diary.note("frames: profile: " + error);
        }
    }

    /** The account whose profile this component draws: its view model, and the user in that. */
    private static String uid(Object component) {
        try {
            if (road == null) {
                java.lang.reflect.Method model = returning(component.getClass(),
                        "com.ss.android.ugc.profile.platform.base.viewmodel.ProfilePlatformViewModel");
                java.lang.reflect.Method user = model == null ? null
                        : returning(model.getReturnType(), "com.ss.android.ugc.aweme.profile.model.User");
                road = new java.lang.reflect.Method[]{model, user};
            }
            if (road[0] == null || road[1] == null) return null;
            Object vm = road[0].invoke(component);
            Object user = vm == null ? null : road[1].invoke(vm);
            Object uid = user == null ? null : user.getClass().getMethod("getUid").invoke(user);
            return uid instanceof String && ((String) uid).length() > 0 ? (String) uid : null;
        } catch (Throwable error) {
            return null;
        }
    }

    private static java.lang.reflect.Method returning(Class<?> type, String name) {
        for (Class<?> step = type; step != null && step != Object.class; step = step.getSuperclass()) {
            for (java.lang.reflect.Method method : step.getDeclaredMethods()) {
                if (method.getParameterTypes().length != 0 || !method.getReturnType().getName().equals(name)) continue;
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    /**
     * How far the frame on this view reaches above it, in pixels: what the layout has to leave free
     * over the avatar so the crown isn't cut off by whatever lies above. 0 with no frame, or while its
     * picture is still on the way.
     */
    static int headroom(View view) {
        try {
            Object tag = view.getTag(TAG);
            if (!(tag instanceof Hung)) return 0;
            Hung hung = (Hung) tag;
            if (hung.frame == null) return 0;
            Bitmap art = picture(view.getContext(), hung.frame.picture());
            int width = view.getWidth(), height = view.getHeight();
            if (art == null || width == 0 || height == 0) return 0;
            float side = Math.min(width, height);
            float size = side * hung.frame.scale;
            float centre = height / 2f + side * hung.frame.dy;
            float top = centre - size / 2f + covers(hung.frame, art)[1] * size;
            return top < 0 ? Math.round(-top) : 0;
        } catch (Throwable error) {
            return 0;
        }
    }

    /** What's hanging on a view. */
    private static final class Hung extends Drawable {
        final View view;
        String uid;
        Frame frame;
        boolean pinned;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect from = new Rect();
        private int tries;

        Hung(View view) { this.view = view; }

        @Override public void draw(Canvas canvas) {
            if (frame == null) return;
            Bitmap art = picture(view.getContext(), frame.picture());
            if (art == null) {
                // on its way: it's a small file, and this is the only view waiting for it
                if (tries++ < 20) view.postDelayed(view::invalidate, 500);
                return;
            }
            from.set(0, 0, art.getWidth(), art.getHeight());
            canvas.drawBitmap(art, from, getBounds(), paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static void show(View view, String uid, Frame frame, boolean pinned) {
        Object tag = view.getTag(TAG);
        Hung hung = tag instanceof Hung ? (Hung) tag : null;
        if (frame == null) {
            if (hung != null) {
                view.getOverlay().remove(hung);
                view.setTag(TAG, null);
                hanging.remove(view);
            }
            return;
        }
        if (hung == null) {
            hung = new Hung(view);
            view.setTag(TAG, hung);
            view.getOverlay().add(hung);
            hanging.add(view);
        }
        hung.uid = uid;
        hung.frame = frame;
        hung.pinned = pinned;
        hung.tries = 0;
        if (view.getTag(WATCHED) == null) {
            view.setTag(WATCHED, Boolean.TRUE);
            view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place(v));
        }
        place(view);
    }

    /** Sized from the avatar, whatever size it has here: a thumbnail in a comment, or the big one on a profile. */
    private static void place(View view) {
        Object tag = view.getTag(TAG);
        if (!(tag instanceof Hung)) return;
        Hung hung = (Hung) tag;
        int width = view.getWidth(), height = view.getHeight();
        if (width == 0 || height == 0 || hung.frame == null) return;
        float density = view.getResources().getDisplayMetrics().density;
        int side = Math.min(width, height);
        // on a picture filling the screen a frame would only be in the way
        if (side > 240 * density || side < 16 * density) { hung.setBounds(0, 0, 0, 0); return; }
        int size = Math.round(side * hung.frame.scale);
        int x = width / 2, y = height / 2 + Math.round(side * hung.frame.dy);
        hung.setBounds(x - size / 2, y - size / 2, x + size / 2, y + size / 2);
        unclip(view, hung.getBounds(), hung.pinned);
        view.invalidate();
    }

    /** The parents let it stick out, only as far up as it takes. A list stays as it is: its rows don't overlap. */
    private static void unclip(View view, Rect wanted, boolean pinned) {
        // getX and getY, not getLeft and getTop: the profile's layout moves its avatar by translation,
        // and without that this thought the frame fit in a parent that was cutting it off
        float left = view.getX() + wanted.left, top = view.getY() + wanted.top;
        float right = view.getX() + wanted.right, bottom = view.getY() + wanted.bottom;
        View at = view;
        for (int depth = 0; depth < 8 && at.getParent() instanceof ViewGroup; depth++) {
            ViewGroup parent = (ViewGroup) at.getParent();
            if (isList(parent)) {
                // a crown or a cat rises out of its row, over the row above. The list's own parent still
                // cuts everything at the list's edge, so nothing spills off it when it scrolls
                if (!parent.getClass().getName().contains("ViewPager")) parent.setClipChildren(false);
                return;
            }
            parent.setClipChildren(false);
            parent.setClipToPadding(false);
            if (pinned) parent.setClipToOutline(false);
            if (left >= 0 && top >= 0 && right <= parent.getWidth() && bottom <= parent.getHeight()) return;
            left += parent.getX(); right += parent.getX();
            top += parent.getY(); bottom += parent.getY();
            at = parent;
        }
    }

    private static boolean isList(View view) {
        for (Class<?> type = view.getClass(); type != null && type != View.class; type = type.getSuperclass()) {
            String name = type.getName();
            if (name.equals("androidx.recyclerview.widget.RecyclerView") || name.equals("androidx.viewpager.widget.ViewPager")) return true;
        }
        return false;
    }

    // --------------------------------------------------------------- their pictures

    private static final Map<String, Bitmap> art = new HashMap<>();
    private static final Map<String, Long> asked = new HashMap<>();

    /** The frame's picture, untouched: unlike a badge's it keeps its margin, the size depends on it. */
    static Bitmap picture(final Context context, final String path) {
        if (path == null || path.length() == 0) return null;
        long now = android.os.SystemClock.elapsedRealtime();
        synchronized (art) {
            Bitmap known = art.get(path);
            if (known != null) return known;
            Long when = asked.get(path);
            if (when != null && now - when < 60000) return null;
            asked.put(path, now);
        }
        final File cache = new File(context.getFilesDir(), "margyt/frames/" + path.replaceAll("[^A-Za-z0-9.]", "_"));
        Net.away("frame picture", () -> {
            byte[] raw = Net.read(cache);
            if (raw == null || remember(path, raw) == null) {
                raw = Net.bytes(Badges.SERVER + "/icon/" + path);
                if (raw != null && remember(path, raw) != null) Net.save(cache, raw);
            }
        });
        return null;
    }

    private static Bitmap remember(String path, byte[] raw) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
        if (bitmap != null) synchronized (art) { art.put(path, bitmap); }
        return bitmap;
    }

    /** Cut to where the picture really is: a crown or a cat is a small part of its square. */
    private static Bitmap tight(Bitmap art) {
        Rect solid = solid(art);
        return solid == null ? art : Bitmap.createBitmap(art, solid.left, solid.top, solid.width(), solid.height());
    }

    private static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    private static volatile Bitmap face;
    private static boolean faceAsked;

    /** Your own avatar, for the settings: fetched once, and the views asking draw again when it comes. */
    private static Bitmap face(final View waiting) {
        Bitmap known = face;
        if (known != null) return known;
        if (!faceAsked) {
            faceAsked = true;
            final String address = Account.profileAvatar();
            if (address != null && address.length() > 0) Net.away("own face", () -> {
                byte[] raw = Net.bytes(address);
                Bitmap made = raw == null ? null : BitmapFactory.decodeByteArray(raw, 0, raw.length);
                if (made != null) { face = made; waiting.post(waiting::invalidate); }
                else faceAsked = false;
            });
        }
        return null;
    }

    private static final Map<String, float[]> covered = new HashMap<>();

    /** The part of a frame's picture that isn't see-through, as fractions: left, top, right, bottom. */
    private static float[] covers(Frame frame, Bitmap art) {
        float[] known = covered.get(frame.picture());
        if (known != null) return known;
        Rect solid = solid(art);
        float[] out = solid == null ? new float[] {0, 0, 1, 1} : new float[] {
                solid.left / (float) art.getWidth(), solid.top / (float) art.getHeight(),
                solid.right / (float) art.getWidth(), solid.bottom / (float) art.getHeight()};
        covered.put(frame.picture(), out);
        return out;
    }

    /** Where the picture really is. The soft shadow round it doesn't count. */
    private static Rect solid(Bitmap art) {
        int width = art.getWidth(), height = art.getHeight();
        int[] pixels = new int[width * height];
        art.getPixels(pixels, 0, width, 0, 0, width, height);
        int l = width, t = height, r = -1, b = -1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if ((pixels[y * width + x] >>> 24) > 180) {
                if (x < l) l = x;
                if (x > r) r = x;
                if (y < t) t = y;
                if (y > b) b = y;
            }
        }
        return r < l ? null : new Rect(l, t, r + 1, b + 1);
    }

    /** You with a frame on, as big as fits: whatever shape the frame is, all of it is in the view. */
    static final class Preview extends View {
        private final Paint plain = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint round = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint empty = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Matrix place = new android.graphics.Matrix();
        private final Rect to = new Rect();
        private Bitmap shaded;
        private Frame frame;

        Preview(Context context, int ground) {
            super(context);
            empty.setColor(ground);
        }

        void set(Frame frame) {
            this.frame = frame;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            Bitmap art = frame == null ? null : picture(getContext(), frame.picture());
            if (frame != null && art == null) postDelayed(this::invalidate, 400);
            // everything in units of the avatar's diameter, with its centre at nought
            float x0 = -0.5f, y0 = -0.5f, x1 = 0.5f, y1 = 0.5f;
            if (art != null) {
                float[] c = covers(frame, art);
                x0 = Math.min(x0, (c[0] - 0.5f) * frame.scale);
                x1 = Math.max(x1, (c[2] - 0.5f) * frame.scale);
                y0 = Math.min(y0, (c[1] - 0.5f) * frame.scale + frame.dy);
                y1 = Math.max(y1, (c[3] - 0.5f) * frame.scale + frame.dy);
            }
            float unit = Math.min(w / (x1 - x0), h / (y1 - y0)) * 0.94f;
            // no frame on: the bare avatar shouldn't fill the whole card
            if (art == null) unit *= 0.62f;
            float cx = w / 2 - (x0 + x1) / 2 * unit, cy = h / 2 - (y0 + y1) / 2 * unit;
            float radius = unit / 2;

            Bitmap own = face(this);
            if (own != null) {
                if (shaded != own) {
                    shaded = own;
                    round.setShader(new android.graphics.BitmapShader(own, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
                }
                float scale = unit / Math.min(own.getWidth(), own.getHeight());
                place.setScale(scale, scale);
                place.postTranslate(cx - own.getWidth() * scale / 2, cy - own.getHeight() * scale / 2);
                round.getShader().setLocalMatrix(place);
                canvas.drawCircle(cx, cy, radius, round);
            } else {
                canvas.drawCircle(cx, cy, radius, empty);
                Bitmap note = Badges.note();
                if (note != null) {
                    int glyph = Math.round(radius * 0.8f);
                    to.set(Math.round(cx) - glyph, Math.round(cy) - glyph, Math.round(cx) + glyph, Math.round(cy) + glyph);
                    canvas.drawBitmap(note, null, to, plain);
                }
            }
            if (art == null) return;
            int side = Math.round(unit * frame.scale);
            int fx = Math.round(cx), fy = Math.round(cy + unit * frame.dy);
            to.set(fx - side / 2, fy - side / 2, fx + side / 2, fy + side / 2);
            canvas.drawBitmap(art, null, to, plain);
        }
    }
}
