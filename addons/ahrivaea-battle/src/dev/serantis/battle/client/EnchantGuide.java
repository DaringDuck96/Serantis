package dev.serantis.battle.client;

/**
 * The "Enchants" tab of the guide: every enchantment in the game, what it does in the world and what it does in
 * creature battles. {heading, paragraph}; null heading = plain paragraph. Keep in step with WeaponEnchants,
 * Equipment, Tunneler and the enchantment files in data/serantis_battle/enchantment.
 */
final class EnchantGuide {
    private EnchantGuide() {}

    static final String[][] TEXT = {
            {null, "Every enchantment works in the world just as it does in Minecraft. In creature battles only the enchantments on "
                    + "the weapon in your main hand count, and only for your weapon attack (staff spells don't use them). Armour "
                    + "enchantments never count in battle. Enchanting and anvils cost XP, the same XP you spend on everything else. "
                    + "The Arcana skill Attuned makes anvils 25% cheaper."},

            {"How to read this", "Each entry gives the levels it comes in, what it goes on, what it does in the world, and then "
                    + "\"In battle:\" what it does in creature battles, if anything."},

            // ---------------------------------------------------------------- Serantis
            {"SERANTIS ENCHANTMENTS", "Enchantments added by Serantis. You find them at the enchanting table, in loot and on "
                    + "enchanted books, just like Minecraft's own."},
            {"Banes (I-III, weapons)", "One for each influence. In battle: your weapon attack deals +10% damage per level to creatures "
                    + "of that influence (+30% at III). A weapon can only carry one Bane. Wildstrike (Normal), Quenching (Fire), "
                    + "Thawing (Frost), Grounding (Storm), Shattering (Earth), Draining (Water), Withering (Nature), Anchoring (Void), "
                    + "Cold Iron (Fae), Dawnlight (Darkness), Nullifying (Arcane), Eclipse (Radiant)."},
            {"Infusions (I, weapons)", "One for each influence except Normal. In battle: your weapon attack takes on that influence, "
                    + "with its matchups. It deals double damage to the influences it's strong against and half damage to the ones "
                    + "that resist it (including its own). A weapon can only carry one Infusion, and the battle screen shows it. "
                    + "Emberbrand (Fire: strong against Nature, Frost), Rimebite (Frost: Storm, Nature), Thunderstrike (Storm: Water, "
                    + "Frost), Quakeborn (Earth: Fire, Storm), Tidecutter (Water: Fire, Earth), Thornlash (Nature: Water, Earth), "
                    + "Voidrend (Void: Radiant, Arcane), Feytouch (Fae: Void, Darkness), Shadowbite (Darkness: Arcane, Void), "
                    + "Runeblade (Arcane: Fae, Radiant), Sunforged (Radiant: Darkness, Fae)."},
            {"Tunneler (I-III, pickaxes)", "Mining a block also mines a 3x3 pane around it facing you: 1 deep at I, 2 deep at II and "
                    + "a 3x3x3 cube at III. Every block drops as normal and costs durability. Blocks your pickaxe can't harvest, "
                    + "unbreakable blocks and blocks much harder than the one you mined (obsidian in a stone wall) are left alone. "
                    + "Sneak to mine a single block. In battle: nothing."},

            // ---------------------------------------------------------------- weapons
            {"MELEE WEAPONS", null},
            {"Sharpness (I-V, swords, axes, spears)", "Extra melee damage: +0.5 per level, plus 0.5. In battle: the same extra damage "
                    + "is added to your weapon's attack, so your weapon attack gets stronger."},
            {"Smite (I-V, swords, axes, maces, spears)", "+2.5 damage per level against undead mobs. Doesn't stack with Sharpness or "
                    + "Bane of Arthropods. In battle: nothing."},
            {"Bane of Arthropods (I-V, swords, axes, maces, spears)", "+2.5 damage per level against spiders, bees, silverfish and "
                    + "endermites, and slows them. In battle: nothing."},
            {"Fire Aspect (I-II, swords, maces, spears)", "Sets what you hit on fire. In battle: +8% damage per level against every "
                    + "creature except Fire creatures."},
            {"Knockback (I-II, swords, spears)", "Hits knock targets back further. In battle: nothing."},
            {"Sweeping Edge (I-III, swords)", "Sweep attacks deal more damage to the mobs around your target. In battle: nothing (an "
                    + "axe is what hits several creatures at once)."},
            {"Looting (I-III, swords, spears)", "Mobs drop more loot, and rarer loot more often. In battle: +5% per level "
                    + "chance that each creature you beat drops its loot twice."},
            {"Lunge (I-III, spears)", "A spear's jab dashes you forward, further with each level, at the cost of some hunger. In "
                    + "battle: nothing."},

            {"MACES", null},
            {"Density (I-V)", "A falling smash deals more damage for every block you fell. In battle: +5% damage per level."},
            {"Breach (I-IV)", "Your hits ignore 15% of the target's armour per level. In battle: +8% damage per level."},
            {"Wind Burst (I-III)", "A smash launches you back up into the air, higher with each level. In battle: nothing."},

            {"BOWS", null},
            {"Power (I-V)", "Arrows deal more damage. In battle: +12% damage per level (+60% at V)."},
            {"Punch (I-II)", "Arrows knock targets back further. In battle: nothing."},
            {"Flame (I)", "Arrows set what they hit on fire. In battle: +15% damage against every creature except Fire creatures."},
            {"Infinity (I)", "Shooting doesn't use up normal arrows (you still need one). Doesn't go with Mending. In battle: nothing."},

            {"CROSSBOWS", null},
            {"Quick Charge (I-III)", "Loads faster. In battle: nothing (a crossbow still has to reload for a turn after it fires)."},
            {"Multishot (I)", "Fires three arrows at once. Doesn't go with Piercing. In battle: nothing."},
            {"Piercing (I-IV)", "Arrows pass through up to one more mob per level. In battle: nothing."},

            {"TRIDENTS", null},
            {"Impaling (I-V)", "+2.5 damage per level against aquatic mobs. In battle: nothing (a trident already deals +40% to "
                    + "Water creatures, and +20% in the rain)."},
            {"Loyalty (I-III)", "A thrown trident comes back to you, faster with each level. Doesn't go with Riptide. In battle: nothing."},
            {"Riptide (I-III)", "Throwing the trident in water or rain launches you instead. Doesn't go with Loyalty or Channeling. "
                    + "In battle: nothing."},
            {"Channeling (I)", "A trident thrown at a mob during a thunderstorm calls down lightning. In battle: nothing."},

            // ---------------------------------------------------------------- tools
            {"TOOLS", null},
            {"Efficiency (I-V, tools)", "Mines faster. In battle: nothing."},
            {"Fortune (I-III, pickaxes, shovels, axes, hoes)", "Ores and some crops and plants drop more. Doesn't go with Silk Touch. "
                    + "In battle: nothing."},
            {"Silk Touch (I, tools)", "Blocks drop themselves: stone instead of cobblestone, the ore block instead of what it holds. In battle: "
                    + "nothing. (Level 20 miners at your base dig with Silk Touch on their own; you can switch it off on the base screen.)"},
            {"Luck of the Sea (I-III, fishing rods)", "Better odds of treasure when fishing. In battle: nothing."},
            {"Lure (I-III, fishing rods)", "Fish bite sooner. In battle: nothing."},

            // ---------------------------------------------------------------- armour
            {"ARMOUR", "None of these count in creature battles."},
            {"Protection (I-IV, any armour)", "Less damage from almost everything."},
            {"Fire Protection (I-IV, any armour)", "Less fire and lava damage, and you burn for less time."},
            {"Blast Protection (I-IV, any armour)", "Less explosion damage and knockback."},
            {"Projectile Protection (I-IV, any armour)", "Less damage from arrows and other projectiles. (Only one Protection of any "
                    + "kind per piece.)"},
            {"Thorns (I-III, any armour)", "Hurts whatever hits you, at the cost of extra durability."},
            {"Respiration (I-III, helmets)", "Breathe longer underwater and see better there."},
            {"Aqua Affinity (I, helmets)", "Mine at normal speed underwater."},
            {"Feather Falling (I-IV, boots)", "Less fall damage."},
            {"Depth Strider (I-III, boots)", "Walk faster underwater. Doesn't go with Frost Walker."},
            {"Frost Walker (I-II, boots)", "Freezes water under you into ice as you walk, and you don't take damage from magma "
                    + "blocks. Treasure only."},
            {"Soul Speed (I-III, boots)", "Faster on soul sand and soul soil. Treasure only."},
            {"Swift Sneak (I-III, leggings)", "Faster while sneaking. Treasure only (Ancient Cities)."},

            // ---------------------------------------------------------------- everything
            {"FOR ANYTHING", null},
            {"Unbreaking (I-III)", "Each use has a chance of not costing durability. Armour gets less of it than tools. In battle: "
                    + "nothing. (Being beaten in battle still costs your gear half its durability.)"},
            {"Mending (I)", "XP orbs you pick up repair the item instead of going to your XP. Treasure only. In battle: nothing."},
            {"Curse of Binding (I)", "Once worn, it can't be taken off until you die (or it breaks). Treasure only."},
            {"Curse of Vanishing (I)", "The item disappears when you die instead of dropping. Treasure only."},
    };
}
