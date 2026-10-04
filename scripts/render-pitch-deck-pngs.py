#!/usr/bin/env python3
"""Rasterise the ReLoop pitch deck to per-slide PNGs (and a PDF) for the README.

GitHub cannot display a .pptx inline, so the README shows the deck as images. Those
images are generated from the committed .pptx itself, so they can never drift from it.

This renders the deck's own shape tree (the small vocabulary the generator uses:
filled rounded rectangles, right arrows, and text boxes) with Pillow, so it needs no
office suite and behaves the same everywhere.

    pip install python-pptx pillow fonttools
    python scripts/build-pitch-deck.py            # regenerate the .pptx first
    python scripts/render-pitch-deck-pngs.py      # -> docs/submission/preview/slide-NN.png
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from pptx import Presentation
from pptx.enum.dml import MSO_FILL_TYPE
from pptx.enum.shapes import MSO_SHAPE

from fontTools.ttLib import TTFont

DECK = "docs/submission/ReLoop-Pitch-Deck-TechGaint.pptx"
OUT_DIR = "docs/submission/preview"

EMU_PER_INCH = 914400.0
BG = (247, 250, 247)

FONT_DIR = Path.home() / "Library" / "Fonts"
REGULAR_CANDIDATES = [FONT_DIR / "Inter-Regular.otf", "/Library/Fonts/Arial.ttf"]
BOLD_CANDIDATES = [FONT_DIR / "Inter-Bold.otf", "/Library/Fonts/Arial Bold.ttf"]
SYMBOL_CANDIDATES = ["/System/Library/Fonts/Apple Symbols.ttf",
                     "/System/Library/Fonts/Supplemental/Arial Unicode.ttf"]


def _first_existing(paths):
    for p in paths:
        if Path(p).exists():
            return str(p)
    raise SystemExit("missing font; look in {}".format(paths))


class Fonts:
    """Cached PIL fonts plus a per-font codepoint set for fallback decisions."""

    def __init__(self):
        self.regular_path = _first_existing(REGULAR_CANDIDATES)
        self.bold_path = _first_existing(BOLD_CANDIDATES)
        self.symbol_path = _first_existing(SYMBOL_CANDIDATES)
        self._pil: dict[tuple[str, int], ImageFont.FreeTypeFont] = {}
        self._cover: dict[str, set[int]] = {}

    def pil(self, path: str, px: int) -> ImageFont.FreeTypeFont:
        key = (path, px)
        if key not in self._pil:
            self._pil[key] = ImageFont.truetype(path, px)
        return self._pil[key]

    def covers(self, path: str, ch: str) -> bool:
        if path not in self._cover:
            font = TTFont(path, fontNumber=0)
            self._cover[path] = set(font.getBestCmap().keys())
            font.close()
        return ord(ch) in self._cover[path]

    def for_char(self, base_path: str, ch: str) -> str:
        if ch in " \t":
            return base_path
        return base_path if self.covers(base_path, ch) else self.symbol_path


# ----------------------------------------------------------------- shape helpers
def rgb_of(color_format):
    try:
        if color_format.type is not None:
            rgb = color_format.rgb
            return (rgb[0], rgb[1], rgb[2])
    except Exception:
        pass
    return None


def fill_rgb(shape):
    try:
        if shape.fill.type == MSO_FILL_TYPE.SOLID:
            return rgb_of(shape.fill.fore_color)
    except Exception:
        pass
    return None


def line_rgb(shape):
    try:
        if shape.line.fill.type == MSO_FILL_TYPE.SOLID:
            return rgb_of(shape.line.color)
    except Exception:
        pass
    return None


def line_width_px(shape, px_per_pt):
    try:
        if shape.line.width is not None:
            return max(1, round(shape.line.width.pt * px_per_pt))
    except Exception:
        pass
    return None


def paragraph_children(paragraph):
    """Yield ('text', run) and ('br', None) in document order.

    Read straight from the XML so explicit line breaks survive the round-trip.
    """
    for child in paragraph._p:
        tag = child.tag.split("}")[-1]
        if tag == "r":
            nodes = child.findall(".//{http://schemas.openxmlformats.org/drawingml/2006/main}t")
            text = "".join(n.text or "" for n in nodes)
            yield "text", (text, child)
        elif tag == "br":
            yield "br", None


def wrap_line(text, fonts, base_path, px, max_width):
    if not text.strip():
        return [""]
    words, lines, current = text.split(" "), [], ""
    for word in words:
        trial = word if not current else current + " " + word
        if measure(trial, fonts, base_path, px) <= max_width or not current:
            current = trial
        else:
            lines.append(current)
            current = word
    lines.append(current)
    return lines


def measure(text, fonts, base_path, px):
    total = 0.0
    for ch in text:
        path = fonts.for_char(base_path, ch)
        total += fonts.pil(path, px).getlength(ch)
    return total


def draw_mixed(draw, x, y, text, fonts, base_path, px, color):
    cursor = x
    for ch in text:
        path = fonts.for_char(base_path, ch)
        font = fonts.pil(path, px)
        draw.text((cursor, y), ch, font=font, fill=color)
        cursor += font.getlength(ch)
    return cursor


# ----------------------------------------------------------------- main render
def render(deck: Path, out_dir: Path, width_px: int, want_pdf: bool) -> int:
    prs = Presentation(str(deck))
    fonts = Fonts()
    scale = width_px / (prs.slide_width / EMU_PER_INCH)   # px per inch
    px_per_pt = scale / 72.0
    height_px = round(prs.slide_height / EMU_PER_INCH * scale)

    out_dir.mkdir(parents=True, exist_ok=True)
    for old in list(out_dir.glob("slide-*.png")) + list(out_dir.glob("*.pdf")):
        old.unlink()

    def to_px(emu):
        return emu / EMU_PER_INCH * scale

    images = []
    for index, slide in enumerate(prs.slides, start=1):
        img = Image.new("RGB", (width_px, height_px), BG)
        draw = ImageDraw.Draw(img)

        for shape in slide.shapes:
            x, y = to_px(shape.left), to_px(shape.top)
            w, h = to_px(shape.width), to_px(shape.height)

            kind = None
            try:
                kind = shape.auto_shape_type
            except Exception:
                kind = None

            fill = fill_rgb(shape)
            outline = line_rgb(shape)

            if kind == MSO_SHAPE.ROUNDED_RECTANGLE:
                try:
                    radius = shape.adjustments[0] * min(w, h)
                except Exception:
                    radius = 0.06 * min(w, h)
                if fill or outline:
                    draw.rounded_rectangle([x, y, x + w, y + h], radius=radius,
                                           fill=fill, outline=outline,
                                           width=line_width_px(shape, px_per_pt) or 1)
            elif kind == MSO_SHAPE.RECTANGLE:
                if fill or outline:
                    draw.rectangle([x, y, x + w, y + h], fill=fill, outline=outline,
                                   width=line_width_px(shape, px_per_pt) or 1)
            elif kind == MSO_SHAPE.RIGHT_ARROW:
                head = min(0.5 * h, w)
                shaft = 0.25 * h
                pts = [(x, y + shaft), (x + w - head, y + shaft), (x + w - head, y),
                       (x + w, y + h / 2), (x + w - head, y + h),
                       (x + w - head, y + h - shaft), (x, y + h - shaft)]
                draw.polygon(pts, fill=fill or outline)

            if not shape.has_text_frame:
                continue
            text_frame = shape.text_frame

            anchor_top = y
            cursor = anchor_top
            for para in text_frame.paragraphs:
                segments = list(paragraph_children(para))
                if not segments:
                    continue
                body = ""
                style_run = None
                for kind_tag, payload in segments:
                    if kind_tag == "br":
                        body += "\n"
                    else:
                        text, run = payload
                        body += text
                        if style_run is None and text.strip():
                            style_run = run
                if not body.strip():
                    continue

                size_pt = 14.0
                bold = False
                color = (74, 90, 80)
                if style_run is not None:
                    rpr = style_run.find(
                        "{http://schemas.openxmlformats.org/drawingml/2006/main}rPr")
                    if rpr is not None:
                        sz = rpr.get("sz")
                        if sz:
                            size_pt = int(sz) / 100.0
                        bold = rpr.get("b") == "1"
                        srgb = rpr.find(
                            "{http://schemas.openxmlformats.org/drawingml/2006/main}solidFill/"
                            "{http://schemas.openxmlformats.org/drawingml/2006/main}srgbClr")
                        if srgb is not None and srgb.get("val"):
                            val = srgb.get("val")
                            color = (int(val[0:2], 16), int(val[2:4], 16), int(val[4:6], 16))

                base_path = fonts.bold_path if bold else fonts.regular_path
                px = max(6, round(size_pt * px_per_pt))
                ascent, descent = fonts.pil(base_path, px).getmetrics()
                line_spacing = para.line_spacing if isinstance(para.line_spacing, float) else 1.18
                advance = (ascent + descent) * line_spacing

                align = str(para.alignment) if para.alignment is not None else "LEFT"
                for hard_line in body.split("\n"):
                    for line in wrap_line(hard_line, fonts, base_path, px, w):
                        if line:
                            line_w = measure(line, fonts, base_path, px)
                            if "CENTER" in align:
                                start = x + (w - line_w) / 2
                            elif "RIGHT" in align:
                                start = x + w - line_w
                            else:
                                start = x
                            draw_mixed(draw, start, cursor, line, fonts, base_path,
                                       px, color)
                        cursor += advance
                space_after = para.space_after
                if space_after is not None:
                    cursor += space_after.pt * px_per_pt
                else:
                    cursor += 7 * px_per_pt

        target = out_dir / "slide-{:02d}.png".format(index)
        img.save(str(target), optimize=True)
        images.append(img.convert("RGB"))

    if want_pdf:
        pdf_path = out_dir / "ReLoop-Pitch-Deck-TechGaint.pdf"
        images[0].save(str(pdf_path), save_all=True, append_images=images[1:],
                       resolution=scale)

    return len(images)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--width", type=int, default=1800, help="pixels per slide (default 1800)")
    ap.add_argument("--no-pdf", action="store_true", help="skip the combined PDF")
    args = ap.parse_args()

    root = Path(__file__).resolve().parent.parent
    deck = root / DECK
    if not deck.exists():
        raise SystemExit("deck not found: {} (run scripts/build-pitch-deck.py first)".format(deck))

    count = render(deck, root / OUT_DIR, args.width, not args.no_pdf)
    print("rendered {} slides -> {} ({}px wide)".format(count, OUT_DIR, args.width))
    return 0


if __name__ == "__main__":
    sys.exit(main())
