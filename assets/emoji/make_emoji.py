"""Telegram emoji for LotusTok: a black Material Symbol (Apache 2.0, in src/) on a
white M3 cookie, transparent around it. Writes 512/ and 100/ (Telegram's size)."""
import math, os
from PIL import Image, ImageDraw, ImageChops, ImageFilter
H=os.path.dirname(os.path.abspath(__file__)); ROOT=os.path.dirname(os.path.dirname(H))
names=open(os.path.join(H,'src','names.txt')).read().split()
grid=Image.open(os.path.join(H,'src','grid.png')).convert('L')
os.makedirs(os.path.join(H,'512'),exist_ok=True); os.makedirs(os.path.join(H,'100'),exist_ok=True)
S=4; B=512*S; c=B/2
def cookie():
    m=Image.new('L',(B,B),0); R=B*0.44
    ImageDraw.Draw(m).polygon([(c+R*(1+0.07*math.cos(9*t))*math.cos(t), c+R*(1+0.07*math.cos(9*t))*math.sin(t))
                                for t in [i*2*math.pi/1440 for i in range(1440)]],fill=255)
    return m
def emoji(glyph, size, name):
    g=glyph.crop(glyph.getbbox()); k=size*S/max(g.size)
    g=g.resize((max(1,round(g.width*k)),max(1,round(g.height*k))),Image.LANCZOS)
    hole=Image.new('L',(B,B),0); hole.paste(g,(round(c-g.width/2),round(c-g.height/2)))
    alpha=cookie()
    img=Image.new('RGBA',(B,B),(255,255,255,0))
    img.paste(Image.new('RGBA',(B,B),(255,255,255,255)),(0,0),alpha)
    img.paste(Image.new('RGBA',(B,B),(12,12,12,255)),(0,0),ImageChops.multiply(hole,alpha))
    big=img.resize((512,512),Image.LANCZOS); big.save(os.path.join(H,'512',name+'.png'))
    big.resize((100,100),Image.LANCZOS).save(os.path.join(H,'100',name+'.png'))
for i,n in enumerate(names):
    cell=grid.crop(((i%5)*400,(i//5)*400,(i%5)*400+400,(i//5)*400+400))
    emoji(ImageChops.invert(cell), 250, n)     # dark glyph -> mask
p=Image.open(os.path.join(ROOT,'assets','petals.jpg')).convert('L').resize((1280,1280),Image.LANCZOS)
mk=p.point(lambda v:255 if v<128 else 0).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.MinFilter(9))
emoji(mk,230,'lotustok')
# a sheet to look at them all
fs=sorted(os.listdir(os.path.join(H,'512')))
sheet=Image.new('RGB',(5*220,math.ceil(len(fs)/5)*220),(40,40,40))
for i,f in enumerate(fs):
    im=Image.open(os.path.join(H,'512',f)).resize((200,200),Image.LANCZOS); sheet.paste(im,((i%5)*220+10,(i//5)*220+10),im)
sheet.save(os.path.join(H,'sheet.png')); print(len(fs))
