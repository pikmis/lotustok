"""The LotusTok mark inside the shapes of Material 3 Expressive, as a Telegram custom emoji pack.

    python3 assets/make_tg_emoji.py

Writes assets/tg_emoji/NN_<shape>_<filled|tonal>.png, 100x100 with a transparent corner, and a contact
sheet, tg_emoji/_sheet.png, to look at. The mark is the vector in margyt/petals.py; the radial shapes
(cookie, clover, sunny, flower, burst, pentagon, arch) are the ones the settings are drawn with
(Expressive.java), the rest are the plain ones of the Material 3 shape library.

The mark is black on white, as the icon is: every shape is white with the mark in black, no rim.
"""
import math
import os
import re
import sys

import cv2
import numpy

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
from margyt import petals  # noqa: E402

OUT = os.path.join(ROOT, "assets", "tg_emoji")
SS = 800          # drawn this big, shrunk to SIZE
SIZE = 100        # what Telegram wants of a custom emoji
WHITE = ((0xFF, 0xFF, 0xFF), (0x00, 0x00, 0x00))        # a white shape, the mark black


# ----------------------------------------------------------------------------- the mark

def mark_mask():
    """The petals as a mask, ink = 255, in a square whose side is the mark's longer side."""
    parts = []
    for sub in petals.PATH.split("Z"):
        points = re.findall(r"(-?[0-9.]+) (-?[0-9.]+)", sub)
        if points:
            parts.append(numpy.array([[float(x), float(y)] for x, y in points]))
    every = numpy.concatenate(parts)
    low, high = every.min(axis=0), every.max(axis=0)
    side = max(high - low)
    big = 1200
    pad = (side - (high - low)) / 2.0
    mask = numpy.zeros((big, big), numpy.uint8)
    for part in parts:
        scaled = ((part - low + pad) / side * (big - 1)).astype(numpy.int32)
        one = numpy.zeros_like(mask)
        cv2.fillPoly(one, [scaled], 255)
        mask ^= one                      # even-odd: a hole in the ink stays open
    return mask


# ----------------------------------------------------------------------------- the shapes

def radial(lobes, depth, radius=0.47):
    """The outline of Expressive.shape: tips reach the radius, valleys sit 2 * depth under it."""
    points = []
    steps = max(360, lobes * 40)
    for i in range(steps):
        t = 2 * math.pi * i / steps
        r = radius * (1.0 - depth * (1.0 - math.cos(lobes * (t + math.pi / 2))))
        points.append((0.5 + r * math.cos(t), 0.5 + r * math.sin(t)))
    return points


def polygon(vertices, rounding):
    return ("poly", vertices, rounding)


def ellipse(a, b, turn):
    points = []
    for i in range(360):
        t = 2 * math.pi * i / 360
        x, y = a * math.cos(t), b * math.sin(t)
        c, s = math.cos(turn), math.sin(turn)
        points.append((0.5 + x * c - y * s, 0.5 + x * s + y * c))
    return points


def heart():
    points = []
    for i in range(360):
        t = 2 * math.pi * i / 360
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        points.append((0.5 + x / 36.0, 0.5 + y / 36.0 - 0.01))
    return points


def rounded_box(left, top, right, bottom, rounding):
    return polygon([(left, top), (right, top), (right, bottom), (left, bottom)], rounding)


#: name -> (outline, mark scale, mark middle)
SHAPES = [
    ("circle", radial(1, 0.0, 0.48), 0.60, (0.5, 0.5)),
    ("square", rounded_box(0.05, 0.05, 0.95, 0.95, 0.22), 0.60, (0.5, 0.5)),
    ("slanted", polygon([(0.2, 0.08), (0.96, 0.08), (0.8, 0.92), (0.04, 0.92)], 0.14), 0.55, (0.5, 0.5)),
    ("pill", rounded_box(0.03, 0.2, 0.97, 0.8, 0.3), 0.46, (0.5, 0.5)),
    ("oval", ellipse(0.48, 0.34, math.radians(-45)), 0.46, (0.5, 0.5)),
    ("diamond", polygon([(0.5, 0.03), (0.97, 0.5), (0.5, 0.97), (0.03, 0.5)], 0.12), 0.44, (0.5, 0.5)),
    ("triangle", polygon([(0.5, 0.08), (0.95, 0.86), (0.05, 0.86)], 0.12), 0.40, (0.5, 0.6)),
    ("semicircle", polygon([(0.04, 0.78), (0.04, 0.5), (0.18, 0.28), (0.5, 0.1), (0.82, 0.28), (0.96, 0.5),
                            (0.96, 0.78)], 0.14), 0.40, (0.5, 0.46)),
    ("gem", polygon([(0.5, 0.03), (0.94, 0.27), (0.94, 0.73), (0.5, 0.97), (0.06, 0.73), (0.06, 0.27)], 0.12),
     0.52, (0.5, 0.5)),
    ("pentagon", radial(5, 0.075, 0.48), 0.52, (0.5, 0.53)),
    ("arch", radial(3, 0.10, 0.48), 0.48, (0.5, 0.52)),
    ("heart", heart(), 0.44, (0.5, 0.47)),
    ("cookie4", radial(4, 0.05), 0.56, (0.5, 0.5)),
    ("cookie6", radial(6, 0.045), 0.56, (0.5, 0.5)),
    ("cookie7", radial(7, 0.065), 0.56, (0.5, 0.5)),
    ("cookie9", radial(9, 0.050), 0.56, (0.5, 0.5)),
    ("cookie12", radial(12, 0.030), 0.58, (0.5, 0.5)),
    ("clover4", radial(4, 0.160), 0.46, (0.5, 0.5)),
    ("clover8", radial(8, 0.190), 0.46, (0.5, 0.5)),
    ("sunny", radial(8, 0.110), 0.50, (0.5, 0.5)),
    ("burst", radial(12, 0.040), 0.54, (0.5, 0.5)),
    ("softburst", radial(10, 0.025), 0.56, (0.5, 0.5)),
    ("flower", radial(6, 0.140), 0.48, (0.5, 0.5)),
    ("puffy", radial(10, 0.060), 0.54, (0.5, 0.5)),
]


def shape_alpha(outline):
    """The shape as an 8-bit mask SS x SS."""
    mask = numpy.zeros((SS, SS), numpy.uint8)
    if isinstance(outline, tuple) and outline[0] == "poly":
        _tag, vertices, rounding = outline
        pts = numpy.array([[x * SS, y * SS] for x, y in vertices], numpy.int32)
        cv2.fillPoly(mask, [pts], 255)
        radius = max(1, int(rounding * SS))
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * radius + 1, 2 * radius + 1))
        mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel)     # rounds the convex corners
    else:
        pts = numpy.array([[x * SS, y * SS] for x, y in outline], numpy.int32)
        cv2.fillPoly(mask, [pts], 255)
    return mask


def compose(shape_mask, mark, scale, middle, colours):
    background, ink = colours
    canvas = numpy.zeros((SS, SS, 4), numpy.float32)
    canvas[..., 0], canvas[..., 1], canvas[..., 2] = background
    alpha = shape_mask.astype(numpy.float32) / 255.0
    # the mark, set where the shape wants it
    size = int(scale * SS)
    small = cv2.resize(mark, (size, size), interpolation=cv2.INTER_AREA).astype(numpy.float32) / 255.0
    left = int(middle[0] * SS - size / 2)
    top = int(middle[1] * SS - size / 2)
    layer = numpy.zeros((SS, SS), numpy.float32)
    layer[top:top + size, left:left + size] = small
    layer *= alpha                                  # never outside the shape
    for c in range(3):
        canvas[..., c] = background[c] * (1 - layer) + ink[c] * layer
    canvas[..., 3] = alpha * 255
    out = cv2.resize(canvas, (SIZE, SIZE), interpolation=cv2.INTER_AREA)
    return numpy.clip(out, 0, 255).astype(numpy.uint8)


def main():
    from PIL import Image
    os.makedirs(OUT, exist_ok=True)
    for old in os.listdir(OUT):
        if old.endswith(".png"):
            os.remove(os.path.join(OUT, old))
    mark = mark_mask()
    names = []
    number = 0
    tiles = []
    for name, outline, scale, middle in SHAPES:
        mask = shape_alpha(outline)
        for label, colours in (("white", WHITE),):
            number += 1
            tile = compose(mask, mark, scale, middle, colours)
            path = os.path.join(OUT, "%02d_%s_%s.png" % (number, name, label))
            Image.fromarray(tile, "RGBA").save(path)
            names.append(path)
            tiles.append(tile)
    # the bare mark, black
    bare = cv2.resize(mark, (SIZE, SIZE), interpolation=cv2.INTER_AREA)
    for label, ink in (("black", (0, 0, 0)),):
        rgba = numpy.zeros((SIZE, SIZE, 4), numpy.uint8)
        rgba[..., 3] = bare
        rgba[..., :3] = ink
        number += 1
        path = os.path.join(OUT, "%02d_mark_%s.png" % (number, label))
        Image.fromarray(rgba, "RGBA").save(path)
        names.append(path)
        tiles.append(rgba)
    # a sheet to look at, on a light and a dark ground
    cols = 10
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 110, rows * 110 * 2), (255, 255, 255))
    for i, tile in enumerate(tiles):
        im = Image.fromarray(tile, "RGBA")
        x, y = (i % cols) * 110 + 5, (i // cols) * 220 + 5
        sheet.paste(im, (x, y), im)
        dark = Image.new("RGB", (100, 100), (30, 30, 34))
        dark.paste(im, (0, 0), im)
        sheet.paste(dark, (x, y + 110))
    sheet = sheet.resize((sheet.width * 2, sheet.height * 2), Image.LANCZOS)
    sheet.save(os.path.join(OUT, "_sheet.png"))
    print("%d emoji in %s" % (len(names), OUT))


if __name__ == "__main__":
    main()
