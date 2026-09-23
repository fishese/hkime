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

    def test_latin_leading_phrase_is_matched_without_changing_its_text(self):
        cantonese_first = {"制": {"j"}}
        cantonese_full = {"制": {"jai"}}
        cangjie_first = {"制": {"h"}}
        cangjie_full = {"制": {"hbln"}}
        self.assertEqual("initials", phrase_match(
            "aaj", "AA制", cantonese_first, cantonese_full))
        self.assertEqual("final_code", phrase_match(
            "aajai", "AA制", cantonese_first, cantonese_full))
        self.assertEqual("initials", phrase_match(
            "aah", "AA制", cangjie_first, cangjie_full, True))
        self.assertEqual("final_code", phrase_match(
            "aahbln", "AA制", cangjie_first, cangjie_full, True))
        self.assertIsNone(phrase_match("aaj", "AA制", cangjie_first, cangjie_full, True))
        self.assertIsNone(phrase_match("aaj", "AAA", cantonese_first, cantonese_full))

    def test_z_shorthand_matches_cantonese_j_readings(self):
        first = {"支": {"j"}, "姿": {"j"}, "整": {"j"},
                 "專": {"j"}, "制": {"j"}, "政": {"j"}, "治": {"j"}}
        full = {"整": {"jing"}, "治": {"ji"}}
        self.assertEqual("initials", phrase_match(
            "zzzz", "支支整整", first, full, cantonese_z_alias=True))
        self.assertEqual("initials", phrase_match(
            "zzzz", "專制政治", first, full, cantonese_z_alias=True))
        self.assertEqual("final_code", phrase_match(
            "zzzzing", "姿姿整整", first, full, cantonese_z_alias=True))
        self.assertIsNone(phrase_match("zzzz", "支支整整", first, full))


if __name__ == "__main__":
    unittest.main()
