# Serantis

A Minecraft 26.3 modpack built around the Ahrivaea mods by jeffreystu10 and DaringDuck96.

## What's in it

| Mod | What it does |
|---|---|
| **Ahrivaea Creatures** | 239 creatures (312 forms) replace every vanilla mob. Fire Emblem-style grid battles in biome arenas, recruiting, Palworld-style bases where creatures work, riding (saddle any medium or bigger creature; fliers fly and swimmers swim), and your own levelled-up character. |
| **Ahrivaea Tower Challenges** | A ruined tower over every stronghold that the whole server rebuilds by donating items, with leaderboards and rewards. A finished tower opens the way to the End, becomes its top donor's base, and gives every offering back. |
| **Ahrivaea Guilds** | Guild members can't hurt each other or each other's creatures, and can build at any tower a member owns. |
| **Ahrivaea Map** | A small minimap (50 pixels across in Serantis; N hides it) and a full-screen map (press M) of everywhere you've been, showing nearby creatures and the other players on the server. On the full map, right-click a spot to mark it with a name and a colour, or right-click a marker to change or delete it. Your markers show on both maps; they're yours alone, saved on your computer per world (or server) and dimension. |
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
| **Serantis Dyed Water** | Craft a Water Bucket with any of the 16 dyes (shapeless) to get that colour's water bucket, e.g. Red Water Bucket. Dyed water flows, can be swum in, puts out fire, hydrates farmland and can be scooped back up like normal water, but it's tinted the dye's colour. Dispensers pour it like water. A few things only work with plain water: it can't waterlog slabs or stairs, fill a cauldron, or make bubble columns. The water bucket is used up whole in the craft, so no empty bucket is left over. |
| **Serantis Towers** | Cheaper first towers (the Ember Spire takes 64 coal, the Timber Watch 64 logs and the Quarry Keep 128 cobblestone), and the first player to make an offering at a tower claims it for good: anyone can help rebuild it, but its Base Core, the extra Base Core and the land around it go to whoever claimed it. |
| **Serantis Battle and Spawning** | Every creature's battle stats come from the Serantis stat sheet. Creatures max out at Level 20, start at half their Level 20 stats at Level 1 and grow in a straight line. Spawns roll their rarity first (70% Common, 15% Uncommon, 10% Rare, 4% Super Rare, 1% Legendary), can be any level from 1 to 20, and have a 1% chance of being Mutated. Creatures fight with 3 moves each from the Move Dex, and every wild enemy gets a random battle mentality. |

### The Arcane Dragon

The creatures mod's Arcane Dragon is retired: the Clockwork Dragon is the arcane dragon in Serantia. It never spawns, and Wandering Traders, Black Market Merchants and Creature Masters never hand it out, so the dex counts 434 creature forms to find.

### The book of Serantia

Every player, whatever their class, starts with the written book **Serantia: A World Forged by Dragons** (the story of the Twelve Great Dragon Lords and the Council of Eternia). Players already in the world get it the next time they join. It's given once; the text is in `addons/ahrivaea-battle/src/dev/serantis/battle/LoreBook.java`.

### Lore books

Every creature you beat in battle has a **2.5% chance** to drop a lore book with the battle's loot, picked at random from all 27 lore books: Tome of the Dragons (12 volumes), Eternita Calling (4), The Histories of Serantia (6) and The Chronicles of the Lost Brothers (5). They're kept in `addons/ahrivaea-battle/serantis_battle/lore_books.json`: a list of books, each with a `title`, an `author` and its `text` (a blank line starts a new paragraph). Each book gets a heading page with its title, then its text laid out over as many pages as it needs.

### Classes

After the welcome guide, every player chooses a class, once (players already in the world choose the next time they join):

| Class | Boost | Head start |
|---|---|---|
| Knight | Attack | An Iron Chestplate and an Iron Sword |
| Bulwark | Defense | A Shield and an Iron Mace (a mace) |
| Wizard | Magic | A Magic Staff linked to a random spell of the influence they choose (any but Normal) |
| Journeyman | Luck | 2 Golden Apples. **Lucky Finds** (for good): beaten creatures drop their loot twice 10% more often (on top of Fortune), and lore books drop twice as often (5%) |
| Performer | Charisma | Chooses their starter's temperament (if they already have a starter, the creature first in their party gets it). **Showstopper** (for good): rallies are twice as strong, and workers at bases you own work 5% faster while you're online |
| Tradesman | None | **Crafts anywhere** (for good): press **U** (Controls > Serantis; K opens your party) to open a full 3×3 crafting grid wherever you are, no crafting table needed (not during a battle) |

The boost is a flat +5 to the class's stat (none for the Tradesman), for good (players start at 33 in every stat).

**Magic Staffs.** A **Wooden Staff** is crafted from three sticks and an Eye of Ender (shapeless). Until it has a spell it's just a wooden mace: 5 attack damage, a slow swing, and it wears out like a wooden tool (59 uses). To give it a spell, right-click an enchanting table while holding it and you're offered three spells at random from the Move Dex (hover one for the full explanation). Pick one and pay **25 XP** (less with Presence) and the staff is linked to that spell for good, becoming that influence's staff: a fire spell turns it into an **Emberwrath Staff**, and its tooltip shows the spell. The offer on a staff stays the same until you pick (sneak-click the table to use it normally). A Wizard's first staff comes already linked to a random spell of the influence they chose. In battle, a linked staff in your main hand or off hand lets you cast its spell as well as your normal attack (two staffs, two spells). Hitting a creature with a staff starts a battle like any weapon. `/give @s serantis_battle:magic_staff` gives a Wooden Staff. Each influence's staff has its own design (there's no Normal staff, and Normal or universal moves are never offered): Emberwrath (Fire), Rimespire (Frost), Thundercall (Storm), Stoneheart (Earth), Tidecaller (Water), Thornbloom (Nature), Riftwalker (Void), Moonpetal (Fae), Gloomreaver (Darkness), Runeweaver (Arcane) and Dawnbringer (Radiant). Beaten mages drop staffs too: every Mage (one for each influence) and the Ember, Rime, Thunder, Rune and Dawn Magi have a 30% chance to drop their influence's staff, already linked to one of its spells at random. The models are converted with `tools/convert_staffs.py`.

### Welcome guide

The first time a player joins, a welcome guide pops up (over the starter screen, which comes back after). It welcomes them to Serantia (the world's name; the pack and repository are called Serantis) and runs through what it's about: picking a starter, battling and recruiting creatures, collecting them, XP as the main currency, rebuilding towers, Creature Masters and their stones, achievements, winning the game with the Eternita Stone, and that it's still Minecraft, where building and creativity are as valid a way to play as any battle. Read it again any time from the **Guide** button on the party screen. The guide has a second tab, **Your Base**, also opened by the **Guide** button on a Base Core's screen: how bases work (area, levels, putting creatures to work), how a worker decides what to do and how fast it works, what every job does and needs, chests and item-frame labels, raids, knock-outs, and what villagers add to a base.

### Battle stats and levels

- Each creature's Level 20 stats are in `addons/ahrivaea-battle/serantis_battle/stats.tsv`, built from the stat sheet with `tools/build_battle_stats.py`. They apply to every creature, including the original creatures mod's, without changing its files.
- Level 1 is 50% of Level 20, rising in a straight line. Creatures can't be fed past Level 20.
- Every creature spawn (wild spawns, replaced vanilla mobs, and battle reinforcements) first rolls a rarity: 70% Common, 15% Uncommon, 10% Rare, 4% Super Rare, 1% Legendary. It then picks a creature of that rarity that lives in the biome, using each creature's spawn weight. If the biome has none of the rolled rarity, the roll is repeated among the rarities it does have (the Nether and outer End have no Legendaries, so their 1% is shared among the rest). Among Common creatures, those of the Normal, Fire, Frost, Earth, Storm, Nature and Water influences have 5% more spawn weight than the others, so they turn up a little more often; the rarity odds don't change. The odds are in `addons/ahrivaea-battle/src/dev/serantis/battle/SpawnOdds.java`.
- Every spawned creature has a 1% chance of being Mutated, with its mutated skin. Enraged keeps the creatures mod's own rare chance.
- A creature's level does not depend on its rarity. Any wild creature can be Level 1–20. Wild levels sit within 5 of the nearest player's own level, kept inside 1–20, so from player level 25 up wild creatures are Level 20. New players get a gentler window that widens as they grow, so early fights aren't stressful: player levels 1–4 meet creatures from 2 levels below to 1 above their own, level 5 from 3 below to 2 above, level 6 from 4 below to 3 above, level 7 from 5 below to 4 above, and level 8 and up the full 5 either way. Reinforcements are at most the level of the creature you attacked, so they stay in the window too. The window also holds when you start a fight: a wild creature above the top of your window (one that appeared near a stronger friend, an old one, a villager, a Mimic) drops to the top of it as the battle begins, and its reinforcements follow. Creature Masters, the Harbinger, the Acolytes, goblin raids and spars keep their levels. Mimics are also rolled from your window (rare chests' 2 levels higher).
- Starters begin at Level 1. The summoned bosses keep their levels for every player, new ones included: End dragons are always Level 20, the False Hydra Level 15-20 and the Harbinger of Calamity Level 16-18.

### Moves

- Every creature knows 3 moves, picked at random when it first appears (or first battles) and kept for good, including after it's caught. Any creature can know universal moves; magic moves are limited to creatures of that influence (Normal magic to creatures with no influence). At least one of the three always deals damage.
- The moves come from the Move Dex (`books/Move_Dex.xlsx`), built into `addons/ahrivaea-battle/serantis_battle/moves.json` by `tools/build_moves.py`. That script also records how each move behaves on the battle grid.
- Damage = Power × 4 × level factor × (Attack + 10) ÷ (Defense + 10). Magic moves use Magic on both sides. Influence matchups and the same-influence bonus (×1.2) still apply.
- Moves that reach more than 1 tile are ranged: 85% base hit chance. Moves that only reach 1 tile are melee: they deal 15% more damage. Luck shifts hit chance either way.
- Crits: a flat 10% chance (20% with high ground: standing at least 1 block above the target), and they double damage.
- Reach: universal ranged moves reach twice the Move Dex's maximum; magic ranged moves 1.5× (rounded up). Reach also grows with the creature's level: ranged attacks average about 2-3 tiles at Levels 1-4, 3-5 at Levels 5-8, 6 at Levels 9-15 and 7-8 at Levels 16-20 (never under 2 tiles). Melee moves always reach 1. (Your own bow, crossbow and staff spells don't change with level.)
- Stat changes in the Move Dex are 10% of that stat per point (Defense +2 = +20%); accuracy changes are percentage points. Healing and damage over time use the same scale as Power.
- Statuses: Bleeding and Poison lose 5% of max HP each turn; Burning deals its stated damage each turn; Frozen, Stunned and Asleep skip turns (Asleep wakes when hit); Immobilized can't move; Slowed moves 2 fewer tiles; Confused attacks a random nearby unit half the time; Frightened deals about 30% less damage.
- **Statuses linger after a battle** for 3 minutes: annoying, never dangerous, and nothing carries into the next battle. **You** get a Minecraft effect, which milk cures as usual: Poison or Bleeding give Hunger; Burning or Doom, Weakness; Frozen or Slowed, Slowness; Immobilized, Slowness II; Stunned or Asleep, Mining Fatigue; Confused switches your movement keys around (forward walks you back, left goes right, and the other way round); Frightened halves how far you can see (your render distance, at least 2 chunks). Milk cures these two as well, and so does dying. **Your creatures** show it on the party screen ("Ember - Poisoned"). Poison, Bleeding, Burning and Doom make them slowly lose HP (1% of their max every 5 seconds, never below half); anything else makes them sluggish while they're out with you. **A bucket of milk is the cure, in or out of battle, and the bucket is used up** (drinking milk never gives the bucket back). Out of battle, drink it yourself, or feed it to a creature (right-click it in your inventory). In battle, the Item action on a unit with a status (yourself, or a creature next to you) uses a bucket of milk if you have one and cures every status on it, without healing; on a unit with no status the Item action uses a healing potion as before. This goes for every creature that ends the battle with a status: yours, a co-op ally's, and enemies still standing if you flee or lose (a wild one keeps it while it roams, and brings it along if recruited). Friendly spars leave nothing behind.
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

**Right-click food in your inventory.** Open your inventory (E) and right-click food or any other consumable (with nothing on your cursor) for a menu: eat or drink it yourself, or give it to a creature in your party (or one of yours that's out nearby). Food and healing potions heal a creature like feeding it from the party screen; a golden apple opens the stat picker for the creature you choose. (Right-clicking other items still splits stacks as usual.)

**Plenty of meat.** Cows, pigs, chickens, sheep, rabbits, goats, ducks, crabs and fish drop three times as much meat and fish as before (beef, porkchops, chicken, mutton, rabbit, cod, salmon and tropical fish), however you get their drops: winning a battle, a base job or a raid. Cows also drop a bucket of milk 20% of the time. Their other drops (leather, wool, feathers and so on) are unchanged.

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

Milestones in twelve areas, plus a line for each influence, pay out golden apples (see Golden apples), each tier with its own title. They're checked every few seconds and unlocked with a chat message; the golden apples wait for you to claim them, so a full inventory never loses any. Press **Claim** on the Achievements page (party screen) to collect every waiting reward that fits in your inventory; anything that doesn't fit stays waiting until you make room. Tiers with a reward waiting blink green. XP rewards are still paid straight away. Open your party screen and press **Achievements** to see the log: the title you've earned in each, your progress toward the next tier, every tier and what it pays (hover a tier for its title and details), and how many golden apples you've earned so far.

| Achievement | Tiers and titles | Golden apples per tier |
|---|---|---|
| Blocks Placed | 100 Some Assembly Required · 1,000 Brick by Brick · 10,000 Not Just a Dirt Hut · 50,000 Zoning Violation · 150,000 Terraformer | 3 · 6 · 12 · 24 · 48 |
| Battles Won | 1 Beginner's Luck · 10 Getting the Hang of It · 50 Tactical Genius (Allegedly) · 100 Undefeated-ish · 250 Grid Lord · 500 Final Boss Energy | 2 · 4 · 8 · 16 · 32 · 48 |
| Creatures Recruited | 5 Making Friends · 25 Petting Zoo · 100 Animal Magnetism · 200 Creature Hoarder · 300 Noah Would Be Proud · 400 Zookeeper Supreme | 2 · 4 · 8 · 16 · 32 · 48 |
| Creatures Seen | 5 Wait, What Was That? · 25 Bird Watcher · 50 Taking Field Notes · 150 I've Seen Things · 300 Walking Encyclopedia · 434 (every form) Seen It All | 2 · 4 · 8 · 16 · 32 · 48 |
| Items Smelted | 100 Hot Stuff · 1,000 Cooking With Lava · 10,000 Total Smeltdown · 50,000 Industrial Revolution · 150,000 Forge of the Gods | 3 · 6 · 12 · 24 · 48 |
| Armor Crafted | 5 Suit Up · 10 Fashion Forward · 25 Runway Ready · 100 Tin Can Collector · 250 Walking Armory | 2 · 4 · 8 · 16 · 32 |
| Seeds Planted | 100 Seeds of Doubt · 1,000 Green Thumb · 10,000 Down on the Farm · 50,000 Old MacDonald Had a Lot · 150,000 Feed the World | 3 · 6 · 12 · 24 · 48 |
| Biomes Visited | 3 Day Tripper · 8 Sightseer · 15 Backpacker · 20 Globetrotter · 30 Professional Tourist | 1 · 2 · 3 · 4 · 6 |
| Towers Built | 1 Home Sweet Spire · 2 Double Decker · 3 Skyline Starter · 4 Real Estate Mogul · 5 Lord of the Spires | 6 · 10 · 16 · 24 · 40 |
| Distance Traveled | 10,000 Stretch Your Legs · 50,000 Out for a Stroll · 150,000 Are We There Yet? · 400,000 Frequent Traveler · 1,000,000 Around the World | 3 · 6 · 12 · 24 · 48 |
| Experience Gained | 100 Shiny Green Orbs · 1,000 Book Smart · 10,000 Big Brain Energy · 75,000 Seasoned Veteran · 250,000 Ascended | 3 · 6 · 12 · 24 · 48 |
| Times Defeated | 1 Oops · 5 Respawn Regular · 10 Fall Damage Enthusiast · 50 Death's Frequent Flyer · 100 Immortal (Technically) | 2 · 4 · 8 · 16 · 32 |
| Lore Books Collected | 1 Light Reading · 5 Bookworm · 10 Well Read · 20 Lorekeeper · 30 Krea's Apprentice | 2 · 4 · 8 · 16 · 32 |
| The Eternita Stone | 1 **I Won! I Won the Game!** (earn the Eternita Stone) | 10,000 XP instead of apples. You've technically beaten the game: nothing else happens, and you keep playing as normal. |
| Chaos Seeker | Face the Acolyte of the Last | No apples: Enraged creatures find you more often (+2% of wild creatures near you if you beat him, +1% if not). |
| Bragging Rights | Win a spar against another player | Nothing at all. Just the bragging rights. |

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

That's 1,285 golden apples in all. Where the counts come from: blocks placed, distance traveled (walking, sprinting, crouching, and riding creatures) and times defeated (deaths) are Minecraft's own statistics, so they include everything you did before these were added; battles won and creatures recruited are the creatures mod's statistics; creatures seen is your creature dex; items smelted counts items you take out of furnaces, smokers and blast furnaces; armor crafted counts helmets, chestplates, leggings and boots you've crafted or upgraded at a smithing table (Minecraft's own statistic, so it includes the past; elytra don't count); towers built counts finished towers that belong to you (the ones you claimed with the first offering); biomes visited counts every different biome you've stood in, in any dimension (from when this was added); seeds planted counts crops you plant by hand, once per spot per in-game day so replanting one spot doesn't farm it (wheat, beetroot, carrots, potatoes, torchflowers, pitcher pods, melon and pumpkin seeds, nether wart, cocoa and sweet berries); experience gained counts all XP you earn (from mining, battles and anything else), not XP you spend.

**Type Mastery:** every 10 creatures of one influence you gain (recruited in battle, swapped from a Wandering Trader or bought from the Black Market, counted from version 0.61; player trades don't count, or swapping back and forth would farm trophies) earns a **Type Mastery Trophy** of that influence and the achievement "*Water Influence Master*" (or whichever influence), again and again with no limit. These pay no golden apples or XP: the trophy, a placeable decorative block, goes straight into your inventory. The cups: Wild Champion's Cup (Normal), Emberforged (Fire), Rimeguard (Frost), Stormcaller (Storm), Stoneheart (Earth), Tidebound (Water), Thornweave (Nature), Voidtouched (Void), Glimmerleaf (Fae), Nightshroud (Darkness), Spellwoven (Arcane) and Sunforged (Radiant) Champion Cups. The achievement log shows each influence's progress to its next trophy and how many you have. Give one by command with `/give @s serantis_battle:water_mastery_trophy`.

### Experience and the skill tree

**One XP currency.** Battles give ordinary Minecraft XP: 10 for a win plus 3 × its level for each creature beaten (x1.5 for Super Rare and Legendary), and then the whole amount x1.5. Beating one creature pays 20 XP at Level 1, 38 at Level 5, 60 at Level 10 and 105 at Level 20, so tougher fights pay much more, on top of mining, mobs, smelting and so on. Recruiting, villager trades, levelling creatures, enchanting, anvils and the skill tree all spend the same XP bar. Party XP saved from before this update is moved into your XP bar, one for one, the first time you play.

**Levelling creatures.** On the party screen you spend XP to level a creature: 8,000 XP in all from Level 1 to Level 20, rising steadily from 150 (Level 1 to 2) by about 30 per level to 692 (19 to 20), and on in the same way past 20. Presence discounts apply.

**Knocked out.** A creature of yours that drops to 0 HP in battle is knocked out for 5 minutes: its party card shows 0 HP and "knocked out", nothing heals it (food, potions, resting) and it sits out battles (it's moved to the back of your party). After 5 minutes it's back on its feet at 1 HP, you get a message, and food or a potion can heal it again. Friendly spars knock no one out. If you fall in battle yourself, the battle is lost; you're back at full health, but everything you're wearing or holding loses half its durability (see Defeat).

**Defeat.** XP is never lost. You keep all of it when you lose a battle or die, and dying drops no XP orbs. Instead, everything you're wearing or holding (armour, weapon, shield, tools) loses half of its full durability. It's never destroyed outright and is left with at least 1 use. On a normal death your items still drop as usual.

**Your player level** is the number of skills you've learned on the skill tree (you start at 0). It sets the level of the wild creatures around you (see the level window) and when villagers stop warning you off.

**Your stats.** Every player starts with 33 in Attack, Defense, Speed, Magic, Luck and Charisma (about a Level 2 creature) and 100 battle HP (10 hearts), and grows by learning skills. Your hearts and your battle HP are the same stat: in the world it shows as hearts, and in battle as HP at 10 HP per heart (10 hearts = 100 HP). Vitality raises it, and so does anything else that adds hearts. The old player levels and stat points are gone.

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


### Creatures of the night
Seven creatures only appear at night in the Overworld, at their usual odds, but never by day: **Ghost** (Darkness, Common), **Nightmare Stalker** (Darkness, Common), **Night Hag** (Darkness, Uncommon), **Yurei** (Darkness, Uncommon), **Vampire** (Darkness, Rare), **Gloomwing** (Darkness, Rare) and **Werewolf** (Normal, Rare). That goes for battle reinforcements too. In the Nether and the End nothing changes. The list is in `NightOnly.java`.

### Blood Moons
Every fifth night (nights 5, 10, 15 and so on) is a **Blood Moon**: the moon rises red instead of white, and everyone in the Overworld gets a "A Blood Moon rises..." message as night falls. **Bloodfang Skreechers** (a Rare, Normal creature) appear only on Blood Moon nights: now and then one turns up 24–48 blocks from a player, on the open surface (under the sky, never in caves or oceans), at most two near you at once, about four or five over the night. Their levels follow your level window. The wild ones are gone by morning; recruit one to keep it. It's a winged flyer.

### Shiny and angry creatures
When a Mutated creature appears anywhere within your render distance you hear a cheerful jingle and see "Did you see something shiny?". When an Enraged one appears you hear a roar and see "Ooh! They look mad!".

### Wild chests and Mimics
Every morning chests appear on the surface around each player in the Overworld (about 5 each, 24-96 blocks away, never inside a base). Most hold ordinary loot like the chests in dungeons, mineshafts, shipwrecks, temples and villages; a rare few (about 1 in 12) hold Orichalcum Ingots, diamonds, emeralds, gold and golden apples. **Each wild chest has a 60% chance to be a Mimic**: open it (or try to break it) and it comes alive and battles you. A Mimic holds no loot: beat it and you only get what the Mimic itself drops (any beaten Mimic also has a 5% chance to drop a wither skeleton skull). Wild chests vanish the next morning (spilling anything left inside) and new ones appear somewhere else, so don't store things in them.

### Starting a battle

Hit a wild creature with any weapon to battle it: a sword, axe, spear, mace, trident, bow or crossbow (held), or anything else Minecraft counts as a weapon. Or shoot it: an arrow from a bow or crossbow, or a thrown trident, starts the battle as soon as it hits (the arrow drops where it hit, so you can pick it up again).

### Shields and changing gear in battle

- **Shields:** a shield in your off hand gives you **+20 Defense** in battle (players start at 33), for as long as it's there.
- **Bows and crossbows:** after choosing where to move, pick your bow and you can shoot any enemy 1 to 6 tiles from that spot (a crossbow 7). Smith improvements and Sharpness add Power, like they do for melee weapons.
- **Changing gear:** on your turn, after choosing where to move, pick **Equip** (under Wait) to put a weapon, shield or staff from your inventory into a hand, or to empty a hand. It takes the place of your attack that turn. Shields always go in the off hand; staffs can go in either. Your attack, your staff spells and your Defense change straight away.

### Creature sounds

Creatures make sounds borrowed from Minecraft's own mobs to suit their bodies: wolves and hounds growl, birds chirp or screech, dragons roar, big beasts grunt, sea monsters groan, spirits whisper, horrors rumble, golems clank, fairies chime, trees creak, and so on. Humanoid people (magi, knights, goblins, giants, villagers...) sound like villagers. Pitch follows size: small creatures sound higher, big ones deeper. Idle sounds come now and then (about every 12 seconds). Each creature's kind is in `addons/ahrivaea-battle/serantis_battle/creature_sounds.tsv` (one line per creature; the kinds and the sounds they use are in `CreatureSounds.java`; use `silent` for none).

### Riding

While you ride a creature, your crosshair passes through the creature you're sitting on, so you can place and break blocks and hit or shoot other creatures to start a battle without getting off.

### Flyers and swimmers in the wild

- **Flyers** (every creature that flies): 60% take to the air when they appear and stay airborne, cruising anywhere from 4 to 40 blocks above the ground (more often in the lower half). When a player is nearby they often swoop low around them, so you can still reach one with a melee weapon (or just shoot it) to start a battle. The other 40% stay on the ground.
- **Swimmers:** 80% stay beneath the surface. Bottom dwellers (Crab, Giant Octopus, Tortoise, Nautiloper, Whirlpool Leech, Reef Spitter, Revenant, Sahuagin, River Zorling) sink and crawl along the sea floor or river bed; every other swimmer swims around underwater. Ducks always paddle on the surface. The other 20% float at the surface.
- Tamed creatures, base workers, ridden or leashed creatures and creatures in battle move as usual. Creatures already in your world pick their way of moving the next time they're loaded.

### Villagers

Villagers in villages now spawn at **Level 5** with their profession already set, so recruiting one (by battle, like any creature) gets you that profession; in battle a villager shows it after its name, e.g. "Villager (Bard)". A villager keeps the profession it was born with: there's no changing it (villagers recruited before this update that were never trained can still be trained once). Each recruit starts on its first deed (toward Novice) from the moment you recruit it.

Professions are the creatures mod's (Farmer, Fisherman, Gardener, Weaver, Hunter, Librarian, Explorer, Smith, Netherwright, Carpenter, Engineer, Cook, Leatherworker, Mason, Jeweler, Voidwalker, Alchemist) plus three that work from your base. Assign them to a base as workers:

- **Bard:** every creature working at that base works 15% faster.
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

**A Master's End Crystal.** The first time you beat each influence's Creature Master it also gives you an **End Crystal** (only once per influence, so up to 12 in all; later wins and Grandmasters don't). Once you're back from the arena it stays a moment for a short cutscene, telling you what Masters hear about the crystals from the End and their unique power, and hands it over.

**Talking to a Creature Master.** Right-click one (sneak-click to skip) and a short cutscene plays: the camera leaves first person and glides to a close-up of whoever is speaking, the Master or you when you answer, with the words in a subtitle bar (click to continue). Each Master has five conversations in its own voice, and one is picked at random each time you talk to it; every conversation ends with your choice: challenge it (the battle starts) or walk away. The lines are in `addons/ahrivaea-battle/serantis_battle/master_dialogue.json`: each influence has a title and five conversations, each with its lines (`master` or `player`), the two choices (`challenge`, `decline`) and the Master's answer to each (`accept`, `farewell`). Hitting a Master with a weapon still starts the battle straight away.

Each Gym Master Stone raises your **mastery** of that influence, up to 5 stones per influence (after 5, beating that influence's Masters still pays double XP but no more stones). Every stone gives creatures of that influence +3% spawn weight around you, up to +15%: they turn up a little more often in the biomes they already live in, and the rarity odds don't change.

Gym Master Stones and the Eternita Stone are **trophies**: place one and it stands on the block as a gem on its pedestal, facing you (it glows faintly, and breaks back into the stone). The Normal stone is Wild Quartz, Fire the Ember Ruby, Frost the Rime Diamond, Storm the Storm Citrine, Earth the Stoneheart Topaz, Water the Tidal Aquamarine, Nature the Grove Emerald, Void the Void Amethyst, Fae the Fae Rose Heart, Darkness the Shadow Onyx, Arcane the Arcane Spellstone and Radiant the Sunstone; the Eternita Stone is the Prismatic Gem. The models are converted with `tools/convert_trophies.py`.

Collect all 5 stones of all 12 influences (60 in all) and you get the rainbow **Eternita Stone**: wild creatures that spawn around you are Mutated 5 times in 100 instead of 1 in 100. It also earns the achievement "I Won! I Won the Game!".

### Backpacks

A **Backpack** is a reward: the base's owner gets one every time one of their bases reaches Level 2. They can't be crafted. Right-click one to strap it on: it leaves your inventory and you wear it. Press **B** to open it: 36 more slots, as many as your inventory. Sneak and press **B** to take it off; it goes back in your inventory with everything still inside (each backpack keeps its own contents, so spares can be handed to friends). You can wear one at a time. A worn backpack is never lost, not even when you die. (The B key isn't rebindable yet.) A worn backpack shows on your back, for you and everyone around you.

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
- **Battle pacing:** creatures (and you) walk across the field tile by tile to where they're going, and what they do there (the hit, the numbers and flashes) shows once they arrive. Enemies take their turns one at a time with a short pause in between, and the colour flashes and shakes last a little longer, so the sequence is easy to follow.
- **Big creatures look their size:** in battle every creature is drawn a little bigger than the tiles it covers, so it spills over their edges: a one-tile creature is up to 1.6 blocks across (smaller ones keep their own size), a 2×2 creature 2.6 and a 3×3 creature 3.6. The summoned bosses (the End dragons, the Harbinger of Calamity and the False Hydra) are bigger still: 2.4 blocks wider than their square, so a 3×3 dragon is 4.4 blocks across. Only width and length are fitted, so tall creatures stay tall. It's only how they're drawn: movement, reach, terrain and arena size still go by tiles.
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

The 20 End dragons (Lindworm, Ddraig Goch, Hrimwyrm, Piasa, Fafnir, Gargouille, Mokele mbembe, Ouroboros, Fairy Dragon, Azi Dahaka, Ladon, Seraphim, Rendmaw, Arcanum, Bastion, Galewing, Fortune, Regalia, The First Dragon and The Last Dragon) only appear when the Ender Dragon is summoned in the End. The creatures mod still marks the vanilla dragon fight as won the moment the Ender Dragon appears, so the exit portal opens as before; the End dragon is there to battle and recruit. The list is in `addons/ahrivaea-end-dragons/serantis_end_dragons/dragons.txt`. **Who fights it.** When the dragon finishes rising, the players around it are sorted into parties: a group together, everyone else on their own. With one party there, the dragon is everyone's. With more, each other party gets its own dragon beside it, a random different kind (no two parties share a kind while there are kinds to go round), which only that party can see, be asked the riddle by, or fight. In a group, the first member to answer the riddle starts the battle and everyone in the group within 48 blocks is pulled into it, and each of them can recruit the dragon afterwards. **Summoning another one:** after the first, use the vanilla respawn ritual: place 4 End Crystals around the exit portal (one on each side of the bedrock fountain on the main island). When the ritual finishes, a random End dragon rises instead of the Ender Dragon, at Level 20. Do it as often as you like.

**Rising and the riddle.** The dragon doesn't just appear: the ground shakes and cracks open and it climbs up out of the earth over three seconds, amid End particles, finishing with a roar. Then it asks every player nearby a riddle, as a cutscene (the camera frames its head, its words in a subtitle bar). The answer is the influence or stat the dragon stands for: Ddraig Goch *Fire*, Hrimwyrm *Frost*, Piasa *Storm*, Fafnir *Earth*, Gargouille *Water*, Mokele mbembe *Nature*, Ouroboros *Void*, Fairy Dragon *Fae*, Azi Dahaka *Darkness*, Ladon *Arcane*, Seraphim *Radiant*, Rendmaw *Attack*, Bastion *Defense*, Galewing *Speed*, Arcanum *Magic*, Fortune *Luck*, Regalia *Charisma*, Lindworm *None*, The First Dragon *Forever* and The Last Dragon *Never*. You pick one of three answers (the right one and two other dragons' answers); a wrong one gets a "try again" from the dragon and you pick again, as often as it takes. The right answer starts the battle. Walking away (Escape) is fine: right-click or hit the dragon later and it asks again; once you've answered, you can fight it without answering again. The riddles and the dragon's lines are in `addons/ahrivaea-battle/serantis_battle/dragon_riddles.json`.

### About the Batch 3 bosses

The Harbinger of Calamity is summoned like a Warden (see below). **The False Hydra** is summoned exactly like the Wither: a T of four soul sand or soul soil blocks with three wither skeleton skulls on top (Death Knights in the Nether drop them, 4%, Mimics 5%, and Netherwright villagers sell them). The blocks vanish and the False Hydra (Legendary, Arcane, always Level 15-20) appears instead of the Wither, a wild creature you can battle and recruit. In a group, whoever starts the battle sends a join pop-up to the others within 48 blocks, the same as any battle.

## How to install

1. Go to the [Releases page](../../releases/latest) and download `Serantis-<version>.zip`.
2. Open your `.minecraft/mods` folder:
   - Windows: press `Win + R`, type `%appdata%\.minecraft\mods`, press Enter.
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
3. Unzip the download and copy every `.jar` from its `mods` folder into your `mods` folder (Fabric API is included). If you're updating, delete the old Ahrivaea, Serantis and Fabric API jars first.
4. Launch Minecraft 26.3 with your Fabric profile (Fabric Loader 0.19.5 or newer; install it from fabricmc.net if you haven't) and play.

Everyone playing together needs the same mods. A server gets the same jars in its own `mods` folder.

## License

All Ahrivaea mods are © jeffreystu10 and DaringDuck96, All Rights Reserved. Please don't re-upload them elsewhere without permission.

**Tunneler (pickaxe enchantment, I-III).** A new enchantment as common as Efficiency: it comes from the enchanting table, enchanted books, librarian trades and loot. Mining a block also mines the blocks around it in a 3x3 pane facing you: 1 deep at Tunneler I, 2 deep at II, and a full 3x3x3 cube at III, going away from you. Every extra block drops normally and costs durability. Blocks your pickaxe can't harvest, bedrock and the like, and blocks much harder than the one you mined (obsidian in a stone wall) are left alone. Sneak to mine just one block.

**Improving weapons at a Smith.** Right-click any Smith (in a village or working at your base) while holding a weapon to have it improved: +1 attack damage per improvement, up to +5 per weapon, in the world and in battle. Swords, axes, spears, maces, tridents, bows, crossbows and Wooden Staffs can be improved. Each step costs XP: the weapon's material price times the improvement you're buying (+1 costs 1x, +5 costs 5x). Prices per step: wooden and golden 10, stone 15, copper 20, bow 20, iron and crossbow 25, diamond 40, mace and trident 50, netherite 60. A netherite sword costs 60 + 120 + 180 + 240 + 300 = 900 XP to take to +5. Presence discounts apply. The weapon's tooltip shows "Improved +N". Without a weapon in hand, a Smith works as before.

**Goblin raids.** Raids come on the same nights as before (at dusk, 25% + 5% per base level, while you're near a base that has workers), but now 3-10 goblins come: Goblins, Treasure Goblins, Bugbears and Scrap Goblins in any mix, levelled for the base (base level 1: Level 1-5, 2: 5-9, 3: 9-13, 4: 13-17, 5: 16-20). They gather at the edge of your base, glowing, a horn sounds, and a 3-minute timer starts on a red bar at the top of the screen.

Click one of them (right-click, or hit it) to hear their terms:
- **Pay** them off and they leave. It costs a quarter of what losing would: 2.5% / 5% / 7.5% / 10% of your XP.
- **Fight** and the raid battle starts. When the timer runs out the battle starts anyway (right after any battle you're in, or as soon as you're back online).

The raid is **Easy** (3-4 raiders), **Medium** (5-6), **Hard** (7-8) or **Very Hard** (9-10). In the raid battle you fight with your party **and every creature working at that base with its Defense job on** (they fight with their own moves and temperaments; the real workers stay at the base). Winning gives the usual battle rewards and keeps your base safe. Losing, or fleeing, is the only way to lose XP in Serantia: 10% of your XP for an Easy raid, 20% Medium, 30% Hard, 40% Very Hard. Raiders no longer steal from chests. A raid is saved on its player: log out or restart while goblins are waiting and they're still there (with the time that was left, at least 30 seconds) when you're back near the base; quit or crash in the middle of the raid battle and it starts again as soon as you're back, so a raid can't be dodged by leaving.

**Only the scene.** During conversations and cutscenes the HUD is hidden (hotbar, chat, minimap, party summary, messages) along with name tags, so you only see the scene and the dialogue. Battles are kept clear the same way: no HUD (chat comes back while you type) and no name tags over the field, and the battle log under the turn banner shows just the latest line.

**The opening scene.** Before anything else, a new player wakes at spawn beside a **Mysterious Figure**. The camera pulls back to show the two of them, then a conversation plays out in the cutscene style: it asks whether you remember anything, you answer (two or three choices at each turn), it tells you this is Serantia, and asks about yourself (you choose your class) and who's with you (you choose your starter). It says Serantia needs you to help rebuild, a roar echoes in the distance, and it hands you the **Atlas of the Fallen Towers**: a book listing every tower within 10,000 blocks of spawn (nearest first, with X and Z coordinates and how far away each is). It tells you to rebuild them, starting with the nearest, and with "Find the towers. Save Serantia." it vanishes as the screen goes dark. When the light comes back you're alone, and the welcome guide opens. Players who already have a class, a creature or have read the guide never see it (they're given the Atlas on their next login instead).

**The Mysterious Figure returns.** At milestones in the early and middle game the Mysterious Figure walks up to you wherever you are (the camera watches from the side and you can't move), says a few words, lets you answer, and vanishes in a puff of smoke. Each visit happens once, at most one every 5 minutes, never during a battle:

| Visit | When | What it's about |
|---|---|---|
| "You're getting stronger." | 10 battles fought, won or lost (fleeing doesn't count; players from before this count their wins) | Ask who it is and it bows: a follower of the First, your guide to destiny; brush it off and it scoffs. Either way it tells you its name, and from then on it's the **Acolyte of the First** in every scene, in its trial and as a recruit |
| "Look at this place." | A base of yours reaches Level 2 | The Age of Ashes; look beyond your walls |
| "A Master's Stone." | Your first Gym Master Stone | The twelve Sovereigns; find the other Masters |
| "You found one." | Your first tower rebuilt | The towers were the First Dragon's watchposts; the roar |
| "So. You've found another realm." | Your first steps in the Nether | Serantia was never just one world |
| "Three towers standing again." | Three towers rebuilt | The Council is watching, and so is something older |
| "So. The Eternita Stone." | You earn the Eternita Stone | It doubts you're worthy and demands you prove it: **a battle** |

**The Eternita trial.** The last visit ends in a fight: your whole party (every creature in it, not just four) and you against the Mysterious Figure alone, with the stats of an average Legendary creature as if it were Level 25 (the level curve carried on past 20, about 13% above a Level 20 Legendary), and **six moves** (instead of three) drawn from every influence, the strongest of a dozen rolled sets. Win and it kneels and offers to follow you: recruit it from the battle results like any creature, and it joins as a standard Level 10 Legendary (the average Legendary's stats, golden apples have no effect on it) with six fresh moves from any influence, named "Mysterious Figure". Lose (or flee) and you keep the stone, but the Figure leaves and can't be recruited. It's a one-time fight, once it's decided: if the battle is cut short without a result (you log out, the game crashes), the trial plays again later.

**Every player gets every scene.** The opening scene, every Acolyte visit and both trials are tracked per player: each player sees each one when *they* reach its milestone, no matter who else on the server has already seen it, and several players can be in their own scenes at once. An Acolyte in someone's scene (and its smoke and sounds) is only visible to that player.

**The Acolyte of the Last.** The Acolyte of the First's counterpart, who serves the Last Dragon and Enrages creatures to spread chaos. He walks up to you three times, like the Acolyte of the First, and goes by the **Spooky Mysterious Figure** until his second visit is over:

| Visit | When | What happens |
|---|---|---|
| 1 | Your third meeting with the Acolyte of the First (the opening scene counts) | He warns you the Acolyte of the First is a liar and a fraud, and hints the creatures growing wilder is his doing |
| 2 | A base of yours reaches Level 3 | He tells you his name, that Serantia must stay in ruin to be reborn correctly, and threatens to destroy you if you keep rebuilding |
| 3 | 400 different creatures recruited (caught in the dex) | He challenges you: **a battle**, the same as the Eternita trial (your whole party against him, a Legendary as strong as Level 25, six moves from any influence) |

Win and you can recruit him (a standard Level 10 Legendary, like the Acolyte of the First), and you earn **Chaos Seeker**: 2% of the wild creatures that spawn near you turn up Enraged on top of the usual odds. Lose (or flee) and you still earn Chaos Seeker, at 1%, but he can't be recruited. He doesn't walk up like the Acolyte of the First: the world darkens (the Warden's Darkness effect), a heartbeat sounds, black smoke and souls gather a few steps away for two seconds, and he steps out of them. When he leaves he sinks back into black smoke and the dark lifts; before his fight it lingers a few seconds into the battle.

**The Acolytes' dex pages.** The Creature Dex counts the 434 forms you can find (the retired Arcane Dragon is left out of it), and the two Acolytes have bonus pages after them that don't count towards the total: seen once you've met them, caught once you've recruited them, so a finished dex can read 435/434 or 436/434.

The words live in `addons/ahrivaea-battle/serantis_battle/figure_scenes.json`, so they're easy to change or add to.

**Pacing (0.103).** The longest grinds were trimmed at the top end only, so the early game plays the same:
- **Achievements:** Blocks Placed, Seeds Planted and Items Smelted now top out at 150,000 (100 · 1k · 10k · 50k · 150k), Experience Gained at 250,000 (… 75k · 250k). Blocks Walked is now **Distance Traveled** (10k · 50k · 150k · 400k · 1M): walking, sprinting, sneaking, swimming, elytra, horses, boats, pigs, striders, minecarts and riding your creatures all count.
- **Your bases count:** every seed your workers plant counts towards your Seeds Planted, and every item a furnace at one of your bases finishes counts towards your Items Smelted (while you're online). Taking items out of a furnace still counts everywhere else.
- **Battle XP grows with the game:** 10 for a win, plus 4 + the creature's level for each one beaten (5 at Level 1, 24 at Level 20), and half again for Super Rare and Legendary creatures. Creature Masters still pay double. Before, every creature paid about 10 and it fell as your skill tree grew.
- **Skill tree:** each skill costs 10% more than the last (was 12.6%): the first few cost about the same, and the whole tree is about 145,000 XP instead of 450,000.

**Collecting makes rare creatures easier to find.** Every 100 *different* creatures you've recruited (your dex's caught count; duplicates don't count) shifts the spawn odds near you towards rarer creatures, up to 400:

| Different creatures recruited | Common | Uncommon | Rare | Super Rare | Legendary |
|---|---|---|---|---|---|
| 0-99 | 70% | 15% | 10% | 4% | 1% |
| 100 | 67% | 16% | 11% | 4.5% | 1.5% |
| 200 | 64% | 17% | 12% | 5% | 2% |
| 300 | 61% | 18% | 13% | 5.5% | 2.5% |
| 400+ | 58% | 19% | 14% | 6% | 3% |

A Noble at your base adds its bonus on top (-20 Common, +12 Rare, +6 Super Rare, +2 Legendary), so at 400 with a Noble it's 38 / 19 / 26 / 12 / 5. More than one Noble doesn't add more.

**Villagers talk before you fight them.** Right-click a villager (or hit it) and it has a word with you, in the same cutscene style as the Creature Masters: one of five conversations for its profession, picked at random each time, ending with your choice to fight it or walk away. Each profession's five cover its everyday life, what it would do at your base, a monster it's scared of, its favourite biome, and one piece of Serantia's lore. Every recruitable villager has them (Farmer, Fisherman, Hunter, Librarian, Explorer, Smith, Mason, Carpenter, Cook, Leatherworker, Gardener, Weaver, Engineer, Alchemist, Jeweler, Netherwright, Voidwalker, Bard, Innkeeper, Noble, Move Tutor, and villagers with no profession); Merchants, Town Guards and others you can't recruit don't. Until you're player level 5, the choice comes with a warning that villagers are tough (and town guards may join in) and are best fought from level 5. The words are in `addons/ahrivaea-battle/serantis_battle/villager_dialogue.json`.

**The Harbinger of Calamity.** The Warden is replaced by the Harbinger of Calamity (Legendary, Darkness, always Level 16-18), summoned the same way: sculk shriekers in the deep dark. It claws its way up out of the ground over three seconds amid cracking stone and sculk souls, and everyone within 48 blocks watches it in a cutscene: the camera takes in the whole beast as it rises, then closes in on its face as it roars: "Gwarrrrrgh!!! It's time for the Harbinger of Calamity to rule!" Battle it and it greets you with "You can't stop the chaos!!!" (on screen and in the battle log). It now sounds like the Warden, too.

**Influence armour.** Every influence except Normal has a full set (helmet, chestplate, leggings, boots), as strong as diamond armour, and named like its staff: Emberwrath (Fire), Rimespire (Frost), Thundercall (Storm), Stoneheart (Earth), Tidecaller (Water), Thornbloom (Nature), Riftwalker (Void), Moonpetal (Fae), Gloomreaver (Darkness), Runeweaver (Arcane) and Dawnbringer (Radiant). In battle, each piece you wear takes 6.25% off the damage you take from attacks of its influence (a full set: 25%). Each piece is crafted like its diamond version, but with **Orichalcum Ingots** in place of the diamonds, and two of those ingots swapped for: fire charges (Fire), blue ice (Frost), lightning rods (Storm), obsidian (Earth), prismarine crystals (Water), moss blocks (Nature), ender pearls (Void), glow berries (Fae), ink sacs (Darkness), amethyst shards (Arcane) or glowstone dust (Radiant). When worn, each set shows its own 3D design on your body (in your inventory the icons still look like diamond armour for now). The models are converted with `tools/convert_armor.py`, which cuts each full suit into the four pieces.

**Teleporting Boots.** Diamond boots + 3 ender pearls (shapeless). As strong as diamond boots; double-tap sneak while wearing them to teleport to the safest spot about 10 blocks the way you're facing: solid ground with room to stand, and no lava, fire, magma, cactus, berry bushes, powder snow, wither roses or dripstone. It looks up to 3 blocks up or down and one block to either side, and lands closer if there's nowhere safe at 10. Each teleport uses 1 durability and they recharge for 3 seconds. In battle they give you **Flight** instead: you move like a flying creature, over water, lava and obstacles. Worn, they show their own 3D design (as do the Winged Boots).

**Weapon enchantments count in battle** (for your weapon attack; staff spells aren't affected):

| Enchantment | In battle |
|---|---|
| Sharpness | +0.5 per level + 0.5 attack damage, as in Minecraft |
| Fire Aspect | +8% per level against anything but Fire creatures |
| Flame | +15% against anything but Fire creatures |
| Power | +12% per level |
| Breach | +8% per level |
| Density | +5% per level |
| Looting | +5% per level that each creature you beat drops its loot twice |

**Influence enchantments.** Two new families for swords, axes, maces, tridents, bows and crossbows, from the enchanting table, books, librarians and loot:

- **Banes** (I-III, one per weapon; about as common as Smite): +10% damage per level against creatures of one influence. Quenching (Fire), Thawing (Frost), Grounding (Storm), Shattering (Earth), Draining (Water), Withering (Nature), Anchoring (Void), Cold Iron (Fae), Dawnlight (Darkness), Nullifying (Arcane), Eclipse (Radiant), Wildstrike (Normal). Smite, Bane of Arthropods and Impaling no longer do anything in battle (they still work on mobs).
- **Infusions** (one level, one per weapon; about as rare as Fire Aspect): your weapon attack takes on an influence, with all its type matchups: super-effective against what that influence beats, resisted by what resists it. Emberbrand (Fire), Rimebite (Frost), Thunderstrike (Storm), Quakeborn (Earth), Tidecutter (Water), Thornlash (Nature), Voidrend (Void), Feytouch (Fae), Shadowbite (Darkness), Runeblade (Arcane), Sunforged (Radiant). The weapon's attack shows its influence in battle, e.g. "Diamond Sword (Fire)". Swap weapons mid-fight with Equip to pick the right one.

A weapon can carry one Bane and one Infusion together.

**The Journal.** A Journal button on the party screen opens four pages: **Next Steps** (hints for what to do now, from your first battles to the towers, the Masters, the Nether, the dex and the End, plus today's bounties), **Progress** (bars for the dex, Gym Master Stones, towers, base level and lore books), **Story** (every Acolyte scene you've seen, to read again) and **Lore** (the lore books you've found).

**Bounty boards.** Right-click any bell (every village has one; sneak to ring it) to see today's three contracts. Each player gets their own three each Minecraft day, and a new day brings new ones: beat 4-8 creatures of an influence, win 3-6 battles, recruit something Rare or rarer, bring the board some goods (iron, bread, wheat, coal, logs...), beat a Creature Master, or repel a goblin raid. Claim a finished one at any bell for XP (more as your skill tree grows), golden apples and **Orichalcum Ingots** (3 for an easy contract, 4 for a harder one, 5 for a Master or a raid; bounties are the only way to get them, and influence armour is made of them). There's a 20% chance of a saddle too, and a 25% chance of a lore book (otherwise a 12% chance, about 9% overall, of a Bane enchanted book).

**Grandmasters.** Creature Masters keep turning up after you've earned all 5 of their stones, as Grandmasters: their team fights as if Level 25, and beating one pays double XP, 3 golden apples and an enchanted book (an Infusion of its influence, or a Bane II-III).

**Helm of Linx.** Craft it from 5 Orichalcum Ingots around a Nether Star (in the helmet shape). It's as strong as a diamond helmet, and while you wear it you get four abilities on their own keys (Z, R, G and V to start with, so they don't clash with vanilla's toolbar keys on X and C. If you played an earlier version your old keys are kept; change them under Options > Controls > Key Binds > Serantis, where the backpack's B key now lives too):
- **Phase Jump (Z):** if there's a ceiling within 10 blocks above your head, you rise up through it and come out standing safely on top. It never goes through bedrock, and never puts you somewhere that would hurt you (lava, fire, cactus, a drop, water). Sneak + Z within a minute takes you back where you were.
- **Shape Lift (R):** R opens a menu (every time you start a new lift: once you set a shape down, the next R asks again; sneak + R also opens it): pick a sphere, cube, diamond, pillar or disc, 3, 5 or 7 blocks across. An outline shows what you're looking at; press R to lift it, carry it, and press R again to set it down. By default only natural blocks move (stone, dirt, sand, gravel, logs, leaves, ores, ice, netherrack and so on); turn **Placed blocks** on in the menu to move things you've built too. Either way, never water, lava, bedrock or blocks that hold items (chests, furnaces and the like). Anything you set down where a block already is simply breaks (no drops, so no free ores). The blocks of a battle being fought on the spot can't be lifted or landed on. Take the helm off or log out while carrying and the blocks fall back where they came from.
- **Waymark (H):** sneak + H marks where you're standing; H takes you back there from anywhere, even another dimension, once a minute.
- **High Jump (V):** turns 2-block jumps on or off. The longer falls that come with it don't hurt.

None of the four work in battle. Worn, the helm shows its own 3D design (in your inventory it still looks like a gold helmet for now).

## Co-op

**Groups.** Right-click another player for a menu: trade creatures, **invite them to your group**, or **challenge them to a spar**. A group is up to 4 players and is saved with the world. The **Co-op** button on the party screen shows your group (who's online, how far away, who's in a battle) and has **Leave group**.

**Joining battles.** When someone in your group starts a battle within 48 blocks of you, you get a pop-up asking if you'd like to join (or press **Join battle** on the Co-op page while it's going). You join on their side with yourself and up to 2 of your party creatures. To keep it a fight, the other side gets one more enemy for you and one for each creature you bring (so up to 3 per ally): the same kind of reinforcements the battle already had (the biome's creatures, Enraged and Mutated ones included; a Master's own team; more goblins in a raid), about as strong as the foes on the field. They can be recruited like any others (except a Master's team). Up to 3 allies can join a battle; an Acolyte's trial can't be joined. Battles with group members nearby get a bigger arena to fit everyone: 2 tiles wider with one possible ally, 3 with two or more (spars get 3 too).
- Everyone commands only their own units (your allies' show greyed out, with their name). **End turn** marks your units done; the enemies move once every player is done. If someone is waiting, the others have 90 seconds before their turn ends on its own.
- The battle is only lost when every player is knocked out.
- Allies who win get the same XP as the battle's owner, battle credit and bounty progress. Loot stays with the player whose battle it was, but **everyone who fought can recruit**: each player gets their own recruit list on the result screen and pays with their own XP, so one player recruiting a creature doesn't take it from the others (an End dragon fought together can join every player's party). **Flee** takes just you out of the battle. Your creatures keep the damage they took, like in your own battles.

**Shared bases.** Group members can use each other's bases as if they were their own: open the Base Core, change jobs and settings, upgrade it, see its quarries, train on its skill tree and use its Move Tutor. Only the owner can add or remove workers, since those creatures would change hands.

**Group contracts.** While you're in a group, the bounty board has a fourth row: one shared contract a day for the whole group (beat 12-20 creatures of an influence, win 8-12 battles, recruit 3 Rare or rarer creatures, or hand in a big delivery, where any amount from anyone helps). Every group gets the same contract that day (so re-forming a group can't re-roll it), and a co-op battle counts once. Everyone's progress counts, and when it's done **each** member claims it (once a day per player, even if you change groups): 1.5x the usual XP, 5 Orichalcum Ingots, 2 golden apples and the usual chance extras.

**Spars.** Challenge a player within 32 blocks to a friendly battle: you against them, each with your own party (they bring up to 4 creatures). The challenger moves first, then the other player; each turn has a 90-second limit. Knock out the other player (or all their units) to win. Losing a spar costs nothing: no armour wear, no lost XP, and everyone's creatures are left exactly as they were. Winning gives no XP, loot, recruits or Battles Won progress either, only the one-time **Bragging Rights** achievement (no reward, just the bragging).

**Recipes in the guide.** The guide (Guide button on the party screen) has a **Recipes** tab showing every craftable thing that isn't in vanilla Minecraft, as crafting grids and smithing rows read straight from the mods' recipe files: battle gear (Magic Staff, Winged Boots, Teleporting Boots, Helm of Linx), all the influence armour, Vessels and creature treats, horse feed, the Mining Post and Creature Dynamo, Stellar gear and dyed water. Cards with many versions (an armour set, the dye colours) cycle through them. Each card lists what it needs and what it does, and the page ends with the things you get other ways (Orichalcum, backpacks, cutting wood in the stonecutter, enchantments, honing, lore books).

**Battle feel.** Hits now land with a sound for the move's influence (fire crackles, frost shatters, storm cracks, earth thuds, water splashes...), plus a crunch on critical hits and a heavy thud on a knockout. Words pop up over the unit: **CRITICAL!**, **MISS**, **Super effective!**, **Not very effective** and a big **KO!**, on top of the damage numbers. The camera gives a short shake on a crit and dips toward the field on a knockout.

**Your first battle.** Until you've finished a battle, the battle screen shows a tip box that follows along: pick a unit, pick a tile, choose an action (moves, Rally, potions), pick a target, end your turn, watch the enemy turn, and what to do on the result screen. After the opening scene, a chat tip points you at your first fight.

**Item tooltips.** Every modpack item that doesn't already explain itself says what it's for when you hover over it: Gym Master Stones and the Eternita Stone, Type Mastery Trophies, staffs, the backpack, Winged Boots, Vessels, treats and feeds, Stellar gear, the Base Core, Mining Post and Creature Dynamo, creature drops and dyed water. (Orichalcum, the Helm of Linx, Teleporting Boots and influence armour already had their own lines; Orichalcum's now also says what it makes.) The tips live in `addons/ahrivaea-battle/serantis_battle/item_tips.json`.

**Workers that can't get somewhere.** A base worker that stops getting closer to its job gets itself through:
- **Jumps:** a step of 1 or 2 blocks with room on top, it jumps (up to twice a normal jump).
- **Digs:** otherwise it digs through natural blocks in its way (stone, dirt, sand, gravel, logs, leaves, ores and the like). It never touches anything a player placed, a block that holds items, crops or saplings, water, or anything harder than stone (obsidian, for example). It only digs inside its base, and the blocks drop as usual.
- **Hops:** after 15 seconds without getting there, it hops right next to the job.
- **Moves on:** if even that fails, it sets that job aside and does its other work, instead of trying the same unreachable spot forever. Every worker at that base skips it too, for 1 minute, then 2, 4 and so on up to about half an hour if it keeps failing. Anything it was carrying for that job (an item for a chest, a sapling to replant) is left at the Base Core.
- **Furnaces and brewing stands:** one that never finishes (something unsmeltable in it, or the output slot holding something else) is set aside the same way, so the other furnaces keep running.
- **Water and lava:** workers keep working and swim up instead of drifting off after their owner. Plants growing underwater aren't on the tending list.
- **Tall chest stacks:** reach is measured from its whole body, not just its feet, so it can use the top chest of a 3-high wall.
- **Nothing gets lost:** what a worker is carrying is saved with it, so a restart or unloaded chunk puts it down safely. A worker sent away mid-job drops its load.
- **Tidying up:** a stale copy of a worker left over from a respawn removes itself, and claims left behind by workers that are gone are cleared, so a quarry never stalls on one.
