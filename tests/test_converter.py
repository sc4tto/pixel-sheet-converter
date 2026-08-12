import tempfile
from pathlib import Path
import zipfile

import numpy as np
from PIL import Image

from pixelsheet.converter import convert_image, target_size
from pixelsheet.exporters import export_png, export_xlsx


def test_target_size_preserves_ratio():
    assert target_size((1086, 1448), 543) == (543, 724)


def test_rgb_conversion_and_exports():
    with tempfile.TemporaryDirectory() as folder:
        folder = Path(folder)
        source = folder / "source.png"
        pixels = np.zeros((12, 16, 3), dtype=np.uint8)
        pixels[:, :5] = (220, 100, 80)
        pixels[:, 5:11] = (60, 190, 90)
        pixels[:, 11:] = (80, 90, 220)
        Image.fromarray(pixels).save(source)
        result = convert_image(source, 32, ["#FF0000", "#00FF00", "#0000FF"], "Floyd-Steinberg serpentino")
        assert result.size == (32, 24)
        assert set(np.unique(result.indices)).issubset({0, 1, 2})
        assert sum(result.counts) == 32 * 24
        png = folder / "out.png"
        xlsx = folder / "out.xlsx"
        export_png(result, png)
        export_xlsx(result, xlsx)
        assert png.is_file() and xlsx.is_file()
        with zipfile.ZipFile(xlsx) as archive:
            assert archive.testzip() is None
            styles = archive.read("xl/styles.xml")
            assert b"FFFF0000" in styles and b"FF00FF00" in styles and b"FF0000FF" in styles

