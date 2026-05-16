#!/usr/bin/env python3
"""Generate 256x16 PNG colour LUTs for the adaptive theming engine.

The shader `core/shader_globals/lut_post_process.gdshader` expects 16-slice
LUTs. We bake one identity LUT plus several mood-tuned variants by applying
hue / saturation / contrast / curve adjustments to the identity grid.
"""
from __future__ import annotations
import colorsys
import math
import os
from PIL import Image

LUT_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "theming", "luts")
SIZE = 16  # cube side
W = SIZE * SIZE  # 256
H = SIZE        # 16


def identity_pixel(x: int, y: int):
    slice_idx = x // SIZE
    r = (x % SIZE) / (SIZE - 1)
    g = y / (SIZE - 1)
    b = slice_idx / (SIZE - 1)
    return r, g, b


def apply(r, g, b, hue_shift=0.0, sat_mul=1.0, val_mul=1.0, warm=0.0, cool=0.0, contrast=1.0):
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    h = (h + hue_shift) % 1.0
    s = max(0.0, min(1.0, s * sat_mul))
    v = max(0.0, min(1.0, v * val_mul))
    r2, g2, b2 = colorsys.hsv_to_rgb(h, s, v)
    # warm/cool: push R, pull B (or vice versa).
    r2 = max(0.0, min(1.0, r2 + warm * 0.08 - cool * 0.04))
    g2 = max(0.0, min(1.0, g2 + warm * 0.03 - cool * 0.02))
    b2 = max(0.0, min(1.0, b2 - warm * 0.06 + cool * 0.10))
    # contrast around 0.5.
    r2 = max(0.0, min(1.0, 0.5 + (r2 - 0.5) * contrast))
    g2 = max(0.0, min(1.0, 0.5 + (g2 - 0.5) * contrast))
    b2 = max(0.0, min(1.0, 0.5 + (b2 - 0.5) * contrast))
    return r2, g2, b2


PRESETS = {
    "identity":             dict(),
    "ghibli_sunkissed":     dict(sat_mul=1.10, val_mul=1.04, warm=0.50, contrast=1.04),
    "warm_lamp_orange":     dict(sat_mul=0.95, val_mul=0.92, warm=1.10, contrast=1.00),
    "dusty_evening":        dict(sat_mul=0.75, val_mul=0.94, warm=0.45, contrast=0.96),
    "warm_hostel":          dict(sat_mul=1.00, val_mul=0.96, warm=0.30, contrast=1.02),
    "fluorescent_soft":     dict(sat_mul=0.65, val_mul=0.92, cool=0.50, contrast=0.98),
    "tired_dusk":           dict(sat_mul=0.55, val_mul=0.88, warm=0.10, cool=0.20, contrast=0.96),
    "loan_statement_blue":  dict(sat_mul=0.45, val_mul=0.82, cool=0.95, contrast=0.94),
    "rain_window":          dict(sat_mul=0.40, val_mul=0.85, cool=0.70, contrast=0.92),
    "dragon_smoke_red":     dict(sat_mul=0.80, val_mul=0.85, warm=0.60, hue_shift=-0.03, contrast=1.06),
    "morning_after":        dict(sat_mul=1.05, val_mul=1.02, warm=0.25, contrast=1.02),
}


def build_lut(preset_name: str, params: dict) -> Image.Image:
    img = Image.new("RGB", (W, H))
    px = img.load()
    for y in range(H):
        for x in range(W):
            r, g, b = identity_pixel(x, y)
            r, g, b = apply(r, g, b, **params)
            px[x, y] = (int(r * 255), int(g * 255), int(b * 255))
    return img


def main() -> None:
    os.makedirs(LUT_DIR, exist_ok=True)
    for name, params in PRESETS.items():
        path = os.path.join(LUT_DIR, f"{name}.png")
        build_lut(name, params).save(path, "PNG")
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
