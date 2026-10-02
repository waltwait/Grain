import unittest
import numpy as np
from PIL import Image

from prepare_kodak_luts import hald_to_cube


class HaldConversionTest(unittest.TestCase):
    def test_identity_hald_preserves_red_fastest_and_channels(self):
        # An independently authored level-2 Hald: four samples per RGB channel.
        pixels = [(r, g, b) for b in (0, 85, 170, 255) for g in (0, 85, 170, 255) for r in (0, 85, 170, 255)]
        image = Image.new("RGB", (8, 8))
        image.putdata(pixels)
        table = hald_to_cube(image)
        self.assertIsInstance(table, np.ndarray)
        self.assertEqual((4, 4, 4, 3), table.shape)
        np.testing.assert_array_equal(table[0, 0, 3], [255, 0, 0])
        np.testing.assert_array_equal(table[0, 3, 0], [0, 255, 0])
        np.testing.assert_array_equal(table[3, 0, 0], [0, 0, 255])
        np.testing.assert_array_equal(table[2, 1, 3], [255, 85, 170])

    def test_rejects_non_hald_dimensions_or_non_rgb_image(self):
        for image in [Image.new("RGB", (8, 7)), Image.new("RGB", (10, 10)), Image.new("L", (8, 8))]:
            with self.assertRaises(ValueError):
                hald_to_cube(image)


if __name__ == "__main__":
    unittest.main()
