"""Convert the villager profession models (Blockbench .bbmodel) for the Serantis battle add-on.

Usage:  python3 tools/convert_villagers.py <addon folder> <zip or folder> [...]

Each input is one villager look, named like "old_farmer_medium_models" or "wild_town_guard_medium_models":
four .bbmodel files (normal, _enraged, _mutated, _enraged_mutated). Writes, inside <addon folder>/assets/ahrivaea/:
  geo/creature/villager/<look>.geo.json and .animation.json
  textures/entity/creature/villager/<look>[_enraged|_mutated|_enraged_mutated].png
The models are drawn at their real size, like the creatures mod's own villager.
"""
import base64, glob, json, os, re, sys, tempfile, zipfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from convert_bbmodels import geometry, animations, longest_side

def convert(addon, src):
    work = tempfile.mkdtemp()
    if src.endswith(".zip"):
        zipfile.ZipFile(src).extractall(work); label = os.path.basename(src)[:-4]
    else:
        work = src; label = os.path.basename(src.rstrip("/"))
    label = re.sub(r"^[0-9a-f]{8}-", "", label)
    m = re.match(r"(?:old|wild)_(.+?)_(?:small|medium|large)(?:_models)?$", label)
    if not m: raise SystemExit(f"Can't read the villager from '{label}'")
    look = m.group(1)
    files = sorted(glob.glob(os.path.join(work, "**/*.bbmodel"), recursive=True))
    variant = lambda p: next((v for v in ("_enraged_mutated", "_enraged", "_mutated") if p.endswith(v + ".bbmodel")), "")
    by = {variant(p): p for p in files}
    base = json.load(open(by[""]))
    geo = geometry(base, "geometry.villager_" + look)
    geo["minecraft:geometry"][0]["description"]["ahrivaea:real_size"] = True
    anim = animations(base)
    gdir = os.path.join(addon, "assets/ahrivaea/geo/creature/villager"); os.makedirs(gdir, exist_ok=True)
    tdir = os.path.join(addon, "assets/ahrivaea/textures/entity/creature/villager"); os.makedirs(tdir, exist_ok=True)
    json.dump(geo, open(os.path.join(gdir, f"{look}.geo.json"), "w"), indent=1)
    json.dump(anim, open(os.path.join(gdir, f"{look}.animation.json"), "w"), indent=1)
    for v, p in by.items():
        png = base64.b64decode(json.load(open(p))["textures"][0]["source"].split(",", 1)[1])
        open(os.path.join(tdir, f"{look}{v}.png"), "wb").write(png)
    bones = geo["minecraft:geometry"][0]["bones"]
    print(f"{look:22} {len(bones):3} bones  anims={','.join(anim['animations'])}  size={round(longest_side(geo), 2)}  skins={len(by)}")

if __name__ == "__main__":
    for s in sys.argv[2:]: convert(sys.argv[1], s)
