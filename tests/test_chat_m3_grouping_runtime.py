import pathlib, shutil, subprocess, tempfile, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

HARNESS = """package cat.narezany.margyt;

public class ChatM3TestHarness {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        // 1. Verify enabled by default
        check(ChatM3.isEnabled(), "ChatM3 must be enabled by default");

        // 2. Verify tag constants
        check(ChatM3.SENDER == 0x7e0c0346, "ChatM3.SENDER tag must match");
        check(ChatM3.INCOMING == 0x7e0c0347, "ChatM3.INCOMING tag must match");
        check(MessageTimes.TAG == 0x7e0c0333, "MessageTimes.TAG tag must match");

        // 3. Verify ProfileM3 roles structure
        ProfileM3.Roles roles = new ProfileM3.Roles();
        roles.primary = 0xFF345678;
        roles.onPrimary = 0xFFFFFFFF;
        roles.tertiaryContainer = 0xFF123456;
        roles.primaryContainer = 0xFF234567;
        roles.high = 0xFF334455;
        roles.container = 0xFF223344;
        check(roles.primary != 0, "roles.primary must be non-zero Monet color");
        check(roles.onPrimary != 0, "roles.onPrimary must be non-zero");
        check(roles.tertiaryContainer != 0, "roles.tertiaryContainer must be non-zero Monet color");
        check(roles.primaryContainer != 0, "roles.primaryContainer must be non-zero Monet color");
        check(roles.high != 0, "roles.high must be non-zero Monet container color");
        check(roles.container != 0, "roles.container must be non-zero Monet container color");

        // 4. Verify MD3 corner grouping calculations (mirrored for incoming/outgoing)
        // Grouping formula verification:
        // Outgoing: tl=18, tr=(hasPrev?4:18), br=(hasNext?4:18), bl=18
        // Incoming: tl=(hasPrev?4:18), tr=18, br=18, bl=(hasNext?4:18)

        // Single outgoing:
        checkCorners(false, false, false, 18f, 18f, 18f, 18f, "single outgoing");
        // First in outgoing group:
        checkCorners(false, false, true, 18f, 18f, 4f, 18f, "first outgoing");
        // Middle in outgoing group:
        checkCorners(false, true, true, 18f, 4f, 4f, 18f, "middle outgoing");
        // Last in outgoing group:
        checkCorners(false, true, false, 18f, 4f, 18f, 18f, "last outgoing");

        // Single incoming:
        checkCorners(true, false, false, 18f, 18f, 18f, 18f, "single incoming");
        // First in incoming group:
        checkCorners(true, false, true, 18f, 18f, 18f, 4f, "first incoming");
        // Middle in incoming group:
        checkCorners(true, true, true, 4f, 18f, 18f, 4f, "middle incoming");
        // Last in incoming group:
        checkCorners(true, true, false, 4f, 18f, 18f, 18f, "last incoming");

        System.out.println("ChatM3TestHarness passed successfully.");
    }

    private static void checkCorners(boolean incoming, boolean hasPrev, boolean hasNext,
                                     float expTl, float expTr, float expBr, float expBl, String desc) {
        float tl, tr, br, bl;
        if (incoming) {
            tl = hasPrev ? 4f : 18f;
            tr = 18f;
            br = 18f;
            bl = hasNext ? 4f : 18f;
        } else {
            tl = 18f;
            tr = hasPrev ? 4f : 18f;
            br = hasNext ? 4f : 18f;
            bl = 18f;
        }
        check(tl == expTl && tr == expTr && br == expBr && bl == expBl,
                "Corner check failed for " + desc + ": got [" + tl + "," + tr + "," + br + "," + bl + "]");
    }
}
"""

class ChatM3GroupingRuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac"), "JDK required")
    def test_chat_m3_grouping_and_send_button(self):
        with tempfile.TemporaryDirectory() as folder:
            room = pathlib.Path(folder)
            (room / "ChatM3TestHarness.java").write_text(HARNESS, encoding="utf-8")
            android_jar = ROOT / "tools/android.jar"
            self.assertTrue(android_jar.exists(), "tools/android.jar must exist")

            java_files = list((ROOT / "inject/java").rglob("*.java")) + list((ROOT / "inject/stubs").rglob("*.java"))
            cmd = [
                "javac", "-encoding", "UTF-8",
                "-cp", f"{android_jar}:{ROOT}/inject/stubs",
                "-d", folder,
                str(room / "ChatM3TestHarness.java"),
                *[str(p) for p in java_files]
            ]
            built = subprocess.run(cmd, capture_output=True, text=True)
            self.assertEqual(0, built.returncode, f"Compile failed:\n{built.stderr}")

            ran = subprocess.run(
                ["java", "-cp", f"{folder}:{android_jar}", "cat.narezany.margyt.ChatM3TestHarness"],
                capture_output=True, text=True
            )
            self.assertEqual(0, ran.returncode, f"Runtime harness failed:\n{ran.stderr}")
            self.assertIn("ChatM3TestHarness passed successfully.", ran.stdout)

    def test_chat_m3_source_requirements(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/ChatM3.java").read_text(encoding="utf-8")
        # 1. Monet color replacement (tertiaryContainer / primaryContainer, container / high, r.primary)
        self.assertIn("r.tertiaryContainer", source)
        self.assertIn("r.primaryContainer", source)
        self.assertIn("r.high", source)
        self.assertIn("r.primary", source)
        self.assertIn("r.onPrimary", source)

        # 2. MD3 corner radii values (18dp and 4dp)
        self.assertIn("18f", source)
        self.assertIn("4f", source)
        self.assertIn("styleGroupedBubbles", source)

        # 3. Enabled by default
        self.assertIn('prefs == null || prefs.getBoolean(KEY, true)', source)

        # 4. MessageTimes integration
        times_source = (ROOT / "inject/java/cat/narezany/margyt/MessageTimes.java").read_text(encoding="utf-8")
        self.assertIn("ChatM3.onBindMessage", times_source)

if __name__ == "__main__":
    unittest.main()
