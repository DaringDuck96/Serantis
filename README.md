# Serantis

A Minecraft 26.3 modpack built around the Ahrivaea mods by jeffreystu10 and DaringDuck96.

## What's in it

| Mod | What it does |
|---|---|
| **Ahrivaea Creatures** | 239 creatures (312 forms) replace every vanilla mob. Fire Emblem-style grid battles in biome arenas, recruiting, Palworld-style bases where creatures work, riding (saddle any medium or bigger creature; fliers fly and swimmers swim), and your own levelled-up character. |
| **Ahrivaea Tower Challenges** | A ruined tower over every stronghold that the whole server rebuilds by donating items, with leaderboards and rewards. A finished tower opens the way to the End, becomes its top donor's base, and gives every offering back. |
| **Ahrivaea Guilds** | Guild members can't hurt each other or each other's creatures, and can build at any tower a member owns. |
| **Ahrivaea Map** | A minimap and a full-screen map of everywhere you've been, showing nearby creatures and every player on the server. |
| **Ahrivaea Guide** | An in-game, searchable guide to every Ahrivaea mod: what it does, how to start, commands and recipes. |
| **Ahrivaea Stellar Gear** | Upgrade netherite tools and armor with a nether star into Stellar gear with special powers. |
| **Ahrivaea Swift Horses** | Faster horses, donkeys and mules with on-screen stats. Feed them up three speed tiers, then breed for the top two. Skeleton horses are always top speed. |
| **Ahrivaea Fast Minecarts** | Minecarts up to 30 blocks/second (vanilla: 8), using the newer minecart physics so they stay on the rails. |
| **Ahrivaea Handheld Lights** | Holding a torch, lantern or other light lights up the area around you as you walk. |
| **Ahrivaea Woodcutting** | The stonecutter cuts wood: logs into planks, stairs, slabs, fences, doors, stripped logs and more. |
| **Ahrivaea Creatures: Batch 3** | 109 more creatures from the Monster Batch 3.0 Book: 87 that spawn across the Overworld, Nether and End, 2 summoned bosses (Harbinger of Calamity, False Hydra), and 20 End dragons. They use all the same battle, recruiting, riding and base-job rules, and all have their models. |
| **Serantis Starters** | The 12 starter creatures, one for each influence: Sixpaw Marauder, Cinder Pangolith, Rime Chimaera, Stormstrider, Delver Horror, Hydrabill, Grove Behemoth, Runeback Basilisk, Voidshell, Dawn Owlbear, Gloomgazer and Feyheart. They're the only creatures offered on the starter screen, each given in its own influence, and they also live in the wild as Rare creatures like any other. |
| **Serantis End Dragons** | Whenever the Ender Dragon is summoned in the End (on your first visit, or by placing End Crystals on the exit portal), one of the 20 End dragons appears on top of the exit portal instead, picked at random, at Level 20. They never spawn any other way. |
| **Serantis Battle and Spawning** | Every creature's battle stats come from the Serantis stat sheet. Creatures max out at Level 20, start at half their Level 20 stats at Level 1 and grow in a straight line. Spawns roll their rarity first (70% Common, 15% Uncommon, 10% Rare, 4% Super Rare, 1% Legendary), can be any level from 1 to 20, and have a 1% chance of being Mutated. Creatures fight with 3 moves each from the Move Dex, and every wild enemy gets a random battle mentality. |

### Battle stats and levels

- Each creature's Level 20 stats are in `addons/ahrivaea-battle/serantis_battle/stats.tsv`, built from the stat sheet with `tools/build_battle_stats.py`. They apply to every creature, including the original creatures mod's, without changing its files.
- Level 1 is 50% of Level 20, rising in a straight line. Creatures can't be fed past Level 20.
- Every creature spawn (wild spawns, replaced vanilla mobs, and battle reinforcements) first rolls a rarity: 70% Common, 15% Uncommon, 10% Rare, 4% Super Rare, 1% Legendary. It then picks a creature of that rarity that lives in the biome, using each creature's spawn weight. If the biome has none of the rolled rarity, the roll is repeated among the rarities it does have (the Nether and outer End have no Legendaries, so their 1% is shared among the rest). The odds are in `addons/ahrivaea-battle/src/dev/serantis/battle/SpawnOdds.java`.
- Every spawned creature has a 1% chance of being Mutated, with its mutated skin. Enraged keeps the creatures mod's own rare chance.
- A creature's level does not depend on its rarity. Any wild creature can be Level 1–20. Wild levels still sit within 5 of the nearest player's own level, kept inside 1–20, so from player level 25 up wild creatures are Level 20.
- Starters begin at Level 1. End dragons appear at Level 20.

### Moves (Stage 1)

- Every creature knows 3 moves, picked at random when it first appears (or first battles) and kept for good, including after it's caught. Any creature can know universal moves; magic moves are limited to creatures of that influence (Normal magic to creatures with no influence). At least one of the three always deals damage.
- The moves come from the Move Dex (`books/Move_Dex.xlsx`), built into `addons/ahrivaea-battle/serantis_battle/moves.json` by `tools/build_moves.py`. That script also records how each move behaves on the battle grid.
- Damage = Power × 4 × level factor × (Attack + 10) ÷ (Defense + 10). Magic moves use Magic on both sides. Influence matchups and the same-influence bonus (×1.2) still apply.
- Moves that reach more than 1 tile are ranged: 85% base hit chance. Moves that only reach 1 tile are melee: they deal 15% more damage. Luck shifts hit chance either way.
- Crits: a flat 10% chance (20% with high ground: standing at least 1 block above the target), and they double damage.
- Reach: universal ranged moves reach twice the Move Dex's maximum; magic ranged moves 1.5× (rounded up). Ranged attacks average about 6 tiles.
- Stat changes in the Move Dex are 10% of that stat per point (Defense +2 = +20%); accuracy changes are percentage points. Healing and damage over time use the same scale as Power.
- Statuses: Bleeding and Poison lose 5% of max HP each turn; Burning deals its stated damage each turn; Frozen, Stunned and Asleep skip turns (Asleep wakes when hit); Immobilized can't move; Slowed moves 2 fewer tiles; Confused attacks a random nearby unit half the time; Frightened deals about 30% less damage.
- Counter Stance: Defense +2 until the user's next turn, and anyone who damages it before then takes 25% of that damage back as recoil.
- Waiting for Stage 2 (not learnable yet): Charged Shot, Inferno Ring, Blazing Meteor, Frozen Ground, Storm Cloud, Sinkhole, Event Horizon and Eternal Night. Pushes, pulls and teleports on other moves are skipped for now.

### Enemy mentalities

Every wild creature in a battle gets one of six mentalities at random, shown next to its name on the battle screen:

| Mentality | How it fights |
|---|---|
| Aggression | Charges the nearest opponent at full speed. |
| Smart Aggression | Charges the player at full speed. |
| Support | Heals, buffs and protects its allies and stays back. It attacks only when there's no one to support. |
| Mixed | Rotates through Aggression, Smart Aggression and Support, one per turn. |
| Archer | Uses ranged moves and keeps its distance. It only goes into melee when it has no ranged move. |
| Hunter | Goes after the opponent who has taken the most damage, or the nearest one if nobody is hurt yet. |

All of them use the terrain. When choosing where to stand they prefer cells that are behind a wall or a ledge two or more blocks high, and cells on higher ground than their opponents. Higher ground also doubles crit chance. Cover only affects where they choose to stand. Attacks through cover still hit normally.

### Starters

The starter list lives in `addons/ahrivaea-starters/serantis_starters/starters.txt`, one creature per line, in the order they appear on the starter screen. Any creature can go on it. If none of the listed creatures are loaded, the starter screen falls back to the creatures mod's original list.

### End dragons

The 20 End dragons (Lindworm, Ddraig Goch, Hrimwyrm, Piasa, Fafnir, Gargouille, Mokele mbembe, Ouroboros, Fairy Dragon, Azi Dahaka, Ladon, Seraphim, Rendmaw, Arcanum, Bastion, Galewing, Fortune, Regalia, The First Dragon and The Last Dragon) only appear when the Ender Dragon is summoned in the End. The creatures mod still marks the vanilla dragon fight as won the moment the Ender Dragon appears, so the exit portal opens as before; the End dragon is there to battle and recruit. The list is in `addons/ahrivaea-end-dragons/serantis_end_dragons/dragons.txt`.

### About the Batch 3 bosses

The book says the Harbinger of Calamity (summoned like a Warden) and the False Hydra (summoned like a Wither) are summoned. That isn't built yet, so for now they don't appear in the wild. A server operator can bring one out with the mod's `/ahrivaea spawn` command.

## How to install

1. Go to the [Releases page](../../releases/latest) and download `Serantis-<version>.zip`.
2. Open your `.minecraft/mods` folder:
   - Windows: press `Win + R`, type `%appdata%\.minecraft\mods`, press Enter.
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
3. Unzip the download and copy every `.jar` from its `mods` folder into your `mods` folder. If you're updating, delete the old Ahrivaea jars first.
4. Launch Minecraft 26.3 with your Fabric profile and play.

Everyone playing together needs the same mods. A server gets the same jars in its own `mods` folder.

## License

All Ahrivaea mods are © jeffreystu10 and DaringDuck96, All Rights Reserved. Please don't re-upload them elsewhere without permission.
