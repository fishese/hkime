import unittest

from tools.generate_method_phrase_overrides import methods


class MethodPhraseOverrideTest(unittest.TestCase):
    def test_confirmed_methods(self):
        self.assertEqual({'cangjie', 'quick'}, methods('Cangjie + Quick', '時間表'))
        self.assertEqual({'quick'}, methods('Quick'))
        self.assertEqual({'cantonese'}, methods('cantonese'))
        self.assertEqual({'english'}, methods('English'))

    def test_changjie_phrase_also_belongs_to_quick(self):
        self.assertEqual({'cangjie', 'quick'}, methods('changjie', '合肥市'))
        self.assertEqual({'cangjie'}, methods('changjie', '昨'))

    def test_all_includes_every_method(self):
        self.assertEqual({'cantonese', 'cangjie', 'quick', 'english'}, methods('all', '🔤'))

    def test_guesses_and_uncertain_are_not_exported(self):
        self.assertEqual(set(), methods('Cangjie + Quick (guess)'))
        self.assertEqual(set(), methods('Cantonese (guess)'))
        self.assertEqual(set(), methods('method uncertain'))


if __name__ == '__main__':
    unittest.main()
