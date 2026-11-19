"""
Tests for Material 3 Seekbar, WavyBar, and video player progress styling in LotusTok.
Verifies:
1. Thin, sleek Material 3 progress bar (2dp height track with clean thumb dot, like in MoltenGram's player).
2. Elimination of bottom navigation bar piercing: WavyBar and Seekbar sit neatly above the bottom bar.
3. Scrubber controls containment: progress bar contained strictly between [ ✕ ] and [ ▶ | 1x ].
4. Scrubber controls styling: compact, non-bulky, 100% opaque matte dark surface with subtle 1px M3 outline.
5. Seekbar safety hooks and in-place translation adjustments.
6. Full Java compilation and runtime harness.
"""

import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent

JAVA_HARNESS = """
package cat.narezany.margyt;

public class SeekbarAndWavyProgressHarness {
    private static void check(boolean condition, String message) {
        if (!condition) {
            System.err.println("FAILED: " + message);
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        System.out.println("Starting SeekbarAndWavyProgressHarness...");

        // 1. Verify Seekbar configuration constants and keys
        check("seekbar_always".equals(Seekbar.KEY), "Seekbar.KEY must be 'seekbar_always'");

        // 2. Verify Seekbar enable/disable state handling
        Seekbar.setEnabled(false);
        check(!Seekbar.isEnabled(), "Seekbar should be disabled initially");
        Seekbar.setEnabled(true);
        check(Seekbar.isEnabled(), "Seekbar should be enabled after setEnabled(true)");
        Seekbar.setEnabled(false);

        // 3. Verify MD3Progress key
        check("md3_progress".equals(MD3Progress.KEY), "MD3Progress.KEY must be 'md3_progress'");

        System.out.println("SeekbarAndWavyProgressHarness PASSED");
    }
}
"""


class SeekbarAndWavyProgressTests(unittest.TestCase):

    def test_wavy_bar_thin_m3_track_specs(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/WavyBar.java").read_text(encoding="utf-8")
        # 2dp height track, like in MoltenGram's player
        self.assertIn("float line = dp(2);", source)
        self.assertIn("float line = dp(2f);", source)
        self.assertIn("played.setStrokeCap(Paint.Cap.ROUND);", source)
        self.assertIn("rest.setStrokeCap(Paint.Cap.ROUND);", source)
        # Clean thumb dot & stop dot
        self.assertIn("thumbRadius = dp(4.5f);", source)
        self.assertIn("canvas.drawCircle(x, mid, thumbRadius, thumbPaint);", source)
        self.assertIn("canvas.drawCircle(right, mid, dp(1.5f), rest);", source)

    def test_wavy_bar_navbar_avoidance(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/WavyBar.java").read_text(encoding="utf-8")
        # Finds navigation bar and clamps mid above it
        self.assertIn("findNavBar(getRootView())", source)
        self.assertIn("navBar.getLocationOnScreen(navAt);", source)
        self.assertIn("mid = navTop - mine[1] - dp(3f);", source)
        self.assertIn("canvas.clipRect(0, 0, getWidth(), clipBottom);", source)

    def test_wavy_bar_scrubber_controls_containment(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/WavyBar.java").read_text(encoding="utf-8")
        # Locates scrubber controls
        self.assertIn("findScrubberControls", source)
        self.assertIn("dressScrubberControls", source)
        # Contained between close button and controls
        self.assertIn("left = Math.max(dp(16), (closeLoc[0] - myLoc[0]) + controls[0].getWidth() + dp(12));", source)
        self.assertIn("right = Math.min(getWidth() - dp(16), (ctrlLoc[0] - myLoc[0]) - dp(12));", source)

    def test_scrubber_controls_opaque_and_compact(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/WavyBar.java").read_text(encoding="utf-8")
        # 100% opaque matte dark surface container (0xFF161823) with subtle 1px M3 outline (0x1FFFFFFF)
        self.assertIn("disc.setColor(0xFF161823);", source)
        self.assertIn("disc.setStroke(Math.max(1, Math.round(1f * density)), 0x1FFFFFFF);", source)
        self.assertIn("pill.setColor(0xFF161823);", source)
        self.assertIn("pill.setStroke(Math.max(1, Math.round(1f * density)), 0x1FFFFFFF);", source)
        self.assertIn("pill.setCornerRadius(18f * density);", source)

    def test_seekbar_safety_and_adjust_bar(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/Seekbar.java").read_text(encoding="utf-8")
        # Attaches safety hook on seekbar views
        self.assertIn("attachSafety(view);", source)
        self.assertIn("adjustBar(barView);", source)
        self.assertIn("navBar.getLocationOnScreen(navLoc);", source)
        self.assertIn("barView.setTranslationY(barView.getTranslationY() + delta);", source)
        self.assertIn("WavyBar.dressScrubberControls", source)

    def test_md3_progress_feed_and_scrub_separation(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/MD3Progress.java").read_text(encoding="utf-8")
        # In feed (!under(view, TAB_HOST)), applyOver & applyScrub are gated, attachSafety is hooked
        self.assertIn("if (!under(view, TAB_HOST)) {", source)
        self.assertIn("Seekbar.attachSafety(view);", source)
        self.assertIn("Seekbar.attachSafety(bar);", source)

    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK required")
    def test_java_runtime_harness(self):
        android_jar = ROOT / "tools/android.jar"
        self.assertTrue(android_jar.exists(), f"tools/android.jar missing at {android_jar}")

        with tempfile.TemporaryDirectory() as temp_dir:
            room = pathlib.Path(temp_dir)
            harness_file = room / "SeekbarAndWavyProgressHarness.java"
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
                "cat.narezany.margyt.SeekbarAndWavyProgressHarness"
            ]
            run = subprocess.run(cmd_run, capture_output=True, text=True)
            self.assertEqual(0, run.returncode, f"Java runtime harness failed:\n{run.stderr}")
            self.assertIn("SeekbarAndWavyProgressHarness PASSED", run.stdout)


if __name__ == "__main__":
    unittest.main()
