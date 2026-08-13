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

COLOR_METRICS = (
    "RGB classico",
    "RGB lineare",
    "OKLab percettivo",
    "CIELAB Delta E 2000",
)


def _srgb_to_linear(rgb: np.ndarray) -> np.ndarray:
    value = np.asarray(rgb, dtype=np.float32) / 255.0
    return np.where(value <= 0.04045, value / 12.92, ((value + 0.055) / 1.055) ** 2.4)


def _linear_to_oklab(linear: np.ndarray) -> np.ndarray:
    r, g, b = np.moveaxis(linear, -1, 0)
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l_, m_, s_ = np.cbrt(l), np.cbrt(m), np.cbrt(s)
    return np.stack((
        0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
        1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
        0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_,
    ), axis=-1)


def _linear_to_lab(linear: np.ndarray) -> np.ndarray:
    r, g, b = np.moveaxis(linear, -1, 0)
    x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
    delta = 6 / 29
    def f(value):
        return np.where(value > delta ** 3, np.cbrt(value), value / (3 * delta ** 2) + 4 / 29)
    fx, fy, fz = f(x), f(y), f(z)
    return np.stack((116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)), axis=-1)


def _transform_color(rgb: np.ndarray, metric: str) -> np.ndarray:
    if metric == "RGB classico":
        return np.asarray(rgb, dtype=np.float32) / 255.0
    linear = _srgb_to_linear(rgb)
    if metric == "RGB lineare":
        return linear
    if metric == "OKLab percettivo":
        return _linear_to_oklab(linear)
    if metric == "CIELAB Delta E 2000":
        return _linear_to_lab(linear)
    raise ValueError(f"Metodo colore sconosciuto: {metric}")


def _delta_e_2000(lab: np.ndarray, palette_lab: np.ndarray) -> np.ndarray:
    # Implementazione vettoriale CIEDE2000; lab può avere qualunque forma prima dell'asse colore.
    l1, a1, b1 = np.moveaxis(lab[..., None, :], -1, 0)
    l2, a2, b2 = np.moveaxis(palette_lab, -1, 0)
    c1, c2 = np.hypot(a1, b1), np.hypot(a2, b2)
    cbar = (c1 + c2) / 2
    g = 0.5 * (1 - np.sqrt(cbar ** 7 / (cbar ** 7 + 25 ** 7)))
    ap1, ap2 = (1 + g) * a1, (1 + g) * a2
    cp1, cp2 = np.hypot(ap1, b1), np.hypot(ap2, b2)
    hp1 = np.mod(np.degrees(np.arctan2(b1, ap1)), 360)
    hp2 = np.mod(np.degrees(np.arctan2(b2, ap2)), 360)
    dl, dc = l2 - l1, cp2 - cp1
    dh = hp2 - hp1
    dh = np.where(dh > 180, dh - 360, np.where(dh < -180, dh + 360, dh))
    dh = np.where((cp1 * cp2) == 0, 0, dh)
    d_h = 2 * np.sqrt(cp1 * cp2) * np.sin(np.radians(dh / 2))
    lbar, cpbar = (l1 + l2) / 2, (cp1 + cp2) / 2
    hpbar = (hp1 + hp2) / 2
    hpbar = np.where((cp1 * cp2) == 0, hp1 + hp2, hpbar)
    hpbar = np.where((cp1 * cp2 != 0) & (np.abs(hp1 - hp2) > 180),
                     np.where(hp1 + hp2 < 360, hpbar + 180, hpbar - 180), hpbar)
    t = (1 - 0.17 * np.cos(np.radians(hpbar - 30))
         + 0.24 * np.cos(np.radians(2 * hpbar))
         + 0.32 * np.cos(np.radians(3 * hpbar + 6))
         - 0.20 * np.cos(np.radians(4 * hpbar - 63)))
    sl = 1 + 0.015 * (lbar - 50) ** 2 / np.sqrt(20 + (lbar - 50) ** 2)
    sc, sh = 1 + 0.045 * cpbar, 1 + 0.015 * cpbar * t
    rt = (-2 * np.sqrt(cpbar ** 7 / (cpbar ** 7 + 25 ** 7))
          * np.sin(np.radians(60 * np.exp(-((hpbar - 275) / 25) ** 2))))
    squared = ((dl / sl) ** 2 + (dc / sc) ** 2 + (d_h / sh) ** 2
               + rt * (dc / sc) * (d_h / sh))
    return np.sqrt(np.maximum(squared, 0))


def _distances(rgb: np.ndarray, palette_metric: np.ndarray, metric: str) -> np.ndarray:
    transformed = _transform_color(rgb, metric)
    if metric == "CIELAB Delta E 2000":
        return _delta_e_2000(transformed, palette_metric)
    return np.sum((transformed[..., None, :] - palette_metric) ** 2, axis=-1)


def target_size(source: tuple[int, int], width: int, height: int | None = None) -> tuple[int, int]:
    sw, sh = source
    if width < 1:
        raise ValueError("La larghezza deve essere positiva.")
    if height is None:
        height = max(1, round(sh * width / sw))
    if width * height > 2_000_000:
        raise ValueError("La conversione supera il limite di sicurezza di 2.000.000 celle.")
    return width, height


def _nearest(pixel: np.ndarray, palette_metric: np.ndarray, metric: str) -> int:
    return int(np.argmin(_distances(pixel, palette_metric, metric)))


def _quantize_none(work: np.ndarray, palette_metric: np.ndarray, metric: str,
                   progress: Progress | None) -> np.ndarray:
    h, w, _ = work.shape
    out = np.empty((h, w), dtype=np.uint16)
    for y in range(h):
        distances = _distances(work[y], palette_metric, metric)
        out[y] = np.argmin(distances, axis=1)
        if progress and y % max(1, h // 100) == 0:
            progress(15 + int(55 * y / h), "Quantizzazione")
    return out


def _diffuse(work: np.ndarray, palette: np.ndarray, palette_metric: np.ndarray, metric: str,
             kernel, serpentine: bool, progress: Progress | None) -> np.ndarray:
    h, w, _ = work.shape
    out = np.empty((h, w), dtype=np.uint16)
    for y in range(h):
        reverse = serpentine and y % 2 == 1
        xs = range(w - 1, -1, -1) if reverse else range(w)
        direction = -1 if reverse else 1
        for x in xs:
            old = work[y, x].copy()
            idx = _nearest(old, palette_metric, metric)
            out[y, x] = idx
            error = old - palette[idx]
            for dx, dy, weight in kernel:
                nx, ny = x + dx * direction, y + dy
                if 0 <= nx < w and 0 <= ny < h:
                    work[ny, nx] = np.clip(work[ny, nx] + error * weight, 0, 255)
        if progress and y % max(1, h // 100) == 0:
            progress(15 + int(55 * y / h), "Dithering")
    return out


def _ordered_bayer(work: np.ndarray, palette_metric: np.ndarray, metric: str,
                   progress: Progress | None) -> np.ndarray:
    matrix = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], dtype=np.float32)
    h, w, _ = work.shape
    adjusted = work.copy()
    threshold = ((matrix / 16.0) - 0.5) * 64
    adjusted += np.tile(threshold, (int(np.ceil(h / 4)), int(np.ceil(w / 4))))[:h, :w, None]
    return _quantize_none(np.clip(adjusted, 0, 255), palette_metric, metric, progress)


def convert_image(path: str | Path, width: int, palette_hex: list[str], dither: str,
                  resampling: str = "Lanczos", progress: Progress | None = None,
                  color_metric: str = "OKLab percettivo") -> ConversionResult:
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
    palette_metric = _transform_color(palette, color_metric)
    work = np.asarray(resized, dtype=np.float32).copy()
    if progress:
        progress(12, "Ridimensionamento completato")

    if dither == "Nessuno":
        indices = _quantize_none(work, palette_metric, color_metric, progress)
    elif dither == "Floyd-Steinberg":
        kernel = [(1, 0, 7/16), (-1, 1, 3/16), (0, 1, 5/16), (1, 1, 1/16)]
        indices = _diffuse(work, palette, palette_metric, color_metric, kernel, False, progress)
    elif dither == "Floyd-Steinberg serpentino":
        kernel = [(1, 0, 7/16), (-1, 1, 3/16), (0, 1, 5/16), (1, 1, 1/16)]
        indices = _diffuse(work, palette, palette_metric, color_metric, kernel, True, progress)
    elif dither == "Atkinson":
        kernel = [(1, 0, 1/8), (2, 0, 1/8), (-1, 1, 1/8), (0, 1, 1/8), (1, 1, 1/8), (0, 2, 1/8)]
        indices = _diffuse(work, palette, palette_metric, color_metric, kernel, False, progress)
    elif dither == "Bayer 4x4":
        indices = _ordered_bayer(work, palette_metric, color_metric, progress)
    elif dither == "Sierra Lite":
        kernel = [(1, 0, 2/4), (-1, 1, 1/4), (0, 1, 1/4)]
        indices = _diffuse(work, palette, palette_metric, color_metric, kernel, True, progress)
    elif dither == "Stucki":
        kernel = [(1, 0, 8/42), (2, 0, 4/42), (-2, 1, 2/42), (-1, 1, 4/42),
                  (0, 1, 8/42), (1, 1, 4/42), (2, 1, 2/42), (-2, 2, 1/42),
                  (-1, 2, 2/42), (0, 2, 4/42), (1, 2, 2/42), (2, 2, 1/42)]
        indices = _diffuse(work, palette, palette_metric, color_metric, kernel, True, progress)
    elif dither == "Jarvis-Judice-Ninke":
        kernel = [(1, 0, 7/48), (2, 0, 5/48), (-2, 1, 3/48), (-1, 1, 5/48),
                  (0, 1, 7/48), (1, 1, 5/48), (2, 1, 3/48), (-2, 2, 1/48),
                  (-1, 2, 3/48), (0, 2, 5/48), (1, 2, 3/48), (2, 2, 1/48)]
        indices = _diffuse(work, palette, palette_metric, color_metric, kernel, True, progress)
    else:
        raise ValueError(f"Dithering sconosciuto: {dither}")

    rgb = palette.astype(np.uint8)[indices]
    result_image = Image.fromarray(rgb, "RGB")
    counts = np.bincount(indices.ravel(), minlength=len(colors)).astype(int).tolist()
    if progress:
        progress(75, "Anteprima pronta")
    return ConversionResult(result_image, indices, colors, counts, original_size)
