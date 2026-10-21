package dev.serantis.battle.client;

/** The "Your Base" tab of the guide: how bases and their workers work. {heading, paragraph}; null heading = plain paragraph. */
final class BaseGuide {
    private BaseGuide() {}

    static final String[][] TEXT = {
            {null, "Your creatures can live and work at a base. Each one has jobs it's good at (the base screen lists them), "
                    + "and once it's working it just gets on with them, day and night, while you're off adventuring."},

            {"Setting up", "Place a Base Core and it's yours: creatures you put to work there look after the chunk it's in and the chunks "
                    + "around it, from 8 blocks below the core to 16 above it. Anything outside that band (crops, trees, chests, furnaces) "
                    + "is invisible to them. The base stays loaded while you're online, wherever you are."},
            {"Putting creatures to work", "Right-click your core and press Work here on a creature from your party or box. It appears "
                    + "beside the core and starts working. Send away brings it back to your party (or box). Always send your workers away "
                    + "before getting rid of a base: you can't break a core that still has workers, and workers whose base is gone stop "
                    + "working for good."},
            {"Base levels", "Upgrade the core by using blocks on it: 4 Iron Blocks for Level 2, 4 Gold Blocks for Level 3, 2 Diamond "
                    + "Blocks for Level 4 and 4 Emerald Blocks for Level 5. Each level gives more workers (5, 7, 9, 12, then 15) and a "
                    + "bigger area: 3x3 chunks at Level 1, growing to 11x11 at Level 5. Your skill tree also opens at Level 1."},

            {"How a worker decides what to do", "It works through its jobs in order and does the first one that has something to do. "
                    + "Use the arrows on the base screen to reorder its jobs, and switch off any you don't want. It always picks the "
                    + "nearest thing that needs doing (Growth picks at random), and two workers never go for the same crop, tree or "
                    + "furnace. If it's carrying something, it puts that away first. With nothing to do it waits, now and then wandering "
                    + "around near the core. Its status on the base screen tells you what it's up to, or why it's idle."},
            {"Work speed", "A Level 1 creature works at 50% speed, and it gets faster as it levels up, the more so the higher its "
                    + "Charisma: at Level 20 a typical creature works at about 150%, the most charming over 200%. A Bard working at the "
                    + "base makes everyone there 25% faster. Speed shortens every task, and furnaces and brewing stands it tends run at "
                    + "its speed. Walking pace doesn't change."},

            {"Farming", "Harvests ripe crops (wheat, carrots, potatoes, beetroot, nether wart, berries, cocoa, melons and pumpkins, sugar "
                    + "cane, cactus and bamboo) and replants them from the harvest, or with seeds from your chests. Cane, cactus and "
                    + "bamboo are cut down to the bottom block; for melons and pumpkins only the fruit is taken. The harvest drops on "
                    + "the ground."},
            {"Growth", "Tends plants, crops and saplings so they grow faster. It uses no bone meal."},
            {"Logging", "Fells grown trees in the base, logs first, then their leaves, and replants a sapling from the ground nearby "
                    + "or from your chests. A tree only counts if it has natural leaves, so trees and log builds you made yourself are "
                    + "left alone (keep builds away from wild trees to be safe)."},
            {"Mining", "Needs a Mining Post with a chest or barrel touching it. It digs out the whole chunk under the post, down to "
                    + "bedrock: first a staircase down the edge, then layer by layer, leaving a wall around the hole. Everything goes "
                    + "straight into the post's chests; when they're full it waits for another chest. It never digs blocks players "
                    + "placed, strongholds, spawners or portals (other structures only if you allow it on the post). Level 20 workers "
                    + "can use Silk Touch: stone, ores and glowstone come out whole."},
            {"Smelting", "Keeps your furnaces, smokers and blast furnaces burning with no fuel while it stands at them. You still put "
                    + "in what to smelt; the results stay in the furnace for a Transport worker."},
            {"Transport", "Your base's tidy-upper: picks up loose items (after they've been on the ground a couple of seconds, even "
                    + "ones you drop), empties furnace output, and carries it all to your chests. Without a Transport worker, harvests "
                    + "and drops just pile up on the ground."},
            {"Ranching", "Every couple of minutes it produces some of its own drops, at its feet. This runs alongside its other jobs."},
            {"Fishing", "Catches fish and treasure from water in the base (a water source with open air above it), now and then."},
            {"Ice", "Makes ice and snowballs from water in the base, and every so often packed or blue ice."},
            {"Brewing", "Keeps brewing stands with a recipe loaded going with no blaze powder. The potions stay in the stand."},
            {"Enchanting", "Studies at an enchanting table: a Bottle o' Enchanting every few sessions, and now and then an enchanted "
                    + "book. They go into a chest touching the table."},
            {"Redstone", "Stands at a Creature Dynamo and powers it (a full redstone signal) while it's there, turning up a little "
                    + "redstone dust into a chest touching the dynamo."},
            {"Crafting", "Works the base's crafters, crafting whatever each one is set up for."},
            {"Lighting", "Takes torches from your chests and puts them on dark spots on the open ground. It doesn't light caves or "
                    + "under roofs."},
            {"Defense", "Fights off raids on the base (see below). It doesn't fight ordinary monsters."},

            {"Chests and sorting", "Workers use chests and barrels in the base (not shulker boxes, hoppers, or the chests by a Mining "
                    + "Post). Put an item in an item frame on a chest to label it: that chest only takes that item or things like it "
                    + "(logs, ores, seeds, foods, and so on), and Transport workers move stacks into the best-labelled chest. Unlabelled "
                    + "chests are used for anything, preferring one that already holds the same item. If every chest is full, items are "
                    + "dropped."},
            {"Raids", "Some nights, if you're at your base, raiders come for it: the bigger the base, the more often and the more of "
                    + "them. They glow, attack your workers and steal from your chests near the core. Defense workers drop what "
                    + "they're doing and fight them; the raid ends when they're beaten, at daybreak, or after five minutes."},
            {"Knocked out, never lost", "Workers can only be hurt by raiders. One that would fall is knocked out instead: it rests for "
                    + "a minute, healing as it goes, then gets back to work. Your creatures are never lost unless you release them."},

            {"Villagers at your base", "Recruited villagers work too, and some professions help the whole base: a Bard makes every "
                    + "worker 25% faster, an Innkeeper refills your hearts when you sleep through the night, a Noble makes creatures "
                    + "spawning in and around the base more often Rare or rarer, and a Move Tutor teaches your creatures new moves "
                    + "(right-click it)."},
            {null, "Tip: check the base screen now and then. Each worker's status says what it's doing, or exactly what it's waiting "
                    + "for (a chest by the Mining Post, something to smelt, a torch, a job switched on)."}};
}
