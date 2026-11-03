"""Generates every Carpoolr brand asset from one geometry source.
Mark: S-curve route, solid end dots (2 destinations), ring stop in the middle."""
import math, os, json, cairosvg
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from PIL import Image

OUT = os.path.dirname(os.path.abspath(__file__))
GREEN, CREAM = "#004720", "#EFEEE0"
FONT = "/workspace/work/Anton.ttf"

# geometry in normalized units (matches approved icon v6)
A, B, M = (-0.8, 0.42), (0.8, -0.42), (0.0, 0.0)
SEG1 = [A, (-0.2, 0.55), (-0.35, 0.0), M]
SEG2 = [M, (0.35, 0.0), (0.2, -0.55), B]
LW, DOT_R, RING_R, HOLE_R = 44/330, 62/330, 70/330, 36/330

def lerp(p, q, t): return (p[0]+(q[0]-p[0])*t, p[1]+(q[1]-p[1])*t)
def split(c, t):
    p0,p1,p2,p3 = c
    a,b,cc = lerp(p0,p1,t), lerp(p1,p2,t), lerp(p2,p3,t)
    d,e = lerp(a,b,t), lerp(b,cc,t); f = lerp(d,e,t)
    return [p0,a,d,f],[f,e,cc,p3]
def pt(c,t): return split(c,t)[0][3]
def t_at_dist(c, center, r, from_end):
    lo,hi = (0.0,1.0)
    # distance to center is monotonic near the ring end; bisection
    for _ in range(60):
        mid=(lo+hi)/2; p=pt(c,mid); d=math.dist(p,center)
        if from_end:   # segment ends at center: find t where d==r (d decreasing)
            lo,hi = (mid,hi) if d>r else (lo,mid)
        else:          # segment starts at center (d increasing)
            lo,hi = (lo,mid) if d>r else (mid,hi)
    return (lo+hi)/2
# trim both segments so the line stops at the ring edge (ring hole stays clean)
s1 = split(SEG1, t_at_dist(SEG1, M, RING_R*0.98, True))[0]
s2 = split(SEG2, t_at_dist(SEG2, M, RING_R*0.98, False))[1]

def f(v): return f"{v:.3f}".rstrip("0").rstrip(".")
def cubic_d(c, T): 
    p=[T(q) for q in c]
    return f"M{f(p[0][0])},{f(p[0][1])} C{f(p[1][0])},{f(p[1][1])} {f(p[2][0])},{f(p[2][1])} {f(p[3][0])},{f(p[3][1])}"
def circle_d(c, r, T, s):
    x,y=T(c); r*=s
    return f"M{f(x-r)},{f(y)} a{f(r)},{f(r)} 0 1,0 {f(2*r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2*r)},0 Z"

def mark_parts(cx, cy, s, k=1.0):
    T=lambda p:(cx+p[0]*s, cy+p[1]*s)
    return dict(
        line=cubic_d(s1,T)+" "+cubic_d(s2,T), lw=LW*s*k,
        dots=circle_d(A,DOT_R,T,s)+" "+circle_d(B,DOT_R,T,s),
        ring=circle_d(M,RING_R,T,s)+" "+circle_d(M,HOLE_R,T,s))

def mark_svg_elems(cx, cy, s, fg, k=1.0):
    p=mark_parts(cx,cy,s,k)
    return (f'<path d="{p["line"]}" fill="none" stroke="{fg}" stroke-width="{f(p["lw"])}" stroke-linecap="round"/>'
            f'<path d="{p["dots"]}" fill="{fg}"/>'
            f'<path d="{p["ring"]}" fill="{fg}" fill-rule="evenodd"/>')

def wordmark(text, x_center, baseline, size):
    font=TTFont(FONT); gs=font.getGlyphSet(); cmap=font.getBestCmap()
    upm=font["head"].unitsPerEm; sc=size/upm
    names=[cmap[ord(ch)] for ch in text]
    width=sum(font["hmtx"][n][0] for n in names)*sc
    x=x_center-width/2; ds=[]
    for n in names:
        pen=SVGPathPen(gs); tp=TransformPen(pen,(sc,0,0,-sc,x,baseline))
        gs[n].draw(tp); ds.append(pen.getCommands()); x+=font["hmtx"][n][0]*sc
    return " ".join(ds)

def svg(w,h,body,bg=None):
    r=f'<rect width="{w}" height="{h}" fill="{bg}"/>' if bg else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">{r}{body}</svg>\n'

def write(name, data):
    os.makedirs(os.path.dirname(os.path.join(OUT,name)), exist_ok=True)
    open(os.path.join(OUT,name),"w").write(data)

# --- SVGs
full_body = mark_svg_elems(512,360,330,CREAM)+f'<path d="{wordmark("carpoolr",512,800,200*1.0)}" fill="{CREAM}"/>'
write("svg/carpoolr-logo.svg", svg(1024,1024,full_body,GREEN))
write("svg/carpoolr-logo-transparent.svg", svg(1024,1024,full_body))
write("svg/carpoolr-mark.svg", svg(512,512,mark_svg_elems(256,256,248,CREAM)))
write("svg/carpoolr-mark-green.svg", svg(512,512,mark_svg_elems(256,256,220,GREEN)))
write("svg/carpoolr-app-icon.svg", svg(512,512,mark_svg_elems(256,256,200,CREAM),GREEN))
write("svg/carpoolr-wordmark.svg", svg(900,260,f'<path d="{wordmark("carpoolr",450,215,250)}" fill="{CREAM}"/>'))
# rounded favicon svg (modern browsers)
fav=('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">'
     f'<rect width="64" height="64" rx="14" fill="{GREEN}"/>'+mark_svg_elems(32,32,28,CREAM,1.5)+'</svg>\n')
write("favicon/favicon.svg", fav)

# --- PNGs + ICO from the SVG sources
def png(src, out, size):
    cairosvg.svg2png(url=os.path.join(OUT,src), write_to=os.path.join(OUT,out), output_width=size, output_height=size)
os.makedirs(os.path.join(OUT,"favicon"),exist_ok=True); os.makedirs(os.path.join(OUT,"png"),exist_ok=True)
for s in (16,32,48): png("favicon/favicon.svg", f"favicon/favicon-{s}x{s}.png", s)
png("svg/carpoolr-app-icon.svg","favicon/apple-touch-icon.png",180)
png("svg/carpoolr-app-icon.svg","favicon/android-chrome-192x192.png",192)
png("svg/carpoolr-app-icon.svg","favicon/android-chrome-512x512.png",512)
png("svg/carpoolr-logo.svg","png/carpoolr-logo-1024.png",1024)
png("svg/carpoolr-mark.svg","png/carpoolr-mark-512.png",512)
Image.open(os.path.join(OUT,"favicon/favicon-48x48.png")).save(os.path.join(OUT,"favicon/favicon.ico"),sizes=[(16,16),(32,32),(48,48)])
json.dump({"name":"Carpoolr","short_name":"Carpoolr","icons":[
  {"src":"/android-chrome-192x192.png","sizes":"192x192","type":"image/png"},
  {"src":"/android-chrome-512x512.png","sizes":"512x512","type":"image/png"}],
  "theme_color":GREEN,"background_color":GREEN,"display":"standalone"},
  open(os.path.join(OUT,"favicon/site.webmanifest"),"w"),indent=2)
write("favicon/head-snippet.html",
 '<link rel="icon" href="/favicon.ico" sizes="48x48">\n<link rel="icon" href="/favicon.svg" type="image/svg+xml">\n'
 '<link rel="apple-touch-icon" href="/apple-touch-icon.png">\n<link rel="manifest" href="/site.webmanifest">\n'
 f'<meta name="theme-color" content="{GREEN}">\n')

# --- Android vector drawables (108dp adaptive canvas, mark inside 66dp safe zone)
def avd(name, parts, fg, comment):
    x=f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="{parts['line']}" android:strokeColor="{fg}" android:strokeWidth="{f(parts['lw'])}" android:strokeLineCap="round" android:fillColor="#00000000"/>
    <path android:pathData="{parts['dots']}" android:fillColor="{fg}"/>
    <path android:pathData="{parts['ring']}" android:fillColor="{fg}" android:fillType="evenOdd"/>
</vector>
'''
    write(name,x)
p=mark_parts(54,54,27)
avd("android/drawable/ic_launcher_foreground.xml",p,CREAM,"Carpoolr route mark, generated by brand/generate.py")
avd("android/drawable/ic_launcher_monochrome.xml",p,"#FFFFFFFF","Themed-icon layer, generated by brand/generate.py")
# in-app logo mark (24dp viewport)
q=mark_parts(12,12,5.6)
write("android/drawable/ic_route_mark.xml",f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="{q['line']}" android:strokeColor="#FFFFFFFF" android:strokeWidth="{f(q['lw'])}" android:strokeLineCap="round" android:fillColor="#00000000"/>
    <path android:pathData="{q['dots']}" android:fillColor="#FFFFFFFF"/>
    <path android:pathData="{q['ring']}" android:fillColor="#FFFFFFFF" android:fillType="evenOdd"/>
</vector>
''')
print("ok")
