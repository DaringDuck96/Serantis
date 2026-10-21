"""Convert the influence armour models (Blockbench "bedrock" .bbmodel, one whole suit each) for the battle add-on.

Usage:  python3 tools/convert_armor.py <battle add-on folder> <zip or folder> [...]

Also takes single boots (<name>_hoverboots.bbmodel: the Winged Boots, <name>_tpboots.bbmodel: the Teleporting Boots),
saved as winged_boots / teleporting_boots, and the Helm of Linx (<name>_linxhelm.bbmodel), saved as linx_helmet.
Each armour input holds one <name>_epic_armor.bbmodel with bones head, body, rightArm, leftArm, rightLeg, leftLeg; the name
says its influence (see NAMES). The suit is cut into the four pieces you can wear:
  helmet      the head bone
  chestplate  the body bone above the waist, and both arms
  leggings    the body bone below the waist (belt, skirt), and the legs above the ankle
  boots       the legs below the ankle
Writes, inside <add-on>/assets/:
  ahrivaea/geo/creature/serantis_armor/<influence>_<piece>.geo.json   (loaded by the creatures mod's model loader)
  serantis_battle/textures/entity/armor/<influence>.png
  serantis_battle/equipment/<influence>.json                          (no vanilla layers: the 3D model is drawn instead)
"""
import base64, copy, glob, json, os, re, sys, tempfile, zipfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from convert_bbmodels import geometry

NAMES = {"infernal_warplate": "fire", "glacial_aegis": "frost", "tempest_regalia": "storm", "mountain_bastion": "earth",
         "abyssal_tide": "water", "verdant_warden": "nature", "riftwalker_plate": "void", "moonpetal_raiment": "fae",
         "nightshroud_plate": "darkness", "runeforged_mantle": "arcane", "sunforged_paladin": "radiant"}
# Single boots (every cube is the boots piece): file suffix -> the add-on's name for them.
BOOTS = {"_hoverboots": "winged", "_tpboots": "teleporting"}
# Single helmets (every cube is the helmet piece).
HELMS = {"_linxhelm": "linx"}
WAIST, ANKLE = 12.0, 5.0      # Blockbench y: below the waist is leggings, below the ankle is boots

def piece_of(bone, e):
    cy = (e["from"][1] + e["to"][1]) / 2
    if bone == "head": return "helmet"
    if bone == "body": return "chestplate" if cy >= WAIST else "leggings"
    if bone in ("rightArm", "leftArm"): return "chestplate"
    return "leggings" if cy >= ANKLE else "boots"

def convert(addon, src):
    work = tempfile.mkdtemp()
    if src.endswith(".zip"): zipfile.ZipFile(src).extractall(work)
    else: work = src
    for path in sorted(glob.glob(os.path.join(work, "**/*.bbmodel"), recursive=True)):
        base = os.path.basename(path)[:-len(".bbmodel")]
        boots = next((v for k, v in BOOTS.items() if base.endswith(k)), None)
        helm = next((v for k, v in HELMS.items() if base.endswith(k)), None)
        if boots: inf, name, pieces = boots, base, ("boots",)
        elif helm: inf, name, pieces, boots = helm, base, ("helmet",), True
        elif base.endswith("_epic_armor"): name = base[:-len("_epic_armor")]; inf = NAMES[name]; pieces = ("helmet", "chestplate", "leggings", "boots")
        else: continue
        bb = json.load(open(path))
        elems = {e["uuid"]: e for e in bb["elements"]}
        counts = {}
        for piece in pieces:
            one = copy.deepcopy(bb)
            for bone in one["outliner"]:
                bone["children"] = [c for c in bone.get("children", [])
                                    if isinstance(c, str) and c in elems and (boots or piece_of(bone["name"], elems[c]) == piece)]
            one["outliner"] = [b for b in one["outliner"] if b["children"]]
            geo = geometry(one, f"geometry.serantis_armor_{inf}_{piece}")
            counts[piece] = sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])
            gdir = os.path.join(addon, "assets/ahrivaea/geo/creature/serantis_armor"); os.makedirs(gdir, exist_ok=True)
            json.dump(geo, open(os.path.join(gdir, f"{inf}_{piece}.geo.json"), "w"), indent=1)
        tdir = os.path.join(addon, "assets/serantis_battle/textures/entity/armor"); os.makedirs(tdir, exist_ok=True)
        open(os.path.join(tdir, inf + ".png"), "wb").write(base64.b64decode(bb["textures"][0]["source"].split(",", 1)[1]))
        edir = os.path.join(addon, "assets/serantis_battle/equipment"); os.makedirs(edir, exist_ok=True)
        json.dump({"layers": {}}, open(os.path.join(edir, inf + ".json"), "w"), indent=2)
        print(f"{inf:9} {name:18} " + "  ".join(f"{p}={n}" for p, n in counts.items()))

if __name__ == "__main__":
    for s in sys.argv[2:]: convert(sys.argv[1], s)
