"""Convert the Gym Master Stone trophy models (Blockbench "bedrock_block" .bbmodel) into Java block models.

Usage:  python3 tools/convert_trophies.py <battle add-on folder> <zip or folder of trophy_*.bbmodel>

Writes, inside <add-on>/assets/serantis_battle/: models/block/<id>.json, textures/block/<id>.png,
blockstates/<id>.json (facing the player who placed it) and items/<id>.json; and the block's loot table
in <add-on>/data/serantis_battle/loot_table/blocks/<id>.json. Element rotations on any axes are written as
Minecraft's x/y/z rotation (26.x block models).
"""
import base64, glob, json, os, sys, tempfile, zipfile

IDS = {"wild_quartz": "none", "ember_ruby": "fire", "rime_diamond": "frost", "storm_citrine": "storm",
       "stoneheart_topaz": "earth", "tidal_aquamarine": "water", "grove_emerald": "nature", "void_amethyst": "void",
       "fae_rose_heart": "fae", "shadow_onyx": "darkness", "arcane_spellstone": "arcane", "sunstone": "radiant"}
NS = "serantis_battle"
DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.45, 0.45, 0.45]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
}

def r(x): x = round(float(x), 4); return 0 if x == 0 else x

def block_id(name):
    if name == "prismatic_gem": return "eternita_stone"
    return IDS[name] + "_master_stone"

def convert(addon, path):
    bb = json.load(open(path))
    name = os.path.basename(path)[len("trophy_"):-len("_trophy.bbmodel")]
    bid = block_id(name)
    res = bb["resolution"]["width"]
    k = 16.0 / res
    elements = []
    for e in bb["elements"]:
        f, t = e["from"], e["to"]
        el = {"from": [r(f[0] + 8), r(f[1]), r(f[2] + 8)], "to": [r(t[0] + 8), r(t[1]), r(t[2] + 8)]}
        rot = e.get("rotation", [0, 0, 0])
        if any(rot):
            o = e.get("origin", [0, 0, 0])
            el["rotation"] = {"origin": [r(o[0] + 8), r(o[1]), r(o[2] + 8)], "x": r(rot[0]), "y": r(rot[1]), "z": r(rot[2]), "rescale": False}
        faces = {}
        for face, d in e.get("faces", {}).items():
            if d.get("texture") is None or d.get("texture") is False: continue
            fc = {"uv": [r(v * k) for v in d["uv"]], "texture": "#0"}
            if d.get("rotation"): fc["rotation"] = d["rotation"]
            faces[face] = fc
        if faces:
            el["faces"] = faces
            elements.append(el)
    tex = f"{NS}:block/{bid}"
    model = {"credit": "Serantis trophy", "texture_size": [res, res], "textures": {"0": tex, "particle": tex},
             "elements": elements, "display": DISPLAY}
    a = os.path.join(addon, "assets", NS)
    for d in ("models/block", "textures/block", "blockstates", "items"): os.makedirs(os.path.join(a, d), exist_ok=True)
    json.dump(model, open(os.path.join(a, "models/block", bid + ".json"), "w"), indent=1)
    png = base64.b64decode(bb["textures"][0]["source"].split(",", 1)[1])
    open(os.path.join(a, "textures/block", bid + ".png"), "wb").write(png)
    variants = {f"facing={f}": ({"model": f"{NS}:block/{bid}", "y": y} if y else {"model": f"{NS}:block/{bid}"})
                for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
    json.dump({"variants": variants}, open(os.path.join(a, "blockstates", bid + ".json"), "w"), indent=2)
    json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:block/{bid}"}}, open(os.path.join(a, "items", bid + ".json"), "w"), indent=2)
    lt = os.path.join(addon, "data", NS, "loot_table/blocks"); os.makedirs(lt, exist_ok=True)
    json.dump({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{bid}"}],
               "conditions": [{"condition": "minecraft:survives_explosion"}]}], "random_sequence": f"{NS}:blocks/{bid}"},
              open(os.path.join(lt, bid + ".json"), "w"), indent=2)
    top = max(el["to"][1] for el in elements)
    print(f"{bid:22} {len(elements):3} elements  {top / 16:.2f} blocks tall")

if __name__ == "__main__":
    src = sys.argv[2]
    if src.endswith(".zip"):
        work = tempfile.mkdtemp(); zipfile.ZipFile(src).extractall(work); src = work
    for p in sorted(glob.glob(os.path.join(src, "**/trophy_*.bbmodel"), recursive=True)): convert(sys.argv[1], p)
