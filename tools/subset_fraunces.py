#!/usr/bin/env python3
"""
Rebuilds the Brainstorm nudge serif (issue #29).

The app bundles one italic serif for exactly one line of text, in one place, at
one size: the Brainstorm nudge in DESIGN.md section 7. Shipping the whole family
for that would be about 415 KB of a face nobody can otherwise reach, so it is cut
to a single static instance and subset to only the glyphs the line needs.

    pip install --user fonttools brotli
    python3 tools/subset_fraunces.py

Run it whenever LINE changes. A character that is not in the subset does not fall
back, it simply does not render, so changing the copy without rerunning this will
silently drop letters.

Fraunces is under the SIL Open Font License 1.1, which is compatible with
bundling in this AGPL app. It is credited in Settings, Licenses.
"""
import os
import subprocess
import sys
import tempfile

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

# DESIGN.md section 7. Keep this identical to ModeNudge.nudgeLine(BRAINSTORM).
LINE = "All right. What have you got?"

UPSTREAM = (
    "https://github.com/google/fonts/raw/main/ofl/fraunces/"
    "Fraunces-Italic%5BSOFT%2CWONK%2Copsz%2Cwght%5D.ttf"
)
# opsz 24 for a single large line, wght 400 to sit beside Manrope rather than
# shout over it, SOFT 0, WONK 1 for the genuinely italic wonky forms that are the
# reason a serif was bundled at all.
PIN = {"opsz": 24, "wght": 400, "SOFT": 0, "WONK": 1}

OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app/src/main/res/font/fraunces_brainstorm_subset.ttf",
)


def main() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "fraunces-italic-var.ttf")
        subprocess.run(["curl", "-sSL", "-o", src, UPSTREAM], check=True)
        original = os.path.getsize(src)

        font = instancer.instantiateVariableFont(
            TTFont(src), PIN, inplace=False, updateFontNames=False,
        )
        # Named by hand: the STAT table has no axis value for opsz 24, and the
        # axes it was cut at are no longer discoverable from the file.
        name = font["name"]
        for nid, value in (
            (1, "Fraunces Subset"),
            (2, "Italic"),
            (4, "Fraunces 24pt Regular Italic WONK (subset)"),
            (6, "FrauncesSubset-Italic"),
        ):
            name.setName(value, nid, 3, 1, 0x409)
        instanced = os.path.join(tmp, "instance.ttf")
        font.save(instanced)

        opts = subset.Options()
        opts.layout_features = ["kern", "liga", "calt"]
        opts.name_IDs = ["*"]
        opts.name_legacy = True
        opts.notdef_outline = True
        opts.drop_tables += ["DSIG"]
        cut = subset.load_font(instanced, opts)
        subsetter = subset.Subsetter(options=opts)
        subsetter.populate(text=LINE)
        subsetter.subset(cut)
        subset.save_font(cut, OUT, opts)

    out = TTFont(OUT)
    cmap = out.getBestCmap()
    missing = [c for c in sorted(set(LINE)) if ord(c) not in cmap]
    if missing:
        print(f"ERROR: no glyph for {missing}", file=sys.stderr)
        return 1

    print(f"{original:,} bytes -> {os.path.getsize(OUT):,} bytes, "
          f"{len(out.getGlyphOrder())} glyphs")
    print(f"wrote {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
