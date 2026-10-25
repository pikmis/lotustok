"""Little labels (Beta, Soon, Free) made of two Telegram custom emoji that join into one pill.

    python3 assets/make_tg_labels.py

Each label is drawn once, as one picture 200 px wide, and cut down the middle into two 100x100 emoji.
Put side by side in a message, with nothing between them, they read as one rounded label that is as wide as the
two of them. The word is set in the middle of the pill, and the cut falls where it falls.

Writes assets/tg_emoji/labels/NN_<label>_1.png and NN_<label>_2.png and a sheet, _labels.png, to look at.
"""
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "assets", "tg_emoji", "labels")
FONT = "C:/Windows/Fonts/segoeuib.ttf"
SS = 8                      # drawn eight times bigger, shrunk after
TILE = 100
HEIGHT = 88                 # how tall the pill is in a 100 px tile
EDGE = 2                    # how far the ends of the pill are from the edge of the two tiles
PAD = 24                    # the least room between the end of the pill and a letter

WHITE, BLACK = (0xFF, 0xFF, 0xFF), (0x00, 0x00, 0x00)    # as the icon: black on white, no rim

#: text, (unused: where a cut in a gap would be), pill colour, text colour
LABELS = [
    ("Beta", 2, WHITE, BLACK),
    ("Soon", 2, WHITE, BLACK),
    ("Free", 2, WHITE, BLACK),
]


def fit(text):
    """The biggest size whose text fits in the pill with room at both ends and leaves air above and below."""
    for size in range(64, 20, -1):
        font = ImageFont.truetype(FONT, size * SS)
        box = font.getbbox(text, anchor="ls")
        if (box[2] - box[0]) <= (2 * TILE - 2 * EDGE - 2 * PAD) * SS and (box[3] - box[1]) <= 0.52 * HEIGHT * SS:
            return font
    return ImageFont.truetype(FONT, 20 * SS)


def build(text, cut, pill, ink):
    font = fit(text)
    box = font.getbbox(text, anchor="ls")
    left_end, right_end = EDGE * SS, (2 * TILE - EDGE) * SS
    start = TILE * SS - (box[0] + box[2]) / 2.0        # the middle of the word in the middle of the pill
    canvas = Image.new("RGBA", (2 * TILE * SS, TILE * SS), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    pill_height = HEIGHT * SS
    top = (TILE * SS - pill_height) / 2.0
    draw.rounded_rectangle((left_end, top, right_end, top + pill_height), radius=pill_height / 2.0, fill=pill + (255,))
    baseline = TILE * SS / 2.0 - (box[1] + box[3]) / 2.0
    draw.text((start, baseline), text, font=font, fill=ink + (255,), anchor="ls")
    return canvas.resize((2 * TILE, TILE), Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    for old in os.listdir(OUT):
        if old.endswith(".png"):
            os.remove(os.path.join(OUT, old))
    number = 25
    shown = []
    for text, cut, pill, ink in LABELS:
        picture = build(text, cut, pill, ink)
        for half in (0, 1):
            number += 1
            tile = picture.crop((half * TILE, 0, (half + 1) * TILE, TILE))
            tile.save(os.path.join(OUT, "%02d_%s_%d.png" % (number, text.lower(), half + 1)))
        shown.append(picture)
    sheet = Image.new("RGB", (2 * TILE * 2 + 40, len(shown) * 130), (255, 255, 255))
    for i, picture in enumerate(shown):
        light = Image.new("RGB", picture.size, (255, 255, 255))
        light.paste(picture, (0, 0), picture)
        dark = Image.new("RGB", picture.size, (24, 37, 51))
        dark.paste(picture, (0, 0), picture)
        sheet.paste(light, (10, i * 130 + 10))
        sheet.paste(dark, (10 + 2 * TILE + 20, i * 130 + 10))
    sheet = sheet.resize((sheet.width * 2, sheet.height * 2), Image.LANCZOS)
    sheet.save(os.path.join(OUT, "_labels.png"))
    print("%d emoji in %s" % (number - 25, OUT))


if __name__ == "__main__":
    main()
