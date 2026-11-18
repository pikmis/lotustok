"""Draw icons/launcher/dark.png and icons/launcher/cookie.png: two more launcher icons."""
import math, os
from PIL import Image, ImageDraw, ImageFilter
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
N=432; S=4; B=N*S
p=Image.open(os.path.join(ROOT,'assets','petals.jpg')).convert('L').resize((1280,1280),Image.LANCZOS)
mask=p.point(lambda v:255 if v<128 else 0).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.MinFilter(9))
mask=mask.crop(mask.getbbox())   # the petals with the hairline seams closed
def petals(img,size,colour):
    m=mask.resize((size,size),Image.LANCZOS)
    img.paste(Image.new('RGB',(size,size),colour),((B-size)//2,(B-size)//2),m)

# Тёмная: the plain icon turned over, white petals on black
img=Image.new('RGB',(B,B),(12,12,12)); petals(img,round(B*0.54),(255,255,255))
img.resize((N,N),Image.LANCZOS).save(os.path.join(ROOT,'icons','launcher','dark.png'))

# Волнистая: the petals on the M3 Expressive cookie, in the bloom icon's lilacs
img=Image.new('RGB',(B,B),(232,222,248)); d=ImageDraw.Draw(img); c=B/2; R=B*0.36
d.polygon([(c+R*(1+0.08*math.cos(9*t))*math.cos(t), c+R*(1+0.08*math.cos(9*t))*math.sin(t))
           for t in [i*2*math.pi/1440 for i in range(1440)]], fill=(33,0,93))
petals(img,round(B*0.36),(232,222,248))
img.resize((N,N),Image.LANCZOS).save(os.path.join(ROOT,'icons','launcher','cookie.png'))
