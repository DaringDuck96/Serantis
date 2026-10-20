"""Build each creature form's battle footprint (how many grid tiles it covers).

Usage:  python3 tools/build_footprints.py <ahrivaea-creatures.jar> <out footprints.tsv> <out sheet.xlsx> <addon dir>...

    small, medium   1x1
    large           2x2
    extra_large     3x3
    xx_large        4x4

The size class comes from the model pack's file name (tools/model_sizes.tsv). Creatures whose models
came with the original creatures mod have no named size, so theirs is judged from the model's size
(its longest side in blocks), using the ranges the named models fall in:
    under 3.6 -> 1x1    3.6 to 7.5 -> 2x2    7.5 and up -> 3x3
"""
import glob, json, os, sys, zipfile
import openpyxl
from openpyxl.styles import Font, PatternFill

TILES = {"small": 1, "medium": 1, "large": 2, "extra_large": 3, "xx_large": 4}
NAMES = {"small": "Small", "medium": "Medium", "large": "Large", "extra_large": "Extra Large", "xx_large": "XX Large"}

def by_model_size(s):
    if s is None or s < 3.6: return "medium"
    if s < 7.5: return "large"
    return "extra_large"

def main(jar, out, sheet, *addons):
    named = {}
    for line in open(os.path.join(os.path.dirname(__file__), "model_sizes.tsv")):
        if line.startswith("#") or not line.strip(): continue
        cid, cls = line.split()
        named[cid] = cls
    creatures = {}
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if n.startswith("data/ahrivaea/creature/") and n.endswith(".json"):
                creatures[n.split("/")[-1][:-5]] = json.loads(z.read(n))
    for d in addons:                                   # add-on files replace or add creatures
        for f in glob.glob(os.path.join(d, "data/ahrivaea/creature/*.json")):
            cid = os.path.basename(f)[:-5]
            data = json.load(open(f))
            if cid in creatures:
                creatures[cid] = dict(creatures[cid], forms={**creatures[cid].get("forms", {}), **data.get("forms", {})})
            else:
                creatures[cid] = data
    rows = []
    for cid in sorted(creatures):
        c = creatures[cid]
        for infl, form in sorted(c.get("forms", {}).items()):
            s = form.get("size")
            cls, src = (named[cid], "model file name") if cid in named else (by_model_size(s), "model size")
            rows.append((cid, infl, c.get("name", cid), cls, TILES[cls], s, src))
    with open(out, "w") as o:
        o.write("# Battle footprint of each creature form (tiles per side). Built by tools/build_footprints.py.\n")
        o.write("# species_id\tinfluence\ttiles\n")
        for cid, infl, name, cls, t, s, src in rows: o.write(f"{cid}\t{infl}\t{t}\n")
    wb = openpyxl.Workbook(); ws = wb.active; ws.title = "Footprints"
    ws.append(["Creature", "Influence", "Size", "Tiles", "Model size (blocks)", "Size taken from"])
    for cell in ws[1]: cell.font = Font(bold=True)
    guess = PatternFill("solid", fgColor="FFF2CC")
    for cid, infl, name, cls, t, s, src in sorted(rows, key=lambda r: (r[2], r[1])):
        ws.append([name, "Normal" if infl == "none" else infl.title(), NAMES[cls], f"{t}x{t}", s, src])
        if src == "model size":
            for cell in ws[ws.max_row]: cell.fill = guess
    for col, w in zip("ABCDEF", (26, 12, 12, 7, 19, 18)): ws.column_dimensions[col].width = w
    ws.freeze_panes = "A2"; ws.auto_filter.ref = ws.dimensions
    wb.save(sheet)
    from collections import Counter
    print(len(rows), "forms:", dict(Counter(r[4] for r in rows)), "| named:", sum(r[6] != "model size" for r in rows))

if __name__ == "__main__":
    main(*sys.argv[1:])
