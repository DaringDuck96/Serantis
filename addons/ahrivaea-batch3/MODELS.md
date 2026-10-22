# Models and textures for Batch 3

The creatures already work without art: until a model is added, each one shows as a plain body tinted in its influence's colour. Drop files into the paths below and push; the add-on jar is rebuilt automatically.

Blockbench `.bbmodel` packs (normal, `_enraged`, `_mutated`, `_enraged_mutated`) can be converted straight into these files with:

```
python3 tools/convert_bbmodels.py addons/ahrivaea-batch3 <influence-prefix>_<creature>_<size>_models.zip ...
```

It also sets each creature's size from its model.

For a creature with id `ID` and influence `INF`, the existing mod expects:

```
assets/ahrivaea/geo/creature/ID/INF.geo.json          Blockbench model (Bedrock / GeckoLib format)
assets/ahrivaea/geo/creature/ID/INF.animation.json    its animations
assets/ahrivaea/textures/entity/creature/ID/INF.png                   normal skin
assets/ahrivaea/textures/entity/creature/ID/INF_enraged.png           enraged skin
assets/ahrivaea/textures/entity/creature/ID/INF_mutated.png           mutated skin
assets/ahrivaea/textures/entity/creature/ID/INF_enraged_mutated.png   enraged + mutated skin
```

(Paths are inside this folder, `addons/ahrivaea-batch3/`.) Example: Ashback Devourer → `assets/ahrivaea/geo/creature/ashback_devourer/fire.geo.json`.

| Creature | ID | Influence |
|---|---|---|
| Abyssal Maw | `abyssal_maw` | `water` |
| Aether Beamer | `aether_beamer` | `arcane` |
| Ancient Spell Engine | `ancient_spell_engine` | `arcane` |
| Arcanum | `arcanum` | `none` |
| Ashback Devourer | `ashback_devourer` | `fire` |
| Azi Dahaka | `azi_dahaka` | `darkness` |
| Basalt Maw | `basalt_maw` | `earth` |
| Bastion | `bastion` | `none` |
| Blaze Dancer | `blaze_dancer` | `fire` |
| Bloom Eye Horror | `bloom_eye_horror` | `nature` |
| Bubble Stalker | `bubble_stalker` | `water` |
| Burrowcoil | `burrowcoil` | `earth` |
| Burrowing Horror | `burrowing_horror` | `earth` |
| Caldera Wyrm | `caldera_wyrm` | `fire` |
| Canopy Skullspider | `canopy_skullspider` | `nature` |
| Celestial Watcher | `celestial_watcher` | `radiant` |
| Cinder Shadow | `cinder_shadow` | `fire` |
| Cinderwing | `cinderwing` | `fire` |
| Coral Dancer | `coral_dancer` | `water` |
| Bloodfang Skreecher | `bloodfang_skreecher` | `none` |
| Crawling Mimic | `crawling_mimic` | `none` |
| Crowned Gleeok | `crowned_gleeok` | `radiant` |
| Dawn Magus | `dawn_magus` | `radiant` |
| Ddraig Goch | `ddraig_goch` | `fire` |
| Dimensional Duelist | `dimensional_duelist` | `void` |
| Dread Knight | `dread_knight` | `darkness` |
| Dreadwalker | `dreadwalker` | `darkness` |
| Dream Dancer | `dream_dancer` | `fae` |
| Drill Shark | `drill_shark` | `water` |
| Elderwood Guardian | `elderwood_guardian` | `nature` |
| Ember Magus | `ember_magus` | `fire` |
| Fafnir | `fafnir` | `earth` |
| Fairy Dragon | `fairy_dragon` | `fae` |
| False Hydra | `false_hydra` | `arcane` |
| Fortune | `fortune` | `none` |
| Galewing | `galewing` | `none` |
| Gargouille | `gargouille` | `water` |
| Gear Guzzler | `gear_guzzler` | `none` |
| Glacier Spitter | `glacier_spitter` | `frost` |
| Glimmer Sprite | `glimmer_sprite` | `fae` |
| Gloom Shadow | `gloom_shadow` | `darkness` |
| Gloomwing | `gloomwing` | `darkness` |
| Glyph Guardian | `glyph_guardian` | `arcane` |
| Grave Lurker | `grave_lurker` | `darkness` |
| Gravewrap | `gravewrap` | `darkness` |
| Harbinger of Calamity | `harbinger_of_calamity` | `darkness` |
| Hollow Grunt | `hollow_grunt` | `none` |
| Hollow Phantom | `hollow_phantom` | `void` |
| Hrimwyrm | `hrimwyrm` | `frost` |
| Ironclad Ravager | `ironclad_ravager` | `none` |
| Ironjaw Brute | `ironjaw_brute` | `none` |
| Ladon | `ladon` | `arcane` |
| Lantern Trickster | `lantern_trickster` | `fae` |
| Lindworm | `lindworm` | `none` |
| Lumina Wyrm | `lumina_wyrm` | `radiant` |
| Magma Bulwark | `magma_bulwark` | `fire` |
| Magma Crawler | `magma_crawler` | `fire` |
| Masked Puck | `masked_puck` | `fae` |
| Mokele mbembe | `mokele_mbembe` | `nature` |
| Moonlit Stag | `moonlit_stag` | `fae` |
| Moonwing | `moonwing` | `fae` |
| Nightmare Stalker | `nightmare_stalker` | `darkness` |
| One Eye Colossus | `one_eye_colossus` | `none` |
| One-Eyed Devourer | `one_eyed_devourer` | `none` |
| Oreback Titan | `oreback_titan` | `earth` |
| Ouroboros | `ouroboros` | `void` |
| Pale Duelist | `pale_duelist` | `none` |
| Pebble Spitter | `pebble_spitter` | `none` |
| Phantom Jester | `phantom_jester` | `fae` |
| Piasa | `piasa` | `storm` |
| Prism Sentinel | `prism_sentinel` | `earth` |
| Quake Brute | `quake_brute` | `earth` |
| Reef Spitter | `reef_spitter` | `water` |
| Regalia | `regalia` | `none` |
| Rendmaw | `rendmaw` | `none` |
| Rift Clutch | `rift_clutch` | `void` |
| Rift Shadow | `rift_shadow` | `void` |
| Rift Wraith | `rift_wraith` | `void` |
| Rime Dancer | `rime_dancer` | `frost` |
| Rime Magus | `rime_magus` | `frost` |
| Rimewing | `rimewing` | `frost` |
| River Zorling | `river_zorling` | `water` |
| Ruin Sentinel | `ruin_sentinel` | `earth` |
| Rune Crawler | `rune_crawler` | `arcane` |
| Rune Magus | `rune_magus` | `arcane` |
| Runic Colossus | `runic_colossus` | `arcane` |
| Sanctum Sentinel | `sanctum_sentinel` | `radiant` |
| Scrap Goblin | `scrap_goblin` | `none` |
| Seraphim | `seraphim` | `radiant` |
| Shardheart | `shardheart` | `frost` |
| Snapvine | `snapvine` | `nature` |
| Snowfang | `snowfang` | `frost` |
| Stone Shadow | `stone_shadow` | `earth` |
| Stormblade Phantom | `stormblade_phantom` | `storm` |
| Stormjaw Leviathan | `stormjaw_leviathan` | `storm` |
| Sunlit Dancer | `sunlit_dancer` | `radiant` |
| Sunwatcher | `sunwatcher` | `radiant` |
| Tempest Flyer | `tempest_flyer` | `storm` |
| Tempest Roc | `tempest_roc` | `storm` |
| The First Dragon | `the_first_dragon` | `none` |
| The Last Dragon | `the_last_dragon` | `none` |
| Thorn Shadow | `thorn_shadow` | `nature` |
| Thorncoil | `thorncoil` | `nature` |
| Thunder Magus | `thunder_magus` | `storm` |
| Tide Shadow | `tide_shadow` | `water` |
| Tidecore Horror | `tidecore_horror` | `water` |
| Umbral Revenant | `umbral_revenant` | `darkness` |
| Void Hand | `void_hand` | `void` |
| Voltwing | `voltwing` | `storm` |
| Whirlpool Leech | `whirlpool_leech` | `water` |
