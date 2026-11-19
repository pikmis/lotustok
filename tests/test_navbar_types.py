import pathlib, shutil, subprocess, tempfile, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

HARNESS = """package cat.narezany.margyt;

public class NavBarTestHarness {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        // 1. Verify style constants
        check(NavBar.STYLE_FLOATING_FAB == 0, "STYLE_FLOATING_FAB must be 0");
        check(NavBar.STYLE_M3_BAR == 1, "STYLE_M3_BAR must be 1");
        check(NavBar.CAPSULE == 0, "CAPSULE alias must be 0");
        check(NavBar.FLOATING == 1, "FLOATING alias must be 1");
        check(NavBar.KEY_STYLE.equals("nav_style"), "KEY_STYLE key name");

        // 2. Verify setStyle and style persistence
        NavBar.setStyle(NavBar.STYLE_M3_BAR);
        check(NavBar.style() == NavBar.STYLE_M3_BAR, "style() returns STYLE_M3_BAR after setStyle(1)");
        NavBar.setStyle(NavBar.STYLE_FLOATING_FAB);
        check(NavBar.style() == NavBar.STYLE_FLOATING_FAB, "style() returns STYLE_FLOATING_FAB after setStyle(0)");

        // 3. Verify color math helpers
        int ground = 0xFF121212;
        int ink = 0xFFFFFFFF;
        int mixed = NavBar.mix(ground, ink, 0.05f);
        check(mixed != 0, "mix produces valid color");
        float c = NavBar.contrast(0xFFFFFFFF, 0xFF000000);
        check(c > 15f, "contrast of black/white is high");

        System.out.println("NavBarTestHarness passed successfully.");
    }
}
"""

class NavBarTypesTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac"), "JDK required")
    def test_navbar_styles_and_harness(self):
        with tempfile.TemporaryDirectory() as folder:
            room = pathlib.Path(folder)
            (room / "NavBarTestHarness.java").write_text(HARNESS, encoding="utf-8")
            android_jar = ROOT / "tools/android.jar"
            self.assertTrue(android_jar.exists(), "tools/android.jar must exist")
            
            # Compile all inject java files and harness
            java_files = list((ROOT / "inject/java").rglob("*.java")) + list((ROOT / "inject/stubs").rglob("*.java"))
            cmd = [
                "javac", "-encoding", "UTF-8",
                "-cp", f"{android_jar}:{ROOT}/inject/stubs",
                "-d", folder,
                str(room / "NavBarTestHarness.java"),
                *[str(p) for p in java_files]
            ]
            built = subprocess.run(cmd, capture_output=True, text=True)
            self.assertEqual(0, built.returncode, f"Compile failed:\\n{built.stderr}")

            # Run test harness
            ran = subprocess.run(
                ["java", "-cp", f"{folder}:{android_jar}", "cat.narezany.margyt.NavBarTestHarness"],
                capture_output=True, text=True
            )
            self.assertEqual(0, ran.returncode, f"Runtime harness failed:\\n{ran.stderr}")
            self.assertIn("NavBarTestHarness passed successfully.", ran.stdout)

    def test_settings_activity_nav_options(self):
        settings_source = (ROOT / "inject/java/cat/narezany/margyt/SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn('Local.of("Плавающая капсула с кнопкой")', settings_source)
        self.assertIn('Local.of("Панель Material 3 с пилюлей")', settings_source)
        self.assertIn('NavBar.setStyle(which)', settings_source)

    def test_navbar_layout_modes(self):
        navbar_source = (ROOT / "inject/java/cat/narezany/margyt/NavBar.java").read_text(encoding="utf-8")
        # Verify both arrange and draw branches exist
        self.assertIn('arrangeFloatingFab', navbar_source)
        self.assertIn('arrangeM3Bar', navbar_source)
        self.assertIn('drawFloatingFab', navbar_source)
        self.assertIn('drawM3Bar', navbar_source)
        # Verify circular indicator for Type A
        self.assertIn('canvas.drawCircle(centre, middle, r, fill)', navbar_source)
        # Verify horizontal pill indicator for Type B
        self.assertIn('canvas.drawRoundRect(box, 16f * unit, 16f * unit, fill)', navbar_source)
        # Verify detached FAB for Type A
        self.assertIn('fabCorner', navbar_source)


if __name__ == "__main__":
    unittest.main()
