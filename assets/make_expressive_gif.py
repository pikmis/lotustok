"""Render assets/lotustok_expressive.gif (or, with the argument margyt, margyt_expressive.gif): the petals in an M3 Expressive shape
that morphs cookie -> clover -> sunny -> squircle and back, changing colour."""
import math, os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
HERE=os.path.dirname(os.path.abspath(__file__))
F=os.environ['LOCALAPPDATA']+'/Microsoft/Windows/Fonts/GoogleSans-VariableFont_GRAD,opsz,wght.ttf'
def font(sz,w): f=ImageFont.truetype(F,sz); f.set_variation_by_axes([18,w,0]); return f
MARGYT=len(sys.argv)>1 and sys.argv[1]=='margyt'
S=2; N=640*S; c=N/2; BG=(245,251,245) if MARGYT else (254,247,255)
p=Image.open(HERE+'/petals.jpg').convert('L').resize((1280,1280),Image.LANCZOS)
mk=p.point(lambda v:255 if v<128 else 0).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.MinFilter(9))
mk=mk.crop(mk.getbbox())
sq=lambda t: (abs(math.cos(t))**5+abs(math.sin(t))**5)**(-1/5)*0.86
shapes=[lambda t:1+0.07*math.cos(9*t), lambda t:0.9+0.15*math.cos(4*t+math.pi), lambda t:1+0.05*math.cos(12*t), sq]
cols=[(0,108,76),(61,99,115),(77,99,87),(28,44,36)] if MARGYT else [(103,80,164),(125,82,96),(56,106,32),(33,0,93)]
def note(draw, cx, cy, scale):
    """MargyT's beamed note (make_banner.py geometry), with the stems cut along
    the slant of the beam so the right stem's corner does not poke out above it."""
    p=lambda x,y: (cx+(x-54)*scale, cy+(y-53)*scale)
    top=lambda x: 30-(x-42)*8/40          # the beam's upper edge
    for hx,hy in ((37,76),(71,68)):
        draw.ellipse([p(hx-11,hy-8),p(hx+11,hy+8)],fill='white')
    draw.polygon([p(42,top(42)),p(48,top(48)),p(48,76),p(42,76)],fill='white')
    draw.polygon([p(76,top(76)),p(82,top(82)),p(82,68),p(76,68)],fill='white')
    draw.polygon([p(42,30),p(82,22),p(82,34),p(42,42)],fill='white')
def mix(a,b,k): return tuple(round(x+(y-x)*k) for x,y in zip(a,b))
def backio(x,s=1.7):
    s*=1.525
    return (2*x)**2*((s+1)*2*x-s)/2 if x<0.5 else ((2*x-2)**2*((s+1)*(2*x-2)+s)+2)/2
HOLD,MORPH=10,14; seg=HOLD+MORPH; total=seg*len(shapes); frames=[]
fw=font(84*S,700)
for i in range(total):
    k,j=divmod(i,seg); a,b=k,(k+1)%len(shapes)
    e=0 if j<HOLD else backio((j-HOLD)/MORPH)
    rot=i/total*(2*math.pi/9)*2   # the cookie repeats every 40 degrees, so the loop has no seam
    R=200*S; pts=[]
    for q in range(360):
        t=q/360*2*math.pi; r=R*(shapes[a](t)*(1-e)+shapes[b](t)*e)
        pts.append((c+r*math.cos(t+rot), c-40*S+r*math.sin(t+rot)))
    img=Image.new('RGB',(N,N),BG); d=ImageDraw.Draw(img)
    d.polygon(pts,fill=mix(cols[a],cols[b],max(0,min(1,e))))
    bounce=1 if j<HOLD else 1-0.18*math.sin(math.pi*(j-HOLD)/MORPH)
    if MARGYT:
        note(d, c, c-40*S, 2.6*S*bounce)
    else:
        L=round(170*S*bounce); m=mk.resize((L,L),Image.LANCZOS)
        img.paste(Image.new('RGB',(L,L),'white'),(round(c-L/2),round(c-40*S-L/2)),m)
    d.text((c,N-70*S),'MargyT' if MARGYT else 'LotusTok',font=fw,fill=(29,27,32),anchor='ms')
    frames.append(img.resize((640,640),Image.LANCZOS).quantize(colors=48))
out=HERE+('/margyt_expressive.gif' if MARGYT else '/lotustok_expressive.gif')
frames[0].save(out,save_all=True,append_images=frames[1:],duration=40,loop=0,optimize=True)
print(out,len(frames),os.path.getsize(out))
