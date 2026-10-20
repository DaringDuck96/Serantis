"""Build the battle add-on's move catalog from the Move Dex.

Usage:  python3 tools/build_moves.py <Move_Dex.xlsx> <out moves.json>

Each move's name, category, influence and Power come from the Move Dex. How each move behaves in
the creatures mod's grid battles (who it can target, what area it hits, its effects) is written
out below, one line per move, from the Move Dex's Range / Area and Effect text. The script checks
every name, Power and range against the sheet so the two can't drift apart.

Rules agreed with the team:
  * A move that can reach more than 1 tile is ranged: 85% base hit chance (Luck shifts it).
  * A move that only reaches 1 tile (including "self, radius 1") is melee: always hits unless Luck
    or accuracy effects say otherwise, and deals 15% more damage.
  * Damage = Power x 4 x level factor x (Attack + 10) / (Defense + 10); magic moves use Magic on
    both sides. The x4 is baked into the stored power. Crits: flat 10%, double damage.
  * Reach: universal Ranged moves reach twice the sheet's maximum; magic moves that reach more than
    1 tile reach 1.5x the sheet's maximum, rounded up. Minimum ranges stay as in the sheet.
  * High ground: an attacker standing at least 1 block above its target has double the crit chance.
  * Stat changes in the sheet ("Defense +2") are 10% of that stat per point. Accuracy changes are
    percentage points. Healing and damage-over-time numbers use the same scale as Power.

Stage 2 (STAGE2 below) adds pushes, pulls, teleports, interrupts, terrain zones, Charged Shot and
Blazing Meteor. Zones belong to the caster's side and only affect the other side.
"""
import json, math, re, sys
import openpyxl

K = 4                 # power -> damage scale
MELEE_BONUS = 1.15
RANGED_ACCURACY = 0.85

def stat(s, n, t=2, who="target", **kw):       return dict(type="stat", stat=s, amount=n, turns=t, who=who, **kw)
def status(s, chance=1.0, t=2, **kw):          return dict(type="status", status=s, chance=chance, turns=t, **kw)
def move_tiles(n, who="target"):               return dict(type="movement", amount=n, who=who)
def heal(n, who="target"):                     return dict(type="heal", amount=n, who=who)
def ignore(s, n):                              return dict(type="ignore", stat=s, amount=n)
def bonus(n, when):                            return dict(type="bonus", amount=n, when=when)

def push(n, origin="user"):                    return dict(type="push", tiles=n, origin=origin)
def pull(n, toward="center", first=False):     return dict(type="pull", tiles=n, toward=toward, first=first)
def interrupt(chance=1.0):                     return dict(type="interrupt", chance=chance)
def zone(kind, turns, amount=0, cells="square"): return dict(type="zone", zone=kind, turns=turns, amount=amount, cells=cells)

# Stage 2 effects, added to the table's effects; every move listed here is switched on.
STAGE2 = {
 "Tackle":         [push(1)],
 "Uppercut":       [push(1)],
 "Stone Toss":     [interrupt(0.25)],
 "Charged Shot":   [dict(type="charge")],
 "Shockwave":      [push(1)],
 "Energy Pulse":   [interrupt()],
 "Phantom Charge": [dict(type="dash")],
 "Lava Bomb":      [zone("burning", 2, 4)],
 "Flame Wave":     [push(1)],
 "Inferno Ring":   [zone("ring", 2, 6, "ring")],
 "Blazing Meteor": [dict(type="delayed")],
 "Eruption":       [push(1, "center")],
 "Frozen Ground":  [zone("ice", 3)],
 "Tornado":        [pull(1)],
 "Wind Burst":     [push(2)],
 "Storm Cloud":    [zone("storm", 2, 6)],
 "Cyclone":        [pull(1)],
 "Earth Spike":    [interrupt()],
 "Boulder Toss":   [push(1)],
 "Sinkhole":       [zone("sink", 2)],
 "Water Whip":     [pull(1, "user")],
 "Tidal Wave":     [push(2)],
 "Geyser":         [dict(type="shift", tiles=1), interrupt()],
 "Whirlpool":      [pull(1)],
 "Gravity Well":   [pull(2)],
 "Void Rift":      [dict(type="teleport", tiles=2)],
 "Singularity":    [pull(1, first=True)],
 "Event Horizon":  [zone("horizon", 2, 5)],
 "Eternal Night":  [zone("night", 2, 4)],
 "Spell Nova":     [interrupt()],
 "Light Burst":    [interrupt()],
}

# name: (min, max, target, shape, radius, hits, effects, later, enabled)
#   target: enemy | ally | self | self_or_ally | any
#   shape:  single | self_radius | target_radius | square (3x3) | line | line_first | arc | cross | chain
S = {
 # ---- Universal melee
 "Smash":            (1,1,"enemy","single",0,1,[],None,True),
 "Tackle":           (1,1,"enemy","single",0,1,[],"push target 1 tile",True),
 "Charging Strike":  (2,3,"enemy","single",0,1,[bonus(3,"moved2")],None,True),
 "Trip":             (1,1,"enemy","single",0,1,[move_tiles(-2)],None,True),
 "Grapple":          (1,1,"enemy","single",0,1,[status("immobilized",t=1)],None,True),
 "Slash":            (1,1,"enemy","single",0,1,[status("bleeding",0.25)],None,True),
 "Sweeping Strike":  (1,1,"enemy","arc",0,1,[stat("attack",-1,1,"hit")],None,True),
 "Stomp":            (1,1,"enemy","target_radius",1,1,[],None,True),
 "Brace":            (0,0,"self","single",0,1,[stat("defense",2,2,"self")],None,True),
 "Pummel":           (1,1,"enemy","single",0,2,[],None,True),
 "Uppercut":         (1,1,"enemy","single",0,1,[],"push target 1 tile",True),
 "Disarm":           (1,1,"enemy","single",0,1,[stat("attack",-3)],None,True),
 "Feint":            (1,1,"enemy","single",0,1,[stat("defense",-2)],None,True),
 "Counter Stance":   (0,0,"self","single",0,1,[stat("defense",2,1,"self"),dict(type="recoil_stance")],None,True),
 "Rallying Strike":  (1,1,"enemy","single",0,1,[stat("attack",1,1,"allies_near_self",radius=1)],None,True),
 # ---- Universal ranged
 "Knife Throw":      (2,4,"enemy","single",0,1,[],None,True),
 "Bow Shot":         (2,5,"enemy","single",0,1,[move_tiles(-2)],None,True),
 "Stone Toss":       (2,3,"enemy","single",0,1,[],"25% chance to interrupt a charged attack",True),
 "Spear Throw":      (2,4,"enemy","single",0,1,[],None,True),
 "Projectile Shot":  (2,4,"enemy","single",0,1,[],None,True),
 "Spike Shot":       (2,4,"enemy","single",0,1,[status("bleeding",0.25)],None,True),
 "Bone Throw":       (2,3,"enemy","single",0,1,[stat("attack",-2)],None,True),
 "Slingshot":        (2,4,"enemy","single",0,1,[],None,True),
 "Multi-Shot":       (2,4,"enemy","single",0,3,[],None,True),
 "Charged Shot":     (3,6,"enemy","single",0,1,[],"needs a preparation turn and can be interrupted",False),
 "Cheer On":         (2,4,"ally","single",0,1,[stat("attack",2)],None,True),
 "Encourage":        (2,4,"ally","single",0,1,[stat("speed",2)],None,True),
 "Distracting Shot": (2,4,"enemy","single",0,1,[stat("attack",-2)],None,True),
 "Suppressing Fire": (2,5,"enemy","single",0,1,[move_tiles(-2)],None,True),
 "Rally Signal":     (2,4,"ally","single",0,1,[stat("defense",2,2,"allies_near_target",radius=1)],None,True),
 # ---- Normal
 "Force Bolt":       (2,4,"enemy","single",0,1,[],None,True),
 "Shockwave":        (1,1,"enemy","self_radius",1,1,[],"push 1 tile",True),
 "Gravity Slam":     (1,3,"enemy","single",0,1,[stat("speed",-2)],None,True),
 "Primal Roar":      (0,0,"self","single",0,1,[stat("attack",2,2,"allies_near_self",radius=2)],None,True),
 "Energy Pulse":     (2,3,"enemy","single",0,1,[],"interrupts a prepared or charged attack",True),
 "Phantom Charge":   (2,4,"enemy","single",0,1,[],"user moves toward the target",True),
 "Kinetic Burst":    (2,3,"enemy","single",0,1,[stat("defense",-3)],None,True),
 "Rift Strike":      (1,2,"enemy","single",0,1,[ignore("defense",3)],None,True),
 "Power Surge":      (0,0,"self","single",0,1,[stat("attack",3,2,"self"),stat("defense",-2,2,"self")],None,True),
 "Titan's Wrath":    (2,3,"enemy","target_radius",1,1,[move_tiles(-2,"self")],None,True),
 # ---- Fire
 "Fireball":         (2,4,"enemy","single",0,1,[],None,True),
 "Flame Burst":      (2,3,"enemy","target_radius",1,1,[],None,True),
 "Lava Bomb":        (2,4,"enemy","square",1,1,[],"burning tiles for 2 turns (4 damage on entry or turn start)",True),
 "Flame Wave":       (1,3,"enemy","line",0,1,[],"push 1 tile",True),
 "Ember Shot":       (2,5,"enemy","single",0,1,[status("burning",0.35,power=2)],None,True),
 "Magma Wave":       (2,3,"enemy","line",0,1,[status("slowed",t=1)],None,True),
 "Inferno Ring":     (2,3,"enemy","target_radius",1,1,[],"ring of tiles that deal 6 damage when crossed, for 2 turns",False),
 "Blazing Meteor":   (3,5,"enemy","square",1,1,[],"lands at the start of the user's next turn on telegraphed tiles",False),
 "Scorching Ray":    (2,5,"enemy","line_first",0,1,[status("burning",0.25,power=2)],None,True),
 "Eruption":         (1,3,"enemy","target_radius",1,1,[],"knockback 1 tile",True),
 # ---- Frost
 "Ice Shard":        (2,4,"enemy","single",0,1,[],None,True),
 "Frost Bolt":       (2,4,"enemy","single",0,1,[stat("speed",-2)],None,True),
 "Blizzard":         (3,5,"enemy","square",1,1,[status("freeze",0.20,t=1)],None,True),
 "Ice Lance":        (2,4,"enemy","single",0,1,[],None,True),
 "Frost Burst":      (1,1,"enemy","self_radius",1,1,[status("slowed",t=2)],None,True),
 "Frozen Ground":    (2,4,"enemy","square",1,1,[],"icy terrain for 3 turns, each tile costs 2 movement",False),
 "Icicle Rain":      (3,5,"enemy","square",1,2,[],None,True),
 "Glacier Spike":    (2,3,"enemy","single",0,1,[status("immobilized",t=1)],None,True),
 "Snowblind":        (2,4,"enemy","square",1,1,[stat("accuracy",-20),stat("attack",-2)],None,True),
 "Absolute Zero":    (2,3,"enemy","single",0,1,[status("freeze",0.15,t=1),dict(type="no_consecutive")],None,True),
 # ---- Storm
 "Lightning Bolt":   (2,5,"enemy","single",0,1,[],None,True),
 "Thunderclap":      (1,1,"enemy","self_radius",1,1,[status("stun",0.20,t=1)],None,True),
 "Wind Blade":       (2,4,"enemy","line",0,1,[],None,True),
 "Tornado":          (2,4,"enemy","target_radius",1,1,[],"pulls enemies 1 tile toward the target",True),
 "Thunderball":      (2,4,"enemy","single",0,1,[status("stun",0.25,t=1)],None,True),
 "Chain Lightning":  (2,4,"enemy","chain",2,1,[dict(type="chain",jumps=2,power=6)],None,True),
 "Wind Burst":       (1,1,"enemy","self_radius",1,1,[move_tiles(2,"self")],"pushes enemies 2 tiles",True),
 "Storm Cloud":      (3,5,"enemy","square",1,1,[],"for 2 turns a random enemy inside takes 6 damage at turn end",False),
 "Cyclone":          (2,4,"enemy","cross",1,1,[status("slowed",t=1)],"pulls enemies toward the center",True),
 "Tempest Strike":   (2,4,"enemy","single",0,1,[stat("magic",-3)],None,True),
 # ---- Earth
 "Rock Blast":       (2,4,"enemy","single",0,1,[],None,True),
 "Earth Spike":      (2,3,"enemy","single",0,1,[],"interrupts a prepared attack",True),
 "Boulder Toss":     (2,4,"enemy","single",0,1,[],"knockback 1 tile",True),
 "Quake":            (1,1,"enemy","self_radius",1,1,[stat("speed",-2,1,"hit")],None,True),
 "Sand Blast":       (2,4,"enemy","line",0,1,[stat("accuracy",-20,2,"hit")],None,True),
 "Stone Barrage":    (2,4,"enemy","single",0,3,[],None,True),
 "Crystal Guard":    (0,1,"self_or_ally","single",0,1,[stat("defense",4)],None,True),
 "Sinkhole":         (2,4,"enemy","square",1,1,[],"difficult terrain for 2 turns, no movement bonuses inside",False),
 "Obsidian Shards":  (2,4,"enemy","single",0,1,[status("bleeding",0.30)],None,True),
 "Mountain Breaker": (1,2,"enemy","single",0,1,[stat("defense",-3)],None,True),
 # ---- Water
 "Water Jet":        (2,4,"enemy","single",0,1,[],None,True),
 "Bubble Blast":     (2,4,"enemy","single",0,1,[stat("speed",-2)],None,True),
 "Water Whip":       (1,3,"enemy","single",0,1,[],"pulls the target 1 tile toward the user",True),
 "Tidal Wave":       (2,4,"enemy","line",0,1,[],"pushes enemies 2 tiles",True),
 "Aqua Sphere":      (2,4,"enemy","single",0,1,[stat("attack",-2)],None,True),
 "Geyser":           (2,4,"enemy","single",0,1,[],"moves the target 1 tile; interrupts preparation",True),
 "Whirlpool":        (2,4,"enemy","target_radius",1,1,[],"pulls enemies 1 tile toward the center",True),
 "Hydro Lance":      (2,4,"enemy","single",0,1,[],None,True),
 "Cleansing Current":(1,3,"ally","single",0,1,[dict(type="cleanse",count=1)],None,True),
 "Abyssal Deluge":   (3,5,"enemy","square",1,1,[move_tiles(-2,"hit")],None,True),
 # ---- Nature
 "Vine Lash":        (1,3,"enemy","single",0,1,[stat("speed",-2)],None,True),
 "Thorn Shot":       (2,4,"enemy","single",0,1,[status("bleeding",0.30)],None,True),
 "Seed Bomb":        (2,4,"enemy","square",1,1,[],None,True),
 "Root Snare":       (2,4,"enemy","single",0,1,[status("immobilized",t=1)],None,True),
 "Leaf Storm":       (2,4,"enemy","square",1,2,[],None,True),
 "Spore Cloud":      (2,4,"enemy","square",1,1,[status("poison",0.30,t=3)],None,True),
 "Bramble Burst":    (2,3,"enemy","target_radius",1,1,[stat("attack",-2,2,"hit")],None,True),
 "Pollen Blast":     (2,4,"enemy","square",1,1,[stat("magic",-2,2,"hit"),stat("accuracy",-20,2,"hit")],None,True),
 "Forest's Grasp":   (2,4,"enemy","single",0,1,[status("immobilized",t=1),status("doom",t=1,power=4)],None,True),
 "Ancient Growth":   (0,1,"self_or_ally","single",0,1,[heal(10),stat("defense",2)],None,True),
 # ---- Void
 "Void Bolt":        (2,4,"enemy","single",0,1,[],None,True),
 "Shadow Pulse":     (1,1,"enemy","self_radius",1,1,[stat("magic",-2,2,"hit")],None,True),
 "Null Field":       (2,4,"enemy","square",1,1,[stat("magic",-3,2,"hit")],None,True),
 "Gravity Well":     (2,4,"enemy","target_radius",1,1,[stat("speed",-2,1,"hit")],"pulls enemies 2 tiles",True),
 "Void Rift":        (2,4,"enemy","single",0,1,[],"teleports the target 2 tiles",True),
 "Entropy Wave":     (2,4,"enemy","line",0,1,[stat("defense",-3,2,"hit")],None,True),
 "Singularity":      (3,5,"enemy","square",1,1,[],"pulls enemies 1 tile toward the center first",True),
 "Dimensional Tear": (1,3,"enemy","single",0,1,[ignore("defense",5)],None,True),
 "Oblivion Burst":   (2,4,"enemy","single",0,1,[dict(type="dispel",count=1,block_turns=1)],None,True),
 "Event Horizon":    (3,5,"enemy","square",1,1,[],"zone for 2 turns that slows and deals 5 damage at turn end",False),
 # ---- Fae
 "Pixie Dust":       (2,4,"enemy","single",0,1,[stat("accuracy",-20)],None,True),
 "Glamour Bolt":     (2,4,"enemy","single",0,1,[stat("attack",-2)],None,True),
 "Thorned Bloom":    (2,4,"enemy","square",1,1,[stat("speed",-1,2,"hit")],None,True),
 "Illusion Burst":   (2,4,"enemy","square",1,1,[status("confused",t=2)],None,True),
 "Enchanted Pollen": (2,3,"enemy","single",0,1,[status("sleep",0.25,t=1)],None,True),
 "Feyfire":          (2,4,"enemy","single",0,1,[stat("magic",-2)],None,True),
 "Bewildering Mist": (2,4,"enemy","square",1,1,[stat("accuracy",-20,2,"hit"),status("slowed",t=2)],None,True),
 "Briar Prison":     (2,4,"enemy","single",0,1,[status("immobilized",t=1)],None,True),
 "Moonbeam":         (2,4,"any","single",0,1,[heal(8,"ally")],None,True),
 "Wild Hunt":        (0,0,"self","single",0,1,[stat("attack",2,2,"self"),stat("speed",2,2,"self")],None,True),
 # ---- Darkness
 "Shadow Bolt":      (2,4,"enemy","single",0,1,[],None,True),
 "Dark Pulse":       (1,1,"enemy","self_radius",1,1,[stat("attack",-2,2,"hit")],None,True),
 "Umbral Wave":      (2,4,"enemy","line",0,1,[stat("magic",-2,2,"hit")],None,True),
 "Nightfall":        (2,4,"enemy","square",1,1,[stat("accuracy",-20,2,"hit")],None,True),
 "Dread Sphere":     (2,4,"enemy","single",0,1,[status("frightened",0.25)],None,True),
 "Shadow Snare":     (2,4,"enemy","single",0,1,[status("immobilized",t=1),stat("speed",-1)],None,True),
 "Black Flame":      (2,4,"enemy","single",0,1,[status("burning",1.0,power=2)],None,True),
 "Terror Surge":     (1,1,"enemy","self_radius",1,1,[stat("attack",-3,1,"hit")],None,True),
 "Abyssal Grasp":    (2,4,"enemy","single",0,1,[status("immobilized",t=1)],None,True),
 "Eternal Night":    (3,5,"enemy","square",1,1,[],"zone for 2 turns: enemies lose 2 Defense and take 4 damage at turn end",False),
 # ---- Arcane
 "Arcane Bolt":      (2,4,"enemy","single",0,1,[],None,True),
 "Mana Burst":       (1,1,"enemy","self_radius",1,1,[stat("magic",-2,1,"hit")],None,True),
 "Magic Missile":    (2,5,"enemy","single",0,3,[],None,True),
 "Runic Blast":      (2,4,"enemy","single",0,1,[stat("defense",-2)],None,True),
 "Prismatic Beam":   (2,5,"enemy","line_first",0,1,[],None,True),
 "Arcane Barrage":   (2,4,"enemy","single",0,4,[],None,True),
 "Spell Nova":       (1,1,"enemy","self_radius",1,1,[],"interrupts prepared attacks",True),
 "Rune Prison":      (2,4,"enemy","single",0,1,[status("immobilized",t=1)],None,True),
 "Aether Lance":     (2,4,"enemy","single",0,1,[ignore("magic",4)],None,True),
 "Arcane Overcharge":(0,0,"self","single",0,1,[stat("magic",4,2,"self"),stat("defense",-2,2,"self")],None,True),
 # ---- Radiant
 "Radiant Bolt":     (2,4,"enemy","single",0,1,[],None,True),
 "Holy Light":       (2,4,"any","single",0,1,[heal(8,"ally")],None,True),
 "Solar Flare":      (2,4,"enemy","square",1,1,[stat("accuracy",-20,1,"hit")],None,True),
 "Light Burst":      (1,1,"enemy","self_radius",1,1,[],"interrupts prepared attacks",True),
 "Sacred Beam":      (2,5,"enemy","line_first",0,1,[],None,True),
 "Dawn Wave":        (1,1,"enemy","self_radius",1,1,[dict(type="cleanse",count=1,who="allies_near_self",radius=1)],None,True),
 "Blinding Flash":   (2,4,"enemy","square",1,1,[stat("accuracy",-25,1,"hit")],None,True),
 "Celestial Spear":  (2,4,"enemy","single",0,1,[],None,True),
 "Judgment Strike":  (1,3,"enemy","single",0,1,[bonus(5,"target_buffed")],None,True),
 "Divine Wrath":     (2,4,"enemy","square",1,1,[stat("magic",-2,2,"hit")],None,True),
}

def sheet_range(text):
    """(min, max) the sheet's Range / Area text implies, for checking the table above."""
    t = str(text).replace("–", "-")
    if t.lower().startswith("self or adjacent"): return (0, 1)
    if t.lower().startswith("self"): return (0, 0) if "radius" not in t else (1, 1)
    if t.lower().startswith("adjacent"): return (1, 1)
    m = re.match(r"(\d+)(?:-(\d+))?", t)
    return (int(m.group(1)), int(m.group(2) or m.group(1)))

def sheet_power(text):
    m = re.match(r"(\d+)\s*(?:[x×]\s*(\d+)\s*hits?)?", str(text))
    return int(m.group(1)), int(m.group(2) or 1)

def main(book, out):
    ws = openpyxl.load_workbook(book, data_only=True).worksheets[0]
    moves, problems = [], []
    for r in ws.iter_rows(min_row=3, values_only=True):
        if not r[3]: continue
        cat, infl, _, name, rng, power, effect = r
        if name not in S: problems.append(f"{name}: not described in the table"); continue
        mn, mx, target, shape, radius, hits, effects, later, enabled = S[name]
        if name in STAGE2:
            effects, later, enabled = effects + STAGE2[name], None, True
        p, h = sheet_power(power)
        if h != hits: problems.append(f"{name}: sheet hits {h}, table {hits}")
        smn, smx = sheet_range(rng)
        if target in ("enemy", "any") and shape != "self_radius" and (smn, smx) != (mn, mx):
            problems.append(f"{name}: sheet range {smn}-{smx}, table {mn}-{mx}")
        sheet_max = mx
        if cat == "Ranged":
            mx = mx * 2                                   # universal ranged: double reach
        elif cat == "Magic" and mx > 1:
            mx = math.ceil(mx * 1.5)                      # magic ranged: 1.5x reach, rounded up
        melee = mx <= 1 and target in ("enemy", "any")
        influence = "universal" if infl == "Universal" else ("none" if infl == "Normal" else infl.lower())
        moves.append({
            "id": re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_"),
            "name": name, "category": cat.lower(), "influence": influence,
            "range": "melee" if melee else ("ranged" if target in ("enemy", "any") else "support"),
            "min": mn, "max": mx, "sheet_max": sheet_max, "target": target, "shape": shape, "radius": radius,
            "sheet_power": p, "hits": h,
            "power": round(p * K * (MELEE_BONUS if melee else 1.0)),
            "accuracy": 1.0 if melee else RANGED_ACCURACY,
            "effects": effects, "later": later, "enabled": enabled,
            "text": "Two physical hits; slows the target's next move by 2 tiles" if False else
                    ("Slows the target's next move by 2 tiles" if name == "Bow Shot" else
                     ("For the rest of the round, attackers that damage the user take recoil; Defense +2 until next turn"
                      if name == "Counter Stance" else str(effect))),
        })
    extra = (set(S) | set(STAGE2)) - {m["name"] for m in moves}
    problems += [f"{n}: in the table but not in the sheet" for n in extra]
    if problems: raise SystemExit("\n".join(problems))
    json.dump(moves, open(out, "w"), indent=1, ensure_ascii=False)
    on = [m for m in moves if m["enabled"]]
    print(f"{len(moves)} moves written ({len(on)} enabled)")

if __name__ == "__main__":
    main(*sys.argv[1:])
