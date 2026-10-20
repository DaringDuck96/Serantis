"""Turn the Starters Monster Book (xlsx) into creature files for the Serantis Starters add-on.

Usage:  python3 tools/build_starters.py <book.xlsx> <addon folder>

Uses the same rules as Batch 3 (tools/build_batch3.py): fixed level range and spawn weight per
rarity, stat totals 500-600, the mod's own job wording. Also writes starters.txt, the list of
creatures the starter screen offers, in book order.

Decisions baked in (agreed with the team):
  * Hydrabill and Rime Chimaera follow their models: Hydrabill is Water, Rime Chimaera is Frost.
    Each takes the biome and drops the book lists for that influence.
"""
import json, os, re, sys
import openpyxl
sys.path.insert(0, os.path.dirname(__file__))
from build_batch3 import (LEVELS, WEIGHT, SIZE, TOP_SIZE, JOB_TEXT, BIOMES, ITEMS, cid,
                          make_stats, total_for)

ITEMS.update({"Raw Beef": "beef", "Redstone Dust": "redstone", "Rabbit's Foot": "rabbit_foot"})
BIOME_FIX = {"cherry_blossom": "cherry_grove"}
INFLUENCE_FIX = {"Hydrabill": "water", "Rime Chimaera": "frost"}      # follow the models
DESIGN = {  # movement, size class, stat shape, two base jobs
    "Sixpaw Marauder":   ("walk", "small",  "beast",     "defense",    "transport"),
    "Cinder Pangolith":  ("walk", "medium", "tank",      "smelting",   "mining"),
    "Rime Chimaera":     ("walk", "medium", "beast",     "ice",        "defense"),
    "Hydrabill":         ("swim", "medium", "beast",     "fishing",    "defense"),
    "Stormstrider":      ("walk", "small",  "striker",   "redstone",   "transport"),
    "Delver Horror":     ("walk", "medium", "brute",     "mining",     "farming"),
    "Grove Behemoth":    ("walk", "medium", "tank",      "logging",    "farming"),
    "Runeback Basilisk": ("walk", "small",  "mage",      "enchanting", "redstone"),
    "Voidshell":         ("walk", "medium", "tank",      "mining",     "transport"),
    "Dawn Owlbear":      ("walk", "medium", "beast",     "lighting",   "defense"),
    "Gloomgazer":        ("walk", "small",  "mage",      "ranching",   "defense"),
    "Feyheart":          ("walk", "medium", "dancer",    "growth",     "lighting"),
}

def tidy_drops(text):
    """Normalise the book's drop text. Returns (parts, names that had no chance/amount)."""
    t = (text or "").replace("–", "-").replace("%,", "% |").strip().rstrip(";")
    t = re.sub(r"\|\s*(\d+(?:-\d+)?)(?=\s*(;|$))", r"| \1)", t)            # close a missing ")"
    parts, guessed = [], []
    for p in (x.strip() for x in t.split(";")):
        if p and "(" not in p:
            guessed.append(p); p += " (30% | 1-2)"                          # no chance given in the book
        if p: parts.append(p)
    return parts, guessed

def parse_drops(parts):
    out = []
    for part in parts:
        m = re.fullmatch(r"(.+?)\s*\((\d+)%\s*\|\s*(\d+)(?:-(\d+))?\)", part)
        if not m: raise SystemExit(f"Can't read drop: {part}")
        name, pct, lo, hi = m.groups()
        out.append({"item": "minecraft:" + ITEMS.get(name, cid(name)), "chance": int(pct) / 100,
                    "min": int(lo), "max": int(hi or lo)})
    return out

def biome_list(text):
    if text.strip().lower() == "all": return ["#minecraft:is_overworld"]
    out = []
    for b in text.split(","):
        b = BIOME_FIX.get(cid(b), cid(b))
        if b not in BIOMES: raise SystemExit(f"Unknown biome: {b}")
        out.append("minecraft:" + b)
    return out

def main(book, addon):
    rows = [r for r in openpyxl.load_workbook(book, data_only=True).worksheets[0].iter_rows(min_row=2, values_only=True) if r[1]]
    by_influence = {r[0].strip().lower(): r for r in rows}
    cdir = os.path.join(addon, "data/ahrivaea/creature"); os.makedirs(cdir, exist_ok=True)
    order, notes = [], []
    for r in rows:
        name = r[1].strip(); rarity = r[2].strip()
        infl = INFLUENCE_FIX.get(name, r[0].strip().lower())
        src = by_influence[infl] if name in INFLUENCE_FIX else r        # biome/drops travel with the influence
        if name in INFLUENCE_FIX: notes.append(f"{name}: {infl} (book said {r[0]}); biome {src[3].strip()}")
        parts, guessed = tidy_drops(src[4])
        notes += [f"{name}: '{g}' had no chance/amount in the book; using 30%, 1-2" for g in guessed]
        move, size, shape, *jobs = DESIGN[name]
        key = cid(name)
        stats = make_stats(key, shape, total_for(key, rarity))
        lo, hi = LEVELS[rarity]
        form = {"stats": stats, "rarity": rarity,
                "spawn": {"biomes": biome_list(src[3]), "min_level": lo, "max_level": hi, "weight": WEIGHT[rarity]},
                "drops": parse_drops(parts), "jobs": [{"job": j, "text": JOB_TEXT[j]} for j in jobs],
                "size": SIZE[size]}
        data = {"name": name, "forms": {infl: form}, "stats": stats, "size": TOP_SIZE[size], "movement": move}
        with open(os.path.join(cdir, key + ".json"), "w") as f:
            json.dump(data, f, indent=2); f.write("\n")
        order.append(key)
    os.makedirs(os.path.join(addon, "serantis_starters"), exist_ok=True)
    with open(os.path.join(addon, "serantis_starters", "starters.txt"), "w") as f:
        f.write("# Creatures offered on the starter screen, in this order. One creature id per line.\n")
        f.write("\n".join(order) + "\n")
    print(f"wrote {len(order)} starters: {', '.join(order)}")
    for n in notes: print("  note:", n)

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
