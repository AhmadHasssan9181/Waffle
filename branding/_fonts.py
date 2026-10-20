import urllib.request, os
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

url = "https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/Manrope%5Bwght%5D.ttf"
src = "branding/Manrope-Variable.ttf"
if not os.path.exists(src):
    urllib.request.urlretrieve(url, src)
print("downloaded", os.path.getsize(src))

for w, name in [(400,"regular"),(500,"medium"),(600,"semibold"),(700,"bold"),(800,"extrabold")]:
    f = TTFont(src)
    inst = instantiateVariableFont(f, {"wght": w})
    out = f"app/src/main/res/font/manrope_{name}.ttf"
    inst.save(out)
    print(out, os.path.getsize(out))
