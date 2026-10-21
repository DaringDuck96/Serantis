"""Convert the Magic Staff models (Blockbench "bedrock" .bbmodel) into Java item models.

Usage:  python3 tools/convert_staffs.py <battle add-on folder> <zip or folder> [...]

Each input holds one <name>_staff.bbmodel; the name says its influence (see NAMES). Writes, inside
<add-on>/assets/serantis_battle/: models/item/<influence>_staff.json (3D, centred, with hand/GUI poses),
textures/item/<influence>_staff.png and items/<influence>_staff.json.
"""
import base64, glob, json, os, sys, tempfile, zipfile

NAMES = {"emberwrath": "fire", "rimespire": "frost", "thundercall": "storm", "stoneheart": "earth", "tidecaller": "water",
         "thornbloom": "nature", "riftwalker": "void", "moonpetal": "fae", "gloomreaver": "darkness", "runeweaver": "arcane",
         "dawnbringer": "radiant"}
NS = "serantis_battle"
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 1, 0.5], "scale": [0.75, 0.75, 0.75]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 1, 0.5], "scale": [0.75, 0.75, 0.75]},
    "firstperson_righthand": {"rotation": [0, -90, 10], "translation": [1, -1, 1], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, 90, -10], "translation": [1, -1, 1], "scale": [0.6, 0.6, 0.6]},
    "gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.42, 0.42, 0.42]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 8, 0], "scale": [0.6, 0.6, 0.6]},
}

def r(x): x = round(float(x), 4); return 0 if x == 0 else x

def convert(addon, path):
    name = os.path.basename(path)[:-len("_staff.bbmodel")]
    inf = NAMES[name]
    bb = json.load(open(path))
    els = bb["elements"]
    lo = [min(e["from"][i] for e in els) for i in range(3)]
    hi = [max(e["to"][i] for e in els) for i in range(3)]
    off = [8 - (lo[0] + hi[0]) / 2, 8 - (lo[1] + hi[1]) / 2, 8 - (lo[2] + hi[2]) / 2]      # centre it on the item's middle
    k = 16.0 / bb["resolution"]["width"]
    elements = []
    for e in els:
        f, t = e["from"], e["to"]
        el = {"from": [r(f[i] + off[i]) for i in range(3)], "to": [r(t[i] + off[i]) for i in range(3)]}
        rot = e.get("rotation", [0, 0, 0])
        if any(rot):
            o = e.get("origin", [0, 0, 0])
            el["rotation"] = {"origin": [r(o[i] + off[i]) for i in range(3)], "x": r(rot[0]), "y": r(rot[1]), "z": r(rot[2]), "rescale": False}
        faces = {}
        for face, d in e.get("faces", {}).items():
            if d.get("texture") is None or d.get("texture") is False: continue
            fc = {"uv": [r(v * k) for v in d["uv"]], "texture": "#0"}
            if d.get("rotation"): fc["rotation"] = d["rotation"]
            faces[face] = fc
        if faces: el["faces"] = faces; elements.append(el)
    a = os.path.join(addon, "assets", NS)
    tex = f"{NS}:item/{inf}_staff"
    json.dump({"texture_size": [bb["resolution"]["width"], bb["resolution"]["height"]], "textures": {"0": tex, "particle": tex},
               "elements": elements, "display": DISPLAY}, open(os.path.join(a, "models/item", inf + "_staff.json"), "w"), indent=1)
    open(os.path.join(a, "textures/item", inf + "_staff.png"), "wb").write(base64.b64decode(bb["textures"][0]["source"].split(",", 1)[1]))
    json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/{inf}_staff"}}, open(os.path.join(a, "items", inf + "_staff.json"), "w"), indent=2)
    print(f"{inf:9} {name:12} {len(elements):3} elements  {(hi[1] - lo[1]) / 16:.2f} blocks long")

if __name__ == "__main__":
    for src in sys.argv[2:]:
        if src.endswith(".zip"):
            work = tempfile.mkdtemp(); zipfile.ZipFile(src).extractall(work); src = work
        for p in glob.glob(os.path.join(src, "**/*_staff.bbmodel"), recursive=True): convert(sys.argv[1], p)
