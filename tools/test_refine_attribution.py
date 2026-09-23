import unittest

from tools.refine_attribution import cangjie_prefix_guess, phrase_match


class RefineAttributionTest(unittest.TestCase):
    def setUp(self):
        self.first = {
            "時": {"a"}, "間": {"a"}, "表": {"q"},
            "星": {"a"}, "巴": {"a"}, "克": {"j"},
            "日": {"a"}, "見": {"b"},
        }
        self.full = {"日": {"a"}, "見": {"buhu"}}

    def test_prefix_mismatch_is_explicit_guess(self):
        self.assertEqual((2, 3), cangjie_prefix_guess("aac", "時間表", self.first)[:2])
        self.assertIn("typed x, reference j", cangjie_prefix_guess("aax", "星巴克", self.first)[2])

    def test_exact_phrase_is_not_a_guess(self):
        self.assertEqual("initials", phrase_match("aab", "日日見", self.first, self.full, True))

    def test_non_han_or_wrong_first_key_has_no_guess(self):
        self.assertIsNone(cangjie_prefix_guess("abc", "🔤", self.first))
        self.assertIsNone(cangjie_prefix_guess("xaa", "時間表", self.first))


if __name__ == "__main__":
    unittest.main()
