from __future__ import annotations

import json
import urllib.request

from .version import APP_VERSION

RELEASES_API = "https://api.github.com/repos/sc4tto/pixel-sheet-converter/releases/latest"
RELEASES_PAGE = "https://github.com/sc4tto/pixel-sheet-converter/releases/latest"


def _version_tuple(value: str) -> tuple[int, ...]:
    clean = value.strip().lower().lstrip("v").split("-", 1)[0]
    return tuple(int(part) for part in clean.split("."))


def check_for_update(timeout: float = 8.0) -> dict:
    request = urllib.request.Request(
        RELEASES_API,
        headers={"Accept": "application/vnd.github+json", "User-Agent": f"PixelSheetConverter/{APP_VERSION}"},
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        release = json.load(response)
    tag = str(release.get("tag_name", "")).strip()
    if not tag:
        raise RuntimeError("La release GitHub non contiene un numero di versione.")
    assets = {asset.get("name"): asset.get("browser_download_url") for asset in release.get("assets", [])}
    return {
        "current": APP_VERSION,
        "latest": tag.lstrip("v"),
        "available": _version_tuple(tag) > _version_tuple(APP_VERSION),
        "page_url": release.get("html_url") or RELEASES_PAGE,
        "download_url": assets.get("PixelSheetConverter-Windows-x64.zip"),
        "notes": release.get("body") or "",
    }

