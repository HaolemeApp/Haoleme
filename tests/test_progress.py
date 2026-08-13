import unittest

from haoleme.progress import ProgressParser, parse_eta, progress_fields


class ProgressParserTest(unittest.TestCase):
    def test_parses_tqdm_percent_and_eta(self):
        parser = ProgressParser()
        self.assertTrue(parser.feed("Epoch 1:  42%|████      | 42/100 [00:10<00:25,  2.31it/s]"))
        self.assertEqual(parser.state.percent, 42.0)
        self.assertEqual(parser.state.eta_seconds, 25)

    def test_parses_epoch_and_loss(self):
        fields = progress_fields("Epoch 3/10  loss=0.4312  acc=0.91")
        self.assertEqual(fields["progress"], 30.0)
        self.assertAlmostEqual(fields["lastLoss"], 0.4312)

    def test_parse_eta_supports_mm_ss(self):
        self.assertEqual(parse_eta("01:05"), 65)
        self.assertEqual(parse_eta("1:02:03"), 3723)
        self.assertIsNone(parse_eta("soon"))

    def test_empty_text_is_a_no_op(self):
        self.assertEqual(progress_fields(""), {})
        self.assertFalse(ProgressParser().feed(""))
