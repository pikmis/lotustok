# -*- coding: utf-8 -*-
"""Сборка квадратной карусели 1080x1080 для анонса LotusTok 1.2.0."""
import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = r"C:\Users\Boxvo\OneDrive\Documentos\project\LotusTok"
HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "fonts")
OUT = os.path.join(HERE, "out")

S = 1080
BG = (10, 10, 12)
LAVENDER = (233, 226, 250)
PILL_BG = (236, 227, 251)
PILL_FG = (18, 14, 26)
HEADER = (104, 104, 116)
SUB = (196, 191, 210)
WHITE = (255, 255, 255)

SHOT_X, SHOT_W = 180, 720
SHOT_TOP = 128
SHOT_R = 34


def font(weight, size):
    return ImageFont.truetype(os.path.join(FONTS, f"Onest-{weight}.ttf"), size)


def blob_mask(size, lobes=12, amp=0.052):
    """Круглый значок с волнистым краем."""
    k = 4
    big = size * k
    mask = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(mask)
    cx = cy = big / 2
    r = big / 2 * (1 - amp)
    pts = []
    steps = 720
    for i in range(steps):
        t = 2 * math.pi * i / steps
        rr = r * (1 + amp * math.cos(lobes * t))
        pts.append((cx + rr * math.cos(t), cy + rr * math.sin(t)))
    d.polygon(pts, fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def petals_alpha(size):
    """Чёрный знак из четырёх лепестков без белого фона."""
    src = Image.open(os.path.join(ROOT, "assets", "petals.jpg")).convert("L")
    a = src.point(lambda v: max(0, min(255, int((255 - v) * 1.06))))
    a = a.filter(ImageFilter.GaussianBlur(0.6))
    bbox = a.getbbox()
    a = a.crop(bbox)
    w, h = a.size
    scale = size / max(w, h)
    a = a.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
    return a


def paste_center(canvas, img, box):
    x, y, w, h = box
    canvas.paste(img, (x + (w - img.width) // 2, y + (h - img.height) // 2), img)


def cover_top(img, w, h):
    """Вписать по ширине, лишнее по высоте обрезать снизу."""
    scale = w / img.width
    nw, nh = w, round(img.height * scale)
    img = img.resize((nw, nh), Image.LANCZOS)
    if nh < h:
        pad = Image.new("RGB", (w, h), BG)
        pad.paste(img, (0, 0))
        return pad
    return img.crop((0, 0, w, h))


def fade(height, start, end, color=BG):
    """Вертикальная заливка от прозрачного к цвету фона."""
    g = Image.new("RGBA", (1, height))
    for y in range(height):
        if y <= start:
            a = 0
        elif y >= end:
            a = 255
        else:
            t = (y - start) / (end - start)
            t = t * t * (3 - 2 * t)
            a = round(255 * t)
        g.putpixel((0, y), color + (a,))
    return g.resize((S, height))


def draw_text_block(img, title, subtitle):
    d = ImageDraw.Draw(img)
    max_w = S - 2 * 58
    size = 96
    while size > 56:
        f = font(700, size)
        lines = wrap(title, f, max_w)
        if len(lines) <= 2 and all(d.textlength(l, font=f) <= max_w for l in lines):
            break
        size -= 2
    f = font(700, size)
    lines = wrap(title, f, max_w)
    lh = round(size * 1.06)
    sub_f = font(400, 36)
    sub_y = 1046 - 42  # верх подписи
    last_bottom = sub_y - 18
    y = last_bottom - len(lines) * lh
    for line in lines:
        d.text((57, y), line, font=f, fill=WHITE)
        y += lh
    d.text((58, sub_y - 6), subtitle, font=sub_f, fill=SUB)


def wrap(text, f, max_w):
    words = text.split()
    lines, cur = [], ""
    probe = ImageDraw.Draw(Image.new("RGB", (10, 10)))
    for w in words:
        t = (cur + " " + w).strip()
        if probe.textlength(t, font=f) <= max_w or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def build(slide):
    img = Image.new("RGB", (S, S), BG)

    # скриншот
    shot = Image.open(slide["shot"]).convert("RGB")
    shot = cover_top(shot, SHOT_W, S - SHOT_TOP)
    h = shot.height
    mask = Image.new("L", (SHOT_W, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, SHOT_W - 1, h + SHOT_R), SHOT_R, fill=255)
    img.paste(shot, (SHOT_X, SHOT_TOP), mask)

    # затемнение снизу под текст
    img = img.convert("RGBA")
    img.alpha_composite(fade(S - 600, 40, 430))
    img = img.convert("RGB")

    # значок
    bsize = 64
    box = (48, 50, bsize, bsize)
    bm = blob_mask(bsize)
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    blob = Image.new("RGBA", (bsize, bsize), LAVENDER + (255,))
    layer.paste(blob, (box[0], box[1]), bm)
    mark = petals_alpha(round(bsize * 0.56))
    paste_center(layer, mark, (box[0], box[1], bsize, bsize))
    img = Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")

    d = ImageDraw.Draw(img)

    # подпись сверху
    d.text((S // 2, 82), "В этом обновлении", font=font(500, 32), fill=HEADER, anchor="mm")

    # плашка версии
    pw, ph = 104, 60
    px, py = S - 48 - pw, 51
    d.rounded_rectangle((px, py, px + pw, py + ph), ph // 2, fill=PILL_BG)
    d.text((px + pw // 2, py + ph // 2 + 1), "1.2.0", font=font(600, 30), fill=PILL_FG, anchor="mm")

    draw_text_block(img, slide["title"], slide["sub"])
    return img


SLIDES = [
    {
        "title": "Несколько фото",
        "sub": "Отмечай нужные и скачивай сразу",
        "shot": os.path.join(ROOT, "promo", "carousel-1.2.0", "src", "download.png"),
    },
]

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for i, s in enumerate(SLIDES, 1):
        p = os.path.join(OUT, f"slide_{i}.png")
        build(s).save(p)
        print(p)