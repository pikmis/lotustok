"""anim/margyt_spin.webm: the cookie turns, MargyT's note stays still and is cut out of it.
One colour only, so Telegram can repaint it (adaptive emoji)."""
import math, os, subprocess, sys
from PIL import Image, ImageDraw, ImageChops
H = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(H))
S = 4; B = 100 * S; c = B / 2; N = 45


def note(draw, cx, cy, scale):
    p = lambda x, y: (cx + (x - 54) * scale, cy + (y - 53) * scale)
    top = lambda x: 30 - (x - 42) * 8 / 40
    for hx, hy in ((37, 76), (71, 68)):
        draw.ellipse([p(hx - 11, hy - 8), p(hx + 11, hy + 8)], fill=255)
    draw.polygon([p(42, top(42)), p(48, top(48)), p(48, 76), p(42, 76)], fill=255)
    draw.polygon([p(76, top(76)), p(82, top(82)), p(82, 68), p(76, 68)], fill=255)
    draw.polygon([p(42, 30), p(82, 22), p(82, 34), p(42, 42)], fill=255)


fr = os.path.join(H, 'anim', 'frames_margyt'); os.makedirs(fr, exist_ok=True)
for i in range(N):
    rot = i / N * (2 * math.pi / 9)
    m = Image.new('L', (B, B), 0); R = B * 0.44
    ImageDraw.Draw(m).polygon([(c + R * (1 + 0.07 * math.cos(9 * t)) * math.cos(t + rot),
                                c + R * (1 + 0.07 * math.cos(9 * t)) * math.sin(t + rot))
                               for t in [k * 2 * math.pi / 1440 for k in range(1440)]], fill=255)
    # gentle pulse of the note, seamless over the loop
    sc = B / 100 * 0.92 * (1 + 0.05 * math.sin(2 * math.pi * i / N))
    hole = Image.new('L', (B, B), 0); note(ImageDraw.Draw(hole), c + 1.5 * S, c + 1 * S, sc)
    a = ImageChops.subtract(m, hole)
    img = Image.new('RGBA', (B, B), (255, 255, 255, 0)); img.putalpha(a)
    img.resize((100, 100), Image.LANCZOS).save(os.path.join(fr, 'f%03d.png' % i))
out = os.path.join(H, 'anim', 'margyt_spin.webm')
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', '30', '-i', os.path.join(fr, 'f%03d.png'),
                '-c:v', 'libvpx-vp9', '-pix_fmt', 'yuva420p', '-b:v', '0', '-crf', '30', '-an', out], check=True)
print(out, os.path.getsize(out))
