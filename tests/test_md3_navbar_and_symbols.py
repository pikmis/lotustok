import os
import pathlib
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

JAVA_HARNESS = """package cat.narezany.margyt;

public class Md3NavBarAndSymbolsHarness {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAIL: " + what);
    }

    public static void main(String[] args) {
        // 1. Verify NavBar bottom bar styles and constants
        check(NavBar.STYLE_FLOATING_FAB == 0, "STYLE_FLOATING_FAB must be 0");
        check(NavBar.STYLE_M3_BAR == 1, "STYLE_M3_BAR must be 1");
        check("nav_style".equals(NavBar.KEY_STYLE), "KEY_STYLE must be 'nav_style'");

        // 2. Verify NavBar style get/set and persistence
        NavBar.setStyle(NavBar.STYLE_FLOATING_FAB);
        check(NavBar.style() == NavBar.STYLE_FLOATING_FAB, "style() must be STYLE_FLOATING_FAB");
        NavBar.setStyle(NavBar.STYLE_M3_BAR);
        check(NavBar.style() == NavBar.STYLE_M3_BAR, "style() must be STYLE_M3_BAR");
        NavBar.setStyle(NavBar.STYLE_FLOATING_FAB);

        // 3. Verify color blending and contrast utilities
        int white = 0xFFFFFFFF;
        int black = 0xFF000000;
        int mixed = NavBar.mix(black, white, 0.5f);
        check(mixed != 0, "NavBar.mix produces valid color");
        float contrast = NavBar.contrast(white, black);
        check(contrast > 15.0f, "contrast of white and black should be > 15");

        // 4. Verify Material Symbols 960x960 vectors
        check(MaterialSymbols.LIKE_ON != null && MaterialSymbols.LIKE_ON.startsWith("M"), "LIKE_ON vector");
        check(MaterialSymbols.LIKE_OFF != null && MaterialSymbols.LIKE_OFF.startsWith("M"), "LIKE_OFF vector");
        check(MaterialSymbols.COMMENT_ON != null && MaterialSymbols.COMMENT_ON.startsWith("M"), "COMMENT_ON vector");
        check(MaterialSymbols.COMMENT_OFF != null && MaterialSymbols.COMMENT_OFF.startsWith("M"), "COMMENT_OFF vector");
        check(MaterialSymbols.FAVORITE_ON != null && MaterialSymbols.FAVORITE_ON.startsWith("m"), "FAVORITE_ON vector");
        check(MaterialSymbols.FAVORITE_OFF != null && MaterialSymbols.FAVORITE_OFF.startsWith("m"), "FAVORITE_OFF vector");
        check(MaterialSymbols.SHARE_ARROW != null && MaterialSymbols.SHARE_ARROW.startsWith("m"), "SHARE_ARROW vector");
        check(MaterialSymbols.MUSIC_NOTE != null && MaterialSymbols.MUSIC_NOTE.startsWith("M"), "MUSIC_NOTE vector");
        check(MaterialSymbols.MUSIC_DISC != null && MaterialSymbols.MUSIC_DISC.startsWith("M"), "MUSIC_DISC vector");
        check(MaterialSymbols.FOLLOW_PLUS != null && MaterialSymbols.FOLLOW_PLUS.startsWith("M"), "FOLLOW_PLUS vector");
        check(MaterialSymbols.PERSON_ADD != null && MaterialSymbols.PERSON_ADD.startsWith("M"), "PERSON_ADD vector");
        check(MaterialSymbols.HOME_ON != null && MaterialSymbols.HOME_ON.startsWith("M"), "HOME_ON vector");
        check(MaterialSymbols.HOME_OFF != null && MaterialSymbols.HOME_OFF.startsWith("M"), "HOME_OFF vector");
        check(MaterialSymbols.FRIENDS_ON != null && MaterialSymbols.FRIENDS_ON.startsWith("M"), "FRIENDS_ON vector");
        check(MaterialSymbols.FRIENDS_OFF != null && MaterialSymbols.FRIENDS_OFF.startsWith("M"), "FRIENDS_OFF vector");
        check(MaterialSymbols.CREATE_ON != null && MaterialSymbols.CREATE_ON.startsWith("M"), "CREATE_ON vector");
        check(MaterialSymbols.INBOX_ON != null && MaterialSymbols.INBOX_ON.startsWith("M"), "INBOX_ON vector");
        check(MaterialSymbols.PROFILE_ON != null && MaterialSymbols.PROFILE_ON.startsWith("M"), "PROFILE_ON vector");

        // 5. Verify MaterialSymbols vectors trace properly via NavPlan
        final StringBuilder said = new StringBuilder();
        NavPlan.Pen pen = new NavPlan.Pen() {
            public void move(float x, float y) { said.append('M'); }
            public void line(float x, float y) { said.append('L'); }
            public void curve(float a, float b, float c, float d, float x, float y) { said.append('C'); }
            public void close() { said.append('Z'); }
        };
        NavPlan.trace(MaterialSymbols.LIKE_ON, pen);
        check(said.length() > 0 && said.charAt(0) == 'M', "LIKE_ON traces valid SVG commands");
        said.setLength(0);
        NavPlan.trace(MaterialSymbols.SHARE_ARROW, pen);
        check(said.length() > 0 && said.charAt(0) == 'M', "SHARE_ARROW traces valid SVG commands");

        // 6. Verify FeedRail native state operations (0: like, 1: comment, 2: fav, 3: share)
        check(FeedRail.shown(0), "FeedRail.shown(0) default true");

        // 7. Verify Md3 keys and constants
        check("md3_feed".equals(Md3.KEY_FEED), "Md3.KEY_FEED");
        check("md3_icons".equals(Md3.KEY_ICONS), "Md3.KEY_ICONS");

        System.out.println("Md3NavBarAndSymbolsHarness PASSED");
    }
}
"""


class Md3NavBarAndSymbolsTests(unittest.TestCase):
    """
    Dedicated test suite covering:
    1. The two MD3 bottom bar types in NavBar.java (Type A: floating capsule + separate FAB; Type B: full-width bar with M3 horizontal pill indicator).
    2. Material Symbols Rounded vector integration in FeedRail.java, NavBar.java, and MaterialSymbols.java.
    3. Elimination of heavy per-frame traversal in Md3.java onPreDraw.
    4. Settings in-place toggle update (no full removeAllViews on single toggle).
    """

    # --------------------------------------------------------------------------
    # 1. MD3 Bottom Bar Types in NavBar.java & Settings
    # --------------------------------------------------------------------------

    def test_navbar_type_constants_and_style_definitions(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/NavBar.java").read_text(encoding="utf-8")
        self.assertIn("STYLE_FLOATING_FAB = 0", source)
        self.assertIn("STYLE_M3_BAR = 1", source)
        self.assertIn('KEY_STYLE = "nav_style"', source)
        self.assertIn("static int style()", source)
        self.assertIn("static void setStyle(int style)", source)

    def test_navbar_type_a_floating_capsule_and_detached_fab(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/NavBar.java").read_text(encoding="utf-8")
        # Layout arrangement method for Type A
        self.assertIn("arrangeFloatingFab", source)
        # Separate detached FAB for Create tab with corner radius
        self.assertIn("fabCorner", source)
        self.assertIn("create(canvas, fab, primary, tab.pressed, fab.width() * 0.18f, fabCorner)", source)
        # Circular indicator for active destination tab
        self.assertIn("canvas.drawCircle(centre, middle, r, fill)", source)
        # Drawing method for Type A
        self.assertIn("drawFloatingFab", source)

    def test_navbar_type_b_m3_fullwidth_bar_and_horizontal_pill(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/NavBar.java").read_text(encoding="utf-8")
        # Layout arrangement method for Type B
        self.assertIn("arrangeM3Bar", source)
        # Drawing method for Type B
        self.assertIn("drawM3Bar", source)
        # M3 horizontal pill indicator (rounded rectangle)
        self.assertIn("canvas.drawRoundRect(box, 16f * unit, 16f * unit, fill)", source)
        # Standard pill width (64dp) and pill height (32dp)
        self.assertIn("64f * unit", source)
        self.assertIn("32f * unit", source)

    def test_settings_activity_nav_bar_type_selection(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn('Local.of("Плавающая капсула с кнопкой")', source)
        self.assertIn('Local.of("Панель Material 3 с пилюлей")', source)
        self.assertIn("NavBar.setStyle(which)", source)
        self.assertIn('Local.of("Нижняя панель")', source)

    # --------------------------------------------------------------------------
    # 2. Material Symbols Rounded Vector Integration
    # --------------------------------------------------------------------------

    def test_material_symbols_rounded_vectors_definitions(self):
        ms_source = (ROOT / "inject/java/cat/narezany/margyt/MaterialSymbols.java").read_text(encoding="utf-8")
        # Feed action buttons
        for symbol in ["LIKE_ON", "LIKE_OFF", "COMMENT_ON", "COMMENT_OFF",
                       "FAVORITE_ON", "FAVORITE_OFF", "SHARE_ARROW",
                       "MUSIC_NOTE", "MUSIC_DISC", "FOLLOW_PLUS", "PERSON_ADD"]:
            self.assertIn(f"public static final String {symbol}", ms_source)

        # Nav bar tabs
        for symbol in ["HOME_ON", "HOME_OFF", "FRIENDS_ON", "FRIENDS_OFF",
                       "CREATE_ON", "CREATE_OFF", "INBOX_ON", "INBOX_OFF",
                       "PROFILE_ON", "PROFILE_OFF"]:
            self.assertIn(f"public static final String {symbol}", ms_source)

    def test_md3_feed_action_glyphs_linked_to_material_symbols(self):
        md3_source = (ROOT / "inject/java/cat/narezany/margyt/Md3.java").read_text(encoding="utf-8")
        self.assertIn("private static final String LIKE = MaterialSymbols.LIKE_ON;", md3_source)
        self.assertIn("private static final String COMMENT = MaterialSymbols.COMMENT_ON;", md3_source)
        self.assertIn("private static final String SAVE = MaterialSymbols.FAVORITE_ON;", md3_source)
        self.assertIn("private static final String SHARE = MaterialSymbols.SHARE_ARROW;", md3_source)
        # Scaling to 960 coordinate space
        self.assertIn("canvas.scale(side / 960f, side / 960f);", md3_source)
        self.assertIn("canvas.translate(0f, 960f);", md3_source)
        # MaterialSymbols.path resolution
        self.assertIn("MaterialSymbols.path(", md3_source)

    def test_feed_rail_remains_lean_and_isolated(self):
        fr_source = (ROOT / "inject/java/cat/narezany/margyt/FeedRail.java").read_text(encoding="utf-8")
        # Ensure FeedRail.java does not bring in heavy android.graphics imports that break headless unit tests
        self.assertNotIn("android.graphics.Canvas", fr_source)
        self.assertNotIn("android.graphics.drawable.Drawable", fr_source)
        self.assertIn("public final class FeedRail", fr_source)
        self.assertIn("public static boolean shown(int kind)", fr_source)

    def test_material_symbols_drawable_generation_and_caching(self):
        ms_source = (ROOT / "inject/java/cat/narezany/margyt/MaterialSymbols.java").read_text(encoding="utf-8")
        self.assertIn("public static synchronized Path path(String data)", ms_source)
        self.assertIn("public static Drawable createDrawable(final String data, final int color, final boolean mirrored)", ms_source)
        self.assertIn("NavPlan.trace(data", ms_source)

    # --------------------------------------------------------------------------
    # 3. Elimination of Heavy Per-Frame Traversal in Md3.java onPreDraw
    # --------------------------------------------------------------------------

    def test_md3_onpredraw_early_exit_and_dirty_gating(self):
        md3_source = (ROOT / "inject/java/cat/narezany/margyt/Md3.java").read_text(encoding="utf-8")
        # onPreDraw must check dirty flag and exit immediately when not dirty
        self.assertIn("if (!dirty) return true;", md3_source)
        self.assertIn("dirty = false;", md3_source)
        # Heavy styling passes moved into applyStyles()
        self.assertIn("void applyStyles()", md3_source)
        self.assertIn("dressRows();", md3_source)
        self.assertIn("dressCells();", md3_source)
        self.assertIn("tabs();", md3_source)
        self.assertIn("feedTabs();", md3_source)

    def test_md3_screen_listeners_and_recycling_hooks(self):
        md3_source = (ROOT / "inject/java/cat/narezany/margyt/Md3.java").read_text(encoding="utf-8")
        # Screen implements OnGlobalLayoutListener, OnPreDrawListener, OnScrollChangedListener
        self.assertIn("implements ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnScrollChangedListener", md3_source)
        # Hook recycling on lists and grids
        self.assertIn("private void hookRecycling(ViewGroup group)", md3_source)
        self.assertIn("group.addOnLayoutChangeListener(", md3_source)
        self.assertIn("group.setOnHierarchyChangeListener(", md3_source)
        self.assertIn("onChildViewAdded(View parent, View child) {", md3_source)
        self.assertIn("onChildViewRemoved(View parent, View child) {", md3_source)

    def test_md3_no_heavy_column_card_behind_action_buttons(self):
        md3_source = (ROOT / "inject/java/cat/narezany/margyt/Md3.java").read_text(encoding="utf-8")
        # PART_COLUMN sausage pod is eliminated: buttons float airily without wear(slot, CARD, false) or card.dress
        self.assertNotIn("card.dress(slot, OVER_VIDEO);", md3_source)
        self.assertIn("strip(slot);", md3_source)
        self.assertIn("flatten(slot, false, true);", md3_source)

    def test_md3_no_black_discs_in_inbox_headers(self):
        md3_source = (ROOT / "inject/java/cat/narezany/margyt/Md3.java").read_text(encoding="utf-8")
        # In activity/inbox headers, black patch discs are removed via strip(view)
        self.assertIn("// In activity/inbox headers, do not draw black patch discs", md3_source)
        self.assertIn("strip(view);", md3_source)

    # --------------------------------------------------------------------------
    # 4. Settings In-Place Toggle Updates (No removeAllViews on Single Toggle)
    # --------------------------------------------------------------------------

    def test_settings_activity_in_place_burn_and_dim_toggle(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/SettingsActivity.java").read_text(encoding="utf-8")
        # Dim extras container updated via setVisibility in-place without rebuildAfterSwitchAnimation()
        self.assertIn("burnExtras.setVisibility(on ? View.VISIBLE : View.GONE);", source)
        self.assertNotIn("Dim.setEnabled(on);\n            rebuildAfterSwitchAnimation();", source)

    def test_settings_activity_in_place_message_times_toggle(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/SettingsActivity.java").read_text(encoding="utf-8")
        # messageTimesExtra container updated via setVisibility in-place without rebuildAfterSwitchAnimation()
        self.assertIn("messageTimesExtra.setVisibility(on ? View.VISIBLE : View.GONE);", source)
        self.assertNotIn("MessageTimes.setEnabled(on);\n                    rebuildAfterSwitchAnimation();", source)

    def test_settings_activity_beta_row_target_and_click_behavior(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/SettingsActivity.java").read_text(encoding="utf-8")
        # betaRow registers in settingTargets for search navigation
        self.assertIn("settingTargets.put(title, row);", source)
        # betaRow row click performs toggle.performClick()
        self.assertIn("row.setOnClickListener(v -> {\n            toggle.performClick();\n        });", source)

    def test_settings_feed_md3_live_toggle_restyle(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/SettingsActivity.java").read_text(encoding="utf-8")
        # Live toggle rows for Feed MD3 settings
        self.assertIn('panel.view(liveToggleRow("tune", Local.of("Подложка под описание"), Md3.partOn(Md3.PART_CAPTION), on -> Md3.setPart(Md3.PART_CAPTION, on)));', source)
        self.assertIn('panel.view(liveToggleRow("tune", Local.of("Подложка под кнопками справа"), Md3.partOn(Md3.PART_COLUMN), on -> Md3.setPart(Md3.PART_COLUMN, on)));', source)

    # --------------------------------------------------------------------------
    # 5. Full Java Compilation & Runtime Test Harness
    # --------------------------------------------------------------------------

    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK required")
    def test_java_runtime_harness(self):
        android_jar = ROOT / "tools/android.jar"
        self.assertTrue(android_jar.exists(), f"tools/android.jar missing at {android_jar}")

        with tempfile.TemporaryDirectory() as temp_dir:
            room = pathlib.Path(temp_dir)
            harness_file = room / "Md3NavBarAndSymbolsHarness.java"
            harness_file.write_text(JAVA_HARNESS, encoding="utf-8")

            java_files = (
                list((ROOT / "inject/java").rglob("*.java")) +
                list((ROOT / "inject/stubs").rglob("*.java")) +
                [harness_file]
            )

            cmd_compile = [
                "javac", "-encoding", "UTF-8",
                "-cp", f"{android_jar}:{ROOT}/inject/stubs",
                "-d", temp_dir,
                *[str(p) for p in java_files]
            ]
            comp = subprocess.run(cmd_compile, capture_output=True, text=True)
            self.assertEqual(0, comp.returncode, f"Java compilation failed:\n{comp.stderr}")

            cmd_run = [
                "java",
                "-cp", f"{temp_dir}:{android_jar}",
                "cat.narezany.margyt.Md3NavBarAndSymbolsHarness"
            ]
            run = subprocess.run(cmd_run, capture_output=True, text=True)
            self.assertEqual(0, run.returncode, f"Java runtime harness failed:\n{run.stderr}")
            self.assertIn("Md3NavBarAndSymbolsHarness PASSED", run.stdout)


if __name__ == "__main__":
    unittest.main()
