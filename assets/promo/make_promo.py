"""Draw assets/promo/lotustok_promo.png: a square to drop in TikTok comments."""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
H=os.path.dirname(os.path.abspath(__file__)); ROOT=os.path.dirname(os.path.dirname(H))
F=os.environ['LOCALAPPDATA']+'/Microsoft/Windows/Fonts/GoogleSans-VariableFont_GRAD,opsz,wght.ttf'
def font(sz,w): f=ImageFont.truetype(F,sz); f.set_variation_by_axes([18,w,0]); return f
S=2; W=1080*S; BG=(12,12,12)
img=Image.new('RGB',(W,W),BG); d=ImageDraw.Draw(img); c=W//2
p=Image.open(os.path.join(ROOT,'assets','petals.jpg')).convert('L').resize((1280,1280),Image.LANCZOS)
mk=p.point(lambda v:255 if v<128 else 0).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.MinFilter(9))
L=170*S; mk=mk.crop(mk.getbbox()).resize((L,L),Image.LANCZOS)
img.paste(Image.new('RGB',(L,L),'white'),(c-L//2,110*S),mk)
d.text((c,445*S),'LotusTok',font=font(150*S,700),fill='white',anchor='ms')
d.text((c,515*S),'Мод на TikTok для Android',font=font(44*S,500),fill=(165,165,165),anchor='ms')
# what it does, as chips
rows=[['Material 3','Редактор интерфейса'],['Папки в чатах','Скачивание фото']]
f=font(40*S,500); y=585*S; ph=84*S; gap=20*S; px=36*S
for row in rows:
    ws=[d.textlength(t,font=f)+2*px for t in row]; x=c-(sum(ws)+gap*(len(row)-1))/2
    for t,w in zip(row,ws):
        d.rounded_rectangle((x,y,x+w,y+ph),radius=ph//2,outline=(90,90,90),width=3*S)
        d.text((x+w/2,y+ph/2),t,font=f,fill='white',anchor='mm'); x+=w+gap
    y+=ph+gap
d.text((c,885*S),'Скачать в Telegram',font=font(40*S,500),fill=(165,165,165),anchor='ms')
tg=Image.open(os.path.join(H,'tg.png')).convert('RGB'); T=88*S; tg=tg.resize((T,T),Image.LANCZOS)
fh=font(76*S,700); tw=d.textlength('@LotusTok',font=fh); g=22*S; x=c-(T+g+tw)/2; yy=915*S
img.paste(tg,(round(x),yy)); d.text((x+T+g,yy+T/2),'@LotusTok',font=fh,fill='white',anchor='lm')
img.resize((1080,1080),Image.LANCZOS).save(os.path.join(H,'lotustok_promo.png'))
