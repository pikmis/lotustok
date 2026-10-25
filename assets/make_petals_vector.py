"""Turn the four-petal mark (assets/petals.jpg) into the path a 24-unit icon is drawn from.

    python3 assets/make_petals_vector.py

The picture is black on white. It is enlarged, so that a curve is a curve and not
a staircase, cut at half grey, and its edges are walked; the points that add
nothing are then dropped. A region of ink winds one way and a hole in it the other,
so a plain (non-zero) fill leaves the holes open -- which is how the outlined petals
and the cut-outs in the filled ones come out.

The mark is set in the 24-unit square the settings icons use, 20 units across. What
it prints is written to margyt/petals.py (PATH) and to SettingsGlyph.java ("lotus").
"""
import os
import sys

import cv2
import numpy

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "assets", "petals.jpg")
VIEWPORT = 24.0
SPAN = 20.0
ENLARGE = 4
#: how far, in picture pixels, a dropped point may have been from the line that replaces it
TOLERANCE = 0.25
#: the smallest piece of ink or hole worth keeping, in square units: the hairline between the petals is not
SMALLEST = 0.02


def contours():
    grey = cv2.imread(SOURCE, cv2.IMREAD_GRAYSCALE)
    big = cv2.resize(grey, None, fx=ENLARGE, fy=ENLARGE, interpolation=cv2.INTER_CUBIC)
    ink = (big < 128).astype(numpy.uint8) * 255
    found, tree = cv2.findContours(ink, cv2.RETR_TREE, cv2.CHAIN_APPROX_NONE)
    return found, tree[0]


def depth(tree, index):
    d = 0
    while tree[index][3] != -1:
        index = tree[index][3]
        d += 1
    return d


def main():
    found, tree = contours()
    everything = numpy.concatenate([c.reshape(-1, 2) for c in found])
    low, high = everything.min(axis=0), everything.max(axis=0)
    scale = SPAN / max(high - low)
    middle = (low + high) / 2.0
    parts = []
    for index, contour in enumerate(found):
        approx = cv2.approxPolyDP(contour, TOLERANCE * ENLARGE, True).reshape(-1, 2).astype(float)
        points = (approx - middle) * scale + VIEWPORT / 2.0
        x, y = points[:, 0], points[:, 1]
        area = 0.5 * float(numpy.dot(x, numpy.roll(y, -1)) - numpy.dot(y, numpy.roll(x, -1)))
        if abs(area) < SMALLEST or len(points) < 3:
            continue
        # ink (even depth) winds one way, holes the other
        want = 1 if depth(tree, index) % 2 == 0 else -1
        if (area > 0) != (want > 0):
            points = points[::-1]
        parts.append(points)
    path = " ".join("M " + " L ".join("%s %s" % (_n(px), _n(py)) for px, py in part) + " Z" for part in parts)
    sys.stdout.write(path + "\n")
    sys.stderr.write("%d outlines, %d points, %d characters\n"
                     % (len(parts), sum(len(p) for p in parts), len(path)))


def _n(value):
    text = ("%.2f" % value).rstrip("0").rstrip(".")
    return text if text not in ("", "-0") else "0"


if __name__ == "__main__":
    main()
