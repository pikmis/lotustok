"""Draw assets/promo/lotustok_tomorrow.png: the 'update tomorrow at 3' teaser."""
import math, os, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter
H=os.path.dirname(os.path.abspath(__file__)); ROOT=os.path.dirname(os.path.dirname(H))
F=os.environ['LOCALAPPDATA']+'/Microsoft/Windows/Fonts/GoogleSans-VariableFont_GRAD,opsz,wght.ttf'
def font(sz,w): f=ImageFont.truetype(F,sz); f.set_variation_by_axes([18,w,0]); return f
S=2; W,Hh=1920*S,1080*S; cx=W//2
img=Image.new('RGB',(W,Hh),(12,12,12)); d=ImageDraw.Draw(img)
# faint outlines of M3 shapes scattered behind everything
sq=lambda t:(abs(math.cos(t))**5+abs(math.sin(t))**5)**(-1/5)*0.86
shapes=[lambda t:1+0.08*math.cos(9*t), lambda t:0.9+0.15*math.cos(4*t), lambda t:1+0.05*math.cos(12*t),
        sq, lambda t:1.0, lambda t:0.85+0.2*math.cos(3*t), lambda t:0.9+0.12*math.cos(5*t)]
# a few big, soft M3 shapes in tones just above the black, cut by the edges
big=[(150,140,520,0,(24,24,24),0.3),(1780,120,430,1,(28,28,28),0.6),(1700,960,560,2,(22,22,22),0.1),
     (220,980,470,3,(27,27,27),0.4),(960,1180,620,0,(19,19,19),0.2),(980,-120,380,5,(20,20,20),0.9)]
for x,y,R,k,col,rot in big:
    f=shapes[k]
    d.polygon([(x*S+R*S*f(t)*math.cos(t+rot), y*S+R*S*f(t)*math.sin(t+rot)) for t in [i*2*math.pi/720 for i in range(720)]],fill=col)
# the tile with the petals
T=220*S; ty=170*S
d.rounded_rectangle((cx-T//2,ty,cx+T//2,ty+T),radius=60*S,fill=(255,255,255))
p=Image.open(os.path.join(ROOT,'assets','petals.jpg')).convert('L').resize((1280,1280),Image.LANCZOS)
mk=p.point(lambda v:255 if v<128 else 0).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.MinFilter(9))
L=128*S; mk=mk.crop(mk.getbbox()).resize((L,L),Image.LANCZOS)
img.paste(Image.new('RGB',(L,L),(12,12,12)),(cx-L//2,ty+(T-L)//2),mk)
d.text((cx,580*S),'LotusTok',font=font(56*S,700),fill=(160,160,160),anchor='ms')
d.text((cx,730*S),'Обнова завтра в 3',font=font(150*S,700),fill='white',anchor='ms')
t='если завтра ничего не случилось, прочитайте снова'; f=font(44*S,500)
tw=d.textlength(t,font=f); ph=96*S; px=48*S; y0=860*S
d.rounded_rectangle((cx-tw/2-px,y0,cx+tw/2+px,y0+ph),radius=ph//2,fill='white')
d.text((cx,y0+ph/2),t,font=f,fill=(12,12,12),anchor='mm')
img.resize((1920,1080),Image.LANCZOS).save(os.path.join(H,'lotustok_tomorrow.png'))
