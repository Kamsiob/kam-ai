#!/usr/bin/env python3
"""Renders the Play Store graphics from the DESIGN.md mark geometry.

Everything here is drawn from the numbers in DESIGN.md section 2 and the
palette in section 3, so the store assets and the app cannot drift apart. No
image is traced by hand and nothing is a mockup.

Outputs, into store-assets/:
  icon-512.png            512 by 512 app icon
  feature-graphic.png     1024 by 500 feature graphic

Usage:  python3 tools/make_store_assets.py
"""

from __future__ import annotations

import math
import pathlib
import subprocess
import sys

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("Pillow is needed. Install it with: pip install --user Pillow")

REPO = pathlib.Path(__file__).resolve().parent.parent
OUT = REPO / "store-assets"
FONT_DIR = REPO / "app/src/main/res/font"

# DESIGN.md section 3, light theme (warm ivory).
IVORY = (246, 244, 236)
ACCENT = (46, 122, 82)
TEXT_PRIMARY = (27, 36, 30)
TEXT_SECONDARY = (97, 112, 95)

# DESIGN.md section 2. The core gradient never changes with the theme.
CORE_HIGHLIGHT = (201, 245, 219)
CORE_MID = (79, 191, 133)
CORE_EDGE = (31, 107, 68)

# Supersampling factor. The mark is all curves and thin strokes, so it is drawn
# large and downsampled rather than antialiased in place.
SS = 8


def _lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _core_colour(t: float):
    """The radial gradient: highlight to mid at 0.55, mid to edge after."""
    if t <= 0.55:
        return _lerp(CORE_HIGHLIGHT, CORE_MID, t / 0.55)
    return _lerp(CORE_MID, CORE_EDGE, (t - 0.55) / 0.45)


def draw_mark(size: int, unit_scale: float = 1.0) -> Image.Image:
    """The mark on a transparent square, using the 48 unit viewbox geometry.

    Outer ring   radius 16, stroke 3.4, 270 degrees, gap at the top right.
    Inner arc    radius 10.5, stroke 2.2, 55 degrees, from 150, 55 percent alpha.
    Core         radius 6.6, radial gradient, highlight at the upper left.
    """
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    unit = (big / 48.0) * unit_scale
    cx = cy = big / 2.0

    def box(radius):
        r = radius * unit
        return [cx - r, cy - r, cx + r, cy + r]

    # Outer ring. Angles run clockwise from three o'clock, so 0 to 270 leaves
    # the opening in the top-right quadrant.
    draw.arc(box(16), start=0, end=270, fill=ACCENT + (255,),
             width=round(3.4 * unit))

    # Rounded caps: the arc primitive has none, so the ends get a disc each.
    # PIL grows an arc's stroke inward from the bounding box rather than
    # centring it on the radius, so the caps go on the stroke centreline at
    # radius minus half the stroke, not on the radius itself. Putting them on
    # the nominal radius leaves them bulging outside the ring.
    cap_r = 1.7 * unit
    for angle in (0, 270):
        rad = math.radians(angle)
        ex = cx + (16 - 1.7) * unit * math.cos(rad)
        ey = cy + (16 - 1.7) * unit * math.sin(rad)
        draw.ellipse([ex - cap_r, ey - cap_r, ex + cap_r, ey + cap_r],
                     fill=ACCENT + (255,))

    # Inner accent arc, the counterweight, at 55 percent opacity.
    inner = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    idraw = ImageDraw.Draw(inner)
    idraw.arc(box(10.5), start=150, end=205, fill=ACCENT + (255,),
              width=round(2.2 * unit))
    cap_r = 1.1 * unit
    for angle in (150, 205):
        rad = math.radians(angle)
        ex = cx + (10.5 - 1.1) * unit * math.cos(rad)
        ey = cy + (10.5 - 1.1) * unit * math.sin(rad)
        idraw.ellipse([ex - cap_r, ey - cap_r, ex + cap_r, ey + cap_r],
                      fill=ACCENT + (255,))
    inner.putalpha(inner.getchannel("A").point(lambda a: round(a * 0.55)))
    img = Image.alpha_composite(img, inner)
    draw = ImageDraw.Draw(img)

    # The lit core. Drawn per pixel so the circle keeps a hard edge and the
    # gradient stays inside it. Stacking ellipses bleeds a soft halo past the
    # radius, which reads as a glow, and DESIGN.md allows no glow effects here.
    core_r = 6.6 * unit
    hx = cx - core_r * 0.4
    hy = cy - core_r * 0.4
    grad_r = core_r * 1.5

    left, top = int(cx - core_r) - 2, int(cy - core_r) - 2
    span = int(core_r * 2) + 4
    core = Image.new("RGBA", (span, span), (0, 0, 0, 0))
    px = core.load()
    for y in range(span):
        for x in range(span):
            gx, gy = left + x + 0.5, top + y + 0.5
            if (gx - cx) ** 2 + (gy - cy) ** 2 > core_r ** 2:
                continue
            d = math.hypot(gx - hx, gy - hy) / grad_r
            px[x, y] = _core_colour(min(d, 1.0)) + (255,)
    img.paste(core, (left, top), core)

    return img.resize((size, size), Image.LANCZOS)


def load_font(name: str, size: int):
    path = FONT_DIR / name
    if not path.exists():
        return ImageFont.load_default()
    try:
        font = ImageFont.truetype(str(path), size)
        try:
            # Variable fonts default to their thinnest axis position.
            font.set_variation_by_axes([700])
        except Exception:
            pass
        return font
    except Exception:
        return ImageFont.load_default()


def make_icon():
    """512 by 512, ivory ground, mark centred, no thin details that die small."""
    size = 512
    img = Image.new("RGB", (size, size), IVORY)
    mark = draw_mark(size, unit_scale=0.72)
    img.paste(mark, (0, 0), mark)
    out = OUT / "icon-512.png"
    img.save(out, "PNG")
    print(f"  {out.relative_to(REPO)}  512x512")


def make_feature_graphic():
    """1024 by 500: the mark, the wordmark, and one line of the positioning."""
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), IVORY)
    draw = ImageDraw.Draw(img)

    mark_size = 190
    mark = draw_mark(mark_size)
    mark_x, mark_y = 92, (h - mark_size) // 2 - 14
    img.paste(mark, (mark_x, mark_y), mark)

    text_x = mark_x + mark_size + 54

    wordmark = load_font("sora_variable.ttf", 76)
    body = load_font("manrope_variable.ttf", 29)

    draw.text((text_x, 186), "Kam AI", font=wordmark, fill=TEXT_PRIMARY)

    # The locked positioning line, straight from DESIGN.md section 1. Never
    # "private ChatGPT", never a claim of matching cloud models.
    draw.text((text_x, 278),
              "A private thinking and drafting tool",
              font=body, fill=TEXT_SECONDARY)
    draw.text((text_x, 316),
              "that runs entirely on your phone.",
              font=body, fill=TEXT_SECONDARY)

    out = OUT / "feature-graphic.png"
    img.save(out, "PNG")
    print(f"  {out.relative_to(REPO)}  1024x500")


def main():
    OUT.mkdir(exist_ok=True)
    print("Rendering store assets from the DESIGN.md geometry:")
    make_icon()
    make_feature_graphic()
    print("Done.")


if __name__ == "__main__":
    main()
