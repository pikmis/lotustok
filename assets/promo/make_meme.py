"""Draw assets/promo/lotustok_meme.png: the 'nobody: / LotusTok:' meme with the wavy bar."""
import math, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
H=os.path.dirname(os.path.abspath(__file__)); ROOT=os.path.dirname(os.path.dirname(H))
F=os.environ['LOCALAPPDATA']+'/Microsoft/Windows/Fonts/GoogleSans-VariableFont_GRAD,opsz,wght.ttf'
def font(sz,w): f=ImageFont.truetype(F,sz); f.set_variation_by_axes([18,w,0]); return f
S=2; W=1080*S
img=Image.new('RGB',(W,W),(12,12,12)); d=ImageDraw.Draw(img)
x=80*S
d.text((x,190*S),'Никто:',font=font(64*S,500),fill=(150,150,150),anchor='ls')
d.text((x,290*S),'Абсолютно никто:',font=font(64*S,500),fill=(150,150,150),anchor='ls')
d.text((x,420*S),'LotusTok:',font=font(84*S,700),fill='white',anchor='ls')
for k,t in enumerate(['а давайте сделаем','полосу прокрутки','волнистой']):
    d.text((x,530*S+k*96*S),t,font=font(80*S,700),fill='white',anchor='ls')
# the wavy progress bar itself, played and unplayed part
y=870*S; x0=80*S; x1=1000*S; split=640*S; amp=16*S; per=58*S
pts=[(xx, y+amp*math.sin(2*math.pi*(xx-x0)/per)) for xx in range(x0, split, 2)]
d.line(pts,fill='white',width=14*S,joint='curve')
d.rounded_rectangle((split+30*S,y-6*S,x1,y+6*S),radius=6*S,fill=(80,80,80))
d.ellipse((split-4*S-18*S,pts[-1][1]-18*S,split-4*S+18*S,pts[-1][1]+18*S),fill='white')
# signature
p=Image.open(os.path.join(ROOT,'assets','petals.jpg')).convert('L').resize((1280,1280),Image.LANCZOS)
mk=p.point(lambda v:255 if v<128 else 0).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.MinFilter(9))
L=54*S; mk=mk.crop(mk.getbbox()).resize((L,L),Image.LANCZOS)
img.paste(Image.new('RGB',(L,L),(120,120,120)),(x0,980*S),mk)
d.text((x0+L+18*S,980*S+L/2),'@LotusTok',font=font(40*S,700),fill=(120,120,120),anchor='lm')
img.resize((1080,1080),Image.LANCZOS).save(os.path.join(H,'lotustok_meme.png'))
