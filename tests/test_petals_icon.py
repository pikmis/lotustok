import os, re, unittest

from margyt import axml, petals

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


class PetalsIconTests(unittest.TestCase):
    def test_the_vector_is_a_vector_of_one_black_path(self):
        parsed = axml.Axml.parse(petals.xml())
        self.assertIsNotNone(parsed)
        self.assertIn("M ", petals.PATH)
        self.assertTrue(petals.PATH.rstrip().endswith("Z"))
        self.assertEqual(petals.PATH.count("M "), petals.PATH.count(" Z"))

    def test_the_java_side_spells_the_same_id(self):
        with open(os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt", "SettingsCell.java"),
                  encoding="utf-8") as handle:
            found = re.search(r"int PETALS = (0x[0-9a-fA-F]+);", handle.read())
        self.assertIsNotNone(found)
        self.assertEqual(int(found.group(1), 16), petals.ID)

    def test_the_id_is_the_first_entry_of_a_package_of_its_own(self):
        from margyt import widget
        taken = {0x30, widget.LAYOUT_PACKAGE, widget.XML_PACKAGE}
        self.assertNotIn(petals.PACKAGE, taken)
        self.assertEqual(petals.ID, (petals.PACKAGE << 24) | (1 << 16))


if __name__ == "__main__":
    unittest.main()
