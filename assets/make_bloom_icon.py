import os

import cv2
import numpy

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "assets", "bloom.png")
OUT = os.path.join(ROOT, "margyt", "bloom_paths.py")
VIEWPORT = 108.0
SPAN = 46.0

SHOWN = 72.0
ENLARGE = 4
TOLERANCE = 0.3
SMALLEST = 0.5

def _n(value):
    text = ("%.2f" % value).rstrip("0").rstrip(".")
    return text if text not in ("", "-0") else "0"

def depth(tree, index):
    d = 0
    while tree[index][3] != -1:
        index = tree[index][3]
        d += 1
    return d

def trace(mask, place):

    big = cv2.resize(mask, None, fx=ENLARGE, fy=ENLARGE, interpolation=cv2.INTER_CUBIC)
    big = (big > 127).astype(numpy.uint8) * 255
    found, tree = cv2.findContours(big, cv2.RETR_TREE, cv2.CHAIN_APPROX_NONE)
    tree = tree[0]
    parts = []
    for index, contour in enumerate(found):
        approx = cv2.approxPolyDP(contour, TOLERANCE * ENLARGE, True).reshape(-1, 2).astype(float) / ENLARGE
        points = place(approx)
        x, y = points[:, 0], points[:, 1]
        area = 0.5 * float(numpy.dot(x, numpy.roll(y, -1)) - numpy.dot(y, numpy.roll(x, -1)))
        if abs(area) < SMALLEST or len(points) < 3:
            continue
        want = 1 if depth(tree, index) % 2 == 0 else -1
        if (area > 0) != (want > 0):
            points = points[::-1]
        parts.append(points)
    return " ".join("M " + " L ".join("%s %s" % (_n(px), _n(py)) for px, py in part) + " Z" for part in parts), len(parts)

def main():
    rgb = cv2.cvtColor(cv2.imread(SOURCE), cv2.COLOR_BGR2RGB).astype(int)
    luma = rgb.mean(axis=2)
    ink = (luma < 140).astype(numpy.uint8) * 255

    blob = ((rgb[:, :, 2] - rgb[:, :, 1] > 36) & (luma >= 140)).astype(numpy.uint8) * 255
    blob = cv2.morphologyEx(blob, cv2.MORPH_OPEN, numpy.ones((9, 9), numpy.uint8))

    count, labels, stats, _ = cv2.connectedComponentsWithStats(blob)
    keep = numpy.zeros_like(blob)
    for label in range(1, count):
        if stats[label, cv2.CC_STAT_AREA] > 4000:
            keep[labels == label] = 255
    blob = keep

    ys, xs = numpy.nonzero(ink)
    low = numpy.array([xs.min(), ys.min()], float)
    high = numpy.array([xs.max(), ys.max()], float)
    scale = SPAN / max(high - low)
    middle = (low + high) / 2.0
    mark, mark_parts = trace(ink, lambda p: (p - middle) * scale + VIEWPORT / 2.0)

    side = float(rgb.shape[0])

    def ground(p):

        q = p * (SHOWN / side) + (VIEWPORT - SHOWN) / 2.0
        edge = (VIEWPORT - SHOWN) / 2.0 + 0.6
        q[q <= edge] = 0.0
        q[q >= VIEWPORT - edge] = VIEWPORT
        return q

    blobs, blob_parts = trace(blob, ground)

    with open(OUT, "w", encoding="utf-8", newline="\n") as handle:
        handle.write('"""Written by assets/make_bloom_icon.py from assets/bloom.png. Do not edit."""\n\n')
        handle.write("MARK = (\n    %r\n)\n\n" % mark)
        handle.write("BLOBS = (\n    %r\n)\n" % blobs)
    print("mark: %d outlines; blobs: %d outlines -> %s" % (mark_parts, blob_parts, OUT))

if __name__ == "__main__":
    main()
