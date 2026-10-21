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
| **Serantis Grinding** | Grindstones grind cobblestone into gravel and gravel into sand, 2 for 1: a stack of 64 makes 32. Put the stack in either top slot with the other slot empty and take the result. An odd item stays in the slot. |
| **Serantis Dyed Water** | Craft a Water Bucket with any of the 16 dyes (shapeless) to get that colour's water bucket, e.g. Red Water Bucket. Dyed water flows, can be swum in, puts out fire, hydrates farmland and can be scooped back up like normal water, but it's tinted the dye's colour. The water bucket is used up whole in the craft, so no empty bucket is left over. |
| **Serantis Towers** | Cheaper first towers (the Ember Spire takes 64 coal, the Timber Watch 64 logs and the Quarry Keep 128 cobblestone), and the first player to make an offering at a tower claims it for good: anyone can help rebuild it, but its Base Core, the extra Base Core and the land around it go to whoever claimed it. |
| **Serantis Battle and Spawning** | Every creature's battle stats come from the Serantis stat sheet. Creatures max out at Level 20, start at half their Level 20 stats at Level 1 and grow in a straight line. Spawns roll their rarity first (70% Common, 15% Uncommon, 10% Rare, 4% Super Rare, 1% Legendary), can be any level from 1 to 20, and have a 1% chance of being Mutated. Creatures fight with 3 moves each from the Move Dex, and every wild enemy gets a random battle mentality. |

### The Arcane Dragon

The creatures mod's Arcane Dragon is retired: the Clockwork Dragon is the arcane dragon in Serantia. It never spawns, and Wandering Traders, Black Market Merchants and Creature Masters never hand it out, so the dex counts 433 creature forms to find.

### Classes

After the welcome guide, every player chooses a class, once (players already in the world choose the next time they join):

| Class | Boost | Head start |
|---|---|---|
| Knight | Attack | An Iron Chestplate and an Iron Sword |
| Bulwark | Defense | A Shield and an Iron Mace (a mace) |
| Wizard | Magic | A Magic Staff linked to a random spell of the influence they choose (any but Normal) |
| Journeyman | Luck | 2 Golden Apples |
| Performer | Charisma | Chooses their starter's temperament (if they already have a starter, the creature first in their party gets it) |

The boost is +15 to the class's stat at first (players start at 50), 3 less for every skill learned on the skill tree, and never less than +3: a real edge for the first few fights that hardly shows after four or five skills.

**Magic Staffs.** A **Wooden Staff** is crafted from three sticks and an Eye of Ender (shapeless). Until it has a spell it's just a wooden mace: 5 attack damage, a slow swing, and it wears out like a wooden tool (59 uses). To give it a spell, right-click an enchanting table while holding it and you're offered three spells at random from the Move Dex (hover one for the full explanation). Pick one and pay **25 XP** (less with Presence) and the staff is linked to that spell for good, becoming that influence's staff: a fire spell turns it into an **Emberwrath Staff**, and its tooltip shows the spell. The offer on a staff stays the same until you pick (sneak-click the table to use it normally). A Wizard's first staff comes already linked to a random spell of the influence they chose. In battle, a linked staff in your main hand or off hand lets you cast its spell as well as your normal attack (two staffs, two spells). Hitting a creature with a staff starts a battle like any weapon. `/give @s serantis_battle:magic_staff` gives a Wooden Staff. Each influence's staff has its own design (there's no Normal staff, and Normal or universal moves are never offered): Emberwrath (Fire), Rimespire (Frost), Thundercall (Storm), Stoneheart (Earth), Tidecaller (Water), Thornbloom (Nature), Riftwalker (Void), Moonpetal (Fae), Gloomreaver (Darkness), Runeweaver (Arcane) and Dawnbringer (Radiant). The models are converted with `tools/convert_staffs.py`.

### Welcome guide

The first time a player joins, a welcome guide pops up (over the starter screen, which comes back after). It welcomes them to Serantia (the world's name; the pack and repository are called Serantis) and runs through what it's about: picking a starter, battling and recruiting creatures, collecting them, XP as the main currency, rebuilding towers, Creature Masters and their stones, achievements, winning the game with the Eternita Stone, and that it's still Minecraft, where building and creativity are as valid a way to play as any battle. Read it again any time from the **Guide** button on the party screen. The guide has a second tab, **Your Base**, also opened by the **Guide** button on a Base Core's screen: how bases work (area, levels, putting creatures to work), how a worker decides what to do and how fast it works, what every job does and needs, chests and item-frame labels, raids, knock-outs, and what villagers add to a base.

### Battle stats and levels

- Each creature's Level 20 stats are in `addons/ahrivaea-battle/serantis_battle/stats.tsv`, built from the stat sheet with `tools/build_battle_stats.py`. They apply to every creature, including the original creatures mod's, without changing its files.
- Level 1 is 50% of Level 20, rising in a straight line. Creatures can't be fed past Level 20.
- Every creature spawn (wild spawns, replaced vanilla mobs, and battle reinforcements) first rolls a rarity: 70% Common, 15% Uncommon, 10% Rare, 4% Super Rare, 1% Legendary. It then picks a creature of that rarity that lives in the biome, using each creature's spawn weight. If the biome has none of the rolled rarity, the roll is repeated among the rarities it does have (the Nether and outer End have no Legendaries, so their 1% is shared among the rest). Among Common creatures, those of the Normal, Fire, Frost, Earth, Storm, Nature and Water influences have 5% more spawn weight than the others, so they turn up a little more often; the rarity odds don't change. The odds are in `addons/ahrivaea-battle/src/dev/serantis/battle/SpawnOdds.java`.
- Every spawned creature has a 1% chance of being Mutated, with its mutated skin. Enraged keeps the creatures mod's own rare chance.
- A creature's level does not depend on its rarity. Any wild creature can be Level 1–20. Wild levels still sit within 5 of the nearest player's own level, kept inside 1–20, so from player level 25 up wild creatures are Level 20.
- Starters begin at Level 1. End dragons appear at Level 20.

### Moves

- Every creature knows 3 moves, picked at random when it first appears (or first battles) and kept for good, including after it's caught. Any creature can know universal moves; magic moves are limited to creatures of that influence (Normal magic to creatures with no influence). At least one of the three always deals damage.
- The moves come from the Move Dex (`books/Move_Dex.xlsx`), built into `addons/ahrivaea-battle/serantis_battle/moves.json` by `tools/build_moves.py`. That script also records how each move behaves on the battle grid.
- Damage = Power × 4 × level factor × (Attack + 10) ÷ (Defense + 10). Magic moves use Magic on both sides. Influence matchups and the same-influence bonus (×1.2) still apply.
- Moves that reach more than 1 tile are ranged: 85% base hit chance. Moves that only reach 1 tile are melee: they deal 15% more damage. Luck shifts hit chance either way.
- Crits: a flat 10% chance (20% with high ground: standing at least 1 block above the target), and they double damage.
- Reach: universal ranged moves reach twice the Move Dex's maximum; magic ranged moves 1.5× (rounded up). Ranged attacks average about 6 tiles.
- Stat changes in the Move Dex are 10% of that stat per point (Defense +2 = +20%); accuracy changes are percentage points. Healing and damage over time use the same scale as Power.
- Statuses: Bleeding and Poison lose 5% of max HP each turn; Burning deals its stated damage each turn; Frozen, Stunned and Asleep skip turns (Asleep wakes when hit); Immobilized can't move; Slowed moves 2 fewer tiles; Confused attacks a random nearby unit half the time; Frightened deals about 30% less damage.
- Counter Stance: Defense +2 until the user's next turn, and anyone who damages it before then takes 25% of that damage back as recoil.
- All 150 moves in the Move Dex can be learned.
- In battle, hover over a creature (or its card) to see its card: level, HP, movement, size, and every move with a details line (physical or magic, influence, reach, base hit chance, Power) and a plain-English explanation of exactly what it does, with real numbers. Click a creature to pin its card; click the card to close it.

### Pushes, zones and charged attacks

- **Pushes and pulls:** Tackle, Uppercut, Boulder Toss, Shockwave, Flame Wave, Wind Burst and Tidal Wave push targets away from the user. Eruption pushes them out from where it lands. Tornado, Cyclone, Whirlpool and Gravity Well pull enemies toward the centre. Singularity pulls them in before it hits. Water Whip pulls its target toward the user. Walls, ledges that are too high, and other creatures stop a push or pull early.
- **Other forced movement:** Void Rift throws its target 2 tiles to a random open tile. Geyser knocks its target 1 tile in a random open direction. Phantom Charge rushes the user next to its target before it hits.
- **Zones:** each zone covers the 3×3 tiles around where it lands, except the Inferno Ring, which is the ring just outside its blast. Zones are coloured on the battle grid and affect every creature in them, friend or foe, so place them carefully.

| Move | Zone | Lasts | Effect |
|---|---|---|---|
| Lava Bomb | Burning ground (orange) | 2 turns | Damage on stepping in, and at the start of each turn spent standing in it |
| Inferno Ring | Ring of fire (red) | 2 turns | Damage for crossing it |
| Frozen Ground | Ice (blue) | 3 turns | Each tile costs 2 movement |
| Sinkhole | Sinkhole (brown) | 2 turns | Each tile costs 2 movement, and no movement bonuses while standing in it |
| Storm Cloud | Storm (grey-blue) | 2 turns | At the end of each side's turn, one random creature of that side inside is struck |
| Event Horizon | Void (violet) | 2 turns | Damage and Slowed at the end of each turn spent inside |
| Eternal Night | Darkness (dark purple) | 2 turns | Damage and Defense −2 at the end of each turn spent inside |

  Flying creatures pass over burning ground, the Inferno Ring, Frozen Ground and Sinkholes. The blue movement preview on the battle screen counts slow tiles.
- **Charged Shot:** the first turn, the user charges in place (it can't move that turn). At the start of its next turn it fires at the same target if it's still in range, and that uses up the turn.
- **Blazing Meteor:** marks a 3×3 area in red where its target stood. At the start of the user's next turn it lands on everyone still in that area except the user, allies included. The user then acts normally.
- **Interrupts:** Energy Pulse, Earth Spike, Geyser, Spell Nova and Light Burst cancel a charging Charged Shot or a falling Blazing Meteor when they hit its user. Stone Toss has a 25% chance to. Stunning, freezing or putting the user to sleep also cancels it, as does knocking it out.
- Enemies avoid zones and red meteor areas, try not to catch their own side in them, hit charging creatures with interrupts when they can, and only charge a Charged Shot when standing still.
- **Flying creatures** pass over ground zones, but ranged attacks (moves that reach more than 1 tile) hit them 1.25× as hard.

### Temperaments

Every creature gets one of 25 temperaments at random when it first appears and keeps it for good. That includes wild creatures, starters and recruits, and it's kept when the creature is caught or sent out. Each temperament raises one stat by 0.5 per level and lowers another by 0.5 per level (+10 / −10 at Level 20). It affects battles, max health, movement (Speed), work speed (Charisma) and riding. Creatures that existed before temperaments were added get one the first time they're loaded. A creature's temperament shows after its name on the party screen and in its battle tags. Starters also get their 3 random moves the moment they're picked.

| Temperament | Raises | Lowers |
|---|---|---|
| Adamant | Attack | Magic |
| Brave | Attack | Speed |
| Bold | Defense | Attack |
| Relaxed | Defense | Speed |
| Impenetrable | Defense | Luck |
| Hardy | HP | Charisma |
| Stalwart | HP | Speed |
| Vital | HP | Magic |
| Swift | Speed | Defense |
| Hasty | Speed | HP |
| Nimble | Speed | Attack |
| Cunning | Luck | Defense |
| Fortunate | Luck | HP |
| Fickle | Luck | Charisma |
| Wise | Magic | Attack |
| Mystic | Magic | Defense |
| Focused | Magic | Luck |
| Scholarly | Magic | Speed |
| Charming | Charisma | Attack |
| Regal | Charisma | Speed |
| Inspiring | Charisma | Luck |
| Intimidating | Attack | Charisma |
| Guardian | Defense | Magic |
| Tenacious | HP | Luck |
| Trickster | Charisma | Magic |


### Golden apples

Feed golden apples to creatures in your party to raise their stats. This lets weaker creatures keep up late in the game. On the party screen, pick a creature and press **Apple**, then choose the stat. Each apple uses one golden apple from your inventory (none in Creative). Every stat of every creature can take up to 10 apples. How much each apple adds depends on the creature's rarity:

| Rarity | Per apple | After 10 apples |
|---|---|---|
| Common | +2 | +20 |
| Uncommon | +1 | +10 |
| Rare | +0.5 | +5 |
| Super Rare | +0.25 | +2.5 |
| Legendary | no effect | — |

The gain goes on the stat's Level 20 value, like the rest of the stat, so it is scaled down at lower levels. Stats are whole numbers, so halves and quarters are counted exactly and the stat goes up each time they add up to a whole point; the apple screen shows the exact total. Apples count everywhere the stat does: battles, max health, movement, work speed and riding. They are saved with the creature and kept when it is sent out, boxed or assigned to a base.

### Achievements

Milestones in twelve areas, plus a line for each influence, pay out golden apples (see Golden apples), each tier with its own title. They're checked every few seconds and paid straight into your inventory (anything that doesn't fit drops at your feet), with a chat message. Open your party screen and press **Achievements** to see the log: the title you've earned in each, your progress toward the next tier, every tier and what it pays (hover a tier for its title and details), and how many golden apples you've earned so far.

| Achievement | Tiers and titles | Golden apples per tier |
|---|---|---|
| Blocks Placed | 100 Some Assembly Required · 1,000 Brick by Brick · 10,000 Not Just a Dirt Hut · 100,000 Zoning Violation · 1,000,000 Terraformer | 3 · 6 · 12 · 24 · 48 |
| Battles Won | 1 Beginner's Luck · 10 Getting the Hang of It · 50 Tactical Genius (Allegedly) · 100 Undefeated-ish · 250 Grid Lord · 500 Final Boss Energy | 2 · 4 · 8 · 16 · 32 · 48 |
| Creatures Recruited | 5 Making Friends · 25 Petting Zoo · 100 Animal Magnetism · 200 Creature Hoarder · 300 Noah Would Be Proud · 400 Zookeeper Supreme | 2 · 4 · 8 · 16 · 32 · 48 |
| Creatures Seen | 5 Wait, What Was That? · 25 Bird Watcher · 50 Taking Field Notes · 150 I've Seen Things · 300 Walking Encyclopedia · 433 (every form) Seen It All | 2 · 4 · 8 · 16 · 32 · 48 |
| Items Smelted | 100 Hot Stuff · 1,000 Cooking With Lava · 10,000 Total Smeltdown · 100,000 Industrial Revolution · 1,000,000 Forge of the Gods | 3 · 6 · 12 · 24 · 48 |
| Armor Crafted | 5 Suit Up · 10 Fashion Forward · 25 Runway Ready · 100 Tin Can Collector · 250 Walking Armory | 2 · 4 · 8 · 16 · 32 |
| Seeds Planted | 100 Seeds of Doubt · 1,000 Green Thumb · 10,000 Down on the Farm · 100,000 Old MacDonald Had a Lot · 1,000,000 Feed the World | 3 · 6 · 12 · 24 · 48 |
| Biomes Visited | 3 Day Tripper · 8 Sightseer · 15 Backpacker · 20 Globetrotter · 30 Professional Tourist | 1 · 2 · 3 · 4 · 6 |
| Towers Built | 1 Home Sweet Spire · 2 Double Decker · 3 Skyline Starter · 4 Real Estate Mogul · 5 Lord of the Spires | 6 · 10 · 16 · 24 · 40 |
| Blocks Walked | 10,000 Stretch Your Legs · 50,000 Out for a Stroll · 250,000 Are We There Yet? · 1,000,000 Who Needs Horses? · 5,000,000 Walked to the Moon | 3 · 6 · 12 · 24 · 48 |
| Experience Gained | 100 Shiny Green Orbs · 1,000 Book Smart · 10,000 Big Brain Energy · 100,000 Seasoned Veteran · 1,000,000 Ascended | 3 · 6 · 12 · 24 · 48 |
| Times Defeated | 1 Oops · 5 Respawn Regular · 10 Fall Damage Enthusiast · 50 Death's Frequent Flyer · 100 Immortal (Technically) | 2 · 4 · 8 · 16 · 32 |
| The Eternita Stone | 1 **I Won! I Won the Game!** (earn the Eternita Stone) | 10,000 XP instead of apples. You've technically beaten the game: nothing else happens, and you keep playing as normal. |

**Recruits by influence.** Twelve more lines, one per influence, count creatures of that influence you recruit: 1 · 10 · 15 · 25 · 30 recruits, paying 1 · 2 · 3 · 4 · 6 golden apples.

| Line | Titles |
|---|---|
| Normal Recruits | Plain and Simple · Nothing Special · Vanilla Flavored · Normcore · Extraordinarily Ordinary |
| Fire Recruits | Playing With Fire · Hot Hands · Fire Hazard · Smoke Alarm Fatigue · Too Hot to Handle |
| Frost Recruits | Chill Vibes · Cold Shoulder · Brain Freeze · Snow Problem · Absolute Zero |
| Storm Recruits | Feeling Charged · Under the Weather · Brainstorming · Shocking Behavior · Eye of the Storm |
| Earth Recruits | Down to Earth · Rock Solid · Between a Rock · Stone Cold · Mountain Mover |
| Water Recruits | Making a Splash · Testing the Waters · In Too Deep · Water You Doing? · Tidal Wave |
| Nature Recruits | Tree Hugger · Back to Nature · Wild at Heart · Photosynthesizer · Mother Nature's Favorite |
| Void Recruits | Null and Void · Lost in Space · Empty Inside · Staring Into the Abyss · The Abyss Stares Back |
| Fae Recruits | Pixie Dust · Away With the Fairies · Glamour Shot · Changeling · Fae-mous |
| Darkness Recruits | Lights Out · Afraid of the Dark · Shady Business · Embrace the Dark · Eternal Night Owl |
| Arcane Recruits | Abracadabra · Spell Check · Mana Addict · Wizard Hat Required · Archmage |
| Radiant Recruits | Bright Idea · Glow Up · Shine On · Sun Worshipper · Supernova |

These counts start from the creatures in your party and box when the line was added, then go up with every recruit. The log scrolls with the mouse wheel.

That's 1,223 golden apples in all. Where the counts come from: blocks placed, blocks walked (walking, sprinting and crouching) and times defeated (deaths) are Minecraft's own statistics, so they include everything you did before these were added; battles won and creatures recruited are the creatures mod's statistics; creatures seen is your creature dex; items smelted counts items you take out of furnaces, smokers and blast furnaces; armor crafted counts helmets, chestplates, leggings and boots you've crafted or upgraded at a smithing table (Minecraft's own statistic, so it includes the past; elytra don't count); towers built counts finished towers that belong to you (the ones you claimed with the first offering); biomes visited counts every different biome you've stood in, in any dimension (from when this was added); seeds planted counts crops you plant by hand (wheat, beetroot, carrots, potatoes, torchflowers, pitcher pods, melon and pumpkin seeds, nether wart, cocoa and sweet berries); experience gained counts all XP you earn (from mining, battles and anything else), not XP you spend.

**Type Mastery:** every 10 creatures of one influence you gain (recruited in battle, received in a player trade, swapped from a Wandering Trader or bought from the Black Market, counted from version 0.61) earns a **Type Mastery Trophy** of that influence and the achievement "*Water Influence Master*" (or whichever influence), again and again with no limit. These pay no golden apples or XP: the trophy, a placeable decorative block, goes straight into your inventory. The cups: Wild Champion's Cup (Normal), Emberforged (Fire), Rimeguard (Frost), Stormcaller (Storm), Stoneheart (Earth), Tidebound (Water), Thornweave (Nature), Voidtouched (Void), Glimmerleaf (Fae), Nightshroud (Darkness), Spellwoven (Arcane) and Sunforged (Radiant) Champion Cups. The achievement log shows each influence's progress to its next trophy and how many you have. Give one by command with `/give @s serantis_battle:water_mastery_trophy`.

### Experience and the skill tree

**One XP currency.** Battles give ordinary Minecraft XP (about 10 per creature defeated), on top of mining, mobs, smelting and so on. Recruiting, villager trades, levelling creatures, enchanting, anvils and the skill tree all spend the same XP bar. Party XP saved from before this update is moved into your XP bar, one for one, the first time you play.

**Defeat.** XP is never lost. You keep all of it when you lose a battle or die, and dying drops no XP orbs. Instead, everything you're wearing or holding (armour, weapon, shield, tools) loses half of its full durability. It's never destroyed outright and is left with at least 1 use. On a normal death your items still drop as usual.

**Your stats.** Every player starts with 40 in every stat (HP, Attack, Defense, Speed, Magic, Luck, Charisma) and grows by learning skills. Your hearts and your battle HP are the same stat: in the world it shows as hearts, and in battle as HP at 10 HP per heart (10 hearts = 100 HP). Vitality raises it, and so does anything else that adds hearts. The old player levels and stat points are gone.

**The skill tree.** Open your own Base Core and press **Skills**. Branches sit side by side at a readable size; on smaller screens (or bigger GUI scales) they wrap into more rows and you scroll with the mouse wheel. Hover a skill for its details and click a glowing one to learn it. Your base must be Level 1 or higher. There are 8 branches of 6 skills, 48 in all:
- **Order:** each skill needs the one above it, and each capstone (the last skill in a branch) also needs one skill from a neighbouring branch.
- **Base level:** the first two skills in a branch need a Level 1 base, and the next four need Levels 2, 3, 4 and 5. Upgrading your base opens up the deeper skills.

| Branch | 1 | 2 | 3 | 4 | 5 | 6 (capstone) |
|---|---|---|---|---|---|---|
| **Might** | Strength I: +3 Attack | Strength II: +3 Attack | Strength III: +4 Attack | Heavy Hitter: your melee hits +5% | Strength IV: +5 Attack | **Overpower:** your crits deal ×2.25 instead of ×2 *(also needs Toughness IV)* |
| **Guard** | Toughness I: +3 Defense | Toughness II: +3 Defense | Toughness III: +4 Defense | Toughness IV: +4 Defense | Steady: ranged hits on you −5% | **Bulwark:** +6 Defense *(also needs Heavy Hitter)* |
| **Vitality** | Vigor I: +½ heart (+5 HP) | Hearty I: +½ heart (+5 HP) | Vigor II: +½ heart (+5 HP) | Hearty II: +1 heart (+10 HP) | Vigor III: +½ heart (+5 HP) | **Juggernaut:** +1 heart (+10 HP) *(also needs Toughness IV)* |
| **Arcana** | Insight I: +3 Magic | Insight II: +3 Magic | Spellpower: your magic attacks +5% | Insight III: +4 Magic | Insight IV: +5 Magic | **Attuned:** anvils cost 25% less XP *(also needs Spoils)* |
| **Swiftness** | Quickness I: +3 Speed | Stride I: +5% walking speed | Footwork I: +1 battle tile | Stride II: +5% walking speed | Footwork II: +1 battle tile | **Fleet:** +10% walking speed, +4 Speed *(also needs Labor III)* |
| **Fortune** | Luck I: +3 Luck | Scavenger: 5% chance mobs you kill drop twice | Luck II: +4 Luck | Spoils: 5% chance a creature you beat in battle drops twice | Luck III: +5 Luck | **Treasure Sense:** rare creature drops (under 25%) twice as likely *(also needs Insight III)* |
| **Industry** | Labor I: +8% mining/chopping speed | Labor II: +8% | Prospector: XP blocks give +1 XP | Labor III: +8% | Labor IV: +8% | **Tireless:** +10% mining speed, half the hunger for breaking blocks *(also needs Stride II)* |
| **Presence** | Charm I: +3 Charisma, XP costs −2% | Haggler: XP costs −2% | Charm II: +4 Charisma | Silver Tongue: XP costs −3% | Charm III: +5 Charisma | **Smooth Talker:** +4 Charisma, XP costs −3% *(also needs Labor III)* |

**Presence** discounts add up to 10% off everything that costs XP: recruiting creatures, villager, merchant and Black Market purchases, levelling creatures with XP from the party screen, and the skill tree itself. Prices are shown already discounted. Charisma makes your Rally action in battle stronger. Anvils and enchanting cost levels rather than XP points, so they aren't included (Arcana's Attuned covers anvils).

**Cost.** The first skill costs 150 XP, and every skill you learn makes the next one cost 12.6% more, whatever branch it's in. The first four take about 735 XP (a couple of hours of early play). The 10th costs 435, the 20th 1,430, the 30th 4,685 and the last 39,665. The whole tree costs about 353,275 XP before Presence discounts, the same as before Presence was added. A fully trained player tops out around 55–60 in their best stats: strong, but still below a Level 20 legendary creature.

### Naming creatures

Right after you recruit a creature in battle, or pick your starter, a box pops up to name it (up to 20 characters). **Keep** leaves it with its species name. A named creature shows its name over its head, in battle and on the party screen, where its temperament and species follow in brackets, e.g. "Sparky (Brave Scrap Goblin)". Names are saved with the creature and kept when it's sent out, boxed or levelled up.


### Starting a battle

Hit a wild creature with any weapon to battle it: a sword, axe, spear, mace, trident, bow or crossbow (held), or anything else Minecraft counts as a weapon. Or shoot it: an arrow from a bow or crossbow, or a thrown trident, starts the battle as soon as it hits (the arrow drops where it hit, so you can pick it up again).

### Riding

While you ride a creature, your crosshair passes through the creature you're sitting on, so you can place and break blocks and hit or shoot other creatures to start a battle without getting off.

### Flyers and swimmers in the wild

- **Flyers** (every creature that flies): 60% take to the air when they appear and stay airborne, cruising anywhere from 4 to 40 blocks above the ground (more often in the lower half). When a player is nearby they often swoop low around them, so you can still reach one with a melee weapon (or just shoot it) to start a battle. The other 40% stay on the ground.
- **Swimmers:** 80% stay beneath the surface. Bottom dwellers (Crab, Giant Octopus, Tortoise, Nautiloper, Whirlpool Leech, Reef Spitter, Revenant, Sahuagin, River Zorling) sink and crawl along the sea floor or river bed; every other swimmer swims around underwater. Ducks always paddle on the surface. The other 20% float at the surface.
- Tamed creatures, base workers, ridden or leashed creatures and creatures in battle move as usual. Creatures already in your world pick their way of moving the next time they're loaded.

### Villagers

Villagers in villages now spawn at **Level 5** with their profession already set, so recruiting one (by battle, like any creature) gets you that profession; in battle a villager shows it after its name, e.g. "Villager (Bard)". A villager keeps the profession it was born with: there's no changing it (villagers recruited before this update that were never trained can still be trained once). Each recruit starts on its first deed (toward Novice) from the moment you recruit it.

Professions are the creatures mod's (Farmer, Fisherman, Gardener, Weaver, Hunter, Librarian, Explorer, Smith, Netherwright, Carpenter, Engineer, Cook, Leatherworker, Mason, Jeweler, Voidwalker, Alchemist) plus three that work from your base. Assign them to a base as workers:

- **Bard:** every creature working at that base works 25% faster.
- **Innkeeper:** while one works at any base of yours, sleeping through the night refills your hearts.
- **Noble:** creatures that spawn inside the base or within a chunk (16 blocks) of its border are Rare or rarer 35% of the time (normally 15%): 50% Common, 15% Uncommon, 22% Rare, 10% Super Rare, 3% Legendary.

**Town Guards.** About one in five village villagers is a Town Guard standing watch ("Town Guard" over its head). They can't be fought, hurt or recruited: try it and you'll be told "I wouldn't try that, punk...". Attack any other villager and **3 to 5 Town Guards** join the battle against you, each a few levels above the villager (up to Level 20) and much tougher than a villager. Defeated guards can't be recruited.

**Merchants** turn up in villages. Like a vanilla wandering trader, each merchant sells its own random handful (6 to 8) of useful things from a pool of about 50: ender pearls, name tags, saddles, diamonds, golden carrots, ingots, blaze rods, shulker shells, blue ice, sponges, spyglasses and so on, for XP. Recruit one and its stock is the same every time you open it.

**Move Tutors** live in villages like other villagers (recruit one by battling it). Put it to work at your base and right-click it to talk to it (sneak and right-click for the usual worker options). Pick a creature in your party and the Tutor offers 3 moves it could learn but doesn't know, at random (moves of its influence and universal moves). Choose one, choose which of its 3 moves to forget, and pay **25 XP** (less with Presence). The offer for each creature stays the same, however often you look, until it's been taught one of them; then talk to the Tutor again for 3 new ones. A creature always keeps at least one move that deals damage.

**Wandering Traders** turn up in the wild, the way vanilla wandering traders do (every vanilla wandering trader becomes one), with "Wandering Trader" over their heads. Right-click one, pick a creature from your party and swap it: you get a random creature of the same level back, of any kind and rarity (the usual wild odds: 70% Common, 15% Uncommon, 10% Rare, 4% Super Rare, 1% Legendary). Each trader does 3 swaps, then moves on; otherwise it leaves after a day. It can't be fought, hurt or recruited.

**Black Market Merchants** never live in villages. Any wild creature that spawns in the Overworld has a 0.05% chance to be a Black Market Merchant instead (nearby players hear whispers in chat). Each one carries 3 to 5 creatures that are Rare (70%), Super Rare (25%) or Legendary (5%), Level 10 to 20, and sells each for **350 XP**: right-click it to see what's for sale (the creature goes to your party, or your box if your party is full). It can't be fought, hurt or recruited, and it leaves after a day, or as soon as it sells out.

The **Alchemist** still sells potions and brewing ingredients for XP, and its trade screen also turns a golden apple from your inventory into 20 XP.

**Villager looks:** each profession wears its own model: Farmer, Fisherman, Hunter (fletcher), Librarian, Explorer (cartographer), Smith (armorer), Leatherworker, Mason, Jeweler (nitwit), Voidwalker (cleric), Gardener and Weaver (shepherd), Netherwright (weaponsmith), Carpenter and Engineer (toolsmith), Cook (butcher), Alchemist, Bard, Innkeeper, Noble, Merchant, Move Tutor, Town Guard, Black Market Merchant and Wandering Trader; villagers with no profession wear the plain villager model. Each Creature Master has a model of its own influence: the Unbound Grandmaster (Normal), Ember Warlord (Fire), Rime Harpoon Master (Frost), Thunderdrum Master (Storm), Mountain Bearer (Earth), Abyssal Anchor Champion (Water), Thornbloom Kama Master (Nature), Hollow Riftwalker (Void), Fey Blademaster (Fae), Umbral Shinobi Master (Darkness), Thousand Blade Archon (Arcane) and Sunthrone Pharaoh (Radiant). Villagers and Creature Masters are never Enraged or Mutated. The models are converted with `tools/convert_villagers.py`.

**Every village has villagers.** The first time someone walks into a village it gets 4 to 7 villagers, each with a different profession, and 3 to 5 Town Guards, spread around its streets and houses. Village villagers never despawn. Every three days (three in-game days of play: 72,000 ticks), the village fills any empty spots, from villagers recruited or beaten in battle, with new villagers of random professions it doesn't already have, and replaces any missing guards; the check happens when someone is in the village. Each villager remembers its village, and each village's makeup is saved in `serantis_villages.json` in the world folder. (Minecraft's own village villagers aren't used.)

How often each profession turns up in a village: Farmer and Mason are the most common; Explorer, Engineer, Leatherworker and Alchemist a little less; Bard, Innkeeper and Move Tutor uncommon; Netherwright and Jeweler rarer; Noble and Voidwalker the rarest.

### Creature Masters

Creature Masters are the gym leaders. They turn up fairly often: any wild creature that spawns has a 1% chance to be a Creature Master instead (nearby players get a chat message). Each one has an influence that fits the biome it appears in, shown in its name ("Fire Creature Master"), is Level 12 to 20, and stays for a day. It can't be hurt outside battle, and you don't have to fight it.

Hit it with a weapon to challenge it. It fights alongside 3 to 5 creatures of its influence, each 3 to 5 levels above or below its own and more often Rare or rarer than wild creatures. Its team is 25% stronger than wild creatures (the Master 40%), and each of them fights with the strongest of several move sets. None of them can be recruited. Beat the Master and it leaves; the battle pays **double XP** and gives you a **Gym Master Stone** of its influence. Lose or run, and it stays until its day is up.

Each Gym Master Stone raises your **mastery** of that influence, up to 5 stones per influence (after 5, beating that influence's Masters still pays double XP but no more stones). Every stone gives creatures of that influence +3% spawn weight around you, up to +15%: they turn up a little more often in the biomes they already live in, and the rarity odds don't change.

Gym Master Stones and the Eternita Stone are **trophies**: place one and it stands on the block as a gem on its pedestal, facing you (it glows faintly, and breaks back into the stone). The Normal stone is Wild Quartz, Fire the Ember Ruby, Frost the Rime Diamond, Storm the Storm Citrine, Earth the Stoneheart Topaz, Water the Tidal Aquamarine, Nature the Grove Emerald, Void the Void Amethyst, Fae the Fae Rose Heart, Darkness the Shadow Onyx, Arcane the Arcane Spellstone and Radiant the Sunstone; the Eternita Stone is the Prismatic Gem. The models are converted with `tools/convert_trophies.py`.

Collect all 5 stones of all 12 influences (60 in all) and you get the rainbow **Eternita Stone**: wild creatures that spawn around you are Mutated 5 times in 100 instead of 1 in 100. It also earns the achievement "I Won! I Won the Game!".

### Taking down a base

Hitting a Base Core asks first: creatures can't be recovered from a destroyed core, so if you say yes, every creature working at that base is sent back to your party (or box) before the core breaks and drops. Only the core's owner can take a base down. "No, keep it" leaves everything as it was.

### Creatures are never lost

A creature only leaves you if you release it. A creature you've sent out that gets left behind (you teleport, go through a portal, die and respawn far away, log out, or just get more than 96 blocks away) comes back to your party, or your box if the party is full: straight away if it's still nearby in the world, or within a second or so if its chunk has unloaded (the game remembers a copy of every creature you have out, and the one left in the world vanishes when its chunk next loads, so it's never duplicated). A creature that belongs to you can't die in the world either: a blow that would finish it leaves it on 1 HP, and if it was out it goes back to your party to recover (creatures working at a base stay put).

### Trading creatures

Right-click another player and choose **Send trade request**. They get a request to accept or decline (it expires after 60 seconds). If they accept, a trade screen opens for both of you: each picks one creature from their party, sees what the other is offering (level, influence, rarity, HP, movement and stats), and presses **Confirm trade**. When both have confirmed, the two creatures swap owners, keeping their level, moves, temperament, name and golden apples. Changing a pick means both have to confirm again, and either player can cancel at any time (closing the screen cancels too). You can't trade during a battle, and only creatures in your party can be traded.
### Movement and work speed

- **Battle movement** comes from Speed and level. Level 1 creatures move 2 to 4 tiles a turn and Level 20 creatures 7 to 10, with the range rising in a straight line between. Where a creature falls in its level's range depends on its Speed: Level 20 Speed 44 is the slowest and 103 the fastest. For example, a typical creature (Speed 62) moves 3 tiles at Level 1, 5 at Level 10 and 8 at Level 20. Speed changes, Slowed, extra movement and terrain costs still apply on top. The player's own movement is unchanged.
- **Work speed at a base** starts at 50% for every creature at Level 1 and climbs with level, faster the higher its Charisma: 50% + (level − 1) ÷ 19 × (Level 20 Charisma ÷ 63). At Level 20 a typical creature (Charisma 63) works at 150%, the most charming (115) about 230% and the least (51) about 130%. At Level 10 those are about 100%, 140% and 90%. The base screen shows each worker's speed.

### Creature sizes and terrain

- **Big creatures cover more of the grid:** small and medium creatures cover 1 tile, large 2×2, extra large 3×3 and XX large 4×4. A big creature can be targeted on any of its tiles, and its range is measured from its nearest edge. It needs room for its whole body to move or stop, and zones, area attacks, pushes and pulls all count every tile it covers. Each creature's size comes from the size word in its model pack's file name (`tools/model_sizes.tsv`). For creatures from the original creatures mod, which came without named models, it's judged from the model's size (under 3.6 blocks → 1×1, 3.6–7.5 → 2×2, 7.5 and up → 3×3). Every creature's size is listed in `books/Creature_Footprints.xlsx` (judged ones highlighted). Edit `tools/model_sizes.tsv` and rerun `tools/build_footprints.py` to change any.
- **Arenas grow** by 2 tiles per size step of the biggest creature in the fight (2×2 → +2, 3×3 → +4, 4×4 → +6), up to 19×19.
- **Difficult terrain costs extra movement.** Each tile costs 1, plus 2 per block climbed (going down is free), plus 2 for shallow water and 3 for deep water. Swimmers pay nothing extra for water. Frozen Ground and Sinkholes add 1. The blue movement preview on the battle screen shows all of this.
- **Flying creatures are always in the air.** Every tile costs them 1, they pass over ground zones, and they never get high ground (no extra hit or crit chance from height).

### Battle effects

The battle grid tile under each creature (and under the player) reacts as the battle plays out:

| What happened | Tile |
|---|---|
| Attacks | The attacker's tile shakes |
| Takes damage (hits, burns, poison, zones, recoil) | Flashes orange |
| Critical hit | The target's tile flashes red |
| Attack misses | The target's tile flashes white |
| Healed | Flashes green |
| Boosted (stat up, extra movement, Counter Stance) | Flashes blue |

If several flashes hit one tile at the same moment, the most important one shows, in the order red, white, orange, green, blue.

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
