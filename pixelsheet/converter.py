from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import numpy as np
from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = False
Progress = Callable[[int, str], None]


@dataclass
class ConversionResult:
    image: Image.Image
    indices: np.ndarray
    colors: list[str]
    counts: list[int]
    source_size: tuple[int, int]

    @property
    def size(self) -> tuple[int, int]:
        return self.image.size


RESAMPLING = {
    "Lanczos": Image.Resampling.LANCZOS,
    "Bicubico": Image.Resampling.BICUBIC,
    "Bilineare": Image.Resampling.BILINEAR,
    "Pixel / nearest": Image.Resampling.NEAREST,
}


def target_size(source: tuple[int, int], width: int, height: int | None = None) -> tuple[int, int]:
    sw, sh = source
    if width < 1:
        raise ValueError("La larghezza deve essere positiva.")
    if height is None:
        height = max(1, round(sh * width / sw))
    if width * height > 2_000_000:
        raise ValueError("La conversione supera il limite di sicurezza di 2.000.000 celle.")
    return width, height


def _nearest(pixel: np.ndarray, palette: np.ndarray) -> int:
    return int(np.argmin(np.sum((palette - pixel) ** 2, axis=1)))


def _quantize_none(work: np.ndarray, palette: np.ndarray, progress: Progress | None) -> np.ndarray:
    h, w, _ = work.shape
    out = np.empty((h, w), dtype=np.uint16)
    for y in range(h):
        distances = np.sum((work[y, :, None, :] - palette[None, :, :]) ** 2, axis=2)
        out[y] = np.argmin(distances, axis=1)
        if progress and y % max(1, h // 100) == 0:
            progress(15 + int(55 * y / h), "Quantizzazione")
    return out


def _diffuse(work: np.ndarray, palette: np.ndarray, kernel, serpentine: bool, progress: Progress | None) -> np.ndarray:
    h, w, _ = work.shape
    out = np.empty((h, w), dtype=np.uint16)
    for y in range(h):
        reverse = serpentine and y % 2 == 1
        xs = range(w - 1, -1, -1) if reverse else range(w)
        direction = -1 if reverse else 1
        for x in xs:
            old = work[y, x].copy()
            idx = _nearest(old, palette)
            out[y, x] = idx
            error = old - palette[idx]
            for dx, dy, weight in kernel:
                nx, ny = x + dx * direction, y + dy
                if 0 <= nx < w and 0 <= ny < h:
                    work[ny, nx] = np.clip(work[ny, nx] + error * weight, 0, 255)
        if progress and y % max(1, h // 100) == 0:
            progress(15 + int(55 * y / h), "Dithering")
    return out


def _ordered_bayer(work: np.ndarray, palette: np.ndarray, progress: Progress | None) -> np.ndarray:
    matrix = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], dtype=np.float32)
    h, w, _ = work.shape
    adjusted = work.copy()
    threshold = ((matrix / 16.0) - 0.5) * 64
    adjusted += np.tile(threshold, (int(np.ceil(h / 4)), int(np.ceil(w / 4))))[:h, :w, None]
    return _quantize_none(np.clip(adjusted, 0, 255), palette, progress)


def convert_image(path: str | Path, width: int, palette_hex: list[str], dither: str,
                  resampling: str = "Lanczos", progress: Progress | None = None) -> ConversionResult:
    if progress:
        progress(2, "Apertura immagine")
    source = Image.open(path)
    source.load()
    source = source.convert("RGB")
    original_size = source.size
    size = target_size(source.size, width)
    resized = source.resize(size, RESAMPLING[resampling])
    colors = [c.upper() for c in palette_hex]
    palette = np.asarray([tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) for c in colors], dtype=np.float32)
    work = np.asarray(resized, dtype=np.float32).copy()
    if progress:
        progress(12, "Ridimensionamento completato")

    if dither == "Nessuno":
        indices = _quantize_none(work, palette, progress)
    elif dither == "Floyd-Steinberg":
        kernel = [(1, 0, 7/16), (-1, 1, 3/16), (0, 1, 5/16), (1, 1, 1/16)]
        indices = _diffuse(work, palette, kernel, False, progress)
    elif dither == "Floyd-Steinberg serpentino":
        kernel = [(1, 0, 7/16), (-1, 1, 3/16), (0, 1, 5/16), (1, 1, 1/16)]
        indices = _diffuse(work, palette, kernel, True, progress)
    elif dither == "Atkinson":
        kernel = [(1, 0, 1/8), (2, 0, 1/8), (-1, 1, 1/8), (0, 1, 1/8), (1, 1, 1/8), (0, 2, 1/8)]
        indices = _diffuse(work, palette, kernel, False, progress)
    elif dither == "Bayer 4x4":
        indices = _ordered_bayer(work, palette, progress)
    else:
        raise ValueError(f"Dithering sconosciuto: {dither}")

    rgb = palette.astype(np.uint8)[indices]
    result_image = Image.fromarray(rgb, "RGB")
    counts = np.bincount(indices.ravel(), minlength=len(colors)).astype(int).tolist()
    if progress:
        progress(75, "Anteprima pronta")
    return ConversionResult(result_image, indices, colors, counts, original_size)

