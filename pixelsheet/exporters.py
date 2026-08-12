from __future__ import annotations

from pathlib import Path
from typing import Callable
import zipfile

import xlsxwriter

from .converter import ConversionResult


def export_png(result: ConversionResult, path: str | Path, scale: int = 1) -> None:
    image = result.image
    if scale > 1:
        image = image.resize((image.width * scale, image.height * scale), resample=0)
    image.save(path, "PNG", optimize=True)


def export_xlsx(result: ConversionResult, path: str | Path, progress: Callable[[int, str], None] | None = None) -> None:
    path = Path(path)
    workbook = xlsxwriter.Workbook(path, {"constant_memory": True})
    image_sheet = workbook.add_worksheet("Immagine pixel")
    info = workbook.add_worksheet("Informazioni")
    palette_sheet = workbook.add_worksheet("Palette e statistiche")
    image_sheet.hide_gridlines(2)
    image_sheet.set_default_row(2.25)
    image_sheet.set_column(0, result.image.width - 1, 0.32)
    formats = [workbook.add_format({"bg_color": color, "font_color": color, "num_format": ";;;"}) for color in result.colors]

    height, width = result.indices.shape
    for y in range(height):
        row = result.indices[y]
        start = 0
        color = int(row[0])
        for x in range(1, width + 1):
            if x == width or int(row[x]) != color:
                fmt = formats[color]
                for col in range(start, x):
                    image_sheet.write_number(y, col, color, fmt)
                if x < width:
                    start, color = x, int(row[x])
        if progress and y % max(1, height // 100) == 0:
            progress(76 + int(22 * y / height), "Esportazione XLSX")

    title = workbook.add_format({"bold": True, "font_size": 16, "font_color": "#FFFFFF", "bg_color": "#17365D"})
    header = workbook.add_format({"bold": True, "bg_color": "#D9EAF7", "border": 1})
    normal = workbook.add_format({"border": 1})
    info.merge_range("A1:D1", "Pixel Sheet Converter - Informazioni", title)
    rows = [
        ("Dimensioni originali", f"{result.source_size[0]} x {result.source_size[1]}"),
        ("Dimensioni celle", f"{width} x {height}"),
        ("Celle totali", width * height),
        ("Compatibilità", "Riempimenti statici - Excel e Google Fogli"),
    ]
    info.write_row("A3", ["Proprietà", "Valore"], header)
    for r, values in enumerate(rows, 3):
        info.write_row(r, 0, values, normal)
    info.set_column("A:A", 25)
    info.set_column("B:B", 48)

    palette_sheet.write_row("A1", ["Indice", "Colore", "Celle", "Percentuale"], header)
    total = width * height
    for i, (color, count) in enumerate(zip(result.colors, result.counts), 1):
        swatch = workbook.add_format({"bg_color": color, "font_color": color, "border": 1})
        palette_sheet.write_number(i, 0, i - 1, normal)
        palette_sheet.write_string(i, 1, color, swatch)
        palette_sheet.write_number(i, 2, count, normal)
        palette_sheet.write_number(i, 3, count / total, workbook.add_format({"num_format": "0.00%", "border": 1}))
    palette_sheet.set_column("A:A", 10)
    palette_sheet.set_column("B:B", 18)
    palette_sheet.set_column("C:D", 16)
    workbook.close()
    with zipfile.ZipFile(path) as archive:
        bad = archive.testzip()
        if bad:
            raise OSError(f"File XLSX non valido: {bad}")
    if progress:
        progress(100, "Esportazione completata")

