"""Draw assets/promo/lotustok_giveaway.png: a giveaway of three custom badges."""
import math, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
H=os.path.dirname(os.path.abspath(__file__)); ROOT=os.path.dirname(os.path.dirname(H))
F=os.environ['LOCALAPPDATA']+'/Microsoft/Windows/Fonts/GoogleSans-VariableFont_GRAD,opsz,wght.ttf'
def font(sz,w): f=ImageFont.truetype(F,sz); f.set_variation_by_axes([18,w,0]); return f
S=2; W=1080*S; c=W//2
img=Image.new('RGB',(W,W),(12,12,12)); d=ImageDraw.Draw(img)
def cookie(cx,cy,R,fill):
    d.polygon([(cx+R*(1+0.08*math.cos(9*t))*math.cos(t), cy+R*(1+0.08*math.cos(9*t))*math.sin(t))
               for t in [i*2*math.pi/1440 for i in range(1440)]], fill=fill)
d.text((c,120*S),'Розыгрыш',font=font(56*S,500),fill=(165,165,165),anchor='ms')
d.text((c,250*S),'3 кастомных',font=font(118*S,700),fill='white',anchor='ms')
d.text((c,370*S),'бейджа',font=font(118*S,700),fill='white',anchor='ms')
# three badges waiting for their owners
for cx,R,col in ((c-300*S,105*S,(60,60,60)),(c,140*S,(255,255,255)),(c+300*S,105*S,(60,60,60))):
    cookie(cx,590*S,R,col)
    d.text((cx,590*S),'?',font=font(round(R*1.15),700),fill=(12,12,12) if col==(255,255,255) else 'white',anchor='mm')
# how it looks next to a name
d.text((c,830*S),'Свой значок рядом с твоим ником в LotusTok',font=font(40*S,500),fill=(165,165,165),anchor='ms')
nf=font(56*S,700); t='@твой_ник'; tw=d.textlength(t,font=nf); B=52*S; x=c-(tw+16*S+B)/2; y=905*S
d.text((x,y),t,font=nf,fill='white',anchor='lm'); cookie(x+tw+16*S+B/2,y,B/2.2,(255,255,255))
p=Image.open(os.path.join(ROOT,'assets','petals.jpg')).convert('L').resize((1280,1280),Image.LANCZOS)
mk=p.point(lambda v:255 if v<128 else 0).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.MinFilter(9))
L=26*S; mk=mk.crop(mk.getbbox()).resize((L,L),Image.LANCZOS)
img.paste(Image.new('RGB',(L,L),(12,12,12)),(round(x+tw+16*S+B/2-L/2),round(y-L/2)),mk)
d.text((c,1020*S),'@LotusTok',font=font(40*S,700),fill=(120,120,120),anchor='ms')
img.resize((1080,1080),Image.LANCZOS).save(os.path.join(H,'lotustok_giveaway.png'))
