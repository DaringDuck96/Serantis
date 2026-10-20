"""Convert Blockbench .bbmodel files into the files Ahrivaea Creatures reads.

Usage:  python3 tools/convert_bbmodels.py <addon folder> <zip or folder> [<zip or folder> ...]

Each input is one creature: four .bbmodel files (normal, _enraged, _mutated, _enraged_mutated)
that share a model and animations and differ only in their skin. The creature is worked out
from the zip/folder name, e.g. "coral_drill_shark_large_models.zip" -> drill_shark (water),
matched against the creature files already in the add-on.

Writes, inside <addon folder>/assets/ahrivaea/:
  geo/creature/<id>/<influence>.geo.json          (Bedrock geometry 1.12.0, same layout as the originals)
  geo/creature/<id>/<influence>.animation.json    (idle / attack / fly, plain keyframe lists like the originals)
  textures/entity/creature/<id>/<influence>[_enraged|_mutated|_enraged_mutated].png
and updates the creature's "size" to the model's longest side in blocks, the way the originals are sized.
"""
import base64, glob, io, json, math, os, re, sys, tempfile, zipfile

PREFIX = {"coral": "water", "briar": "nature", "astral": "arcane", "duskborn": "darkness",
          "ashen": "fire", "snowveil": "frost", "squall": "storm", "mossback": "earth",
          "umbral": "void", "gossamer": "fae", "hallowed": "radiant", "old": "none",
          "arcane": "arcane"}
ALIASES = {"blooming_horror": "bloom_eye_horror", "scrap_goblin": "scrap_gobblin",   # artist name -> book name
           "shardheart_colossus": "shardheart", "snowfang_wolf": "snowfang"}
SIZE_CLASS = {"small": 0.7, "medium": 1.0, "large": 1.6, "extra_large": 1.6}
FPS = 24                                                   # sampling rate for smooth curves

def r(x, n=4):
    x = round(float(x), n)
    return 0 if x == 0 else x

# ---------------- geometry ----------------
def geometry(bb, ident):
    elems = {e["uuid"]: e for e in bb["elements"]}
    bones, used = [], set()

    def cube(e):
        f, t = e["from"], e["to"]
        inf = e.get("inflate", 0)
        size = [r(t[i] - f[i]) for i in range(3)]
        c = {"origin": [r(-(f[0] + size[0])), r(f[1]), r(f[2])], "size": size}
        if inf: c["inflate"] = r(inf)
        rot = e.get("rotation", [0, 0, 0])
        if any(rot):
            o = e.get("origin", [0, 0, 0])
            c["pivot"] = [r(-o[0]), r(o[1]), r(o[2])]
            c["rotation"] = [r(-rot[0]), r(-rot[1]), r(rot[2])]
        uv = {}
        for face, d in e.get("faces", {}).items():
            if d.get("texture") is None or d.get("texture") is False: continue
            u = d["uv"]
            if face in ("up", "down"):
                fu = {"uv": [r(u[2]), r(u[3])], "uv_size": [r(u[0] - u[2]), r(u[1] - u[3])]}
            else:
                fu = {"uv": [r(u[0]), r(u[1])], "uv_size": [r(u[2] - u[0]), r(u[3] - u[1])]}
            if d.get("rotation"): fu["uv_rotation"] = d["rotation"]
            uv[face] = fu
        c["uv"] = uv
        if e.get("mirror_uv"): c["mirror"] = True
        return c

    def walk(node, parent):
        if isinstance(node, str):            # a cube directly under the root
            return [node]
        if node.get("export") is False: return []
        b = {"name": node["name"]}
        if parent: b["parent"] = parent
        o = node.get("origin", [0, 0, 0])
        b["pivot"] = [r(-o[0]), r(o[1]), r(o[2])]
        rot = node.get("rotation", [0, 0, 0])
        if any(rot): b["rotation"] = [r(-rot[0]), r(-rot[1]), r(rot[2])]
        if node.get("mirror_uv"): b["mirror"] = True
        cubes, loose = [], []
        bones.append(b)
        for ch in node.get("children", []):
            if isinstance(ch, str):
                e = elems.get(ch)
                if e and e.get("type", "cube") == "cube" and e.get("export", True) is not False:
                    cubes.append(cube(e)); used.add(ch)
            else:
                walk(ch, node["name"])
        if cubes: b["cubes"] = cubes
        return loose

    root_loose = []
    for n in bb["outliner"]:
        root_loose += walk(n, None)
    if root_loose:                            # Blockbench puts root-level cubes in a "bb_main" bone
        bones.insert(0, {"name": "bb_main", "pivot": [0, 0, 0],
                         "cubes": [cube(elems[u]) for u in root_loose if u in elems]})
    tex = bb["textures"][0]
    desc = {"identifier": ident, "texture_width": tex.get("uv_width", bb["resolution"]["width"]),
            "texture_height": tex.get("uv_height", bb["resolution"]["height"]), "ahrivaea:real_size": True}
    return {"format_version": "1.12.0", "minecraft:geometry": [{"description": desc, "bones": bones}]}

def longest_side(geo):
    lo, hi = [1e9] * 3, [-1e9] * 3
    for b in geo["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            for i in range(3):
                lo[i] = min(lo[i], c["origin"][i]); hi[i] = max(hi[i], c["origin"][i] + c["size"][i])
    return max(hi[i] - lo[i] for i in range(3)) / 16

# ---------------- animations ----------------
def num(v):
    try: return float(v)
    except (TypeError, ValueError): return None

def catmull(p0, p1, p2, p3, t):
    t2, t3 = t * t, t * t * t
    return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)

def channel(kfs):
    kfs = sorted(kfs, key=lambda k: k["time"])
    vals = [[k["data_points"][0].get(a, "0") for a in "xyz"] for k in kfs]
    if len(kfs) == 1 and kfs[0]["time"] == 0:
        return [str(v) for v in vals[0]]                     # constant or molang expression
    numeric = all(num(v) is not None for row in vals for v in row)
    smooth = numeric and any(k.get("interpolation") == "catmullrom" for k in kfs)
    out = {}
    if not smooth:
        for k, row in zip(kfs, vals):
            out[f"{r(k['time'])}"] = [str(v) if num(v) is None else str(r(num(v), 3)) for v in row]
        return out
    pts = [[num(v) for v in row] for row in vals]
    times = [k["time"] for k in kfs]
    for i in range(len(kfs) - 1):
        a, b = times[i], times[i + 1]
        steps = max(1, math.ceil((b - a) * FPS))
        cm = kfs[i].get("interpolation") == "catmullrom" or kfs[i + 1].get("interpolation") == "catmullrom"
        for s in range(steps):
            t = s / steps
            if cm:
                p = [catmull(pts[max(i - 1, 0)][ax], pts[i][ax], pts[i + 1][ax], pts[min(i + 2, len(pts) - 1)][ax], t) for ax in range(3)]
            else:
                p = [pts[i][ax] + (pts[i + 1][ax] - pts[i][ax]) * t for ax in range(3)]
            out[f"{r(a + (b - a) * t)}"] = [str(r(x, 3)) for x in p]
    out[f"{r(times[-1])}"] = [str(r(x, 3)) for x in pts[-1]]
    return out

def animations(bb):
    anims = {}
    for a in bb.get("animations", []):
        name = a["name"].split(".")[-1]
        entry = {"loop": a.get("loop") == "loop", "animation_length": r(a.get("length", 0))}
        if a.get("loop") == "hold": entry["loop"] = "hold_on_last_frame"
        bones = {}
        for an in a.get("animators", {}).values():
            if an.get("type", "bone") != "bone": continue
            by = {}
            for k in an.get("keyframes", []): by.setdefault(k["channel"], []).append(k)
            ch = {c: channel(k) for c, k in by.items() if c in ("rotation", "position", "scale")}
            if ch: bones[an["name"]] = ch
        entry["bones"] = bones
        anims[name] = entry
    return {"format_version": "1.8.0", "animations": anims}

# ---------------- one creature ----------------
def creature_of(label, creature_dir):
    m = re.match(r"([a-z]+)_(.+?)_(small|medium|extra_large|large)_models$", label)
    if not m: raise SystemExit(f"Can't read creature from '{label}'")
    prefix, name, sclass = m.groups()
    name = ALIASES.get(name, name)
    path = os.path.join(creature_dir, name + ".json")
    if not os.path.exists(path): raise SystemExit(f"No creature called '{name}' in the add-on ({label})")
    return name, PREFIX[prefix], sclass, path

def convert(addon, src):
    work = tempfile.mkdtemp()
    if src.endswith(".zip"):
        zipfile.ZipFile(src).extractall(work); label = os.path.basename(src)[:-4]
    else:
        work = src; label = os.path.basename(src.rstrip("/"))
    label = re.sub(r"^[0-9a-f]{8}-", "", label)                 # strip chat-upload prefix
    cdir = os.path.join(addon, "data/ahrivaea/creature")
    cid, infl, sclass, cpath = creature_of(label, cdir)
    files = sorted(glob.glob(os.path.join(work, "**/*.bbmodel"), recursive=True))
    variant = lambda p: next((v for v in ("_enraged_mutated", "_enraged", "_mutated") if p.endswith(v + ".bbmodel")), "")
    by = {variant(p): p for p in files}
    if "" not in by: raise SystemExit(f"{label}: no base .bbmodel")
    base = json.load(open(by[""]))
    if (base["meta"].get("model_format"), base["meta"].get("box_uv")) != ("bedrock", False):
        raise SystemExit(f"{label}: expected a Bedrock model with per-face UV")
    notes = []
    geo = geometry(base, "geometry." + cid)
    anim = animations(base)
    for v, p in by.items():                                      # variants must share the model
        other = json.load(open(p))
        if v and (geometry(other, "geometry." + cid) != geo or animations(other) != anim):
            notes.append(f"{v[1:]} model differs from the normal one; using the normal model")
    gdir = os.path.join(addon, f"assets/ahrivaea/geo/creature/{cid}"); os.makedirs(gdir, exist_ok=True)
    tdir = os.path.join(addon, f"assets/ahrivaea/textures/entity/creature/{cid}"); os.makedirs(tdir, exist_ok=True)
    json.dump(geo, open(os.path.join(gdir, f"{infl}.geo.json"), "w"), indent=1)
    json.dump(anim, open(os.path.join(gdir, f"{infl}.animation.json"), "w"), indent=1)
    for v, p in by.items():
        src_tex = json.load(open(p))["textures"][0]["source"]
        png = base64.b64decode(src_tex.split(",", 1)[1])
        open(os.path.join(tdir, f"{infl}{v}.png"), "wb").write(png)
    missing = [v or "normal" for v in ("", "_enraged", "_mutated", "_enraged_mutated") if v not in by]
    if missing: notes.append("missing skins: " + ", ".join(missing))
    c = json.load(open(cpath))
    if infl not in c["forms"]: raise SystemExit(f"{label}: {cid} has no {infl} form")
    size = round(longest_side(geo), 2)
    c["forms"][infl]["size"] = size
    c["size"] = SIZE_CLASS[sclass]
    json.dump(c, open(cpath, "w"), indent=2); open(cpath, "a").write("\n")
    nb = len(geo["minecraft:geometry"][0]["bones"]); nc = sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])
    print(f"{cid:22} {infl:8} {nb:3} bones {nc:4} cubes  anims={','.join(anim['animations'])}  size={size} ({sclass})" + ("  NOTE: " + "; ".join(notes) if notes else ""))

if __name__ == "__main__":
    for s in sys.argv[2:]: convert(sys.argv[1], s)
