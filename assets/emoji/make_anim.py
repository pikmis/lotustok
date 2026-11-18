"""anim/lotustok_spin.webm: the cookie turns, the petals stay still and are cut out of it.
One colour only, so Telegram can repaint it to the text colour (adaptive emoji)."""
import math, os, subprocess
from PIL import Image, ImageDraw, ImageChops, ImageFilter
H=os.path.dirname(os.path.abspath(__file__)); ROOT=os.path.dirname(os.path.dirname(H))
S=4; B=100*S; c=B/2; N=45   # 1.5 s at 30 fps
p=Image.open(os.path.join(ROOT,'assets','petals.jpg')).convert('L').resize((1280,1280),Image.LANCZOS)
mk=p.point(lambda v:255 if v<128 else 0).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.MinFilter(9))
L=round(B*0.45); mk=mk.crop(mk.getbbox()).resize((L,L),Image.LANCZOS)
hole=Image.new('L',(B,B),0); hole.paste(mk,(round(c-L/2),round(c-L/2)))
fr=os.path.join(H,'anim','frames')
for i in range(N):
    rot=i/N*(2*math.pi/9)          # the cookie repeats every 40 degrees: a seamless loop
    m=Image.new('L',(B,B),0); R=B*0.44
    ImageDraw.Draw(m).polygon([(c+R*(1+0.07*math.cos(9*t))*math.cos(t+rot), c+R*(1+0.07*math.cos(9*t))*math.sin(t+rot))
                               for t in [k*2*math.pi/1440 for k in range(1440)]],fill=255)
    a=ImageChops.subtract(m,hole)
    img=Image.new('RGBA',(B,B),(255,255,255,0)); img.putalpha(a)
    img.resize((100,100),Image.LANCZOS).save(os.path.join(fr,'f%03d.png'%i))
out=os.path.join(H,'anim','lotustok_spin.webm')
subprocess.run(['ffmpeg','-y','-loglevel','error','-framerate','30','-i',os.path.join(fr,'f%03d.png'),
                '-c:v','libvpx-vp9','-pix_fmt','yuva420p','-b:v','0','-crf','30','-an',out],check=True)
print(out, os.path.getsize(out))
