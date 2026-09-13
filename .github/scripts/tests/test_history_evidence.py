import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("history", Path(__file__).parents[1] / "brotv-history-evidence.py")
history = importlib.util.module_from_spec(spec)
spec.loader.exec_module(history)


class HistoryTests(unittest.TestCase):
    def setUp(self):
        self.seed = ET.parse(Path(__file__).parents[2] / "tests/demo-local-state.xml").getroot()

    def test_each_clear_preserves_other_data(self):
        for kind in ("clear-live", "clear-movies", "clear-series"):
            after = ET.fromstring(ET.tostring(self.seed))
            for node in list(after):
                if history.belongs(node.get("name"), kind):
                    after.remove(node)
            history.verify(self.seed, after, kind)

    def test_noop_does_not_pass(self):
        with self.assertRaises(ValueError):
            history.verify(self.seed, self.seed, "clear-movies")

    def test_empty_seed_does_not_pass(self):
        empty = ET.fromstring("<map/>")
        with self.assertRaises(ValueError):
            history.verify(empty, empty, "clear-series")

    def test_deleting_everything_does_not_pass(self):
        with self.assertRaises(ValueError):
            history.verify(self.seed, ET.fromstring("<map/>"), "clear-live")
