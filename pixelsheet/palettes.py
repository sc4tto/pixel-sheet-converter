from __future__ import annotations

import re
import numpy as np

BUILTIN_PALETTES = {
    "RGB primari": ["#FF0000", "#00FF00", "#0000FF"],
    "RGB + bianco e nero": ["#000000", "#FF0000", "#00FF00", "#0000FF", "#FFFFFF"],
    "CMY": ["#00FFFF", "#FF00FF", "#FFFF00"],
    "Scala di grigi (8)": [f"#{v:02X}{v:02X}{v:02X}" for v in np.linspace(0, 255, 8).astype(int)],
}


def parse_hex_color(value: str) -> tuple[int, int, int]:
    value = value.strip().upper()
    if not re.fullmatch(r"#[0-9A-F]{6}", value):
        raise ValueError(f"Colore non valido: {value}. Usa il formato #RRGGBB.")
    return tuple(int(value[i:i + 2], 16) for i in (1, 3, 5))


def normalize_palette(values: list[str]) -> tuple[list[str], np.ndarray]:
    cleaned: list[str] = []
    for value in values:
        normalized = value.strip().upper()
        parse_hex_color(normalized)
        if normalized not in cleaned:
            cleaned.append(normalized)
    if len(cleaned) < 2:
        raise ValueError("La palette deve contenere almeno due colori diversi.")
    return cleaned, np.asarray([parse_hex_color(v) for v in cleaned], dtype=np.float32)

