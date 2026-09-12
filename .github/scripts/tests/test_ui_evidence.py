import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("ui", Path(__file__).parents[1] / "brotv-ui-evidence.py")
ui = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ui)


def tree(package=ui.PACKAGE, enabled="true", bounds="[10,20][110,80]", label="Refresh"):
    return ET.fromstring(f'''<hierarchy><node package="{package}" clickable="true" enabled="{enabled}" bounds="{bounds}">
      <node package="{package}" text="{label}" /></node></hierarchy>''')


class EvidenceTests(unittest.TestCase):
    def test_launcher_cannot_satisfy_app_readiness_or_target(self):
        root = tree(package="com.google.android.tvlauncher")
        self.assertFalse(ui.ready(root))
        self.assertIsNone(ui.locate(root, "Refresh"))

    def test_blank_splash_is_not_ready(self):
        self.assertFalse(ui.ready(ET.fromstring('<hierarchy><node package="com.brotv.iptv" /></hierarchy>')))
        self.assertFalse(ui.ready(tree(label="")))

    def test_nearest_clickable_ancestor(self):
        root = tree()
        self.assertTrue(ui.ready(root))
        self.assertEqual((60, 50), ui.locate(root, "Refresh"))

    def test_disabled_and_zero_area_controls_are_not_actionable(self):
        for root in (tree(enabled="false"), tree(bounds="[0,0][0,0]")):
            self.assertFalse(ui.ready(root))
            self.assertIsNone(ui.locate(root, "Refresh"))

    def test_exact_and_prefix_are_distinct(self):
        root = tree(label="مسلسل تجريبي 2101")
        self.assertIsNone(ui.locate(root, "مسلسل تجريبي"))
        self.assertEqual((60, 50), ui.locate(root, "مسلسل تجريبي", prefix=True))

    def test_clickable_leaf_is_found(self):
        root = ET.fromstring('<hierarchy><node package="com.brotv.iptv" text="Refresh" clickable="true" enabled="true" bounds="[0,0][20,20]" /></hierarchy>')
        self.assertEqual((10, 10), ui.locate(root, "Refresh"))


if __name__ == "__main__":
    unittest.main()
