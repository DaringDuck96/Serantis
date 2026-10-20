"""Turn the creature stats workbook into the battle add-on's stats table.

Usage:  python3 tools/build_battle_stats.py <workbook.xlsx> <creatures jar> <out stats.tsv> [addon dirs...]

Reads the 'Combined Creatures' sheet (each creature's Level 20 stats) and matches every row to a
creature form in the pack: the original creatures mod (read from its jar) plus each add-on folder.
Writes one line per form:  species_id  influence  hp attack defense speed magic luck charisma
(the column order the creatures mod's Stats record uses).

Matching: by name and influence ("Normal" = no influence). If only the name matches, the pack's
influence is kept. The sheet's "Dragon" rows are the original Dragon's forms, one per influence.
"""
import json, os, re, sys, zipfile
import openpyxl

SHEET_FIXES = {"scrapgobblin": "scrapgoblin", "ghome": "gnome", "nameanlion": "nemeanlion", "zmeygorymych": "zmeygorynych"}
STAT_ORDER = ["HP", "Attack", "Defense", "Speed", "Magic", "Luck", "Charisma"]   # Stats(hp, attack, defense, speed, magic, luck, charisma)

def norm(s):
    s = re.sub(r"\(.*\)", "", s.lower())
    s = re.sub(r"[^a-z0-9]", "", s)
    return SHEET_FIXES.get(s, s)

def influence(s):
    return "none" if s.strip() == "Normal" else s.strip().lower()

def pack_forms(creatures_jar, addon_dirs):
    forms = []                                              # (species_id, influence, display name, source)
    with zipfile.ZipFile(creatures_jar) as z:
        for n in sorted(z.namelist()):
            m = re.fullmatch(r"data/ahrivaea/creature/([a-z0-9_]+)\.json", n)
            if m:
                j = json.loads(z.read(n))
                for inf, fv in j["forms"].items():
                    forms.append((m.group(1), inf, fv.get("name") or j["name"], "creatures mod"))
    for d in addon_dirs:
        cdir = os.path.join(d, "data/ahrivaea/creature")
        for f in sorted(os.listdir(cdir)):
            j = json.load(open(os.path.join(cdir, f)))
            for inf, fv in j["forms"].items():
                forms.append((f[:-5], inf, fv.get("name") or j["name"], os.path.basename(d.rstrip("/"))))
    return forms

def main(book, jar, out, *addons):
    ws = openpyxl.load_workbook(book, data_only=True)["Combined Creatures"]
    header = [c.value for c in ws[1]]
    col = {h: i for i, h in enumerate(header) if h}
    rows = [r for r in ws.iter_rows(min_row=2, values_only=True) if r[col["Creature Name"]]]
    forms = pack_forms(jar, addons)
    by_name = {}
    for f in forms: by_name.setdefault(norm(f[2]), []).append(f)
    by_key = {(f[0], f[1]): f for f in forms}

    table, notes = {}, []
    for r in rows:
        name, inf = r[col["Creature Name"]], influence(r[col["Influence"]])
        stats = [int(r[col[s]]) for s in STAT_ORDER]
        if norm(name) == "dragon":
            target = by_key.get(("dragon", inf))
        else:
            cands = by_name.get(norm(name), [])
            exact = [f for f in cands if f[1] == inf]
            target = exact[0] if len(exact) == 1 else (cands[0] if len(cands) == 1 else None)
            if target and not exact:
                notes.append(f"{name}: sheet says {r[col['Influence']]}, pack has {target[1]}; matched by name")
        if not target:
            raise SystemExit(f"No creature in the pack matches sheet row: {name} ({r[col['Influence']]})")
        key = (target[0], target[1])
        if key in table:
            if table[key] != stats: raise SystemExit(f"Two sheet rows with different stats match {key}")
            notes.append(f"{name} ({r[col['Influence']]}) is listed twice with the same stats; used once")
            continue
        table[key] = stats

    with open(out, "w") as f:
        f.write("# Level 20 stats for every creature form, from the creature stats workbook.\n")
        f.write("# species_id\tinfluence\thp\tattack\tdefense\tspeed\tmagic\tluck\tcharisma\n")
        for (sid, inf), s in sorted(table.items()):
            f.write("\t".join([sid, inf] + [str(v) for v in s]) + "\n")
    missing = [f for f in forms if (f[0], f[1]) not in table]
    print(f"{len(table)} forms written to {out}")
    for n in notes: print("  note:", n)
    for m in missing: print(f"  not in the sheet (keeps its current stats): {m[2]} ({m[1]}) from {m[3]}")

if __name__ == "__main__":
    main(*sys.argv[1:])
